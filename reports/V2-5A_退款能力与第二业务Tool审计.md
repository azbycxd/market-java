# V2-5A：退款能力与第二业务 Tool 审计

审计日期：2026-08-29  
审计范围：`D:\workspace\java\group-buy-market-jiusi`。仅做源码、Mapper、SQL 初始化脚本与当前 `group_buy_market` 数据库结构的只读审计；未访问或修改 `group-buy-agent`，未修改 Java/Python 业务代码、数据库、Prompt 或 `.env`。

## 1. 当前 Java 退款相关源码检索结果

项目存在明确的**拼团退单（reverse trade）执行域**，不是仅有注释或枚举名称：

| 层 | 真实证据 |
| --- | --- |
| API | `group-buy-market-api/.../IMarketTradeService.java` 定义 `refundMarketPayOrder`；`RefundMarketPayOrderRequestDTO` 的输入为 `userId/outTradeNo/source/channel`。 |
| Trigger | `group-buy-market-trigger/.../http/MarketTradeController.java#refundMarketPayOrder` 暴露 `POST /api/v1/gbm/trade/refund_market_pay_order` 并调用退单 Service。`TimeoutRefundJob` 每分钟扫描超时未支付订单后发起退单。 |
| Domain | `ITradeRefundOrderService`、`TradeRefundOrderService`、`TradeRefundRuleFilterFactory`、`DataNodeFilter`、`UniqueRefundNodeFilter`、`RefundOrderNodeFilter` 与三个策略：`Unpaid2RefundStrategy`、`Paid2RefundStrategy`、`PaidTeam2RefundStrategy`。 |
| Infrastructure | `TradeRepository#unpaid2Refund`、`#paid2Refund`、`#paidTeam2Refund` 更新订单/队伍并写本地 `notify_task`；`RefundSuccessTopicListener` 消费退款主题以恢复锁单库存。 |
| Types | `TradeOrderStatusEnumVO.CLOSE(2, "用户退单")`、`RefundTypeEnumVO`、`TaskNotifyCategoryEnumVO`。 |

检索也确认不存在 Java 支付渠道退款网关实现：在 Java 源码（排除历史文档/构建输出）中未发现 Alipay、WeChat 或等价支付退款查询/执行适配器。

## 2. 当前真实退款业务链路

这条链路是**有副作用的退单执行流程**，不是 Facts 查询：

```text
POST /api/v1/gbm/trade/refund_market_pay_order
  -> MarketTradeController#refundMarketPayOrder
  -> TradeRefundOrderService#refundOrder
  -> DataNodeFilter
       -> TradeRepository#queryMarketPayOrderEntityByOutTradeNo(userId, outTradeNo)
       -> TradeRepository#queryGroupBuyTeamByTeamId(teamId)
  -> UniqueRefundNodeFilter（CLOSE 则返回 REPEAT）
  -> RefundOrderNodeFilter
       -> RefundTypeEnumVO 选择策略
       -> unpaid2Refund / paid2Refund / paidTeam2Refund
  -> TradeRepository 对 group_buy_order_list、group_buy_order 做 UPDATE
  -> INSERT notify_task（退款主题）
  -> TradeTaskService / RabbitMQ topic_team_refund
  -> RefundSuccessTopicListener#restoreTeamLockStock
  -> Redis 恢复库存计数
```

`TimeoutRefundJob` 使用同一服务对“超时、未支付、未成团”的订单发起该命令。三个策略的业务含义如下：

- 未支付且未成团：订单关闭、队伍 `lock_count` 减一、发送库存恢复消息。
- 已支付且未成团：订单关闭、队伍 `lock_count` 与 `complete_count` 均减一、发送库存恢复消息。
- 已支付且已成团：订单关闭、队伍计数回退；最后一笔退单会把队伍设为 `FAIL`，否则为 `COMPLETE_FAIL`。

这是拼团域的逆向状态处理与库存恢复。源码没有调用任何外部支付机构退款接口，也没有接收支付机构退款完成回调。

## 3. 退款数据来源

### 3.1 当前数据库与初始化脚本

