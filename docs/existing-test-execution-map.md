# 现有测试执行映射

## 扫描基线

- 仓库：`group-buy-market-jiusi`
- 分支：`tag-v3.0`
- 基线提交：`1b15eb09797c47f77e4f27c3246e8bf572f79b76`
- 测试所在模块：`group-buy-market-app`
- Spring Profile：`dev`
- 数据依赖：MySQL `group_buy_market`、Redis、RabbitMQ，以及回调/商城 HTTP 服务
- 构建注意事项：项目目标版本为 Java 8；`group-buy-market-app/pom.xml` 中 Surefire 2.6 默认配置了 `skipTests=true`，实际执行必须用命令行覆盖。

## 测试清单

| 阶段 | 业务功能 | 测试类 | 测试方法 | 模块 | 环境依赖 | 主要测试数据/注意事项 |
|---|---|---|---|---|---|---|
| 基础 | 活动 DAO | `GroupBuyActivityDaoTest` | `test_queryGroupBuyActivityList` | app | MySQL | 活动表 |
| 基础 | 折扣 DAO | `GroupBuyDiscountDaoTest` | `test_queryGroupBuyDiscountList` | app | MySQL | 折扣表 |
| 基础 | 责任链示例 | `Link01Test` | `test_model01_01` | app | Spring | 规则链演示 |
| 基础 | 规则树示例 | `Link02Test` | `test_model02_01`、`test_model02_02` | app | Spring | 规则树演示 |
| 标签 | 人群标签写入 | `ITagServiceTest` | `test_tag_job` | app | MySQL、Redis | tagId=`RQ_KJHKL98UU78H66554GFDV`，batchId=`10001` |
| 标签 | 人群标签读取 | `ITagServiceTest` | `test_get_tag_bitmap` | app | Redis | `xiaofuge` 应命中，`gudebai` 不命中 |
| 标签 | 空标签 | `ITagServiceTest` | `test_null_tag_bitmap` | app | Redis | key=`null` |
| 正向 A | 营销试算 | `IIndexGroupBuyMarketServiceTest` | `test_indexMarketTrial` | app | MySQL、Redis | `xiaofuge/s01/c01/9890001` |
| 异常 | 无标签试算 | `IIndexGroupBuyMarketServiceTest` | `test_indexMarketTrial_no_tag` | app | MySQL、Redis | `dacihua/s01/c01/9890001` |
| 异常 | 无商品/活动试算 | `IIndexGroupBuyMarketServiceTest` | `test_indexMarketTrial_error` | app | MySQL、Redis | goodsId=`9890002` |
| 接口 | 营销配置接口 | `MarketIndexControllerTest` | `test_queryGroupBuyMarketConfig` | app | MySQL、Redis | `xfg01/s01/c01/9890001` |
| DCC | 动态降级开关 | `DCCControllerTest` | `test_updateConfig` | app | Redis | `downgradeSwitch=1`，会改变共享配置 |
| DCC | 降级后试算 | `DCCControllerTest` | `test_updateConfig2indexMarketTrial` | app | MySQL、Redis | 改开关后执行 `xiaofuge` 试算 |
| 正向 B | 领域锁单 | `ITradeLockOrderServiceTest` | `test_lockMarketPayOrder` | app | MySQL、Redis | 固定 outTradeNo=`909000098111`；若已存在会提前返回 |
| 正向 B/C | 连续开团/参团锁单 | `ITradeReverseStockServiceTest` | `test_lockMarketPayOrder` | app | MySQL、Redis、RabbitMQ | `xfg801..803`；首笔 `teamId=null`，后续使用真实返回值；随机 12 位 outTradeNo |
| 接口/MQ | MQ 锁单 | `MarketTradeControllerTest` | `test_lockMarketPayOrder_mq` | app | MySQL、Redis、RabbitMQ | `xfg01`，首笔 `teamId=null`，随机 outTradeNo |
| 接口/HTTP | URL 回调锁单 | `MarketTradeControllerTest` | `test_lockMarketPayOrder` | app | MySQL、Redis | `xfg03`，随机 outTradeNo，notifyUrl 指向 8091 |
| 风险用例 | 指定团参团 | `MarketTradeControllerTest` | `test_lockMarketPayOrder_teamId_not_null` | app | MySQL、Redis | 硬编码 teamId=`29487599`，不得用于本轮关联流程 |
| 风险用例 | 批量开团 | `MarketTradeControllerTest` | `test_lockMarketPayOrder_list` | app | MySQL、Redis | 当前循环只执行 `xfg01` 一笔且 `teamId=null` |
| 风险用例 | 指定团参团/库存恢复 | `ITradeReverseStockServiceTest` | `test_lockMarketPayOrder_reverse` | app | MySQL、Redis、RabbitMQ | 硬编码 teamId=`23165018`，不得用于本轮关联流程 |
| 正向 D/E | 支付结算 | `TradeSettlementOrderServiceTest` | `test_settlementMarketPayOrder` | app | MySQL、Redis、RabbitMQ | 固定 `xfg01/303596099292` 且末尾无限等待；必须使用本轮真实订单数据才有业务意义 |
| 回调 | 回调网关 | `GroupBuyNotifyServiceTest` | `test_notify_api`、`test` | app | 8091 HTTP 服务 | 固定旧 teamId/outTradeNo；仅验证 HTTP 网关 |
| MQ | 生产/消费 | `ApiTest` | `test_rabbitmq` | app | RabbitMQ | 发布 5 条消息且无限等待 |
| Redis | 分布式锁 | `ApiTest` | `test_lock_thread_1`、`test_lock_thread_2` | app | Redis | lock key=`group_buy_market_notify_job_exec` |
| 基础 | Supplier 演示 | `ApiTest` | `test_Supplier` | app | 无 | 非业务测试 |
| 逆向 1/2/3 | 退单策略 | `ITradeRefundOrderServiceTest` | `test_refundOrder`、`test_refundOrder_01`、`test_refundOrder_02`、`test_refundOrder_03` | app | MySQL、Redis、RabbitMQ | 全部使用固定历史订单且无限等待；需与真实前置订单匹配 |
| 逆向任务 | 超时未支付退单 | `ITradeRefundOrderServiceTest` | `test_queryTimeoutUnpaidOrderList2Refund` | app | MySQL、Redis、RabbitMQ | 查询真实超时订单，逐笔退单，末尾无限等待 |
| 库存恢复 | 退单后恢复锁单量 | `ITradeReverseStockServiceTest` | `test_refundOrder` | app | MySQL、Redis、RabbitMQ | 固定 `xfg803/356654963071` 且无限等待 |

