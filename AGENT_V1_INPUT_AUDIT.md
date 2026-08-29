# 拼团客服与订单诊断 Agent V1 接入前专项审计

> 审计范围：`group-buy-market-jiusi` 当前工作区的 Java、MyBatis Mapper、SQL 初始化脚本与 Maven 依赖。  
> 审计方式：静态分析；未启动服务、未访问数据库或 Redis、未修改业务代码。  
> 结论口径：仅将可由源码确认的内容写为事实；不能由源码确认的内容标记为【待确认】。

## 结论摘要

当前项目已有锁单、结算、拼团进度、营销试算、退单和通知任务的领域能力，但没有可供客服/Agent 安全调用的“订单诊断”读接口，也没有可信的请求身份边界。Agent V1 不应直连 MySQL 或 Redis，更不能把 `userId` 当成 Agent 自报参数传给现有 Controller。

最小接入应新增一个只读 Java Facade：由已认证上下文提供 `authenticatedUserId`，按 `user_id + out_trade_no` 加载订单，再加载团队、活动、通知任务并返回脱敏的诊断 DTO。Python Agent 只能调用此高层 Facade/API。

---

## 1. 身份与鉴权

### 现状

| 检查项 | 静态结论 | 代码证据 |
|---|---|---|
| 登录 | 未找到登录 Controller、用户账户领域或登录流程。 | 在 `group-buy-market-trigger/src/main/java` 的 Controller 中仅有营销、交易、DCC 与测试回调接口。 |
| JWT / Token | Maven 声明了 `jjwt` 与 `java-jwt`，但源码中未找到 Token 生成、验签、`Authorization` 解析或 JWT Filter。不能将依赖视为鉴权实现。 | `pom.xml`、`group-buy-market-app/pom.xml`、`group-buy-market-domain/pom.xml`；未发现对应 Java 用法。 |
| Spring Security | 未找到 `spring-boot-starter-security`/`spring-security` 依赖或 `SecurityContext` 用法。 | 根及模块 `pom.xml`；源码检索结果。 |
| Session | 未找到 `HttpSession`、Session 认证或会话用户解析。 | 源码检索结果。 |
| Header userId | 未找到 `@RequestHeader` 读取 userId。 | 源码检索结果。 |
| HTTP 过滤器 | `TraceIdFilter` 使用 `HttpServletRequest`，用途是 TraceId，不是鉴权。 | `group-buy-market-app/src/main/java/cn/bugstack/config/TraceIdFilter.java`。 |
| CORS | 两个业务 Controller 使用 `@CrossOrigin("*")`。 | `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketIndexController.java`、`MarketTradeController.java`。 |

### 当前 userId 的进入点与可信性

`userId` 直接来自 JSON 请求体 DTO：

- 营销试算/首页：`GoodsMarketRequestDTO.userId` → `MarketIndexController.queryGroupBuyMarketConfig`。文件：`group-buy-market-api/src/main/java/cn/bugstack/api/dto/GoodsMarketRequestDTO.java`、`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketIndexController.java`。
- 锁单：`LockMarketPayOrderRequestDTO.userId` → `MarketTradeController.lockMarketPayOrder`。文件：`group-buy-market-api/src/main/java/cn/bugstack/api/dto/LockMarketPayOrderRequestDTO.java`、`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketTradeController.java`。
- 结算：`SettlementMarketPayOrderRequestDTO.userId` → `MarketTradeController.settlementMarketPayOrder`。文件：`group-buy-market-api/src/main/java/cn/bugstack/api/dto/SettlementMarketPayOrderRequestDTO.java`、`MarketTradeController.java`。
- 退单：`RefundMarketPayOrderRequestDTO.userId` → `MarketTradeController.refundMarketPayOrder`。文件：`group-buy-market-api/src/main/java/cn/bugstack/api/dto/RefundMarketPayOrderRequestDTO.java`、`MarketTradeController.java`。

结论：**当前请求身份不可信**。Controller 只做空值校验，不校验“请求者是否为该 userId”。现有订单查询虽然使用 `user_id + out_trade_no` 联合条件（见 `group_buy_order_list_mapper.xml` 的 `queryGroupBuyOrderRecordByOutTradeNo`），但该 `userId` 同样由客户端提交，不能形成访问控制。

### Agent 的 authenticatedUserId 绑定方式

应由 API Gateway / Java 身份认证 Filter 验证访问令牌后，创建不可由请求 JSON 覆盖的 `AuthenticatedPrincipal`（或同等请求上下文）。Facade 的签名应为：

