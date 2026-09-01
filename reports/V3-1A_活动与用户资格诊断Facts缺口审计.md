# V3-1A 活动与用户资格诊断 Facts 缺口审计

## 1. V3 诊断目标

V3 的目标是让 Agent 对“为什么我参加不了活动”“为什么活动看起来有效但仍无法参加”逐项验证事实、排除或保留候选原因，而不是由 Java 直接返回单一原因码。

本报告为只读设计审计：未修改 V2 Runtime、Java 业务代码、数据库、配置、测试或 Python Agent，未新增 API。

审计结论是：源码中存在足够的活动与当前用户资格组成事实，适合设计 `Activity Facts` 与 `Eligibility Facts` 两个只读 API；但不存在覆盖活动试算、锁单和指定团队库存的单一完整 `eligible` 判定。因此不得设计 `eligible`、`cannotJoinReason`、`diagnosisReason`、`rootCause` 或 `recommendation` 字段。

## 2. Activity 真实领域模型

| 真实模型/文件 | 字段或符号 | 可证实的语义 |
| --- | --- | --- |
| `GroupBuyActivity` PO | `activityId`、`groupType`、`takeLimitCount`、`target`、`validTime`、`status`、`startTime`、`endTime`、`tagId`、`tagScope` | 数据库活动配置的原始字段。 |
| `GroupBuyActivityEntity` | 同上（不含 `source`、`channel`） | 锁单规则使用的活动领域实体。 |
| `ActivityStatusEnumVO` | `CREATE`、`EFFECTIVE`、`OVERDUE`、`ABANDONED` | 活动状态枚举。 |
| `GroupBuyActivityDiscountVO` | `target`、`takeLimitCount`、`startTime`、`endTime`、`tagScope`、`tagId` | 试算/标签链使用的活动营销配置。 |

注意：活动字段名称是 `startTime` / `endTime`；`validStartTime` / `validEndTime` 是团队 `GroupBuyTeamEntity` 的字段，不能混称为活动原始字段。

`GroupBuyActivityDiscountVO` 声明了 `source`、`channel`，但 `ActivityRepository.queryGroupBuyActivityDiscountVO` 没有填充它们；`GroupBuyActivityEntity` 也没有这两个字段。因此当前源码没有可靠的、可按 `activityId` 查询并返回的活动级 `source` / `channel` Facts，V3 第一批不应暴露它们。

## 3. Activity 查询链

### 3.1 原始活动查询

```text
Activity Facts（拟议）
  → 新的只读 Activity 查询服务（未来实现）
  → ITradeRepository.queryGroupBuyActivityEntityByActivityId
  → TradeRepository.queryGroupBuyActivityEntityByActivityId
  → IGroupBuyActivityDao.queryGroupBuyActivityByActivityId
  → group_buy_activity_mapper.xml
  → group_buy_activity
```

`queryGroupBuyActivityByActivityId` 按 `activity_id` 查询，不按状态过滤，因而能够诊断创建、过期、废弃等状态。当前 `TradeRepository` 在 DAO 返回空值时会直接解引用，未来 Facts 实现必须在新读取边界做空值处理，不能把 NPE 当作活动不存在语义。

### 3.2 现有营销配置查询的限制

`ActivityRepository.queryGroupBuyActivityDiscountVO(activityId)` 调用 `queryValidGroupBuyActivityId`，该 SQL 只返回 `status = 1` 的活动。这条现有链适合营销试算，**不适合**作为 V3 活动诊断的唯一数据源，否则无法返回非生效活动的原始状态。

## 4. Activity Facts 候选字段

