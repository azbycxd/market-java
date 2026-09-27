# C3 agent-dev isolated refund environment

Start the application with both profiles:

```powershell
--spring.profiles.active=dev,agent-dev
```

`agent-dev` never points to `group_buy_market`. It uses MySQL database
`group_buy_market_agent_test`, a dedicated Redis container at `127.0.0.1:16380`, and RabbitMQ
vhost `agent-dev`. Both the business Redis client and the dynamic-configuration client use that
dedicated Redis instance.
Both write-capable scheduled jobs are disabled only in this profile.

Reset the isolated state from the repository root:

```powershell
$env:AGENT_DEV_MYSQL_PASSWORD = '<local MySQL password>'
.\scripts\agent-dev\reset-agent-dev.ps1
```

The reset imports the versioned V3 schema, removes its sample rows, inserts only the C3 seed,
starts/flushes the dedicated Redis instance, and purges queues in the `agent-dev` RabbitMQ vhost. It refuses to target
any database other than `group_buy_market_agent_test`.

| Case | userId | outTradeNo | teamId | activityId |
| --- | --- | --- | --- | --- |
| UNPAID | `agent_c3_unpaid` | `930000000001` | `91000001` | `900001` |
| PAID_UNFORMED | `agent_c3_paid_unformed` | `930000000002` | `91000002` | `900001` |
| PAID_FORMED | `agent_c3_paid_formed` | `930000000003` | `91000003` | `900001` |
| CLOSED | `agent_c3_closed` | `930000000004` | `91000004` | `900001` |