当前真实 `group_buy_market` 的表为：`crowd_tags`、`crowd_tags_detail`、`crowd_tags_job`、`group_buy_activity`、`group_buy_discount`、`group_buy_order`、`group_buy_order_list`、`notify_task`、`sc_sku_activity`、`sku`。

对 `information_schema.columns` 的退款表/字段检索没有命中专用退款表或下列字段：

```text
refund_id, refund_status, refund_amount,
refund_apply_time, refund_complete_time, refund_reason
```

SQL 初始化脚本同样没有 `CREATE TABLE ... refund...`。当前可见的两类“退单痕迹”是：

| 持久化位置 | 能表达什么 | 不能表达什么 |
| --- | --- | --- |
| `group_buy_order_list.status=2` | 本项目订单已进入 `TradeOrderStatusEnumVO.CLOSE`；`update_time` 是该订单通用更新时间。 | 无 refundId、退款金额、申请/完成时间、退款原因、支付渠道退款结果。 |
| `notify_task` | 退款策略写入 `trade_unpaid2refund`、`trade_paid2refund`、`trade_paid_team2refund` 类别的消息任务；参数 JSON 有时含用户、订单、团队、活动及退单类型。 | 它是可重试/可更新/可清理的通用通知任务，不是退款账本；没有稳定的按 `authenticated user + outTradeNo` 查询 Mapper，也没有金额、原因或支付退款完成语义。 |

`group_buy_order_list.pay_price` 是下单支付价，不等于退款金额；`out_trade_time` 是支付结算写入时间，也不是退款时间。

### 3.2 退款查询能力

不存在以下能力：

- 面向退款事实的 `Repository` 查询方法；
- 以 `userId + outTradeNo` 查询退款记录的 DAO / Mapper SQL；
- `RefundFactsService` 或只读退款 Controller；
- 返回 refundId、退款状态、金额、申请时间、完成时间或原因的 DTO/VO。

现有 `TradeRepository#queryMarketPayOrderEntityByOutTradeNo(userId, outTradeNo)` 是订单读取，读取 `group_buy_order_list` 的订单状态、价格与引用；它服务于退单命令的前置加载和已有 Order Facts，不能提升为退款记录查询。

## 4. ORDER/CLOSE 与退款之间是否存在可靠关系

必须区分两个结论：

1. **在本项目拼团域内，`CLOSE` 是退单状态的可靠源码证据。** `TradeOrderStatusEnumVO` 将 `CLOSE(2)` 明确定义为“用户退单”；`UniqueRefundNodeFilter` 也将 `CLOSE` 视为退单幂等重复。因此不能把它当成任意普通的“取消”。
2. **`CLOSE` 不能证明资金已经由支付渠道退款。** 已支付策略只执行本项目订单/队伍 UPDATE、`notify_task` 写入、MQ 与库存恢复；没有支付退款网关、退款流水或渠道完成回调。它没有 refundAmount/refundCompleteTime/refundReason，也无法区分“退单命令已提交”“本地通知已消费”“外部资金已到账”。

因此已有 `get_order_facts.order.status=CLOSE` 可以如实表述为“拼团订单处于本项目的退单关闭状态”，但绝不能被 Python Agent 解释为“已退款到账”。

## 5. Refund Facts Tool 是否有真实业务依据

**不具备足够依据，不应实现 `get_refund_facts`。**

虽然有真实的退单执行 Domain，但 Facts Tool 需要稳定、可授权、可解释的读取面。当前只有订单状态与泛化通知任务，缺少退款实体、金额、时间线、原因、渠道结果及安全查询接口。把 `CLOSE` 包装为 `refundStatus` 或把 `pay_price` 包装为 `refundAmount` 都会虚构业务语义。

现有退款 HTTP 接口也不是替代方案：它是写命令，接收请求体中的 `userId`，会更新 MySQL、写 `notify_task`、发送 MQ 并可能操作 Redis；不符合 Agent Facts 的认证、只读和最小暴露边界。

## 6. 当前 get_order_facts 已覆盖哪些能力

