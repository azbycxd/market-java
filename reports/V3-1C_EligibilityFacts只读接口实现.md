# V3-1C：Eligibility Facts 只读接口实现验收

## 1. 实现范围与修改文件

本次只在 Java 项目 `group-buy-market-jiusi` 增加用户 Eligibility Facts 读取 API；没有修改 Python Agent、Prompt、数据库、Redis 数据、V2 API 合同或既有业务规则。

新增：

- `group-buy-market-api/src/main/java/cn/bugstack/api/dto/AgentEligibilityFactsRequestDTO.java`
- `group-buy-market-api/src/main/java/cn/bugstack/api/dto/AgentEligibilityFactsResponseDTO.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/model/valobj/EligibilityFactsVO.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/IAgentEligibilityFactsService.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/service/facts/AgentEligibilityFactsService.java`
- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/AgentEligibilityFactsController.java`
- `group-buy-market-app/src/test/java/cn/bugstack/test/agent/AgentEligibilityFactsServiceTest.java`
- `group-buy-market-app/src/test/java/cn/bugstack/test/agent/AgentEligibilityFactsControllerTest.java`

修改：

- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/repository/IActivityRepository.java`
- `group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/model/valobj/GroupBuyActivityFactsSourceVO.java`（新增仅内部使用的 tagId 原始来源，不进入任何 API Response）
- `group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/ActivityRepository.java`

## 2. HTTP Contract 与可信身份

接口：`POST /api/v1/agent/activity/eligibility-facts`

唯一合法 body：

```json
{"activityId": 100123}
```

`AgentEligibilityFactsRequestDTO` 通过 `@JsonAnySetter` 拒绝未知字段。`userId`、token、authorization、header、baseUrl、SQL、Redis key、tagId 等不会被接受或静默忽略。

当前用户仅来自 `AuthenticatedUserProvider#getAuthenticatedUserId`。Controller 在调用 Eligibility Service 之前验证该可信身份；缺失时返回 `AUTH_REQUIRED`。身份只作为 Java 内部查询参数，未进入 Response、日志模板或本报告中的 HTTP 样例。

成功返回的 `data` 只有：

```text
activityId
tagRuleConfigured
tagCrowdDataAvailable
tagGatePassed
tagVisibilityAllowed
tagParticipationAllowed
userTakeCount
userTakeLimit
participationLimitReached
marketDowngraded
userWithinReleaseRange
```

活动不存在统一返回已由 V3-1B 引入的 `ACTIVITY_NOT_FOUND`；不会因为 tag、次数限制为空而触发 NPE。

## 3. Eligibility 调用链

```text
HTTP POST /api/v1/agent/activity/eligibility-facts
  -> AgentEligibilityFactsController#getEligibilityFacts
  -> AuthenticatedUserProvider#getAuthenticatedUserId
  -> IAgentEligibilityFactsService#getEligibilityFacts
  -> AgentEligibilityFactsService#getEligibilityFacts
  -> IActivityRepository#queryGroupBuyActivityFactsSourceByActivityId
  -> ActivityRepository#queryGroupBuyActivityFactsSourceByActivityId
  -> IGroupBuyActivityDao#queryGroupBuyActivityByActivityId
  -> group_buy_activity_mapper.xml -> group_buy_activity

  -> IActivityRepository#isTagCrowdDataAvailable / #isTagCrowdRange
  -> ActivityRepository -> Redis RBitSet（仅内部观测与既有门禁）

  -> IActivityRepository#queryOrderCountByActivityId
  -> IGroupBuyOrderListDao#queryOrderCountByActivityId
  -> group_buy_order_list_mapper.xml -> group_buy_order_list

  -> IActivityRepository#downgradeSwitch / #cutRange
  -> ActivityRepository -> DCCService
```

活动查询沿用无状态过滤的现有 Mapper：

```sql
select activity_id, activity_name, discount_id, group_type, take_limit_count,
       target, valid_time, status, start_time, end_time, tag_id, tag_scope
from group_buy_activity
where activity_id = #{activityId}
```

活动 Facts 边界先判空，旧 `TradeRepository` 的锁单空值行为未被改变。

