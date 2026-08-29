# 拼团项目最终版本定位报告

> 调查日期：2026-07-28  
> 当前工作区：`D:\workspace\java\group-buy-market-jiusi`  
> 当前分支/Commit：`2-20-xfg-redis-cache` / `e0140f7131558d1f80bbd109b1b5e9c7a800cc47`  
> 调查方式：只读 Git 引用、提交拓扑、`git show`/`git grep`/`git ls-tree` 代码证据、官方课程目录和官方部署文档；未切换当前工作区，未修改业务代码。  
> 官方课程目录：[拼团支付平台系统](https://bugstack.cn/md/project/group-buy-market/group-buy-market.html)

# 1. 最终结论

最终完整课程项目不是当前 `2-20-xfg-redis-cache` 分支，也不是单个仓库，而是以下两个仓库组合：

| 角色 | 官方仓库 | 推荐最终分支 | Commit SHA | 覆盖章节 |
|---|---|---|---|---|
| 拼团营销平台 | `KnowledgePlanet/group-buy-market` | `master` | `6b12ec69e82919d24865f30535778c6cb8fcb0b3` | 1-x、2-1 至 2-31、监控/MCP、拼团侧 3-x 对接、4-x 部署修复 |
| 小型支付商城 | `KnowledgePlanet/s-pay-mall-ddd-market` | `master`（与 `3-8-xfg-refund-intf-mq` 同 SHA） | `e53e378757861fe465ad7eda36532e4773a8dd71` | 3-3 至 3-8，含鉴权、订单列表、商城退款与 MQ 对接 |

**课程发布时的可复现 V3 部署快照**则是：

| 仓库 | 发布分支/Tag | Commit SHA | 说明 |
|---|---|---|---|
| `group-buy-market` | 分支 `tag-v3.0`；本地轻量 Tag `拼团v3.0` 指向同一提交 | `eaee133fbfc7b8561cfc9a07e7a62d36b9e2fcff` | 2-31 后的 V3 部署快照 |
| `s-pay-mall-ddd-market` | 分支 `tag-v3.0` | `24f8949637b6190377ed56cf8d32a9ad58446706` | 商城 V3 第三阶段部署快照 |

两套取法的含义不同：

- 要复现课程发布包和部署文档，使用两仓库的 `tag-v3.0`。
- 要审计当前官方维护后的最完整代码，使用两个仓库的 `master`。
- 拼团仓库 `master` 是 `tag-v3.0` 的后代并包含后续并发、时效、通知修复。
- 商城仓库 `master` 与 `tag-v3.0` 从共同业务提交分叉：`master` 合并了 3-7 UI 分支，`tag-v3.0` 增加发布部署文件。因此严格复现部署时用 `tag-v3.0`，日常看最终业务代码用 `master`。

# 2. 仓库身份与远端核验

## 2.1 当前用户工作仓库

- 路径：`D:\workspace\java\group-buy-market-jiusi`
- 配置远端：`git@gitcode.com:m0_53834312/market-jiusi.git`
- 当前分支：`2-20-xfg-redis-cache`
- 当前 SHA：`e0140f7131558d1f80bbd109b1b5e9c7a800cc47`
- 该用户远端的 `master` 已核验为 `6b12ec69e82919d24865f30535778c6cb8fcb0b3`。

## 2.2 本机官方拼团仓库克隆

- 路径：`D:\workspace\java\group-buy-market`
- 远端：`git@gitcode.net:KnowledgePlanet/group-buy-market.git`
- 对官方远端执行只读 `git ls-remote --heads --tags`，确认：
  - `master`：`6b12ec69e82919d24865f30535778c6cb8fcb0b3`
  - `tag-v3.0`：`eaee133fbfc7b8561cfc9a07e7a62d36b9e2fcff`
  - Tag `拼团v3.0`：`eaee133fbfc7b8561cfc9a07e7a62d36b9e2fcff`
  - 2-21 至 2-31 各阶段分支均仍存在。

## 2.3 小型支付商城仓库

- 本机路径：`D:\workspace\java\s-pay-mall-ddd-market`
- 当前用户 fork 远端：`git@gitcode.com:m0_53834312/small-pay-market.git`
- 官方仓库：`git@gitcode.net:KnowledgePlanet/s-pay-mall-ddd-market.git`
- 官方远端只读核验结果：
  - `3-3-250209-xfg-s-pay-mall-lock-order`：`45e2765d2ddea6e3ed662f681d9f4340b5dfc9fd`
  - `3-4-250215-xfg-s-pay-mall-settlement-order`：`721e5cc0ab2e765c07cf562a7d32ad31607ec6a4`
  - `3-5-250216-xfg-s-pay-mall-ui`：`27bd6156f4d8bdc141df0224723c24ad21cc7589`
  - `3-6-250223-xfg-s-pay-mall-fingerprintjs`：`731438dfadae02a5e5cfb21ce1cdd98410bc6ad9`
  - `3-7-xfg-order-list`：`710db0e3ddaeebae2ca0ebe011128fcc5228bd83`
  - `3-8-xfg-refund-intf-mq`：`e53e378757861fe465ad7eda36532e4773a8dd71`
  - `tag-v3.0`：`24f8949637b6190377ed56cf8d32a9ad58446706`
  - `master`：`e53e378757861fe465ad7eda36532e4773a8dd71`

当前用户 fork 的实时远端只剩 `2-18-xfg-mq-consumer`；本机 `origin/3-x` 跟踪引用是旧快照。上述最终 SHA 已直接向官方 KnowledgePlanet SSH 远端复核，因而不依赖用户 fork 的残留引用。

# 3. 全部分支、Tag 与提交关系

## 3.1 拼团仓库引用清单

已检查的本地分支与官方远端分支包括：

```text
241207-xfg-init-project
2-2-241214-xfg-design-framework
2-3-241221-xfg-multi-thread
2-4-241222-xfg-discount-calculate
2-5-241228-xfg-crowd-tags
2-6-250101-xfg-split-table
2-7-250102-xfg-tagNode
2-8-250104-xfg-dcc
2-9-250111-xfg-trade
2-10-250118-xfg-link-design
2-11-250125-xfg-trade-rule-link
2-12-250128-xfg-trade-settlement
2-12-250129-xfg-settlement-rule
2-13-250129-xfg-settlement-rule
2-14-250131-xfg-notify-job
2-15-250203-xfg-ui-http
2-16-250208-xfg-rabbitmq
2-17-xfg-task-mq
2-19-xfg-redis-lock
2-20-xfg-redis-cache
2-21-xfg-introduce-wrench
2-22-xfg-wrench-rate-limiter
2-23-xfg-elk-ai-mcp
2-24-xfg-grafana-ai-mcp
2-25-xfg-reverse-process-analysis
2-26-xfg-unpaid-2-refund
2-27-xfg-paid-2-refund
2-28-xfg-paid-team-2-refund
2-29-xfg-refund-2-reverse-stock
2-30-xfg-refund-design-refactor
2-31-xfg-refund-intf-job
3-1-250202-xfg-ui
3-2-250203-xfg-ui-2-interface
3-3-250209-xfg-s-pay-mall-lock-order
3-5-250216-xfg-s-pay-mall-ui
refactor-market-node-completeable-future
fix-time-count
tag-v1.0
tag-v2.0
tag-v3
tag-v3.0
master
```

本地 Git Tags：

```text
拼团v2.0 -> 33c10a40ea69cf8c49856f6e067cd99d7b21ea21
拼团v3.0 -> eaee133fbfc7b8561cfc9a07e7a62d36b9e2fcff
```

当前官方远端同时保留 `拼团v2.0`、`拼团v3.0` 两个 Tag。

## 3.2 拼团后半程主线

Git `merge-base --is-ancestor` 已逐段验证以下关系全部成立：

```text
e0140f7  2-20 Redis 缓存
  -> 083032a  2-21 扳手工程
  -> 69dab34  2-22 动态限流
  -> 36a962e  2-23 ELK + AI MCP
  -> c6f9fee  2-24 系统监控 + AI MCP
  -> a796218  2-26 未支付退单
  -> 50d410a  2-27 已支付未成团退单
  -> 42df46e  2-28 已支付已成团退单
  -> 90a75e0  2-29 锁单量恢复
  -> 6172653  2-30 退单设计模式重构
  -> 9d5e1f1  2-31 退订接口与定时任务
  -> eaee133  tag-v3.0 / 拼团v3.0
  -> 后续部署、时效、并发与通知修复
  -> 6b12ec6  master
```

`2-25-xfg-reverse-process-analysis` 与 `2-24-xfg-grafana-ai-mcp` 指向同一 SHA `c6f9fee...`，说明 2-25 是分析章节，没有单独代码提交。

## 3.3 小型支付商城引用清单

官方远端分支：

```text
2-16-250208-xfg-rabbitmq
2-18-xfg-mq-consumer
3-3-250209-xfg-s-pay-mall-lock-order
3-4-250215-xfg-s-pay-mall-settlement-order
3-5-250216-xfg-s-pay-mall-ui
3-6-250223-xfg-s-pay-mall-fingerprintjs
3-7-xfg-order-list
3-8-xfg-refund-intf-mq
tag-v2.0
tag-v3.0
master
```

关键业务提交链：

```text
c58c3ac / 45e2765  3-3 商城对接拼团锁单
  -> 721e5cc  3-4 商城对接营销结算
  -> 27bd615  3-5 商城 UI 与接口
  -> 8b77259 / 731438d  3-6 浏览器指纹、ticket、无痕登录
  -> ea8189b / 710db0e  3-7 用户订单列表和退单 UI
  -> 796ce56  3-8 退单退款服务对接
  -> e53e378  master / 3-8-xfg-refund-intf-mq
```

`3-8` 分支 tip 是一个合并提交，真正引入 3-8 退款业务的直接提交是：

```text
796ce56679c822a42fb56d9f659b2de081c55395
feat：《拼团交易平台系统》第3-8节：退单退款服务对接
```

# 4. 代码能力逐项证据

| 能力 | 所属仓库/首次明确阶段 | 最终版本代码证据 | 判断 |
|---|---|---|---|
| 退单责任链 | 拼团仓库 2-30 | `TradeRefundRuleFilterFactory`、`DataNodeFilter`、`UniqueRefundNodeFilter`、`RefundOrderNodeFilter` | 已实现 |
| 退单枚举策略 | 拼团仓库 2-26 至 2-30 | `RefundTypeEnumVO`，三种 `IRefundOrderStrategy` 实现 | 已实现 |
| 未支付退单 | 拼团仓库 2-26 | `Unpaid2RefundStrategy`、`TradeRepository.unpaid2Refund` | 已实现 |
| 已支付未成团退单 | 拼团仓库 2-27 | `Paid2RefundStrategy`、支付完成数量/状态更新 | 已实现 |
| 已支付已成团退单 | 拼团仓库 2-28 | `PaidTeam2RefundStrategy`、团退款通知任务 | 已实现 |
| 锁单量恢复 | 拼团仓库 2-29 | `reverseStock`、`doReverseStock`、`RefundSuccessTopicListener` | 已实现 |
| 退订接口 | 拼团仓库 2-31 | `IMarketTradeService.refundMarketPayOrder`、`MarketTradeController.refundMarketPayOrder` | 已实现 |
| 超时退订任务 | 拼团仓库 2-31 | `TimeoutRefundJob` | 已实现 |
| 动态限流 | 拼团仓库 2-22 | `@RateLimiterAccessInterceptor`、wrench rate-limiter starter、DCC 限流开关 | 已实现 |
| ELK | 拼团仓库 2-23 | ELK Compose、Logstash encoder、`TraceIdFilter`、Logback Logstash appender | 已配置 |
| Prometheus/Grafana | 拼团仓库 2-24 | Micrometer Prometheus、Actuator path、Prometheus/Grafana Compose 和 datasource | 已配置 |
| MCP/AI 分析 | 拼团仓库 2-23/2-24 与后续 master | `grafana-mcp` Compose 服务；官方说明 AI MCP 对接 ELK/Prometheus | 集成配置存在，不是仓库内独立 Java Agent 模块 |
| 鉴权域 | 商城仓库 3-6 | `domain/auth`、`IAuthService`、`WeixinLoginService`、`LoginController`、fingerprint/ticket UI | 已实现于商城 |
| 用户订单列表 | 商城仓库 3-7 | `QueryOrderList*DTO`、`query_user_order_list`、`order-list.html/js` | 已实现于商城 |
| 商城退单接口 | 商城仓库 3-7/3-8 | `IPayService.refundOrder`、`AliPayController.refund_order`、`OrderService.refundOrder` | 已实现于商城 |
| 退款服务对接拼团 | 商城仓库 3-8 | `IGroupBuyMarketService` 退款 DTO/接口、`ProductPort`、`RefundSuccessTopicListener` | 已实现 |
| 支付商城锁单/结算 | 商城仓库 3-3/3-4 | Retrofit `lock_market_pay_order`、`settlement_market_pay_order`，支付成功 Listener | 已实现 |
| 第三阶段部署 | 两仓库 tag-v3.0 | 拼团 `docs/tag/v3.0/tag-v3.0.md` 明确同时 clone 两仓库 | 两仓组合 |

## 4.1 拼团逆向流程的具体结构

`group-buy-market` 最终代码包含：

```text
TradeRefundOrderService
  -> TradeRefundRuleFilterFactory
       -> UniqueRefundNodeFilter
       -> DataNodeFilter
       -> RefundOrderNodeFilter
  -> RefundTypeEnumVO 选择策略
       -> Unpaid2RefundStrategy
       -> Paid2RefundStrategy
       -> PaidTeam2RefundStrategy
  -> notify_task / RabbitMQ
  -> RefundSuccessTopicListener
  -> reverseStock / 锁单量恢复
```

这与官方课程页对退单流程的描述一致：先走责任链，再按枚举策略执行退单，最后通过 MQ 驱动库存恢复。

## 4.2 商城与拼团的职责边界

`group-buy-market` 负责：

- 营销试算、锁单和成团。
- 退单业务分类、团/营销订单状态和团锁单量恢复。
- 退单通知任务和超时自动退订。

`s-pay-mall-ddd-market` 负责：

- 微信 ticket/浏览器指纹登录和用户身份。
- 商城支付订单、支付页面、用户订单列表。
- 用户发起商城退款。
- 调用拼团退订接口。
- 接收拼团退款成功 MQ，更新商城订单并对接支付退款流程。

因此“鉴权域、用户订单列表、退款服务对接”不应在拼团仓库中寻找，它们设计上属于商城边界。

# 5. 是否存在第三个业务仓库

没有发现另一个必须参与最终业务闭环的“第二个拼团仓库”。

本机存在：

- `group-buy-market-jiusi`：用户 fork/学习工作仓库。
- `group-buy-market`：同一官方拼团仓库的另一份本地克隆。
- 若干 `group-buy-market-jiusi - ...` 副本：本地副本，不是独立课程系统。

最终闭环只需要两个业务仓库：

1. `group-buy-market`
2. `s-pay-mall-ddd-market`

另外还有外部组件/服务依赖，但不是第三个业务仓库：

- `xfg-wrench` Starter/BOM：设计框架、动态配置、限流组件。
- MySQL、Redis、RabbitMQ。
- Elasticsearch、Logstash、Kibana。
- Prometheus、Grafana。
- `grafana-mcp` 容器。
- 微信与支付平台接口。

# 6. 课程章节与版本映射

| 章节 | 拼团仓库版本 | 商城仓库版本 | 主要产物 |
|---|---|---|---|
| 2-20 | `e0140f7` | 不需要 | 函数式缓存与 DB 降级 |
| 2-21 | `083032a` | 不需要 | 引入 wrench 组件 |
| 2-22 | `69dab34` | 不需要 | 动态限流 |
| 2-23 | `36a962e` | 不需要 | ELK、Trace、AI MCP 检索环境 |
| 2-24/2-25 | `c6f9fee` | 不需要 | Prometheus/Grafana/MCP；逆向分析 |
| 2-26 | `a796218` | 不需要 | 未支付退单 |
| 2-27 | `50d410a` | 不需要 | 已支付未成团退单 |
| 2-28 | `42df46e` | 不需要 | 已支付已成团退单 |
| 2-29 | `90a75e0` | 不需要 | 退单锁单量恢复 |
| 2-30 | `6172653` | 不需要 | 责任链+枚举策略重构 |
| 2-31 | `9d5e1f1` | 不需要 | 退订 API、超时任务 |
| 3-1/3-2 | 对应 UI 分支 | 不需要/早期页面 | DeepSeek UI 和接口 |
| 3-3 | 拼团端已有锁单 API | `45e2765` | 商城调用营销锁单 |
| 3-4 | 拼团端已有结算 API | `721e5cc` | 商城支付后营销结算 |
| 3-5 | `6d4644c` 等 UI 配套 | `27bd615` | 商城 UI 联调 |
| 3-6 | 部署 UI 配套 | `731438d` | 指纹、ticket、无痕登录 |
| 3-7 | 最终 UI 配套 | `710db0e`/`ea8189b` | 订单列表和退单 UI |
| 3-8 | `9d5e1f1` 及其后继 | `796ce56`，最终 tip `e53e378` | 退款服务、MQ 双向对接 |
| 4-1/4-2 | tag-v1.0/tag-v2.0 | tag-v1.0/tag-v2.0 | 阶段部署 |
| 4-3 | `eaee133` | `24f8949` | V3 两仓完整部署 |

# 7. 最终版本选择建议

## 7.1 用于重新做全面审计

建议组合：

```text
group-buy-market:
  branch: master
  sha: 6b12ec69e82919d24865f30535778c6cb8fcb0b3

s-pay-mall-ddd-market:
  branch: master
  sha: e53e378757861fe465ad7eda36532e4773a8dd71
```

理由：覆盖全部课程能力，并包含课程发布后的拼团时效、并发和通知修复。

## 7.2 用于严格复现课程 V3 部署

建议组合：

```text
group-buy-market:
  branch: tag-v3.0
  sha: eaee133fbfc7b8561cfc9a07e7a62d36b9e2fcff

s-pay-mall-ddd-market:
  branch: tag-v3.0
  sha: 24f8949637b6190377ed56cf8d32a9ad58446706
```

证据：`group-buy-market` 的 `docs/tag/v3.0/tag-v3.0.md` 明确要求分别 clone 这两个官方仓库的 `tag-v3.0`。

# 8. 安全的 worktree 检出建议

本次没有执行以下命令。建议在项目根目录之外创建只读审计 worktree，避免切换或污染当前 `2-20` 工作区。

```powershell
# 拼团最新维护版
git -C D:\workspace\java\group-buy-market-jiusi worktree add `
  D:\workspace\java\audit-worktrees\group-buy-market-master `
  6b12ec69e82919d24865f30535778c6cb8fcb0b3

# 商城最终业务版
git -C D:\workspace\java\s-pay-mall-ddd-market worktree add `
  D:\workspace\java\audit-worktrees\s-pay-mall-master `
  e53e378757861fe465ad7eda36532e4773a8dd71
```

严格复现课程 V3 时，把 SHA 替换为：

```text
group-buy-market: eaee133fbfc7b8561cfc9a07e7a62d36b9e2fcff
s-pay-mall:       24f8949637b6190377ed56cf8d32a9ad58446706
```

建议使用 detached worktree（直接指定 SHA），避免创建或移动分支。检出后仍应先做秘密扫描，不要直接启动，因为历史部署目录中可能包含示例 Token、支付表单、默认凭据或测试交易数据。

# 9. 结论可信度与限制

高可信结论：

- 官方课程目录确实覆盖 2-21 至 2-31、3-1 至 3-8 和 4-x。
- 拼团官方远端 `master`/`tag-v3.0` SHA 已实时核验。
- 商城官方远端 `master`/`3-8`/`tag-v3.0` SHA 已实时核验。
- 所列业务能力均由具体类、方法、配置或文件树证据确认，不依赖分支名称猜测。
- 完整系统由拼团平台和小型支付商城两个仓库组成。

限制：

- 本次没有构建或启动最终两仓版本。
- 没有调用支付、微信、ELK、Prometheus、Grafana 或 MCP 服务。
- MCP/AI 部分确认的是部署与集成配置；未发现仓库内独立实现的 Java Agent/MCP 分析模块。
- 用户 fork 的商城远端分支已大量删除，但官方 KnowledgePlanet 远端仍保留完整课程分支。

# 10. 对原审计报告的修正

先前基于 `2-20-xfg-redis-cache` 得出的“取消、退款、超时关团、鉴权、订单列表缺失”只适用于第 2-20 节快照，不能代表课程最终项目。

对最终版本应修正为：

- 拼团逆向退单、超时退订、锁单量恢复：已在 2-26 至 2-31 实现。
- 动态限流、ELK、Prometheus、Grafana、MCP 集成：已在 2-21 至 2-24 及后续 master 实现。
- 鉴权域、用户订单列表、商城退款：已在 `s-pay-mall-ddd-market` 3-6 至 3-8 实现。
- 完整运行和 Agent readiness 仍需基于两个 `master` SHA 重新构建、测试与安全审计。
