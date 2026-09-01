# V3-1B：Activity Facts 只读接口实现验收

## 1. 实现范围与调用链

本次只在 Java `group-buy-market-jiusi` 中新增活动事实读取能力；未修改 Python Agent，未修改既有 V2 Agent API 的请求、响应或业务语义。

实际 HTTP 调用链如下：

```text
POST /api/v1/agent/activity/facts
  -> AgentActivityFactsController#getActivityFacts
  -> AuthenticatedUserProvider#getAuthenticatedUserId
  -> IAgentActivityFactsService#getActivityFacts
  -> AgentActivityFactsService#getActivityFacts
  -> IActivityRepository#queryGroupBuyActivityFactsSourceByActivityId
  -> ActivityRepository#queryGroupBuyActivityFactsSourceByActivityId
  -> IGroupBuyActivityDao#queryGroupBuyActivityByActivityId
  -> group_buy_activity_mapper.xml#queryGroupBuyActivityByActivityId
  -> group_buy_activity
```

涉及文件：

- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/AgentActivityFactsController.java`
- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/AuthenticatedUserProvider.java`（既有，仅复用）
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/IAgentActivityFactsService.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/facts/AgentActivityFactsService.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/repository/IActivityRepository.java`
- `group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/ActivityRepository.java`
- `group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/dao/IGroupBuyActivityDao.java`（既有 Mapper 接口）
- `group-buy-market-app/src/main/resources/mybatis/mapper/group_buy_activity_mapper.xml`（既有只读 SQL）

## 2. 请求、认证与返回边界

接口为 `POST /api/v1/agent/activity/facts`，唯一允许的请求字段是正整数 `activityId`。`AgentActivityFactsRequestDTO` 使用 `@JsonAnySetter` 拒绝任何未知字段，因此 `userId`、token、鉴权信息、source/channel、SQL/Redis 参数等均不会进入服务。

Controller 在调用 Facts 服务前从 `AuthenticatedUserProvider` 获取可信身份；缺失身份统一返回 `AUTH_REQUIRED`。身份不来自 request body，服务也不接受模型传入的用户标识。

成功响应仅有：

```text
data.activity.activityId
data.activity.status
data.activity.startTime
data.activity.endTime
data.activity.tagScope
data.activity.userTakeLimit
data.activity.evaluatedAt
data.activity.withinValidTime
```

响应没有 `groupType`、`validTime`、`target`、`source`、`channel`、`tagId`、`discountId`，也没有 `diagnosis`、`rootCause`、`cannotJoinReason`、`recommendation` 或 `eligible`。

活动不存在时，Repository 在新 Facts 边界安全返回 `null`，Controller 返回新且专用的 `ACTIVITY_NOT_FOUND`。未修改旧 `TradeRepository#queryGroupBuyActivityEntityByActivityId`，因此未改变既有交易锁单链的空值行为。

## 3. MyBatis 查询与只读性

Facts Repository 直接调用现有的无状态过滤 Mapper，而不是 `queryGroupBuyActivityDiscountVO` / `queryValidGroupBuyActivityId`：

```sql
select activity_id, activity_name, discount_id, group_type, take_limit_count,
       target, valid_time, status, start_time, end_time, tag_id, tag_scope
from group_buy_activity
where activity_id = #{activityId}
```

该 Mapper 不含 `status = 1`，所以 `CREATE`、`OVERDUE`、`ABANDONED` 的活动均可作为原始 Facts 返回。新增的 Java 读取路径不调用 Redis、锁单、退款、结算、通知或写 DAO；SQL 为单条 `SELECT`。

`withinValidTime` 仅是时间窗口观察，严格复用 `ActivityUsabilityRuleFilter` 的边界：

```java
!evaluatedAt.before(startTime) && !evaluatedAt.after(endTime)
```

因此活动开始/结束时刻相等时为 `true`，并且不会因为 status 不是 `EFFECTIVE` 而被强制改为 `false`。状态和时间观察保持相互独立；Java 未加入资格判断或诊断结论。

## 4. 聚焦测试

使用 JDK 8 执行：

```text
mvn -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false \
  -Dtest=AgentActivityFactsControllerTest,AgentActivityFactsServiceTest,
         AgentJoinableTeamFactsControllerTest,AgentJoinableTeamFactsServiceTest,
         AgentFactsControllerTest,OrderFactsServiceTest,
         DevHeaderAuthenticatedUserProviderTest test
```

结果：32 passed，0 failures，0 errors，0 skipped。

- 新增 `AgentActivityFactsServiceTest`：9 项，覆盖活动不存在、有效窗口内、窗口前/后、开始边界、结束边界，以及 `CREATE`、`OVERDUE`、`ABANDONED` 状态。
- 新增 `AgentActivityFactsControllerTest`：6 项，覆盖最小白名单响应、三种非生效状态真实返回、缺失认证、空/非法 ID、额外字段拒绝、`ACTIVITY_NOT_FOUND` 与内部异常隐藏。
- 既有 V2 Agent Facts 回归集：17 项通过（Order Facts、Joinable Team Facts、dev header provider）。