```java
OrderDiagnosisDTO getOrderDiagnosis(String authenticatedUserId, String outTradeNo)
```

Facade 必须以 `authenticatedUserId + outTradeNo` 访问订单；Agent 的 Tool 入参禁止出现可自由指定的 `userId`。找不到订单和无权访问应返回同一外部错误（例如 `ORDER_NOT_FOUND_OR_NOT_AUTHORIZED`），避免枚举其他用户订单。

认证令牌的签发方、Claims、网关位置在本仓库源码中均为【待确认】。

---

## 2. 订单链路：从 LockMarketPayOrderRequestDTO / outTradeNo 开始

当前工程没有单独命名为 Application Service 的一层；HTTP Controller 直接编排 Domain Service。完整锁单调用链如下。

```text
POST /api/v1/gbm/trade/lock_market_pay_order
  LockMarketPayOrderRequestDTO(userId, teamId, activityId, goodsId, source, channel, outTradeNo, notifyConfigVO)
  └─ MarketTradeController.lockMarketPayOrder(...)
     ├─ ITradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo(userId, outTradeNo)
     │  └─ TradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo(...)
     │     └─ ITradeRepository.queryMarketPayOrderEntityByOutTradeNo(...)
     │        └─ TradeRepository.queryMarketPayOrderEntityByOutTradeNo(...)
     │           └─ IGroupBuyOrderListDao.queryGroupBuyOrderRecordByOutTradeNo(...)
     │              └─ group_buy_order_list_mapper.xml / group_buy_order_list
     ├─ [已有 teamId] ITradeLockOrderService.queryGroupBuyProgress(teamId)
     │  └─ TradeRepository → IGroupBuyOrderDao.queryGroupBuyProgress
     │     └─ group_buy_order_mapper.xml / group_buy_order
     ├─ IIndexGroupBuyMarketService.indexMarketTrial(...)
     │  └─ IndexGroupBuyMarketServiceImpl → 策略树
     │     RootNode → SwitchNode → MarketNode → TagNode → EndNode
     │     └─ ActivityRepository
     │        ├─ group_buy_activity / group_buy_discount（缓存回源）
     │        ├─ sku、sc_sku_activity
     │        └─ Redis RBitSet 人群标签
     └─ ITradeLockOrderService.lockMarketPayOrder(...)
        └─ TradeLockOrderService.lockMarketPayOrder(...)
           ├─ 规则责任链：ActivityUsabilityRuleFilter
           │                 → UserTakeLimitRuleFilter
           │                 → TeamStockOccupyRuleFilter
           └─ TradeRepository.lockMarketPayOrder(GroupBuyOrderAggregate)
              ├─ teamId 为空：INSERT group_buy_order（新团）
              ├─ teamId 非空：UPDATE group_buy_order.lock_count + 1
              └─ INSERT group_buy_order_list（订单明细，写入 out_trade_no）
```

关键类/文件：

- API DTO：`group-buy-market-api/src/main/java/cn/bugstack/api/dto/LockMarketPayOrderRequestDTO.java`。
- API 接口：`group-buy-market-api/src/main/java/cn/bugstack/api/IMarketTradeService.java`。
- Controller：`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketTradeController.java` 的 `lockMarketPayOrder`。
- Domain Service：`group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/lock/TradeLockOrderService.java`。
- 领域仓储契约：`group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/repository/ITradeRepository.java`。
- 仓储实现：`group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java`。
- DAO：`IGroupBuyOrderDao.java`、`IGroupBuyOrderListDao.java`，位于 `group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/dao/`。
- Mapper：`group-buy-market-app/src/main/resources/mybatis/mapper/group_buy_order_mapper.xml`、`group_buy_order_list_mapper.xml`。

`outTradeNo` 的幂等查询按 `(user_id, out_trade_no)` 执行；锁单成功后作为 `group_buy_order_list.out_trade_no` 保存。订单 ID 是仓储中生成的 `orderId`，不是 `outTradeNo`。

---

## 3. 数据关系