| 字段 | 来源 | 分类 | 实时 | 适合 Agent | 结论 |
| --- | --- | --- | --- | --- | --- |
| `activityId` | `GroupBuyActivityEntity.activityId` | A Raw | 是 | 是 | 必须。 |
| `status` | `ActivityStatusEnumVO` / 实体状态 | A Raw | 是 | 是 | 必须；不能单独等同于可参与。 |
| `startTime`、`endTime` | 活动实体 | A Raw | 是 | 是 | 必须。 |
| `evaluatedAt` | Facts 服务读取时的 Java 当前时间 | A Raw observation | 是 | 是 | 建议，用于解释时间派生值。 |
| `withinValidTime` | `ActivityUsabilityRuleFilter.apply` 的相同边界 | B Derived | 是 | 是 | 建议。定义为 `evaluatedAt >= startTime && evaluatedAt <= endTime`；不产生原因文本。 |
| `targetCount` | 活动 `target` 的 API 语义别名 | A Raw alias | 是 | 是 | 建议，需在合同说明其来自 `target`。 |
| `groupType` | 活动实体 | A Raw | 是 | 是 | 可选，真实存在但不是首批两个场景的必需原因。 |
| `validTimeMinutes` | 活动 `validTime` | A Raw | 是 | 是 | 可选，表示新建团队时长配置，不等同于活动窗口。 |
| `takeLimit` | 活动 `takeLimitCount` | A Raw | 是 | 是 | 必须，Eligibility Facts 也复用。 |
| `tagScope` | 活动实体 | A Raw | 是 | 是 | 必须，编码语义须由 Java/知识规则解释。 |
| `tagId`、`discountId` | 活动实体 | A Raw internal identifier | 是 | 否 | 不暴露。 |
| `source`、`channel` | 现有活动按 ID 查询 | 无可靠来源 | — | 否 | 第一批不返回。 |

## 5. Eligibility 真实业务链

当前源码有两条相关但并非同一“资格”链路：

```text
营销试算 / 可见与可参与展示
MarketProduct
  → RootNode（参数）
  → SwitchNode（降级、用户切量）
  → MarketNode（活动/商品/优惠）
  → TagNode（标签）
  → EndNode（isVisible、isEnable）

锁单
TradeLockOrderService.lockMarketPayOrder
  → ActivityUsabilityRuleFilter（活动状态、活动时间）
  → UserTakeLimitRuleFilter（同活动订单记录数）
  → TeamStockOccupyRuleFilter（仅指定已有团队时的库存占用）
```

试算链的 `SwitchNode` 会读取动态降级开关和当前用户切量结果；锁单链会检查活动、次数和指定团队库存。`TagNode` 会生成展示层 `isVisible` / `isEnable`，但它不在锁单 Rule 链中。由此可证：现有源码没有统一、完整、单一的 `eligible` 布尔值。

## 6. Redis Tag Crowd 真实语义

`ActivityRepository.isTagCrowdRange(tagId, userId)` 的最终代码如下语义：

| 条件 | `isTagCrowdRange` 返回 | TagNode 最终影响 |
| --- | --- | --- |
| 活动 `tagId` 为空 | TagNode 不调用该方法 | `visible=true`，`enable=true`。 |
| `tagId` 非空且对应 BitSet 不存在 | `true` | `visible = baseVisible || true = true`，`enable = baseEnable || true = true`。缺失标签人群数据的当前行为是默认放行。 |
| BitSet 存在且用户 bit 为 `true` | `true` | 同样将可见/参与结果放行为 `true`。 |
| BitSet 存在且用户 bit 为 `false` | `false` | 最终结果保留 `GroupBuyActivityDiscountVO.isVisible/isEnable` 的基础值；当 `tagScope` 对相应维度配置限制时，可见或参与会为 `false`。 |

因此 `tagMatched=true` 不能被解释为“已从存在的人群成员数据中确认匹配”：它也可能是 BitSet 缺失时的 permissive fallback。该历史风险必须保留在 V3 Eval 中。

为保持隐私，Eligibility Facts 不得返回 BitSet、Redis key、成员列表或其他用户标签。若未来需要让 Agent 区分真实标签匹配与缺失数据默认放行，可安全设计布尔派生事实 `tagCrowdDataAvailable`（仅表示该活动的人群数据是否可用于判断，不暴露存储实现或成员）；是否首批开放应作为产品安全评审项。

## 7. Participation Limit 真实语义

调用链：

