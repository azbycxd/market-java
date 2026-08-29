# V2-5B：可加入拼团 Facts API 实施报告

实施日期：2026-08-29  
范围：仅 `group-buy-market-jiusi` Java 项目。未修改 Python Agent、退款领域、核心拼团 SQL/规则、数据库测试数据、Prompt 或 `.env`。

## 1. 复用的真实 Java 查询链

新增 API 没有新写型 Repository、DAO 或 SQL，而是复用活动域的既有读取能力：

```text
HTTP POST /api/v1/agent/team/joinable-facts
  -> AgentJoinableTeamFactsController#getJoinableTeamFacts
  -> AuthenticatedUserProvider#getAuthenticatedUserId
  -> IAgentJoinableTeamFactsService#getJoinableTeamFacts
  -> AgentJoinableTeamFactsService
  -> IIndexGroupBuyMarketService#queryInProgressUserGroupBuyOrderDetailList
  -> IndexGroupBuyMarketServiceImpl
  -> IActivityRepository / ActivityRepository
  -> IGroupBuyOrderListDao#queryInProgressUserGroupBuyOrderDetailListByRandom
  -> group_buy_order_list_mapper.xml (SELECT)
  -> IGroupBuyOrderDao#queryGroupBuyProgressByTeamIds
  -> group_buy_order_mapper.xml (SELECT)

  -> IIndexGroupBuyMarketService#queryTeamStatisticByActivityId
  -> ActivityRepository#queryTeamStatisticByActivityId
  -> IGroupBuyOrderListDao#queryInProgressUserGroupBuyOrderDetailListByActivityId
  -> IGroupBuyOrderDao#queryAllTeamCount / queryAllTeamCompleteCount / queryAllUserCount
  -> group_buy_order_list_mapper.xml / group_buy_order_mapper.xml (SELECT)
```

`AgentJoinableTeamFactsService` 只调用上述两个既有 `IIndexGroupBuyMarketService` 读方法，固定 `ownerCount=0`、`randomCount=2`；不调用 lock、settlement、refund、notify 或 Redis API。

## 2. 新增 API

```http
POST /api/v1/agent/team/joinable-facts
Content-Type: application/json
X-Dev-Authenticated-User-Id: <dev/test only>
```

Controller：

```text
group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/
  AgentJoinableTeamFactsController.java
```

运行时继续复用现有 `DevHeaderAuthenticatedUserProvider`：仅当 profile 是 `dev` 或 `test` 且 `agent.facts.dev-header-auth.enabled=true` 时读取开发 Header；生产 profile 不信任该 Header。

## 3. Request Contract

唯一合法请求体：

```json
{
  "activityId": 100123
}
```

`AgentJoinableTeamFactsRequestDTO` 没有 `userId`、`authenticatedUserId`、`token`、`header` 或 `outTradeNo` 字段。DTO 用 `@JsonAnySetter` 拒绝**所有**额外 JSON 字段，而不是默认忽略；例如带 `userId` 的 body 会在 Controller 前转为 `INVALID_ARGUMENT`。

`activityId` 为空或小于等于零同样返回 `INVALID_ARGUMENT`。

## 4. Auth 边界

可信身份仅来自：

```text
trusted request context
  -> AuthenticatedUserProvider
  -> authenticatedUserId
  -> AgentJoinableTeamFactsService(activityId, authenticatedUserId)
```

Controller 在调用 Service 前处理认证；无认证返回 `AUTH_REQUIRED`。客户端无法借由 body 指定或覆盖查询用户。Service 把认证用户 ID 传入原有候选查询，原 Mapper 的随机候选查询包含 `user_id != #{userId}`。

## 5. Response Contract

`AgentJoinableTeamFactsResponseDTO` 是新的、最小的 Agent Projection：

```json
{
  "activityId": 100123,
  "candidateTeams": [
    {
      "teamId": "...",
      "targetCount": 3,
      "completeCount": 1,
      "lockCount": 1,
      "validEndTime": "2026-08-29T20:00:00+08:00"
    }
  ],
  "statistics": {
    "allTeamCount": 0,
    "allTeamCompleteCount": 0,
    "allTeamUserCount": 0
  }
}
```