| 字段 | 主要实体 / PO | 数据表字段 | 写入或查询位置 |
|---|---|---|---|
| `userId` | `GroupBuyOrderList`、`MarketProductEntity`、各交易 DTO | `group_buy_order_list.user_id` | 锁单由 `TradeRepository.lockMarketPayOrder` 写入；查询用 `queryGroupBuyOrderRecordByOutTradeNo`。 |
| `outTradeNo` | `GroupBuyOrderList`、`PayDiscountEntity`、交易 DTO | `group_buy_order_list.out_trade_no` | 锁单写入；结算以其查询并更新。 |
| `teamId` | `GroupBuyOrder`、`GroupBuyOrderList`、`GroupBuyTeamEntity` | `group_buy_order.team_id`、`group_buy_order_list.team_id`、`notify_task.team_id` | 新团在 `TradeRepository.lockMarketPayOrder` 生成；订单明细引用该值。 |
| `activityId` | `GroupBuyActivity`、`GroupBuyOrder`、`GroupBuyOrderList`、`NotifyTask` | `group_buy_activity.activity_id`、`group_buy_order.activity_id`、`group_buy_order_list.activity_id`、`notify_task.activity_id` | 请求/试算确定后写入团队及订单明细。 |
| `goodsId` | `Sku`、`SCSkuActivity`、`GroupBuyOrderList` | `sku.goods_id`、`sc_sku_activity.goods_id`、`group_buy_order_list.goods_id` | `ActivityRepository.querySkuByGoodsId` 读取；锁单写明细。 |
| `source` | `GroupBuyActivity`、`SCSkuActivity`、`GroupBuyOrder`、`GroupBuyOrderList` | 同名 `source` 字段 | 试算匹配活动/SC，锁单写团队与明细。 |
| `channel` | `GroupBuyActivity`、`SCSkuActivity`、`GroupBuyOrder`、`GroupBuyOrderList` | 同名 `channel` 字段 | 同上。 |

来源：PO 位于 `group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/dao/po/`；字段映射位于 `group_buy_order_mapper.xml`、`group_buy_order_list_mapper.xml`、`group_buy_activity_mapper.xml`。

关联关系：

```text
group_buy_order_list（一个用户的一笔拼团订单）
  ├─ team_id ───────────────► group_buy_order.team_id（一个拼团队伍）
  └─ activity_id ───────────► group_buy_activity.activity_id（活动）

group_buy_order（团队）
  └─ activity_id ───────────► group_buy_activity.activity_id

notify_task
  ├─ team_id ───────────────► group_buy_order.team_id
  └─ activity_id ───────────► group_buy_activity.activity_id
```

`group_buy_order_list` 是客服诊断的订单事实起点；`group_buy_order` 是团队进度事实；`group_buy_activity` 是活动规则事实。

---

## 4. 状态枚举、含义与转换

| 对象/表 | 状态值 | 含义 | 转换位置 |
|---|---:|---|---|
| 订单明细 `group_buy_order_list.status` | `0` | 初始创建/锁单未结算（`CREATE`） | INSERT 时由 `TradeRepository.lockMarketPayOrder` 设为 `TradeOrderStatusEnumVO.CREATE`；Mapper `group_buy_order_list_mapper.xml`。 |
| 订单明细 | `1` | 消费完成/支付结算完成（`COMPLETE`） | `TradeRepository.settlementMarketPayOrder` → `IGroupBuyOrderListDao.updateOrderStatus2COMPLETE`；Mapper 仅允许 `status=0` 更新。 |
| 订单明细 | `2` | 用户退单（`CLOSE`） | Mapper 的 `unpaid2Refund`、`paid2Refund`、`paidTeam2Refund` 均更新至 2；由三种退款策略经 `TradeRepository` 调用。 |
| 团队 `group_buy_order.status` | `0` | 拼单中（`PROGRESS`） | INSERT 默认 0；`GroupBuyOrderEnumVO` 位于 `group-buy-market-types/src/main/java/cn/bugstack/types/enums/GroupBuyOrderEnumVO.java`。 |
| 团队 | `1` | 完成（`COMPLETE`） | 支付结算最后一人：`TradeRepository.settlementMarketPayOrder` → `updateOrderStatus2COMPLETE`。判定为读取到的 `targetCount - completeCount == 1`。 |
| 团队 | `2` | 失败（`FAIL`） | 已支付已成团退单后只剩一人：`paidTeam2RefundFail`。 |
| 团队 | `3` | 完成-含退单（`COMPLETE_FAIL`） | 已支付已成团退单且仍有其他已完成成员：`paidTeam2Refund`。 |
| 活动 `group_buy_activity.status` | `0/1/2/3` | 创建/生效/过期/废弃 | `ActivityStatusEnumVO` 位于 `group-buy-market-types/src/main/java/cn/bugstack/types/enums/ActivityStatusEnumVO.java`。本仓库未找到活动后台写状态的代码，状态来源为【待确认】。锁单只接受 `EFFECTIVE(1)`。 |
| 通知 `notify_task.notify_status` | `0/1/2/3` | 初始/完成/重试/失败 | `TradeTaskService.execNotifyJob` 根据回调响应更新；Mapper `nofify_task_mapper.xml`。 |

补充状态/策略：