Phase 2A 已有 `POST /api/v1/agent/order/facts` 的 `OrderFactsService`，安全地以认证用户 ID 与 `outTradeNo` 读取：

- `order.status`；
- 当前订单所属 `team.status/targetCount/lockCount/completeCount/validEndTime`；
- `activity.status`；
- `references.teamId/activityId`。

它已经覆盖“这一个订单”和“它所在这一个队伍”的状态。第二 Tool 若只再输出 team 状态、人数和有效期，或只重包装 activity status，均没有独立价值。

## 7. 第二 Tool 必须补充什么新信息

应补充**当前订单之外、同一活动中仍可加入的其他队伍与活动级统计**：

- 可加入候选队伍列表（不是订单所属队伍的重复输出）；
- 候选队伍的剩余名额、当前完成/锁单数量、有效期；
- 活动范围内的开团队伍数、已成团队伍数、总参团人数。

这让 Agent 能处理“这个订单不能继续时还有没有可加入的团”“活动当前还有多少进行中的团”等问题，并且可以由 OrderFacts 返回的 `references.activityId` 触发第二次、不同能力的调用。

## 8. 推荐第二 Tool

**唯一推荐：`get_joinable_team_facts`。**

真实源码依据已存在于活动域：

```text
IIndexGroupBuyMarketService#queryInProgressUserGroupBuyOrderDetailList
  -> IndexGroupBuyMarketServiceImpl
  -> IActivityRepository
  -> ActivityRepository
  -> IGroupBuyOrderListDao
     #queryInProgressUserGroupBuyOrderDetailListByRandom
  -> group_buy_order_list_mapper.xml
  -> IGroupBuyOrderDao#queryGroupBuyProgressByTeamIds
  -> group_buy_order_mapper.xml

IIndexGroupBuyMarketService#queryTeamStatisticByActivityId
  -> ActivityRepository#queryTeamStatisticByActivityId
  -> group_buy_order_list_mapper.xml
     #queryInProgressUserGroupBuyOrderDetailListByActivityId
  -> group_buy_order_mapper.xml
     #queryAllTeamCount / #queryAllTeamCompleteCount / #queryAllUserCount
```

上述 Mapper 全是 `SELECT`。其中候选队伍 SQL 明确限制 `activity_id`、`group_buy_order.status=0`、`status in (0,1)`、`end_time > now()`，并排除 `user_id != #{userId}`；团队进度 SQL 还限制 `target_count > lock_count` 与 `valid_end_time > now()`。

审计时，当前库中 `activity_id=100123` 的可加入队伍数量为 `0`，但这是当前测试数据已过期的事实，不影响该既有查询链和空列表语义的真实性。

## 9. 推荐 Tool 的输入 / 输出 Contract

本节仅为后续阶段候选 Contract，**本阶段不创建 DTO、API 或 Tool**。

输入：

```json
{
  "activityId": 100123
}
```

认证用户 ID 必须只来自 `AuthenticatedUserProvider`；不允许 request body 中出现 `userId`。`activityId` 可由已有 `get_order_facts.references.activityId` 提供。

候选只读输出（仅投影现有实体真实字段）：

```json
{
  "activityId": 100123,
  "candidateTeams": [
    {
      "teamId": "...",
      "targetCount": 3,
      "completeCount": 1,
      "lockCount": 1,
      "validEndTime": "..."
    }
  ],
  "statistics": {
    "allTeamCount": 0,
    "allTeamCompleteCount": 0,
    "allTeamUserCount": 0
  }
}
```

不得输出已有 `UserGroupBuyOrderDetailEntity` 中的其他用户 `userId` 或 `outTradeNo`。已有首页 DTO 会暴露这些字段，故不能直接作为 Agent DTO 复用。

## 10. 推荐 Java API 落点

仅建议后续实现时按如下真实模块落点，不在本阶段创建：

```text
group-buy-market-api
  AgentJoinableTeamFactsRequestDTO / ResponseDTO（新、最小投影）
group-buy-market-trigger
  AgentFactsController 或独立 Agent Team Facts Controller
  -> AuthenticatedUserProvider
group-buy-market-domain/domain/activity
  独立 read Service，复用 IIndexGroupBuyMarketService 的两项读取能力
group-buy-market-infrastructure
  ActivityRepository + 已有 OrderList/Order DAO Mapper SELECT
```