## 4. Tag Rule、数据可用性与 Scope 语义

`tagRuleConfigured` 由活动内部 tagId 是否为空决定，但 tagId 从不离开 Repository/Service 边界。

`tagCrowdDataAvailable` 来自当前 `RBitSet#isExists()`，表示当前是否有可用于成员门禁的数据集；它不表示成员命中，也不暴露 Redis、位图、键或成员列表。

`tagGatePassed` 复用 `ActivityRepository#isTagCrowdRange` 的既有语义：人群数据存在时读取当前可信用户对应成员位；人群数据不存在时，该旧方法返回 `true`。因此不使用会产生误导的 `tagMatched` 字段。

TagNode 的真实最终规则被保留：

```text
tagVisibilityAllowed    = baseVisible || tagGatePassed
tagParticipationAllowed = baseEnable  || tagGatePassed
```

其中 base 值由现有 `GroupBuyActivityDiscountVO#isVisible/#isEnable` 原样计算：

| tagScope | baseVisible | baseEnable |
| --- | --- | --- |
| 空或其它未命中规则值 | true | true |
| `1` | false | true |
| `2` | true | false |
| `1,2` | false | false |

无 Tag Rule 时，TagNode 的现有默认行为是 visibility/participation 都为 `true`；Facts 对应为 `tagRuleConfigured=false`、`tagCrowdDataAvailable=false`、`tagGatePassed=true`。

## 5. Take Limit、Switch 与 Release

`userTakeCount` 调用与 `UserTakeLimitRuleFilter` 相同语义的 Mapper SQL：

```sql
select count(id) from group_buy_order_list
where user_id = #{userId} and activity_id = #{activityId}
```

该 SQL 没有 status 条件，故字段语义是“该可信用户在该活动下的所有 order-list 记录计数”，不是支付、成团或有效订单次数。

`participationLimitReached` 保持 UserTakeLimitRuleFilter 的边界：`userTakeLimit != null && userTakeCount >= userTakeLimit`。当限制为 `null` 时为 `false`，即当前规则不阻止参与。

`marketDowngraded` 和 `userWithinReleaseRange` 分别复用既有 `SwitchNode` 所使用的 `downgradeSwitch()` 与 `cutRange(authenticatedUserId)` 结果。API 只暴露布尔业务观测，不暴露 DCC key、灰度比例、哈希、bucket 或配置细节。

## 6. Raw / Derived / Diagnosis 边界

Raw：`activityId`、`userTakeCount`、`userTakeLimit`。

Derived：`tagRuleConfigured`、`tagCrowdDataAvailable`、`tagGatePassed`、`tagVisibilityAllowed`、`tagParticipationAllowed`、`participationLimitReached`、`marketDowngraded`、`userWithinReleaseRange`。

未加入 Diagnosis：没有统一 `eligible` / `canParticipate`，也没有 `cannotJoinReason`、`diagnosisReason`、`rootCause`、`reasonCode`、`recommendation`。Activity status、有效期等已有 V3-1B Activity Facts 职责也没有在本接口重复输出。

## 7. 隐私与 Redis fallback 回归

Response 不包含可信身份、任何 userId、其它订单、其它成员、人群成员列表、tagId、Redis key、RBitSet/bitmap、SQL、内部灰度配置或数据库记录。

明确回归用例：存在 Tag Rule、`tagCrowdDataAvailable=false`、既有门禁 fallback 返回 `true`。测试验证 Facts 保持 `tagGatePassed=true`，以及 `tagScope=1,2` 时最终 visibility/participation 均为 `true`。本次仅将“数据不可用”和“旧门禁放行”分开观察，**没有**修复或改变既有 Redis fallback 业务规则。

## 8. 测试与 V2 回归

使用 JDK 8 执行：

```text
mvn -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false \
  -Dtest=AgentEligibilityFactsControllerTest,AgentEligibilityFactsServiceTest,
         AgentActivityFactsControllerTest,AgentActivityFactsServiceTest,
         AgentJoinableTeamFactsControllerTest,AgentJoinableTeamFactsServiceTest,
         AgentFactsControllerTest,OrderFactsServiceTest,
         DevHeaderAuthenticatedUserProviderTest test
```