- `TradeOrderStatusEnumVO`：`CREATE(0)`、`COMPLETE(1)`、`CLOSE(2)`，文件 `group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/model/valobj/TradeOrderStatusEnumVO.java`。
- `RefundTypeEnumVO` 以“团队状态 + 订单状态”选择策略：`PROGRESS + CREATE` → `UNPAID_UNLOCK`；`PROGRESS + COMPLETE` → `PAID_UNFORMED`；`COMPLETE/COMPLETE_FAIL + COMPLETE` → `PAID_FORMED`。文件 `group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/model/valobj/RefundTypeEnumVO.java`。
- 无法匹配退单组合时 `RefundTypeEnumVO.getRefundStrategy` 直接抛出 `RuntimeException`，没有统一业务错误码；对 Agent 属于 `UNSUPPORTED_REFUND_STATE` 的候选 reasonCode，但原始异常映射需新增 Facade 后统一处理。

---

## 5. 异常与可诊断业务原因

统一异常类型为 `AppException`，定义在 `group-buy-market-types/src/main/java/cn/bugstack/types/exception/AppException.java`；业务码定义在 `group-buy-market-types/src/main/java/cn/bugstack/types/enums/ResponseCode.java`。

| 原始 code | 原始含义 | 触发位置 | Agent diagnosis reasonCode 建议 |
|---|---|---|---|
| `0002` | 非法参数 | `RootNode`、`MarketTradeController` | `INVALID_REQUEST` |
| `0003` | 唯一索引冲突 | `TradeRepository.lockMarketPayOrder` 捕获 `DuplicateKeyException` | `DUPLICATE_ORDER_REQUEST` |
| `0004` | 更新记录为 0 | `TradeRepository` 的结算/退单更新 | `STATE_CHANGED_OR_CONCURRENT_UPDATE` |
| `0005` | HTTP 接口调用异常 | `GroupBuyNotifyService.groupBuyNotify` | `NOTIFY_HTTP_ERROR` |
| `0006` | 接口限流 | `MarketIndexController` 的限流回退 | `RATE_LIMITED` |
| `E0001` | 不存在对应折扣计算服务 | `MarketNode` | `DISCOUNT_PLAN_UNSUPPORTED` |
| `E0002` | 无拼团营销配置 | `ErrorNode` | `NO_MARKET_CONFIGURATION` |
| `E0003` | 拼团活动降级拦截 | `SwitchNode` | `ACTIVITY_DOWNGRADED` |
| `E0004` | 拼团活动切量拦截 | `SwitchNode` / `DCCService.isCutRange` | `ACTIVITY_TRAFFIC_NOT_ELIGIBLE` |
| `E0005` | 拼团组队失败，记录更新为 0 | `TradeRepository.lockMarketPayOrder` 参团时锁单量更新失败 | `TEAM_LOCK_UPDATE_FAILED` |
| `E0006` | 锁单量已达成 | `MarketTradeController.lockMarketPayOrder` | `TEAM_FULL` |
| `E0007` | 人群限定，不可参与 | `MarketTradeController.lockMarketPayOrder`；资格来自 `TagNode` | `TAG_NOT_ELIGIBLE` |
| `E0008` | 缓存库存不足 | `TeamStockOccupyRuleFilter` | `TEAM_STOCK_EXHAUSTED` |
| `E0101` | 活动未生效 | `ActivityUsabilityRuleFilter` | `ACTIVITY_NOT_EFFECTIVE` |
| `E0102` | 不在活动有效时间内 | `ActivityUsabilityRuleFilter` | `ACTIVITY_OUT_OF_TIME_RANGE` |
| `E0103` | 用户参与次数达上限 | `UserTakeLimitRuleFilter` | `USER_TAKE_LIMIT_REACHED` |
| `E0104` | 外部单号不存在或用户已退单 | `OutTradeNoRuleFilter` | `ORDER_NOT_FOUND_OR_CLOSED` |
| `E0105` | SC 渠道黑名单 | `SCRuleFilter` | `SC_CHANNEL_BLACKLISTED` |
| `E0106` | 支付交易时间不在拼团有效期内 | `SettableRuleFilter` | `PAYMENT_OUTSIDE_TEAM_VALIDITY` |

客服“为什么未拼团成功”最直接的现有事实不是异常码，而是订单、团队与有效期的组合：订单仍为 `CREATE`、团队仍为 `PROGRESS`、`complete_count < target_count`、或团队已 `FAIL/COMPLETE_FAIL`。建议 Facade 返回稳定的业务 reasonCode，不把 Java 异常文本直接交给 Agent。

