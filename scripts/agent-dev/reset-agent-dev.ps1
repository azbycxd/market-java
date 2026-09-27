[CmdletBinding()]
param(
    [string]$MysqlHost = '127.0.0.1',
    [int]$MysqlPort = 13306,
    [string]$MysqlUser = 'root',
    [string]$RabbitContainer = 'rabbitmq',
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 16380
)

$ErrorActionPreference = 'Stop'
$targetDatabase = 'group_buy_market_agent_test'
$rabbitVhost = 'agent-dev'
$redisDatabase = 0
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$baseSchema = Join-Path $repositoryRoot 'docs\tag\v3.0\mysql\sql\group_buy_market.sql'
$clearData = Join-Path $PSScriptRoot 'c3-clear-data.sql'
$seedData = Join-Path $PSScriptRoot 'c3-seed.sql'
$redisCompose = Join-Path $PSScriptRoot 'docker-compose-agent-dev.yml'

if ([string]::IsNullOrWhiteSpace($env:AGENT_DEV_MYSQL_PASSWORD)) {
    throw 'Set AGENT_DEV_MYSQL_PASSWORD before running this reset script.'
}
foreach ($requiredPath in @($baseSchema, $clearData, $seedData, $redisCompose)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required agent-dev resource is missing: $requiredPath"
    }
}

$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($mysql)) {
    $mysql = Join-Path ${env:ProgramFiles} 'MySQL\MySQL Server 8.0\bin\mysql.exe'
}
if (-not (Test-Path -LiteralPath $mysql)) {
    throw 'MySQL client was not found. Install it or add mysql to PATH.'
}

$redisCli = (Get-Command redis-cli -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($redisCli)) {
    $redisCli = 'D:\soft\redis\Redis5\redis-cli.exe'
}
if (-not (Test-Path -LiteralPath $redisCli)) {
    throw 'redis-cli was not found. Install it or add redis-cli to PATH.'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required to reset only the agent-dev RabbitMQ vhost.'
}

& docker compose -f $redisCompose up -d
if ($LASTEXITCODE -ne 0) {
    throw "Unable to start the isolated agent-dev Redis container (exit code $LASTEXITCODE)."
}

function Invoke-Mysql([string[]]$Arguments) {
    & $mysql --protocol=tcp -h $MysqlHost -P $MysqlPort -u $MysqlUser @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed with exit code $LASTEXITCODE."
    }
}

function Invoke-Rabbit([string[]]$Arguments) {
    & docker exec $RabbitContainer rabbitmqctl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "RabbitMQ command failed with exit code $LASTEXITCODE."
    }
}

$oldMysqlPassword = $env:MYSQL_PWD
$schemaTempFile = $null
try {
    $env:MYSQL_PWD = $env:AGENT_DEV_MYSQL_PASSWORD

    # This is intentionally the only database destruction in the script; the name is a fixed
    # agent-dev test database and cannot be supplied as a parameter.
    Invoke-Mysql @('-e', ("DROP DATABASE IF EXISTS ``" + $targetDatabase + "``"))

    $schema = Get-Content -LiteralPath $baseSchema -Raw
    $schema = $schema.Replace('`group_buy_market`', '`group_buy_market_agent_test`')
    $schemaTempFile = Join-Path ([System.IO.Path]::GetTempPath()) ('group-buy-market-agent-dev-' + [guid]::NewGuid().ToString('N') + '.sql')
    [System.IO.File]::WriteAllText($schemaTempFile, $schema, (New-Object System.Text.UTF8Encoding($false)))
    Invoke-Mysql @('-e', ("source " + $schemaTempFile.Replace('\', '/')))
    Invoke-Mysql @('-e', ("source " + $clearData.Replace('\', '/')))
    Invoke-Mysql @('-e', ("source " + $seedData.Replace('\', '/')))

    & $redisCli -h $RedisHost -p $RedisPort -n $redisDatabase FLUSHDB
    if ($LASTEXITCODE -ne 0) {
        throw "Redis DB $redisDatabase reset failed with exit code $LASTEXITCODE."
    }

    $isRabbitRunning = (& docker inspect -f '{{.State.Running}}' $RabbitContainer).Trim()
    if ($LASTEXITCODE -ne 0 -or $isRabbitRunning -ne 'true') {
        throw "RabbitMQ container '$RabbitContainer' is not running."
    }
    $vhosts = @(Invoke-Rabbit @('-q', 'list_vhosts'))
    if ($vhosts -notcontains $rabbitVhost) {
        Invoke-Rabbit @('add_vhost', $rabbitVhost)
    }
    Invoke-Rabbit @('set_permissions', '-p', $rabbitVhost, 'admin', '.*', '.*', '.*')
    # rabbitmqctl versions differ on whether a column heading is emitted in quiet mode. Only
    # agent-dev's explicit queue namespace is eligible for purging; headings and other vhost
    # output can therefore never become queue names.
    $queues = @(Invoke-Rabbit @('-q', 'list_queues', '-p', $rabbitVhost, 'name') |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -like 'agent_dev_group_buy_market_queue_*' })
    foreach ($queue in $queues) {
        if (-not [string]::IsNullOrWhiteSpace($queue)) {
            Invoke-Rabbit @('purge_queue', '-p', $rabbitVhost, $queue)
        }
    }

    Write-Host "agent-dev reset complete: mysql=$targetDatabase redis=127.0.0.1:$RedisPort/db$redisDatabase rabbit-vhost=$rabbitVhost"
} finally {
    if ($null -ne $schemaTempFile -and (Test-Path -LiteralPath $schemaTempFile)) {
        Remove-Item -LiteralPath $schemaTempFile -Force
    }
    if ($null -eq $oldMysqlPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldMysqlPassword
    }
}