```text
UserTakeLimitRuleFilter.apply
  → ITradeRepository.queryOrderCountByActivityId(activityId, authenticatedUserId)
  → TradeRepository.queryOrderCountByActivityId
  → IGroupBuyOrderListDao.queryOrderCountByActivityId
  → group_buy_order_list
```

实际 SQL 语义为按 `user_id` 和 `activity_id` 统计 `group_buy_order_list` 的全部记录；没有 `status` 条件。故它统计的是该用户在同一活动的**全部订单记录数**，不是仅支付、仅成团、仅锁单中或仅某一个状态的记录数。`takeLimitCount` 非空且 `count >= takeLimitCount` 时，锁单 Rule 抛出 `E0103`。

可安全暴露：`userTakeCount`、`userTakeLimit`（可空）和 `participationLimitReached`。后者是确定性派生值，而不是诊断原因。

## 8. Activity 状态与时间语义

`ActivityStatusEnumVO` 的状态为创建、生效、过期、废弃。`ActivityUsabilityRuleFilter.apply` 在锁单链最先执行：

1. `status != EFFECTIVE`：抛出 `E0101`；
2. `currentTime.before(startTime) || currentTime.after(endTime)`：抛出 `E0102`；
3. 仅在两者都通过时进入次数与团队库存 Rule。

边界等于开始或结束时间时不触发 `before` / `after`，即与活动窗口相容。`status=EFFECTIVE` 不代表当前用户一定能参加：仍可能不在时间窗内、达到次数限制、在试算路径被降级/切量/标签影响，或在指定团队路径遇到容量/库存约束。

## 9. Raw / Derived / Diagnosis 分层

| 层级 | 候选 | 可否返回 |
| --- | --- | --- |
| A Raw Facts | `activityId`、`status`、`startTime`、`endTime`、`targetCount`、`groupType`、`validTimeMinutes`、`tagScope`、`userTakeCount`、`userTakeLimit` | 可以；按最小必要原则选择。 |
| B Deterministic Derived Facts | `evaluatedAt`、`withinValidTime`、`tagRuleConfigured`、`tagMatched`（须说明 fallback）、`tagVisibilityAllowed`、`tagParticipationAllowed`、`participationLimitReached`、`marketDowngraded`、`userWithinReleaseRange`、可选 `tagCrowdDataAvailable` | 可以；需逐项复用当前明确逻辑，且不转化为原因文本。 |
| C Diagnosis / Cause | `eligible`、`cannotJoinReason`、`diagnosisReason`、`rootCause`、`recommendation` | 禁止。`eligible` 会错误合并多条并不完全一致的业务路径，并隐藏并发存在的约束。 |

## 10. Auth 与隐私

Activity Facts 本身不使用用户身份查询，活动数据也无当前源码的“按用户授权”规则；但作为 Agent-facing API，建议仍沿用 `AuthenticatedUserProvider` 并要求 `AUTH_REQUIRED`，保持 V2 可信调用边界与访问收敛。请求 body 仅接受 `activityId`，不接受 `userId`、token、header、auth、base URL、SQL 或 Redis key。

Eligibility Facts 必须使用 `AuthenticatedUserProvider` 提供的可信当前用户，且请求 body 也只接受 `activityId`。它只返回该可信用户的次数、标签判定和动态开关/切量结果；不得返回其他用户 ID、订单、标签成员、Redis BitSet、Redis key 或内部动态配置原值。

## 11. 与现有 V2 Tool 的重叠分析

`get_order_facts` 仅能从用户已有订单返回 `activity.status` 与引用 `activityId`，不返回活动时间窗、次数上限/使用量、标签/切量/降级事实，也要求已有 `outTradeNo`。因此用户只提供 `activityId` 时，Activity Facts 具有独立价值。

`get_joinable_team_facts` 返回当前用户下的候选团队和活动级团队统计，不返回活动状态、时间、标签、次数、降级或切量事实。它不等同于 Eligibility Facts。它可在后续具体“有队伍但无法加入”场景中提供候选团队证据，但不能裁决指定团队的实时 Redis 库存占用。