---

## 6. 拼团进度

| 字段 | 表 | 含义 | 读取/更新位置 |
|---|---|---|---|
| `target_count` | `group_buy_order` | 团队成团目标人数 | 新团由 `PayActivityEntity.targetCount` 写入；读取为 `GroupBuyProgressVO.targetCount`。 |
| `lock_count` | `group_buy_order` | 已锁单人数（包含尚未结算订单） | 新团为 1；参团由 `updateAddLockCount` 加 1（条件 `< target_count`）；锁单失败回滚 Redis 占用；退单策略更新团队计数。 |
| `complete_count` | `group_buy_order` | 已支付并结算完成的人数 | 新团为 0；`TradeRepository.settlementMarketPayOrder` 调 `updateAddCompleteCount` 加 1，条件 `< target_count`；退单策略更新团队计数。 |

可直接复用的查询能力是：

- `ITradeLockOrderService.queryGroupBuyProgress(String teamId)`；实现 `TradeLockOrderService.queryGroupBuyProgress` → `TradeRepository.queryGroupBuyProgress` → `IGroupBuyOrderDao.queryGroupBuyProgress`。返回 `GroupBuyProgressVO(targetCount, completeCount, lockCount)`。文件分别位于 `group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/`、`.../service/lock/`、`group-buy-market-infrastructure/src/main/java/.../adapter/repository/`、`.../dao/`。
- 该 Service 没有 Controller/API 暴露，也没有所有权校验，不能让 Agent 直接调用。

注意：`lock_count` 不是“已支付人数”；面向用户的成团判断应以团队状态与 `complete_count / target_count` 为主，并结合订单状态。

---

## 7. 活动规则

### 确定性规则

| 规则 | 实现位置 | 结果 |
|---|---|---|
| 活动必须 `EFFECTIVE(1)` | `ActivityUsabilityRuleFilter.apply` | 否则 `E0101`。 |
| 当前时间必须在活动 `startTime` 至 `endTime` 之间 | `ActivityUsabilityRuleFilter.apply` | 否则 `E0102`。 |
| 用户参加次数不得达到 `takeLimitCount` | `UserTakeLimitRuleFilter.apply` → `TradeRepository.queryOrderCountByActivityId` | 否则 `E0103`。 |
| 已有团队锁单不得到目标数 | `MarketTradeController.lockMarketPayOrder` 先查 `queryGroupBuyProgress`；Mapper 更新也有 `lock_count < target_count` 条件 | 已达成返回 `E0006`；并发更新失败抛 `E0005`。 |
| 支付时间必须早于团队 `validEndTime` | `SettableRuleFilter.apply` | 否则 `E0106`。 |
| 试算请求必须含 userId/goodsId/source/channel | `RootNode.doApply` | 否则 `0002`。 |
| 试算必须找到活动和 SKU | `ErrorNode.doApply` | 否则 `E0002`。 |
| DCC 降级开关必须关闭 | `SwitchNode.doApply` → `IActivityRepository.downgradeSwitch` | 开启时 `E0003`。 |
| 用户必须位于灰度切量范围 | `SwitchNode.doApply` → `IActivityRepository.cutRange` | 不在范围时 `E0004`。 |
| `tagId` 为空 | `TagNode.doApply` | 直接 `isVisible=true`、`isEnable=true`。 |
| `tagId` 非空 | `TagNode.doApply` → `ActivityRepository.isTagCrowdRange` | 最终可见/可参与为 `tagScope` 的基础允许值 OR 用户在 Redis 位图中。 |
| 结算渠道不得被 SC 黑名单拦截 | `SCRuleFilter.apply` | 否则 `E0105`。 |

### 字段定位

- 团队有效期：`group_buy_order.valid_start_time`、`valid_end_time`，Mapper `group_buy_order_mapper.xml`；新团在 `TradeRepository.lockMarketPayOrder` 以当前时间和活动 `validTime` 计算。
- 活动有效期：`group_buy_activity.start_time`、`end_time`，Mapper `group_buy_activity_mapper.xml`；锁单规则使用它们。
- `tag_scope`：`group_buy_activity.tag_scope`，映射到 `GroupBuyActivityDiscountVO.tagScope`；解释逻辑在 `GroupBuyActivityDiscountVO.isVisible()` / `isEnable()`。
- `isVisible`、`isEnable`：不是独立数据库字段，是 `TrialBalanceEntity` 的动态试算结果；最终由 `TagNode` 写入、`MarketTradeController.lockMarketPayOrder` 拒绝不可参与用户。