未运行已知存在独立环境/历史问题的 Maven 全量套件；本次验证不将其混同为 V3-1B 回归。

## 5. 真实 HTTP + MySQL 冒烟

已用当前构建启动 Spring Boot：

- profile：`dev`
- Tomcat：`8091`
- 数据源：dev 配置的 `127.0.0.1:13306/group_buy_market`
- Redis：`localhost:16379` 连接成功
- RabbitMQ：`localhost:5672` 连接成功
- `agent.facts.dev-header-auth.enabled=true` 已由 dev 配置启用；请求以 dev header provider 提供的可信身份完成，不在此报告暴露该测试身份值。

现存活动请求：

```http
POST http://127.0.0.1:8091/api/v1/agent/activity/facts
Content-Type: application/json
X-Dev-Authenticated-User-Id: [dev test identity]

{"activityId":100123}
```

实际 HTTP 200 响应：

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "activity": {
      "activityId": 100123,
      "status": "EFFECTIVE",
      "startTime": "2024-12-07T10:19:40+08:00",
      "endTime": "2029-12-07T10:19:40+08:00",
      "tagScope": "2",
      "userTakeLimit": 1,
      "evaluatedAt": "2026-09-01T21:40:34+08:00",
      "withinValidTime": true
    }
  }
}
```

不存在活动请求 `{"activityId":999999999}` 也实际返回 HTTP 200、业务码 `ACTIVITY_NOT_FOUND`，且 `data` 为 `null`。

应用启动日志证明 dev profile、Tomcat、Redis、RabbitMQ 和 Hikari/MySQL 初始化完成；HTTP 正向响应来自新 Mapper 的 `group_buy_activity` 查询链。服务当前仍在 PID 22856、端口 8091 运行。

注意：应用自身存在既有 `TimeoutRefundJob`，启动后会自动执行一次扫描；本次启动日志显示“未发现超时未支付订单”。该调度器不属于本次 Activity Facts 请求或新增代码。新增 Facts 调用链本身只有上述 `SELECT`，未触发业务写入。

## 6. 修改清单

新增：

- `group-buy-market-api/src/main/java/cn/bugstack/api/dto/AgentActivityFactsRequestDTO.java`
- `group-buy-market-api/src/main/java/cn/bugstack/api/dto/AgentActivityFactsResponseDTO.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/model/valobj/GroupBuyActivityFactsSourceVO.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/model/valobj/ActivityFactsVO.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/IAgentActivityFactsService.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/facts/AgentActivityFactsService.java`
- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/AgentActivityFactsController.java`
- `group-buy-market-app/src/test/java/cn/bugstack/test/agent/AgentActivityFactsServiceTest.java`
- `group-buy-market-app/src/test/java/cn/bugstack/test/agent/AgentActivityFactsControllerTest.java`

修改：

- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/repository/IActivityRepository.java`
- `group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/ActivityRepository.java`
- `group-buy-market-types/src/main/java/cn/bugstack/types/enums/ResponseCode.java`

未修改现有 V2 Controller、V2 DTO、Order Facts、Joinable Team Facts、旧 TradeRepository、Mapper XML、数据库、Python Agent 或 `.env`。

## 7. 结论与剩余风险

剩余风险：本次真实冒烟只能验证当前数据库中 `EFFECTIVE` 的活动 `100123`；非生效活动的 MyBatis 可读取性由无 status 条件 SQL、Repository 映射及 3 个状态单元测试证明，尚未以真实数据库中的 `CREATE`/`OVERDUE`/`ABANDONED` 样本再次做 HTTP 冒烟。没有执行全量 Maven 套件，因为此前已确认存在与本改动无关的历史红测/卡住问题。

```text
V2_RUNTIME_CHANGED = NO
ACTIVITY_FACTS_API_IMPLEMENTED = PASS
ACTIVITY_QUERY_SUPPORTS_NON_EFFECTIVE = PASS
ACTIVITY_NOT_FOUND_SAFE = PASS
TIME_BOUNDARY_MATCHES_DOMAIN_RULE = PASS
TRUSTED_AUTH_PRESERVED = PASS
MODEL_CONTROLLED_IDENTITY = NO
DIAGNOSIS_LOGIC_ADDED_TO_JAVA = NO
FOCUSED_TESTS = 32
FOCUSED_TESTS_PASS = PASS
V2_AGENT_API_REGRESSION = PASS
REAL_HTTP_SMOKE = PASS
READY_FOR_V3_1C = YES
V3_1B_ACCEPTANCE = PASS
```