返回字段均来自已有 `UserGroupBuyOrderDetailEntity` 和 `TeamStatisticVO`。没有新增、推断或重命名为新的业务事实。

## 6. Candidate Team 真实筛选语义

本实现保持原有 `ActivityRepository#queryInProgressUserGroupBuyOrderDetailListByRandom` 的规则，未在 Agent Controller 复制业务判断：

1. `group_buy_order_list` 限制 `activity_id = #{activityId}`、`status in (0,1)`、`end_time > now()`；
2. 其 `team_id` 必须属于同活动 `group_buy_order.status = 0` 的队伍；
3. 排除 `user_id != #{authenticatedUserId}`，防止当前用户自身订单成为随机候选；
4. Repository 将候选团队再读取为 `queryGroupBuyProgressByTeamIds`，该 SQL 额外限制 `group_buy_order.status = 0`、`target_count > lock_count`、`valid_end_time > now()`；
5. 既有 Repository 先取 `randomCount * 2`，再随机取最多 `randomCount`。本 API 固定 `randomCount=2`，客户端不能传 limit/topK/pagination。

## 7. Statistics 真实 SQL 语义

Statistics 不与“当前可加入候选”完全同义，字段保持既有 `TeamStatisticVO` 命名，语义如下：

1. 先从 `group_buy_order_list` 取 `activity_id=#{activityId}`、订单 `status in (0,1)` 的记录并按 `team_id` 分组。该种子集合**没有** `end_time > now()` 或 `group_buy_order.status=0` 限制。
2. `allTeamCount`：`group_buy_order` 中该种子 teamId 集合的 `count(id)`。
3. `allTeamCompleteCount`：同一集合中 `group_buy_order.status=1` 的 `count(id)`。
4. `allTeamUserCount`：同一集合中 `sum(group_buy_order.lock_count)`。

所以 Statistics 表示“活动内仍有状态 0/1 订单的队伍集合”的既有汇总，可能包含过期或已完成队伍；它不是“当前可加入队伍数”。该差异已保留并未为 Agent 重写业务规则。

## 8. 隐私字段过滤

现有首页 `GoodsMarketResponseDTO.Team` 与中间实体含 `userId`、`outTradeNo`，不能复用。新 Response 仅映射：

```text
teamId, targetCount, completeCount, lockCount, validEndTime
```

不会返回其他用户 userId/outTradeNo、手机号、地址、支付字段、source/channel、notify_task.parameter_json、SQL 字段或 Redis key。Controller 与 Service 单测均对含这些字段的中间实体/响应做了验证。

## 9. 空列表行为

空候选是合法成功结果：Service 将 `null` 或空候选转换为 `[]`，Controller 返回 `0000`，不会返回 `NOT_FOUND` 或 `INTERNAL_SERVICE_ERROR`。

现有读取链没有先做活动存在性查询；因此 activity 不存在与 activity 存在但没有候选都遵循同一安全、无枚举信息的成功语义：`candidateTeams=[]`，Statistics 由既有查询返回零值。没有新增 `ACTIVITY_NOT_FOUND` 枚举或暴露活动存在性。

## 10. 修改文件

新增：

```text
group-buy-market-api/src/main/java/cn/bugstack/api/dto/
  AgentJoinableTeamFactsRequestDTO.java
  AgentJoinableTeamFactsResponseDTO.java

group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/model/valobj/
  JoinableTeamFactsVO.java
group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/
  IAgentJoinableTeamFactsService.java
group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/facts/
  AgentJoinableTeamFactsService.java

group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/
  AgentJoinableTeamFactsController.java

group-buy-market-app/src/test/java/cn/bugstack/test/agent/
  AgentJoinableTeamFactsControllerTest.java
  AgentJoinableTeamFactsServiceTest.java

reports/
  V2-5B_可加入拼团Facts_API.md
```

未修改已有 Order Facts、活动 Repository、DAO、Mapper XML、数据库或核心拼团规则。

## 11. 测试

### 聚焦 Agent Facts 测试：通过

命令：

```text
mvn -q -Dmaven.repo.local=.m2-v25b -pl group-buy-market-app -am \
  -DskipTests=false -DfailIfNoTests=false \
  -Dtest=AgentJoinableTeamFactsControllerTest,AgentJoinableTeamFactsServiceTest,
         AgentFactsControllerTest,OrderFactsServiceTest,DevHeaderAuthenticatedUserProviderTest test
```