`tag_scope` 的业务编码（例如 `1` 和 `2` 的完整产品语义）只能由 `GroupBuyActivityDiscountVO` 的条件代码确认；外部配置生成规则为【待确认】。

---

## 8. Redis

| 范围 | 当前用途 | 代码证据 | Agent V1 结论 |
|---|---|---|---|
| 人群标签 | `tagId` 为 key 的 `RBitSet`；用户 ID 经 MD5 转 bit index。 | `ActivityRepository.isTagCrowdRange`、`TagRepository.addCrowdTagsUserId`、`IRedisService.getIndexFromUserId`。 | 不允许 Agent 直读；Facade 只返回“资格是否满足”和可解释原因。 |
| 活动/优惠 | 活动与优惠配置采用缓存优先、DB 回源、缓存异常降级。 | `ActivityRepository.queryGroupBuyActivityDiscountVO`、`AbstractRepository.getFromCacheOrDb`；`GroupBuyActivity.cacheRedisKey`、`GroupBuyDiscount.cacheRedisKey`。 | 不允许 Agent 直读；活动读取必须经 Java 仓储/Facade。 |
| 拼团库存 | 已有 teamId 的锁单通过 `group_buy_market_team_stock_key_{activityId}_{teamId}` 及 recovery key 做 Redis 原子占用与恢复。 | `TeamStockOccupyRuleFilter`、`TradeLockRuleFilterFactory.generateTeamStockKey`、`TradeRepository.occupyTeamStock/recoveryTeamStock/refund2AddRecovery`。 | 不允许 Agent 读取或写入；它是并发控制内部状态，不是客服事实源。 |
| 订单 | 未发现以订单诊断为目的的订单缓存；订单事实落在 MySQL `group_buy_order_list`。 | `TradeRepository.queryMarketPayOrderEntityByOutTradeNo` 直接 DAO 查询。 | Agent 不需要直连 Redis。 |
| 通知/定时任务 | 通知任务用 team/uuid 分布式锁；定时任务使用 Redisson 锁。 | `TradePort.groupBuyNotify`、`GroupBuyNotifyJob`、`TimeoutRefundJob`。 | Agent 不操作。 |
| DCC | Redis 动态配置/订阅用于降级、切量、缓存开关。 | `DCCService`、`DCCController`、`ActivityRepository`。 | Agent 不操作；若要说明活动不可用，由 Facade 提供只读结论。 |

默认结论：**Agent V1 不允许直接操作 Redis，也不需要直接访问 Redis。** 所有缓存、位图、库存、锁和 DCC 信息由 Java 领域层收敛为只读诊断字段。

---

## 9. 通知/回调：`/api/v1/test/group_buy_notify`

真实结算与通知链路：

```text
MarketTradeController.settlementMarketPayOrder
  → TradeSettlementOrderService.settlementMarketPayOrder
    → 结算责任链（outTradeNo、SC、团队有效期）
    → TradeRepository.settlementMarketPayOrder
      1. group_buy_order_list.status: 0 → 1，并写 out_trade_time
      2. group_buy_order.complete_count + 1
      3. 最后一人时 group_buy_order.status: 0 → 1
      4. 创建 notify_task(status=0, category=trade_settlement)
    → 异步 TradeTaskService.execNotifyJob(notifyTask)
      → TradePort.groupBuyNotify
        ├─ HTTP：GroupBuyNotifyService.groupBuyNotify(notifyUrl, parameterJson)
        └─ MQ：EventPublisher.publish(notifyMQ, parameterJson)
      → notify_task.status 更新为 1(成功) / 2(重试) / 3(失败)
```

文件证据：

- 结算 Controller：`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketTradeController.java`。
- 结算领域服务：`group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/settlement/TradeSettlementOrderService.java`。
- 持久化和创建通知任务：`group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java` 的 `settlementMarketPayOrder`。
- 执行和重试统计：`group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/task/TradeTaskService.java`。
- HTTP/MQ 端口：`group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradePort.java`。
- HTTP 客户端：`group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/gateway/GroupBuyNotifyService.java`。
- 定时补偿：`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/job/GroupBuyNotifyJob.java`。

`/api/v1/test/group_buy_notify` 仅是测试接收端：`TestApiClientController.groupBuyNotify` 记录请求并返回字符串 `success`。它不查询、不写入订单，也不改变拼团状态。文件：`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/TestApiClientController.java`。

### get_notify_status 可行性

可以提供，但当前没有安全的现成 API：