结果：52 passed，0 failures，0 errors，0 skipped。

- Eligibility Service：15 项，覆盖无 Tag Rule、数据存在命中/未命中、Redis 人群数据缺失 fallback、scope、次数边界、空 limit、降级/切量、活动不存在。
- Eligibility Controller：5 项，覆盖最小字段、身份缺失、非法/注入 body、活动不存在和内部异常隐藏。
- 既有 V2 Agent API 与 V3-1B Activity Facts 测试：32 项通过。

## 9. 真实 HTTP Smoke

当前构建以 `dev` profile 启动，Tomcat 监听 `8091`，Redis 与 RabbitMQ 连接成功，Hikari/MySQL 初始化成功。`agent.facts.dev-header-auth.enabled=true` 由当前 dev 配置启用。服务保持在 PID 45220 运行。

使用可信 dev HTTP 身份（值不记录）请求现存活动：

```http
POST http://127.0.0.1:8091/api/v1/agent/activity/eligibility-facts
Content-Type: application/json
X-Dev-Authenticated-User-Id: [not recorded]

{"activityId":100123}
```

实际 HTTP 200 响应：

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "activityId": 100123,
    "tagRuleConfigured": true,
    "tagCrowdDataAvailable": true,
    "tagGatePassed": false,
    "tagVisibilityAllowed": true,
    "tagParticipationAllowed": false,
    "userTakeCount": 0,
    "userTakeLimit": 1,
    "participationLimitReached": false,
    "marketDowngraded": false,
    "userWithinReleaseRange": true
  }
}
```

该结果真实体现数据存在与门禁未通过可区分，且 visibility 与 participation 可独立不同。不存在活动 `999999999` 实际返回 HTTP 200 / `ACTIVITY_NOT_FOUND`；移除身份 header 实际返回 HTTP 200 / `AUTH_REQUIRED`。

Real Smoke 未通过修改 Redis 数据构造“数据不存在”场景；该场景由确定性单元测试覆盖，以避免为验收改变共享 dev 环境状态。

应用本身有既有 `TimeoutRefundJob`，启动后执行扫描并记录“未发现超时未支付订单”。它不属于本次 Eligibility API 调用或新增代码；新增 HTTP 调用链只执行活动 SELECT、订单 count SELECT、Redis 读和 DCC 读。

## 10. 已知限制与结论

`tagCrowdDataAvailable=true` 仅能证明当前人群数据集存在，不证明其完整性或新鲜度；`tagGatePassed=true` 在数据不存在时仍可能是旧业务 fallback 的结果，这正是本 Facts API 显式返回两个字段的原因。真实 dev HTTP 只覆盖了当前活动的动态状态；缺失数据、其他 tagScope、次数边界和开关/切量反例均由 mock 边界测试覆盖，未改变运行环境。

```text
V2_RUNTIME_CHANGED = NO
ELIGIBILITY_FACTS_API_IMPLEMENTED = PASS
TRUSTED_IDENTITY_PRESERVED = PASS
MODEL_CONTROLLED_USER_ID = NO
TAG_MATCHED_FIELD_EXPOSED = NO
TAG_CROWD_DATA_AVAILABLE_IMPLEMENTED = PASS
TAG_GATE_PASSED_IMPLEMENTED = PASS
TAG_SCOPE_SEMANTICS_PRESERVED = PASS
TAKE_LIMIT_SEMANTICS_PRESERVED = PASS
SWITCH_RELEASE_FACTS_IMPLEMENTED = PASS
REDIS_MISSING_FALLBACK_PRESERVED = PASS
DIAGNOSIS_LOGIC_ADDED_TO_JAVA = NO
PRIVACY_BOUNDARY = PASS
FOCUSED_TESTS = 52
FOCUSED_TESTS_PASS = PASS
V2_AGENT_API_REGRESSION = PASS
REAL_HTTP_SMOKE = PARTIAL
READY_FOR_V3_1D = YES
V3_1C_ACCEPTANCE = PASS
```
