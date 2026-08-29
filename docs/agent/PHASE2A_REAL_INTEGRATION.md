# Phase 2A.1：Order Facts 真实 MySQL + HTTP 联调报告

验收时间：2026-08-25 20:56（Asia/Shanghai）  
范围：仅 `D:\workspace\java\group-buy-market-jiusi` 的 Phase 2A Order Facts API；未进入 Phase 2B，未操作 `group-buy-agent`。

## A. Spring Boot 是否成功启动

**成功。** 使用项目打包产物 `group-buy-market-app/target/group-buy-market-app.jar`，以 JDK 8 和真实 `dev` profile 启动：

```text
D:\soft\java\jdk-8u501\bin\java.exe -Dspring.profiles.active=dev -jar group-buy-market-app.jar
```

应用日志确认：`Tomcat started on port(s): 8091`，`Application - Started Application`。端口来自项目 `application.yml` / `application-dev.yml` 的实际配置，未猜测或改写端口。测试结束后已停止本次启动的 PID 44448；8091 已不再监听。

`dev` profile 内 `agent.facts.dev-header-auth.enabled=true`，因此本次测试使用的 `DevHeaderAuthenticatedUserProvider` 可读取 `X-Dev-Authenticated-User-Id`。该 Provider 同时要求 profile 为 `dev` 或 `test`；生产 profile 不会默认信任该 Header。

## B. 实际连接的 MySQL 数据库

项目 `application-dev.yml` 的真实数据源为：

```text
jdbc:mysql://127.0.0.1:13306/group_buy_market
username=root
```

运行日志显示 Hikari 连接池 `Retail_HikariCP` 已启动。本报告中的三表核验通过 MySQL TCP `127.0.0.1:13306/group_buy_market` 完成，并非 Mock Repository。

Redis（`localhost:16379`）和 RabbitMQ（`localhost:5672`）也按项目既有 dev 配置建立了连接；未为本次联调修改任何核心业务配置或逻辑。

## C. 三张表真实数据是否存在

测试前和测试后均执行下列只读核验，结果相同：

| 表 | 查询键 | 实际结果 |
| --- | --- | --- |
| `group_buy_order_list` | `user_id=xfg05`, `out_trade_no=644398015396` | `team_id=18781389`, `activity_id=100123`, `status=2` |
| `group_buy_order` | `team_id=18781389` | `activity_id=100123`, `status=0`, `target_count=3`, `lock_count=0`, `complete_count=0`, `valid_end_time=2026-07-30 03:45:37` |
| `group_buy_activity` | `activity_id=100123` | `status=1` |

因此正向联调的三层关联均真实存在：`xfg05 + 644398015396 -> 18781389 -> 100123`。

## D. 实际 HTTP 请求

所有请求均发往实际启动的本地服务：`POST http://127.0.0.1:8091/api/v1/agent/order/facts`，`Content-Type: application/json`。

正向请求：

```http
X-Dev-Authenticated-User-Id: xfg05

{"outTradeNo":"644398015396"}
```

越权请求仅将 Header 改为 `X-Dev-Authenticated-User-Id: xfg03`。无认证请求不携带该 Header。非法参数请求携带 `xfg05` Header 和 `{"outTradeNo":""}`。

## E. 实际 HTTP Response

正向实际响应（HTTP 200）：

```json
{"code":"0000","info":"成功","data":{"order":{"status":"CLOSE"},"team":{"status":"PROGRESS","targetCount":3,"lockCount":0,"completeCount":0,"validEndTime":"2026-07-30T03:45:37+08:00"},"activity":{"status":"EFFECTIVE"},"references":{"teamId":"18781389","activityId":100123}}}
```

越权实际响应（HTTP 200）：

```json
{"code":"ORDER_NOT_FOUND_OR_NOT_AUTHORIZED","info":"订单不存在或无权访问","data":null}
```

无认证实际响应（HTTP 200）：

```json
{"code":"AUTH_REQUIRED","info":"认证信息缺失","data":null}
```

非法参数实际响应（HTTP 200）：

```json
{"code":"INVALID_ARGUMENT","info":"请求参数无效","data":null}
```

## F. 正向查询结果与实际调用链

本次正向请求已经通过真实 HTTP 得到业务事实，返回字段和值如下：

| Facts 字段 | 真实来源 / 返回值 |
| --- | --- |
| `order.status` | `group_buy_order_list.status=2` -> `CLOSE` |
| `team.status` | `group_buy_order.status=0` -> `PROGRESS` |
| `team.targetCount` / `lockCount` / `completeCount` | `3` / `0` / `0` |
| `team.validEndTime` | `2026-07-30T03:45:37+08:00` |
| `activity.status` | `group_buy_activity.status=1` -> `EFFECTIVE` |
| `references.teamId` / `activityId` | `18781389` / `100123` |

真实调用链：

```text
HTTP POST /api/v1/agent/order/facts
  -> AgentFactsController#getOrderFacts
     group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/AgentFactsController.java
  -> DevHeaderAuthenticatedUserProvider#getAuthenticatedUserId
     group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/DevHeaderAuthenticatedUserProvider.java
  -> OrderFactsService#getOrderFacts(authenticatedUserId, outTradeNo)
     group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/facts/OrderFactsService.java
  -> TradeRepository#queryMarketPayOrderEntityByOutTradeNo
     group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java
  -> IGroupBuyOrderListDao#queryGroupBuyOrderRecordByOutTradeNo
  -> group_buy_order_list_mapper.xml#queryGroupBuyOrderRecordByOutTradeNo
  -> group_buy_order_list

  -> TradeRepository#queryGroupBuyTeamByTeamId
  -> IGroupBuyOrderDao#queryGroupBuyTeamByTeamId
  -> group_buy_order_mapper.xml#queryGroupBuyTeamByTeamId
  -> group_buy_order

  -> TradeRepository#queryGroupBuyActivityEntityByActivityId
  -> IGroupBuyActivityDao#queryGroupBuyActivityByActivityId
  -> group_buy_activity_mapper.xml#queryGroupBuyActivityByActivityId
  -> group_buy_activity
```