- `INotifyTaskDao.queryUnExecutedNotifyTaskByTeamId` / Mapper 只查询 `notify_status in (0,2)`，不能返回成功 `1` 或失败 `3`。
- `ITradeTaskService.execNotifyJob(String teamId)` 是**执行/写入**任务，不是查询接口，Agent 不得调用。
- 因此 `get_notify_status` 需要新增只读仓储查询（按 teamId，返回全部状态），并由 Facade 先验证订单归属。

---

## 10. 可复用查询能力

| 目标能力 | 现有可复用组件 | 可复用程度 | 缺口 |
|---|---|---|---|
| `get_order_diagnosis` | `ITradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo` → `queryMarketPayOrderEntityByOutTradeNo` | 可复用底层订单查询，但仅返回少量字段 | 无高层诊断 DTO、无身份上下文、无团队/活动/通知聚合。 |
| `get_team_progress` | `ITradeLockOrderService.queryGroupBuyProgress` | 可复用领域查询 | 无 API、无所有权校验、未返回团队状态和有效期。 |
| `get_activity` | `IIndexGroupBuyMarketService.indexMarketTrial`；`ActivityRepository.queryGroupBuyActivityDiscountVO` | 试算可复用 | 对外试算依赖 client userId，且返回价格/资格，不等价于标准活动详情。 |
| `get_market_config` | `IMarketIndexService.queryGroupBuyMarketConfig` / `MarketIndexController` | 已有 HTTP API | 无身份绑定，`userId` 来自 body；返回拼团列表/统计，适合商城首页而非客服订单诊断。 |
| `get_notify_status` | `INotifyTaskDao`、`ITradeRepository.queryUnExecutedNotifyTaskList(teamId)` | 仅可复用部分数据访问 | 仅未执行/重试任务，不返回全部状态；没有只读公共接口。 |

`MarketIndexController` 的接口还可查询进行中团队和活动统计（`IndexGroupBuyMarketServiceImpl.queryInProgressUserGroupBuyOrderDetailList` / `queryTeamStatisticByActivityId`），但它针对展示与选团，不能代替“当前用户某笔订单”的诊断。

---

## 11. 建议新增 Java Facade（最小化）

建议新增一个独立的只读应用层边界，例如：

```text
GroupBuyOrderDiagnosisFacade
  ├─ getOrderDiagnosis(authenticatedUserId, outTradeNo)
  ├─ getTeamProgress(authenticatedUserId, outTradeNo)
  ├─ getActivity(authenticatedUserId, activityId)             [可选]
  └─ getNotifyStatus(authenticatedUserId, outTradeNo)         [可选]
```

最小约束：

1. Facade 的 `authenticatedUserId` 必须来自认证上下文，不能来自 Agent Tool 的 JSON。
2. Facade 只依赖 Domain Service / Repository 契约，禁止 Python Agent 直 SQL、直 Redis 或调用 DAO。
3. 首先以 `authenticatedUserId + outTradeNo` 查订单；未命中统一返回“订单不存在或无权访问”。
4. 仅当订单归属确认后，才读取 `teamId` 对应团队、`activityId` 对应活动及通知任务。
5. DTO 只返回客服需要的状态、数量、时间、reasonCode 和通知状态；不返回 Redis key、内部 MQ routing key、完整 `parameter_json`、其他用户标识或内部异常堆栈。
6. 只读 Facade 不调用 `lockMarketPayOrder`、`settlementMarketPayOrder`、`refundMarketPayOrder`、`execNotifyJob`、DCC 更新或 Redis 写操作。

现有仓库缺少“按订单归属读取完整订单/团队/活动/通知”的一个聚合查询契约，因此需要新增少量 Repository 方法及 Mapper。不能直接把 `TradeRepository` 或 DAO 暴露给 Agent。

---

## 12. Agent Tool 最终表格