## 代码中未发现的独立现有测试

以下能力存在生产代码或运维配置，但没有对应的、可独立点名执行的现有 JUnit 方法：`queryNoPayMarketPayOrderByOutTradeNo`、`execSettlementNotifyJob(teamId)`、`GroupBuyNotifyJob`、退款 MQ 消费幂等、回调重试上限、Redis 故障降级、并发超卖、动态限流、ELK、Prometheus/Grafana、MCP/AI 分析。

当前仓库也没有 `OrderServiceTest#test_createOrder`；商城创建订单测试需要到 `s-pay-mall-ddd-market` 仓库核验。

## 既有测试的安全执行顺序

1. DAO/责任链基础测试。
2. 标签写入与读取。
3. 营销试算及营销 HTTP 接口。
4. 使用 `ITradeReverseStockServiceTest#test_lockMarketPayOrder` 验证首笔开团和后续真实 teamId 传递。
5. 从实际返回值/数据库提取本轮 `teamId`、`userId`、`outTradeNo` 后执行支付结算。
6. 验证 `group_buy_order`、`group_buy_order_list`、`notify_task`、Redis 和 RabbitMQ。
7. 分别准备未支付未成团、已支付未成团、已支付已成团订单，再执行匹配的退单策略和锁单量恢复。
8. 执行超时未支付退单任务、重复请求和非法数据等异常场景。
9. 联调可访问的商城仓库。
10. 最后用覆盖 Surefire 跳过配置的命令执行全量 Maven 回归。

## 执行限制

- 含无限 `CountDownLatch.await()` 的测试必须由外部超时控制并结合日志、数据库和中间件状态判定；不删除等待代码、不跳过断言、不吞异常。
- 硬编码历史 teamId/outTradeNo 的测试不能证明本轮端到端链路，必须先确认对应记录真实存在且关联正确。
- 现有测试几乎只记录日志、缺少断言；“Maven 通过”不等于业务通过，必须同时核验持久化和中间件状态。