## G. 越权查询结果

Header 为 `xfg03` 且请求同一 `outTradeNo=644398015396` 时，实际返回 `ORDER_NOT_FOUND_OR_NOT_AUTHORIZED`。首个 Mapper 的 SQL 条件同时绑定 `out_trade_no` 与已认证用户 ID：

```sql
select ...
from group_buy_order_list
where out_trade_no = #{outTradeNo} and user_id = #{userId}
```

因此不先按订单号读取 `xfg05` 的订单；不存在订单和其他用户订单均由同一空结果映射为该错误码，对外不可区分。

## H. 无认证结果

不携带 `X-Dev-Authenticated-User-Id` 时，`AuthenticatedUserProvider` 返回空，Controller 在调用 Service/Repository 前返回 `AUTH_REQUIRED`。本次实际 HTTP 已验证该路径。

## I. 非法参数结果

`outTradeNo` 为空字符串时，Controller 在认证及数据库调用之前返回 `INVALID_ARGUMENT`。本次实际 HTTP 已验证该路径。

## J. 是否真实执行 MyBatis SQL

**是。** 本次并非 Mock：运行的是真实 Spring Boot 进程，使用 dev 数据源连接真实 `group_buy_market`，并通过上述 Mapper 调用链取得了与三表记录一致的 Facts 响应。

Facts 路径实际调用的三个 Mapper statement 均为 `SELECT`：

```sql
-- group_buy_order_list_mapper.xml
select user_id, team_id, order_id, activity_id, start_time, end_time, goods_id,
       source, channel, original_price, deduction_price, pay_price, status
from group_buy_order_list
where out_trade_no = #{outTradeNo} and user_id = #{userId};

-- group_buy_order_mapper.xml
select team_id, activity_id, target_count, complete_count, lock_count, status,
       valid_start_time, valid_end_time, notify_type, notify_url
from group_buy_order
where team_id = #{teamId};

-- group_buy_activity_mapper.xml
select activity_id, activity_name, discount_id, group_type, take_limit_count,
       target, valid_time, status, start_time, end_time, tag_id, tag_scope
from group_buy_activity
where activity_id = #{activityId};
```

当前日志级别没有输出 MyBatis 的逐条预编译 SQL/绑定参数，且 `performance_schema` 未保留可供本次会话读取的 statement history；因此不能把“日志中打印 SQL”作为额外证据。真实 HTTP 返回、真实数据源启动和三表前后核验共同证明此路径已连接 MySQL，而非 Mock。

## K. 是否发现写操作

Order Facts 的 Service 和本次调用的三个 Mapper statement 均为只读 `SELECT`；该 API 路径不调用 INSERT、UPDATE、DELETE、Redis 写入、退款、结算、锁单或通知发布代码。测试后复查的目标订单、团队和活动字段均未变化。

**环境问题（必须如实记录）：** 使用未改动的项目 dev 配置启动后，应用既有 `TimeoutRefundJob` 在 `20:57:00` 运行了一次“超时退单定时任务”扫描。日志为“扫描数据，超时组队未支付订单”及“未发现超时未支付订单”。这不是 Order Facts HTTP 路径触发，且未观察到退款/订单状态/团队状态/活动状态变化；但“本次进程期间完全没有后台退款扫描任务执行”这一更严格条件**不成立**。为避免后续调度，本次验证完成后立即停止了应用。

没有发现由四个 HTTP 请求引起的写操作；但由于未启用数据库 general log，无法将整个应用进程的所有 SQL 以逐条审计日志形式穷尽证明为零写入。

## L. 遇到的环境问题

1. Docker CLI 当前没有访问权限，无法用 `docker ps` 查看容器；不过 MySQL `13306`、Redis `16379`、RabbitMQ `15672` 的 TCP 服务均可达，应用也成功建立连接。
2. 应用以项目真实 dev 配置启动会自动运行既有 `TimeoutRefundJob` 扫描；本次未修改配置或业务逻辑来屏蔽它。该扫描无结果，但属于需要在隔离联调环境中处理的背景任务干扰。
3. MyBatis SQL 日志未在当前配置开启，`performance_schema` 无可用历史记录；已以 Mapper 源码、真实服务连接和真实响应交叉验证。

## M. 修改了哪些文件

本次 Phase 2A.1 联调**没有修改 Java、Mapper、Spring 配置或业务逻辑**。唯一新增的持久化文件是本报告：

```text
docs/agent/PHASE2A_REAL_INTEGRATION.md
```

打包与启动产生的 `target/` 日志和 Maven 临时本地仓库属于测试工件，不属于业务实现修改。

## N. 最终结论

真实 Spring Boot、真实 MySQL、真实 HTTP 正向请求和三条错误路径均已跑通；越权请求在第一条按 `user_id + out_trade_no` 的查询中被隔离。

```text
REAL_MYSQL_INTEGRATION = PASS
LIVE_HTTP_INTEGRATION = PASS
AUTHORIZATION_INTEGRATION = PASS
```

附带限制：Phase 2A API 本身保持只读且未发现其造成的写入；但本次按原样启动 dev 应用时，既有 `TimeoutRefundJob` 的无结果扫描确实执行过一次。因此若验收标准要求“测试进程中任何退款/后台扫描任务都不得运行”，该严格环境隔离项为 **FAIL**，应在后续独立的无调度联调环境中处理；这不改变本次已验证的 Order Facts HTTP -> MyBatis -> MySQL 调用链结果。