Controller 必须使用认证用户做候选过滤，设置服务器固定、受限的候选数量，并且只映射白名单字段。不要复用 `MarketIndexController` 的用户自报 `userId` 入参，也不要把其 `GoodsMarketResponseDTO.Team` 原样暴露给 Agent。

## 11. 推荐 Python Tool 落点

后续（非本阶段）可在 Python Agent 的现有 Tool Registry 中登记 `get_joinable_team_facts`，经 Java Agent Facts HTTP API 调用；Router 仅在问题涉及可加入队伍、活动参与容量或活动团体规模，且已有活动 ID 时调用。

Python 不直接查询 MySQL/Redis，不调用当前写型 `/api/v1/gbm/trade/refund_market_pay_order`，也不从 `CLOSE` 推断资金退款结果。

## 12. 为什么这个能力适合 Agent

- 与 OrderFacts 的单订单观察不同，它回答活动级、候选队伍级的实时问题。
- 已有 Domain Service、Repository、DAO 和 Mapper 的端到端只读数据来源，面试时可清楚说明 Java 保持业务数据边界、Agent 只做编排。
- 读取可投影为不含其他用户身份和交易号的公开队伍事实，适合最小数据暴露。
- 允许空结果；不会为了“第二 Tool”而伪造可加入队伍或退款结论。

## 13. 为什么其它候选不选

| 候选 | 不作为主要建议的原因 |
| --- | --- |
| `get_refund_facts` | 退单执行域真实存在，但无独立退款账本、字段与授权查询面；会把本地 `CLOSE` 误包装为资金退款。 |
| `get_team_facts` | 已有 OrderFacts 已返回当前订单所属 team 的状态、人数、有效期；纯拆包没有新信息。 |
| `get_activity_facts` | 当前 OrderFacts 已有 activity status/reference；单独重复状态价值低。活动配置详情可以扩展，但不如“其他可加入队伍 + 统计”直接且已有专门查询链。 |
| `get_user_eligibility_facts` | TagNode/`isTagCrowdRange` 的源码真实存在，但试算需要 `activityId/source/channel/goodsId`，且活动缓存路径可能写 Redis 缓存；不如候选队伍查询维持清晰的 MySQL 只读边界。 |
| `get_trade_status_facts` | 订单交易状态已是 `get_order_facts.order.status`，缺乏独立信息。 |

## 14. Remaining Risks

1. `notify_task.parameter_json` 是字符串 JSON，历史样本中 `outTradeNo` 并不总出现；不能作为稳定退款查询索引。
2. `CLOSE` 能说明本项目退单状态，不代表支付渠道的退款成功、到账或金额。
3. 现有 `MarketIndexController` 的候选队伍 DTO 会包含 `userId/outTradeNo`；未来 Agent API 必须重新投影并进行认证用户过滤。
4. 当前活动 `100123` 没有未过期、未满的队伍，候选 Tool 的真实正确响应应允许空数组与零统计，不应伪造样本。
5. 候选 Contract 尚未实现、未进行 HTTP 或并发/隐私测试；本结论只说明源码证据与架构落点足够，不等同于已交付第二 Tool。

## 最终结论

```text
REFUND_DOMAIN_EXISTS = YES
REFUND_PERSISTENCE_EXISTS = NO
REFUND_QUERY_CAPABILITY_EXISTS = NO
REFUND_TOOL_JUSTIFIED = NO

SECOND_TOOL_RECOMMENDATION = get_joinable_team_facts

SECOND_TOOL_HAS_INDEPENDENT_VALUE = PASS
JAVA_SOURCE_EVIDENCE_SUFFICIENT = PASS
V2_5A_ACCEPTANCE = PASS
```

`REFUND_PERSISTENCE_EXISTS = NO` 的含义是：没有独立、稳定、可授权读取的退款记录或等价退款事实结构；并非否认退单流程会更新订单状态及写入通用通知任务。