结果：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`。

其中新增测试覆盖：无认证、合法 body、额外 `userId` 拒绝、白名单 Projection、无 userId/outTradeNo 泄露、空候选成功、统计映射、固定服务端候选数量、异常隐藏。既有 Order Facts Controller/Service 和 dev header Provider 一并通过。

### 全量既有测试集：未通过（与本次代码无关，但必须如实记录）

同一 Maven reactor 的全量测试编译成功，但：

- `ITradeLockOrderServiceTest#test_lockMarketPayOrder` 直接抛出既有 `E0103` 业务异常；
- `IIndexGroupBuyMarketServiceTest#test_indexMarketTrial_error` 直接抛出既有 `E0002` 业务异常；
- 随后停在既有 `ApiTest`，其 Surefire 报告为空，测试 JVM 已停止以避免继续占用环境。

上述测试类和核心业务代码均未在本次改动中修改。它们使“全量 Java 测试套件”无法判定为通过；本阶段未为绿灯而改动无关旧测试。

## 12. 真实 DB 烟测

已重新打包并以 `dev` profile 启动 Spring Boot；日志确认 MySQL Hikari、Redis、RabbitMQ 连接并监听实际端口 `8091`。

真实请求：

```http
POST http://127.0.0.1:8091/api/v1/agent/team/joinable-facts
X-Dev-Authenticated-User-Id: xfg05
Content-Type: application/json

{"activityId":100123}
```

实际响应（HTTP 200）：

```json
{"code":"0000","info":"成功","data":{"activityId":100123,"candidateTeams":[],"statistics":{"allTeamCount":1,"allTeamCompleteCount":1,"allTeamUserCount":3}}}
```

调用前后只读核验的行数均一致：

| 表 / 条件 | 调用前 | 调用后 |
| --- | ---: | ---: |
| `group_buy_order_list where activity_id=100123` | 5 | 5 |
| `group_buy_order where activity_id=100123` | 3 | 3 |
| `notify_task where activity_id=100123` | 3 | 3 |

该 endpoint 的 Service 调用链和 Mapper statement 均为 SELECT；烟测未发出写型业务 HTTP 请求。启动完整 dev 应用时，既有 `TimeoutRefundJob` 仍会运行一次无结果扫描（日志“未发现超时未支付订单”）；这是应用原有调度，不由本 Agent API 调用触发，也不改变本 API 的只读调用链。严格隔离联调环境仍应在后续单独处理该背景任务。

## 13. Remaining Issues

1. 全量 Maven 测试仍有两个既有红测和一个 `ApiTest` 卡住，须由原测试维护方修复/隔离后，才能将全量 `JAVA_TEST_SUITE` 标为 PASS。
2. 当前 dev profile 会运行已有退款扫描调度；虽然本次无结果且 API 未触发它，端到端自动化环境应禁用或隔离背景写任务。
3. 统计口径可能含过期/已完成队伍，调用方不能将 `allTeamCount` 当成当前可加入数量；当前 Contract 已忠实保留既有 SQL 语义。
4. 新 API 未增加活动存在性检查，故不存在活动与无候选统一返回空事实；这是避免枚举信息泄露的当前读取链选择。
5. 服务当前保持以包含新 API 的 dev 实例运行在 8091；后续 Python 联调前不应将 `CLOSE` 等订单状态解释为退款到账。

## 最终状态

```text
JOINABLE_TEAM_API = PASS
TRUSTED_AUTH_BOUNDARY = PASS
READ_ONLY_BOUNDARY = PASS
CANDIDATE_TEAM_CONTRACT = PASS
STATISTICS_CONTRACT = PASS
PRIVACY_PROJECTION = PASS
EMPTY_RESULT_SEMANTICS = PASS
ORDER_FACTS_REGRESSION = PASS
JAVA_TEST_SUITE = FAIL
LIVE_DB_SMOKE = PASS
V2_5B_ACCEPTANCE = FAIL
```

由于 `JAVA_TEST_SUITE = FAIL`，不应开始 Python V2-5C。