## 12. Activity Facts Contract 草案（只设计）

```text
POST /api/v1/agent/activity/facts
body: { "activityId": <positive integer> }
trusted identity: AuthenticatedUserProvider（建议保留 V2 Agent 边界；不在 body）

data.activity:
  activityId                 A / realtime / non-user-specific
  status                     A / realtime / non-user-specific
  startTime                  A / realtime / non-user-specific
  endTime                    A / realtime / non-user-specific
  targetCount                A（来自 target）/ realtime / non-user-specific
  groupType                  A / realtime / non-user-specific（可选）
  validTimeMinutes           A（来自 validTime）/ realtime / non-user-specific（可选）
  tagScope                   A / realtime / non-user-specific
  userTakeLimit              A（来自 takeLimitCount）/ realtime / non-user-specific
  evaluatedAt                A observation / realtime
  withinValidTime            B / realtime
```

不得返回 `tagId`、`discountId`、活动级 `source/channel`（当前无可靠按 ID 来源）、诊断文本或原因码。

建议错误：复用 `AUTH_REQUIRED`、`INVALID_ARGUMENT`、`INTERNAL_SERVICE_ERROR`；活动未找到需要一个清晰且最小的 `ACTIVITY_NOT_FOUND` 语义（当前 ResponseCode 不存在该专用码，留待 V3 实现时统一评审，不应借用订单未找到码）。

## 13. Eligibility Facts Contract 草案（只设计）

```text
POST /api/v1/agent/activity/eligibility-facts
body: { "activityId": <positive integer> }
trusted identity: AuthenticatedUserProvider（必需）

data:
  activityId                 A / realtime / user-related response context
  tagScope                   A / realtime / activity configuration
  userTakeCount              A / realtime / current user only
  userTakeLimit              A / realtime / activity configuration, nullable
  tagRuleConfigured          B / tagId 是否为空
  tagMatched                 B / isTagCrowdRange 的返回；必须记录 BitSet 缺失时为 true 的 fallback 语义
  tagVisibilityAllowed       B / TagNode 最终 visible
  tagParticipationAllowed    B / TagNode 最终 enable
  participationLimitReached  B / count >= non-null limit
  marketDowngraded           B / SwitchNode 全局降级结果
  userWithinReleaseRange     B / SwitchNode 对可信用户的切量结果
  optional tagCrowdDataAvailable
                             B / 仅表示可用于判断的人群数据是否存在；不返回 bitmap、key 或成员
```

该 API 不返回统一 `eligible` 或任何原因文本。`marketDowngraded`、`userWithinReleaseRange` 是当前试算链真实存在的组成事实，若首批不包含，Scene B 仍可能保留无法验证的 DCC 候选原因；因此它们应列为 MUST，而非隐藏的内部细节。

建议错误与 Activity Facts 相同：`AUTH_REQUIRED`、`INVALID_ARGUMENT`、未来统一评审的 `ACTIVITY_NOT_FOUND`、`INTERNAL_SERVICE_ERROR`。没有源码依据支持 `NOT_AUTHORIZED` 或多个细分诊断错误码。

## 14. 两个诊断 Case 演算

### Case A：活动有效、时间有效、标签不匹配

若 Observation 为：

```text
activity.status = EFFECTIVE
activity.withinValidTime = true
eligibility.tagRuleConfigured = true
eligibility.tagMatched = false
eligibility.tagParticipationAllowed = false
eligibility.participationLimitReached = false
eligibility.marketDowngraded = false
eligibility.userWithinReleaseRange = true
```

Agent 可基于证据排除状态、时间、次数和动态开关，验证“当前标签参与限制未通过”这一候选原因。Java 没有返回 `TAG_MISMATCH` 原因码。

### Case B：活动生效但不在时间窗内

若 Observation 为：

```text
activity.status = EFFECTIVE
activity.withinValidTime = false
eligibility.tagMatched = true
eligibility.tagParticipationAllowed = true
eligibility.participationLimitReached = false
```