| Agent Tool | 业务用途 | 输入 | 输出 | 复用现有类 | 是否需要新增代码 | 权限风险 | 失败类型 |
|---|---|---|---|---|---|---|---|
| `get_order_diagnosis` | 回答某笔订单为何未成团 | 认证上下文 `authenticatedUserId`；`outTradeNo` | 订单状态、团队状态/进度/有效期、活动状态、reasonCode、通知摘要 | `ITradeLockOrderService`、`ITradeRepository`、`IGroupBuyOrderDao/ListDao` | 是，Facade + 诊断 DTO + 完整订单读取 | 高：若 userId 来自请求体会越权；必须服务端绑定 | 订单不存在/无权、数据不完整、内部读取失败 |
| `get_team_progress` | 查询当前订单所属团队的缺口 | 认证上下文；`outTradeNo` | `targetCount`、`completeCount`、`lockCount`、团队状态、有效期 | `ITradeLockOrderService.queryGroupBuyProgress` | 是，Facade 包装与所有权校验；建议补团队状态/时间 DTO | 中：teamId 不能由 Agent 任意指定 | 订单不存在/无权、团队不存在 |
| `get_activity` | 说明活动是否有效、是否过期/受限 | 认证上下文；订单 `outTradeNo` 或已授权 `activityId` | 活动状态、活动起止时间、有效时长、资格结论 | `ITradeRepository.queryGroupBuyActivityEntityByActivityId`、`IIndexGroupBuyMarketService.indexMarketTrial` | 是，安全 API/DTO | 中：直接按 activityId 可能泄露营销配置 | 活动不存在、活动状态无效、资格不可确认 |
| `get_market_config` | 商城首页营销配置和可选拼团 | 认证上下文（服务器注入）+ `source/channel/goodsId` | 已有 `GoodsMarketResponseDTO` | `IMarketIndexService.queryGroupBuyMarketConfig` | 是，至少替换 body userId 为认证身份 | 中：当前接口可伪造 userId | 非法参数、限流、无配置、DCC/资格拦截 |
| `get_notify_status` | 解释成团后回调是否完成/是否重试 | 认证上下文；`outTradeNo` | `notifyCategory/type/status/count/updatedAt` 的脱敏摘要 | `INotifyTaskDao`、`TradeRepository` | 是，按 teamId 查询全部状态的只读 DAO/Facade | 中：通知含内部 URL/MQ 信息，不能原样输出 | 订单不存在/无权、无通知任务、读取失败 |

---

## 13. 《Agent V1 最小接入建议》

### 唯一场景

用户询问：**“我的拼团订单为什么还没有拼团成功？”**

### 当前可追溯 Java 事实链

```text
客户订单标识 outTradeNo
  → group_buy_order_list（用 user_id + out_trade_no 查询）
  → team_id
  → group_buy_order（target_count / lock_count / complete_count / status / valid_end_time）
  → activity_id
  → group_buy_activity（status / start_time / end_time / valid_time / tag_scope）
  → notify_task（只在团队结算完成或退单后出现）
```

对应的真实可复用调用：

```text
TradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo
  → TradeRepository.queryMarketPayOrderEntityByOutTradeNo
  → IGroupBuyOrderListDao.queryGroupBuyOrderRecordByOutTradeNo

TradeLockOrderService.queryGroupBuyProgress
  → TradeRepository.queryGroupBuyProgress
  → IGroupBuyOrderDao.queryGroupBuyProgress
```

这两条链分别只能得到订单摘要和三项进度，未提供单次调用完成的“订单 + 团队状态 + 活动 + 通知”诊断结果；也没有身份边界。因此 Agent V1 最少新增如下代码：

1. **认证上下文接入**：在现有 HTTP 边界之前完成 Token/Session/Gateway 身份校验，并提供服务端 `authenticatedUserId`。认证协议本仓库未实现，需由实际身份系统确定。
2. **只读 `GroupBuyOrderDiagnosisFacade`**：一个方法 `getOrderDiagnosis(authenticatedUserId, outTradeNo)`，顺序执行订单归属校验、团队读取、活动读取、通知读取与 reasonCode 计算。
3. **最少的只读数据访问补齐**：复用现有 `(user_id, out_trade_no)` 查询；补充按 `team_id` 查询完整团队字段和查询全部 `notify_task` 状态的 Mapper/Repository 方法。不得让 Agent 使用 DAO/SQL。
4. **稳定诊断 DTO/reasonCode**：至少覆盖 `ORDER_UNPAID`、`GROUP_IN_PROGRESS`、`GROUP_FULL_BUT_AWAITING_PAYMENT`（需由 `lockCount` 与 `completeCount` 推导）、`GROUP_COMPLETED`、`GROUP_FAILED`、`ORDER_REFUNDED`、`NOTIFY_PENDING/RETRY/FAILED`、`ORDER_NOT_FOUND_OR_NOT_AUTHORIZED`。

其中 `GROUP_FULL_BUT_AWAITING_PAYMENT` 是 Facade 根据 `lockCount == targetCount && completeCount < targetCount` 的派生诊断，不是现有枚举；应清晰标注为推导结果。活动到期后是否一定自动将团队改为失败，当前源码未展示自动状态迁移，故只能报告“团队有效期已过”，自动结局为【待确认】。

Python Agent 的职责仅限：将用户问题转换为 `outTradeNo`、调用已授权 Tool、把 Java 返回的 reasonCode 转换成客服语言。所有订单授权、数据库读取、缓存访问、状态解释和脱敏必须位于 Java Facade 内。