Agent 可排除标签和次数限制，并以活动时间事实验证时间约束。`evaluatedAt`、`startTime`、`endTime` 允许答案说明判断依据，而不把时间判断交给模型自行猜测。

## 15. 多原因情况

若 `withinValidTime=false` 且 `tagParticipationAllowed=false`，两项约束可同时成立。拟议 Facts 会同时提供它们，Agent 可输出多个被验证的当前约束，而不是因 Java 单一 `reasonCode` 只保留一个原因。

必须额外提醒：`tagMatched=true` 可能来自标签数据缺失时的默认放行，因此涉及标签数据质量的 Case 应同时读取或在后续启用 `tagCrowdDataAvailable`。不能把 `tagMatched=true` 无条件说成已验证的成员关系。

## 16. 是否需要 Specific Team Facts

**当前不需要立即新增 Specific Team Facts。** 首批两个诊断场景可由 Activity Facts + Eligibility Facts 覆盖活动状态、时间、标签、次数、降级与切量候选原因；现有 `get_joinable_team_facts` 已可提供候选团队与活动团队统计。

但“指定某个团队存在却无法加入”仍可能涉及 `TeamStockOccupyRuleFilter` 的容量/Redis 库存占用，该事实需要具体 `teamId` 且不能由活动级 Eligibility API 完整判断。该更窄的团队诊断需求应 DEFER，等 V3 场景/评估明确后再决定是否设计 Team Facts；不能为架构完整性抢先新增 Tool。

## 17. 最小新增能力

| 优先级 | 能力 | 理由 |
| --- | --- | --- |
| MUST_HAVE | `get_activity_facts` | 用户仅给 `activityId` 时可独立查询非生效状态、时间窗及活动约束原始事实。 |
| MUST_HAVE | `get_user_eligibility_facts` | 为可信用户提供标签、次数、降级与切量的组成事实，支持 V3 诊断验证而非直接给原因。 |
| NICE_TO_HAVE | `tagCrowdDataAvailable` | 可区分标签 BitSet 缺失的 permissive fallback 与真实成员匹配；需产品安全评审。 |
| DEFER | Specific Team Facts | 首批两个场景不必需；指定团队容量/Redis 库存场景另行设计。 |
| DEFER | `eligible`、原因码、建议文本 | 会合并不同链路、丢失多原因信息或越过 Java/Agent 责任边界。 |

## 18. 风险与遗留问题

- 当前 `TradeRepository.queryGroupBuyActivityEntityByActivityId` 对不存在活动没有 null 防护；新 Facts 查询必须显式处理，而非复用异常副作用。
- 当前营销配置查询只取生效活动，不能代替诊断活动查询。
- 标签 BitSet 不存在时默认放行，造成 `tagMatched` 名称的语义歧义；需将 fallback 明确写入合同/测试。
- 活动试算与锁单规则链并不一致；任何文档或 Agent 都不得把某一条链的成功等同于另一条链保证成功。
- 指定团队的实时库存占用是后续诊断缺口，但不在首批两个活动级场景的 MUST 范围内。

```text
V2_RUNTIME_MODIFIED = NO
ACTIVITY_DOMAIN_AUDITED = PASS
ELIGIBILITY_DOMAIN_AUDITED = PASS
TAG_MATCH_SEMANTICS_IDENTIFIED = PASS
PARTICIPATION_LIMIT_SEMANTICS_IDENTIFIED = PASS
RAW_DERIVED_DIAGNOSIS_BOUNDARY = PASS
ACTIVITY_FACTS_JUSTIFIED = YES
ELIGIBILITY_FACTS_JUSTIFIED = YES
SPECIFIC_TEAM_FACTS_REQUIRED_NOW = NO
AUTH_BOUNDARY_DEFINED = PASS
PRIVACY_BOUNDARY_DEFINED = PASS
ACTIVITY_FACTS_CONTRACT_READY = YES
ELIGIBILITY_FACTS_CONTRACT_READY = YES
READY_FOR_V3_1B = YES
V3_1A_ACCEPTANCE = PASS
```
