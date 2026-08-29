# 拼团项目 Agent 建设前置审计报告

> 审计日期：2026-07-28  
> 审计分支：`2-20-xfg-redis-cache`  
> 审计方式：只读源码、配置键、SQL、测试与部署文件；未启动应用、未连接数据库/Redis/RabbitMQ、未调用外部业务接口。  
> 工作区基线：审计开始前 `group-buy-market-app/src/main/resources/application-dev.yml` 已有用户修改，本次未修改该文件，也未披露其中的值。

# 1. 执行摘要

本项目是一个基于 DDD 分层的拼团营销服务：按来源、渠道和商品匹配活动，完成优惠试算、开团/参团锁单、支付后结算、成团通知，并提供简单静态前端演示。核心证据为 `MarketIndexController`、`MarketTradeController`、`TradeLockOrderService`、`TradeSettlementOrderService` 和 `TradeRepository`。

**静态完成度：约 58%。** 估算口径：核心业务闭环 45%、工程可运行性 15%、测试 15%、安全与权限 15%、运维可观测性 10%。活动试算、锁单、支付结算和通知主体存在，但取消、退款、超时失败、库存释放、身份鉴别、支付回调验签、可靠事件与可观测性缺失；构建是否成功需以运行验证报告为准。

**当前是否可以运行：未知/待验证。** 仓库具备 Spring Boot 入口、Maven 多模块、MySQL/Redis/RabbitMQ Compose 和 Dockerfile，但运行依赖真实基础设施，README 不是项目运行手册，配置存在环境耦合。

最主要的 5 个阻塞项：

1. **无鉴权和授权边界。** 所有业务接口及 DCC 写接口均未发现 Spring Security、过滤器或权限注解；DCC 还用 GET 改状态。证据：`group-buy-market-trigger/.../DCCController.java:18-37`。
2. **支付结算入口无可信回调校验。** 客户端可直接提交 `userId/source/channel/outTradeNo/outTradeTime` 触发结算，未发现签名、重放窗口、支付金额/商户核对。证据：`MarketTradeController.java:183-209`。
3. **拼团生命周期不完整。** 未发现取消、退款、超时关单、失败团、未支付库存释放实现；枚举虽有 `CLOSE`/`FAIL`，没有迁移代码。
4. **关键并发与幂等约束不完整。** `out_trade_no`、`biz_id` 在当前 SQL 中无唯一键；“最后一人成团”基于事务前读取的 `completeCount` 判断，存在并发漏成团风险。证据：`TradeRepository.java:239-281`、`group_buy_order_list` DDL。
5. **缺乏可自动验证的测试与运维基线。** Surefire 默认 `skipTests=true`；测试几乎全部是 `@SpringBootTest` 且未发现断言，依赖外部组件；未发现 Actuator、Metrics、Trace、CI 或告警配置。

**是否适合现在接入 Agent：适合做只读诊断型 MVP，不适合让 Agent 直接执行交易写操作。** 最合适的是“拼团客服与订单诊断 Agent”：只读聚合订单、团进度、活动和通知状态，输出解释与升级建议。退款、改价、DCC、强制成团等写操作在 P0 安全与一致性整改前不应暴露。

# 2. 技术栈与运行方式

| 类别 | 识别结果 | 证据 |
|---|---|---|
| 语言 | Java 8 目标版本 | 根 `pom.xml` 的 `java.version`、`maven.compiler.source/target` |
| 框架 | Spring Boot 2.7.12、Spring MVC、Spring TX、Scheduling | 根/模块 POM；`Application.java` |
| ORM | MyBatis 2.1.4 | 根 POM、`resources/mybatis/mapper` |
| 数据库 | MySQL 8.x | MySQL connector 8.0.22、Compose、SQL |
| 缓存/锁 | Redis + Redisson 3.26.0 | `RedisClientConfig`、`RedissonService` |
| 消息队列 | RabbitMQ / Spring AMQP | `RabbitMQConfig`、`EventPublisher`、`TeamSuccessTopicListener` |
| HTTP 客户端 | OkHttp 3.14.9 | `OKHttpClientConfig`、`GroupBuyNotifyService` |
| 前端 | 静态 HTML/CSS/JS，由 Nginx 部署 | `docs/ui/html`、`docs/dev-ops/nginx/html` |
| 构建 | Maven 多模块 | 根 `pom.xml` |
| 容器 | Docker、Docker Compose | `group-buy-market-app/Dockerfile`、`docs/dev-ops/docker-compose-*.yml` |
| K8s/CI | 未发现 | 仓库文件扫描 |
| 搜索 | 未发现 Elasticsearch 等 | POM/源码扫描 |

配置通过 `application.yml` 选择 profile，并由 `application-dev.yml`/`prod`/`test` 及环境变量提供端口、数据源、RabbitMQ、Redis、线程池和日志配置。配置文件及 Compose 中存在明文默认凭据线索；本报告不记录值。

建议本地启动步骤（待验证）：

1. 使用与目标兼容的 JDK（优先 JDK 8 或项目明确支持的版本）。
2. 修复本机 Maven settings，并从根目录执行 `mvn clean package`；注意当前 App POM 默认跳过测试。
3. 在隔离环境启动 MySQL、Redis、RabbitMQ，导入与当前分支匹配的 SQL。
4. 使用 `dev` profile 启动 `group-buy-market-app`，确认配置不指向共享/生产资源。
5. 访问 HTTP API；静态前端可通过 Nginx 或本地静态服务器提供。

测试命令候选：`mvn test -DskipTests=false`。现有测试为外部依赖型 Spring 集成测试，执行前必须隔离数据库、缓存、MQ 和回调地址。

无法确认的运行依赖：准确 JDK/Lombok 兼容组合、当前分支对应 SQL 版本、RabbitMQ 拓扑初始化、测试数据、回调目标、前后端联调地址、生产密钥注入方式。

# 3. 仓库与模块地图

| 模块/目录 | 职责 | 关键入口 | 依赖 | 当前状态 | 证据 |
|---|---|---|---|---|---|
| `group-buy-market-api` | 对外接口、DTO、统一响应 | `IMarketIndexService`、`IMarketTradeService`、`IDCCService` | Lombok、Validation API | 基本完成，DTO 校验未启用 | 模块 POM、接口源码 |
| `group-buy-market-app` | 启动、配置、Mapper、测试、打包 | `Application` | trigger、infrastructure | 基本完成，环境耦合明显 | App POM、resources |
| `group-buy-market-domain` | 活动、折扣、标签、交易领域逻辑 | `IndexGroupBuyMarketServiceImpl`、`TradeLockOrderService`、`TradeSettlementOrderService` | types | 核心主链已实现，逆向流程缺失 | domain 源码 |
| `group-buy-market-trigger` | HTTP、定时任务、MQ 监听 | 三个 Controller、`GroupBuyNotifyJob` | api、domain、types | 接口可见但无安全层 | trigger 源码 |
| `group-buy-market-infrastructure` | 仓储、DAO、Redis、MQ、HTTP 网关、DCC | `ActivityRepository`、`TradeRepository`、`TradePort` | domain | 主体实现，可靠性边界不足 | infrastructure 源码 |
| `group-buy-market-types` | 枚举、异常、通用设计框架 | 状态枚举、责任链/策略树 | 通用库 | 已实现 | types 源码 |
| `docs/dev-ops` | MySQL/Redis/RabbitMQ/Nginx/Compose | Compose、SQL | Docker | 可用于演示，默认凭据与版本一致性待治理 | dev-ops 文件 |
| `docs/ui`、`docs/tag` | 静态 UI 与历史版本材料 | HTML/JS、版本 SQL | Nginx | 演示材料，非完整前端工程 | docs |

依赖方向总体符合 API/Trigger → Domain ← Infrastructure 的 DDD 分层，但 `app` 负责最终组装。

# 4. 拼团核心业务流程

## 4.1 商品与 SKU — 基本完成

`source + channel + goodsId` 查询 SKU 与活动映射 → 获取有效活动/折扣 → 策略树检查开关、人群标签 → 四种折扣算法计算优惠。证据：`MarketNode`、`TagNode`、`SwitchNode`、`ActivityRepository`、`*CalculateService`。

## 4.2 创建拼团/开团 — 基本完成

`POST /api/v1/gbm/trade/lock_market_pay_order` → 参数检查 → 活动试算 → 锁单责任链（活动可用性、参与次数、团库存）→ 无 `teamId` 时生成 8 位数字团 ID → 插入 `group_buy_order` 和 `group_buy_order_list`。证据：`MarketTradeController.java:51-178`、`TradeRepository.java:86-168`。

缺口：随机 ID 碰撞依赖数据库异常；首开团不走 Redis 团库存；`outTradeNo` 未作为必填校验；`notifyConfigVO` 可空导致 NPE。

## 4.3 参加拼团 — 基本完成

有 `teamId` 时 Redis 原子计数抢占名额，再以 SQL `lock_count < target_count` 原子更新兜底，随后插入个人订单明细；数据库失败会记录 Redis 恢复量。证据：`TeamStockOccupyRuleFilter`、`TradeRepository.java:128-160,359-390`。

## 4.4 团人数与成团条件 — 部分完成

`target_count` 为目标人数，`lock_count` 为锁单人数，`complete_count` 为支付完成人数。锁单满员和支付满员分别由条件更新限制。成团判断使用“事务前读取的 `targetCount - completeCount == 1`”，并发回调可能都基于旧值判断而漏迁移。

## 4.5 拼团价与优惠计算 — 基本完成

支持直减、折扣、满减、固定价四类算法；价格落入订单快照。证据：`DiscountTypeEnum`、`ZJ/ZK/MJ/NCalculateService`、`group_buy_order_list` 字段。

缺口：未发现货币精度统一策略、价格配置管理接口、负数/异常表达式的系统性校验测试。

## 4.6 库存预占、扣减、释放 — 部分完成

实现的是“团名额”Redis 预占和数据库 `lock_count`，不是商品 SKU 实物库存。数据库插入失败时增加恢复量；未发现未支付超时、取消、团失败后的释放。不要将其等同于商品库存闭环。

## 4.7 订单创建 — 基本完成

团主表与明细表在一个 `@Transactional` 方法内写入。订单快照包含活动、商品、价格、外部单号和业务 ID。

## 4.8 支付 — 仅结算通知入口

系统不负责支付下单，只接收外部支付完成数据并做营销结算。未发现支付平台 SDK、签名验证、金额核对或支付状态实体。

## 4.9 成团 — 部分完成

支付明细变为完成 → 团 `complete_count + 1` → 最后一人时团状态改为完成 → 写通知任务。数据库事务能保证这些数据库写原子提交，但并发判定存在漏迁移风险。

## 4.10 团失败/超时关闭 — 未实现

虽有 `GroupBuyOrderEnumVO.FAIL` 和 `TradeOrderStatusEnumVO.CLOSE`，未找到写入这些状态的业务代码或定时任务。

## 4.11 取消 — 未实现

未找到 Controller、Service、Repository 或 Mapper 的取消流程。

## 4.12 退款 — 未实现

未找到退款状态、退款接口、幂等键或外部退款集成。

## 4.13 通知 — 基本完成

成团时写 `notify_task`；提交后线程池尝试立即 HTTP/MQ 通知；失败任务由每日定时任务扫描补偿。`TradePort` 使用 Redis 锁减少重复执行。

缺口：HTTP URL 来自请求且无白名单，形成 SSRF 风险；MQ 消费者仅记录日志；回调无签名；任务状态更新无前置状态条件；没有指数退避、死信告警。

## 4.14 定时任务/延迟消息 — 部分完成

仅发现每日 0 点的通知补偿任务；未发现团超时关闭、未支付订单释放或延迟队列。

# 5. 业务状态机

| 对象 | 状态 | 已发现迁移 | 触发方 | 幂等/异常 |
|---|---|---|---|---|
| 活动 | CREATE(0)、EFFECTIVE(1)、OVERDUE(2)、ABANDONED(3) | 未发现管理迁移；锁单仅允许 EFFECTIVE 且在时间窗内 | 运营/数据初始化，未知 | 状态管理未知 |
| 团 | PROGRESS(0)、COMPLETE(1)、FAIL(2) | PROGRESS → COMPLETE | 最后一笔支付结算 | SQL 要求原状态 0；并发判定可能漏迁移；FAIL 无实现 |
| 交易明细 | CREATE(0)、COMPLETE(1)、CLOSE(2) | CREATE → COMPLETE | 外部支付结算 | `WHERE status=0` 提供回调幂等；CLOSE 无实现 |
| 通知 | WAIT(0)、SUCCESS(1)、RETRY(2)、ERROR(3)，由 SQL/代码推断 | WAIT/RETRY → SUCCESS/RETRY/ERROR | 即时线程/定时任务 | Redis 锁减重，但 DB 更新无状态 CAS |
| 支付 | 无独立状态机 | 仅以交易明细 COMPLETE 表示已消费 | 外部系统 | 未验签、未核金额 |
| 退款 | 无 | 无 | 无 | 缺失 |
| 商品库存 | 无 | 无 | 外部商品系统，未知 | 本仓库仅团名额 |

状态定义分散在 types、domain 和 SQL 魔法数字中。`TradeOrderStatusEnumVO.valueOf` 对未知状态静默返回 CREATE，可能掩盖脏数据。团状态读取与写入之间缺乏版本号/行锁/基于计数的原子迁移。

# 6. 数据模型与数据库

核心关系：

```text
sku --(source,channel,goods_id)--> sc_sku_activity --> group_buy_activity
group_buy_activity --discount_id--> group_buy_discount
group_buy_activity --tag_id--> crowd_tags --> crowd_tags_detail
group_buy_activity 1---N group_buy_order 1---N group_buy_order_list
group_buy_order 1---0..1 notify_task
```

| 表 | 关键键/索引 | 主要风险 |
|---|---|---|
| `sku` | PK `id`；UK `goods_id` | source/channel 与 goods 唯一规则是否充分待确认 |
| `sc_sku_activity` | UK `(source,channel,goods_id)` | 无 FK |
| `group_buy_activity` | UK `activity_id` | 无状态/时间 CHECK、无版本号 |
| `group_buy_discount` | UK `discount_id` | 表达式合法性依赖代码 |
| `group_buy_order` | UK `team_id` | 无 `(status,valid_end_time)` 索引；无 CHECK 保证计数关系 |
| `group_buy_order_list` | UK `order_id`；索引 `(user_id,activity_id)` | `out_trade_no`、`biz_id` 无唯一键；常用 team/status 查询索引不足 |
| `notify_task` | UK `team_id` | 无状态+更新时间索引；无租约/下次执行时间 |
| 人群标签三表 | tag、tag+user、batch 唯一 | Redis bitmap 与 DB 同步机制需验证 |

表普遍有 `create_time/update_time`，但未发现创建人、修改人、请求 ID、版本号、软删除或外键。逻辑关联丰富但数据库约束少，孤儿数据和跨表不一致主要依靠应用事务。

高风险约束缺失：

- 外部交易单号至少应按业务域建立唯一约束。
- 代码注释称 `biz_id` 用唯一索引限制参与次数，但当前审计 SQL 未发现其唯一键。
- 计数应满足 `0 <= complete_count <= lock_count <= target_count`，数据库无 CHECK。
- 团、订单和通知需要版本/CAS 条件或明确的锁策略。

# 7. 接口与权限

| 接口 | 类型 | 当前鉴权 | 校验 | Agent 分类 |
|---|---|---|---|---|
| `POST /api/v1/gbm/index/query_group_buy_market_config` | 用户只读 | 未发现 | 手工非空 | A |
| `POST /api/v1/gbm/trade/lock_market_pay_order` | 用户写 | 未发现 | 不完整 | C |
| `POST /api/v1/gbm/trade/settlement_market_pay_order` | 内部高风险写 | 未发现/未验签 | 手工非空 | D（整改前） |
| `GET /api/v1/gbm/dcc/update_config` | 管理写 | 未发现 | 任意 key/value | D |
| `POST /api/v1/test/group_buy_notify` | 测试接口 | 未发现 | 无 | D（生产应禁用） |

所有 Controller 使用 `@CrossOrigin("*")`。API 模块虽依赖 Validation API，但 DTO 未发现 Bean Validation 注解，Controller 也未使用 `@Valid`。`userId` 由请求体直接提供，存在水平越权/身份伪造。未发现限流、角色、审计日志或敏感操作审批。

适合封装为 Agent Tool 的接口必须另建只读服务层，不能直接复用当前无鉴权 Controller。分类：

- A：活动解释、优惠试算解释、团进度、订单只读诊断、通知状态查询。
- B：创建客服工单、增加内部备注（当前不存在，需新建且幂等）。
- C：重新发送通知、活动配置草案提交，必须审批且限制范围。
- D：任意 DCC 修改、直接结算、退款、改价、任意 URL 回调、任意 SQL。

# 8. 并发、一致性与幂等

| 场景 | 当前实现与证据 | 风险 |
|---|---|---|
| 多用户同时参团 | Redis INCR+占位锁；DB `lock_count < target_count` | 中；双层控制但 Redis 恢复模型复杂 |
| 最后一人成团 | 先读 completeCount，后更新并按旧值判断 | **高：并发支付可能漏成团** |
| 超卖 | 团名额有 Redis+SQL；商品库存无实现 | 团名额中；商品库存高/外部未知 |
| 重复支付回调 | 明细 `UPDATE ... WHERE status=0`，失败回滚 | 中低；DB 幂等有效，但入口无验签/重放保护 |
| 重复参团/锁单 | 先查未支付订单；参与次数计数；插入捕获 DuplicateKey | **高：outTradeNo/bizId 缺唯一键，检查-写有竞态** |
| 重复退款 | 无退款 | 高/缺失 |
| 团超时与支付并发 | 仅校验支付时间小于团结束；无关团任务 | 高 |
| 消息重复消费 | Listener 只日志，无业务幂等 | 中，当前无副作用但能力未落地 |
| 通知重复执行 | Redis 团级锁；通知任务持久化 | 中；锁释放后对端超时可能重复，缺业务幂等键/签名 |
| 数据库事务 | 锁单与结算仓储方法有 `@Transactional(timeout=500)` | 中；事务边界明确但超时过长 |
| 乐观/悲观锁 | 未发现 version 或 `FOR UPDATE` | 高 |
| Outbox/补偿 | `notify_task` 类似业务 Outbox | 中；未形成严格发布确认/状态 CAS/退避闭环 |

额外问题：`TradePort` 捕获任何异常后调用 `Thread.currentThread().interrupt()`，即使异常不是中断，也会污染线程中断状态并把结果归为 NULL；通知执行器的异常处理需重构。

# 9. 外部依赖与工具能力

| 依赖 | 真实接入 | 失败策略 | Agent 安全性 |
|---|---|---|---|
| MySQL | 是，MyBatis | 事务回滚；连接池 | 只允许参数化只读查询服务，不开放 SQL |
| Redis/Redisson | 是 | 库存恢复量、分布式锁 | 只读指标可用；禁止任意 key 操作 |
| RabbitMQ | 是 | 发布异常返回 NULL；定时补偿 | 经固定 routing key 的受控 Tool 可考虑 |
| HTTP 回调 | 是 | OkHttp 异常转业务异常；任务重试 | 当前不安全，URL 可控且无签名/白名单 |
| 支付 | 未真实接入 | 仅接收结算数据 | Agent 不可直接调用 |
| 短信/邮件/对象存储/搜索 | 未发现 | 未知 | 不适用 |
| 静态前端/Nginx | 演示接入 | 未见前端错误治理 | 不作为 Agent Tool |

未发现明确的 HTTP connect/read/write timeout 配置、熔断器或统一重试策略。RabbitMQ producer confirm、return callback、DLQ 未确认。

# 10. 测试与完成度

仓库有约 13 个测试类、约 24 个 `@Test` 方法。大部分使用 `@SpringBootTest`，会加载完整上下文并依赖 MySQL/Redis/RabbitMQ/HTTP；扫描未发现断言。App POM 的 Surefire 配置默认 `skipTests=true`，所以普通 `mvn test/package` 可能给出误导性的“成功但未测试”。

未发现：真正隔离的单元测试、Testcontainers、并发测试、状态机测试、支付重放/验签测试、安全测试、E2E 自动化、覆盖率配置。

| 功能 | 状态 | 后端 | 前端 | 数据库 | 测试 | 可演示性 | 证据 | 缺口 |
|---|---|---|---|---|---|---|---|---|
| 活动/SKU 查询 | 基本完成 | 有 | 有静态页 | 有 | 集成样例 | 中 | ActivityRepository | 鉴权、异常测试 |
| 人群标签 | 基本完成 | 有 | 未确认 | 有 | 集成样例 | 中 | TagService/Redis bitmap | 同步一致性 |
| 优惠试算 | 基本完成 | 有四算法 | 有 | 有 | 集成样例 | 高 | discount services | 边界断言 |
| 开团/参团锁单 | 基本完成 | 有 | 有请求代码 | 有 | 集成样例 | 中 | TradeLockOrderService | 幂等唯一键 |
| 支付结算 | 部分完成 | 有入口 | 未确认 | 有 | 集成样例 | 中 | TradeSettlementOrderService | 验签、并发 |
| 成团通知 | 部分完成 | HTTP/MQ | 无 | task 表 | 集成样例 | 中 | TradePort/NotifyJob | SSRF、签名、可靠性 |
| 取消/超时关闭 | 未实现 | 无 | 无 | 状态字段有 | 无 | 否 | 全仓搜索 | 全流程 |
| 退款 | 未实现 | 无 | 无 | 无状态 | 无 | 否 | 全仓搜索 | 全流程 |
| 管理活动 | 仅骨架/占位 | 仅 DCC | 无管理端 | 活动表有 | 无 | 低 | DCCController | 正式 RBAC/审计 |
| 可观测性 | 仅骨架/占位 | 日志 | 无 | 无 | 无 | 低 | logback | metrics/trace/alert |

# 11. 可观测性与运维

已有：SLF4J/Logback 日志、Docker JSON 日志轮转、容器和基础设施 Compose、MySQL/Redis healthcheck。

未发现：Spring Boot Actuator、标准健康端点、Micrometer/Prometheus、分布式 Trace、业务指标、告警规则、结构化审计日志、部署流水线、K8s、自动回滚、数据备份方案。

Agent Evaluation 前至少补充：

- `order_lock_requested/succeeded/rejected`，带拒绝原因而不带敏感值。
- `payment_settlement_received/deduplicated/rejected/succeeded`。
- `team_completed/team_expired/team_failed`。
- `notify_attempt/succeeded/retried/dead`。
- 团成团率、平均成团时长、超时率、重复回调率、库存补偿量。
- Agent Tool 调用日志、策略拒绝、人工审批、答案引用命中率、升级人工率。

# 12. 安全审计

## Critical

- **未发现 Critical 级已证实的任意代码执行或硬编码生产密钥。** 但配置文件中的真实值未在报告中披露，仍需由秘密扫描工具在受控环境验证。

## High

- 无鉴权的 DCC 写接口，可通过 GET 修改任意 key/value，且日志记录 value。证据：`DCCController.java:29-43`。
- 支付结算入口无签名、身份绑定、金额核对或重放窗口。证据：`MarketTradeController.java:183-209`。
- HTTP 通知 URL 来自锁单请求并直接交给 OkHttp，未见协议/主机白名单，形成 SSRF。证据：`LockMarketPayOrderRequestDTO`、`TradeRepository.java:121-123`、`GroupBuyNotifyService.java`。
- 无身份体系，客户端直接声明 `userId`，所有接口 `@CrossOrigin("*")`。
- Compose/配置存在明文默认凭据线索，不应进入生产或公开仓库。

## Medium

- 锁单日志序列化整个请求，可能记录用户 ID、外部单号、回调地址。
- Test Controller 在主源码中，生产可能暴露。
- DTO 无 Bean Validation；`notifyConfigVO` 空指针和枚举非法值会退化为通用错误。
- `outTradeNo/bizId` 缺唯一约束，影响幂等和参与限制。
- 使用较旧依赖与插件，包括 OkHttp、XStream、dom4j、jjwt、Maven compiler/surefire；仅凭版本不能断言漏洞，需执行 SCA。
- 回调无 HMAC/签名、时间戳、nonce；对端也无法可靠去重。

## Low

- 全局异常转统一通用错误，缺请求 ID，不利于安全审计。
- 状态数字散落在 Mapper，状态契约不集中。
- `TradeOrderStatusEnumVO` 对未知值回落 CREATE，掩盖异常。

SQL 注入方面，已检查 Mapper 使用 `#{}` 参数绑定，未发现 `${}` 动态拼接证据；结论仅限当前扫描范围。

# 13. Agent 接入机会清单

| 候选 | 痛点/为何 Agent | 输入/输出 | Tool 与数据 | 范式 | 风险 | 价值/难度/优先级 |
|---|---|---|---|---|---|---|
| 拼团客服与订单诊断 Agent | 信息跨活动、团、订单、通知；规则可判定但自然语言解释与多轮追问适合 Agent | 用户问题+授权身份 → 带证据诊断和下一步 | 订单、团进度、活动规则、通知只读；FAQ RAG；短会话 Memory | Routing、Tool Use、RAG、Guardrails、HITL、Evaluation | 越权、幻觉 | 高/中/P1 |
| 拼团运营分析 Agent | 运营需组合成团率、渠道、人群、时段并解释异常 | 时间窗/活动 → 指标、归因假设、行动建议 | 只读聚合指标；指标字典 RAG | Planning、Tool Use、Prioritization、Reflection | 统计口径误导 | 高/中/P2 |
| 异常团/风险团处置建议 Agent | 并发、通知、临期团需要跨信号诊断 | 异常团列表 → 风险分类和建议 | 只读状态/日志/指标；工单写入需审批 | Routing、Exception Handling、HITL | 错误处置 | 中高/高/P2 |
| 活动配置辅助 Agent | 折扣表达式和活动约束易错；自然语言转草案有价值 | 活动目标 → 配置草案+模拟 | 规则校验、价格模拟、配置 schema RAG | Prompt Chaining、Tool Use、Guardrails | 错价 | 中/中/P2；只生成草案 |
| 成团促进与召回 Agent | 根据临期程度和用户偏好生成文案 | 候选团/用户同意 → 分群与文案 | 只读候选、合规偏好；发送需审批/频控 | Planning、Memory、Guardrails、HITL | 骚扰、隐私 | 中/高/P3 |
| 开发运维诊断 Agent | 当前缺少统一排障入口 | 错误/traceId → 证据化根因候选 | 日志、指标、健康、部署只读 | Tool Use、RAG、Reflection、Exception Handling | 日志泄密 | 中高/高/P2 |

普通规则足够的部分：价格计算、状态迁移、库存扣减、支付验签、权限判断、退款金额、限流和幂等必须保持确定性代码。Agent 只负责理解意图、选择只读工具、组织证据和提出建议。

# 14. Agent Tool 候选目录

| Tool 名称 | 业务含义 | 现有代码入口 | 输入 | 输出 | 读/写 | 风险 | 幂等要求 | 权限 | 人工确认 |
|---|---|---|---|---|---|---|---|---|---|
| `get_market_config` | 查询商品活动与价格 | `queryGroupBuyMarketConfig` | 身份上下文、SC、goodsId | 活动、价格、团列表 | 读 | A | 无 | 用户本人/公开活动 | 否 |
| `get_team_progress` | 查询团进度 | `queryGroupBuyProgress` | teamId | target/lock/complete/status/time | 读 | A | 无 | 团成员/客服 | 否 |
| `get_order_diagnosis` | 聚合订单诊断 | 需新增只读 facade | subjectId、outTradeNo/orderId | 脱敏状态与原因码 | 读 | A | 无 | 本人/授权客服 | 否 |
| `get_activity_rules` | 解释活动资格/时限 | Activity Repository/规则链 | activityId、用户上下文 | 规则与判定证据 | 读 | A | 无 | 运营/客服 | 否 |
| `get_notify_status` | 查询成团通知状态 | Notify DAO，需只读封装 | teamId | 次数、状态、最后时间 | 读 | A | 无 | 内部客服 | 否 |
| `create_support_case` | 创建工单 | 当前缺失 | 幂等键、摘要、证据引用 | caseId | 写 | B | 必须 | 客服 | 可按策略 |
| `retry_team_notification` | 重试固定目标通知 | `execSettlementNotifyJob(teamId)`，需重构 | teamId、reason、idempotencyKey | 任务结果 | 写 | C | 必须 | 运维 | 是 |
| `propose_activity_config` | 生成配置草案 | 当前缺失 | 业务目标/约束 | 草案+模拟 | 只写草案 | B/C | 版本化 | 运营 | 发布前是 |
| `update_dcc` | 动态配置 | `DCCController` | key/value | 更新结果 | 写 | D | 必须 | 管理员 | 当前不应暴露 |
| `settle_payment` | 结算支付 | `settlementMarketPayOrder` | 支付数据 | 结算结果 | 写 | D | 必须 | 可信支付系统 | 不应由 Agent 调用 |

严禁开放任意 SQL、任意 Redis key、任意 HTTP URL、任意代码执行、任意改价、直接退款。

# 15. 推荐的最小 Agent MVP

**唯一推荐：拼团客服与订单诊断 Agent（只读）。**

选择理由：核心数据读链路已有，能展示 Tool Use/RAG/Guardrails/Evaluation，且可绕开尚未完成的退款、超时和高风险写操作。暂不选运营分析，因为指标与可观测性不足；不选异常自动处置和召回，因为需要更成熟的状态机、权限、数据同意与写操作治理；不选配置发布，因为错误价格风险高。

架构：

```text
用户/客服
  -> 身份与权限网关（确定性）
  -> 意图路由
  -> 诊断工作流
       -> 订单只读 Tool
       -> 团进度 Tool
       -> 活动规则 Tool
       -> 通知状态 Tool
       -> FAQ/错误码 RAG
  -> 证据校验与敏感信息脱敏
  -> 答复 / 升级人工
```

工作流：识别订单问题 → 要求并校验订单定位信息 → 服务端从身份上下文绑定用户 → 并行读取订单/团/活动/通知 → 确定性规则生成事实标签 → LLM 组织解释 → 引用 tool evidence → 高风险/未知项转人工。

Guardrails：

- Tool 参数不接受任意 userId，由网关注入 subject。
- 只读 DB 账号、字段白名单、行级授权、结果脱敏。
- 不允许模型声称已退款/已成团，除非 Tool 返回对应确定状态。
- 禁止 DCC、结算、退款、改价和任意回调。
- 每次答复带状态时间、证据 ID、置信度和未知项。
- Prompt injection 不得改变 Tool 权限或数据范围。

Human-in-the-Loop：退款、补偿、重发通知、修改活动和任何资金/触达动作仅生成工单或建议，由人工审批。

Evaluation：订单定位准确率、状态解释准确率、证据引用完整率、越权阻断率、幻觉率、人工升级召回率、平均工具调用数、P95 延迟、用户问题一次解决率。构建包含 50–100 个脱敏金标案例，覆盖正常、重复支付、团满、过期、通知失败、未知订单和越权。

失败与降级：任一 Tool 超时即返回“系统暂不可核验”及人工渠道；数据冲突时展示冲突而非自行裁决；RAG 不可用时仍可基于结构化状态模板回答；模型不可用时降级为确定性状态查询页。

两周演示范围：

- 第 1 周：新增四个只读查询 facade、身份模拟/授权、脱敏、FAQ/错误码知识库、10 个核心诊断模板。
- 第 2 周：Agent 路由与工具编排、证据引用、人工升级、50 个评测集、审计日志和演示 UI。

面试可体现：Routing、Tool Use、RAG、Guardrails、HITL、Exception Handling、Evaluation；不强行加入长期 Memory，仅保留会话内订单上下文。

# 16. 改造路线图

| 阶段 | 工作内容 | 涉及模块 | 依赖/风险 | 验收条件 |
|---|---|---|---|---|
| P0 | 补身份鉴别、RBAC、主体绑定；关闭生产测试接口；DCC 改 POST+管理员审批+白名单+审计 | trigger/api/app | 统一身份 | 未授权请求全部拒绝，越权测试通过 |
| P0 | 支付回调验签、金额/商户核对、nonce/时间窗、唯一幂等键 | trade/api/db | 支付契约 | 重放和伪造回调被拒绝 |
| P0 | 修复 outTradeNo/bizId 唯一约束和最后一人成团原子迁移 | trade/mapper/sql | 数据清理 | 并发测试无重复订单、无漏成团 |
| P0 | 实现超时关团、订单关闭、名额释放；明确退款边界 | domain/trigger/infra | 调度/MQ | 状态机测试通过、可补偿 |
| P0 | 回调 URL 白名单/固定注册、签名、超时、重试、死信；秘密外置 | infra/app | 配置中心 | SSRF 测试通过，无明文生产秘密 |
| P0 | 建立 JDK/Maven 可复现构建，升级旧插件，测试默认不跳过 | pom/CI | 依赖兼容 | 干净环境编译和安全测试通过 |
| P1 | 只读诊断 facade、Agent Tools、FAQ RAG、权限与脱敏 | 新 Agent 层+现有模块 | P0 身份/状态 | 50 个金标准确率达标 |
| P1 | Agent 审计、限流、超时、熔断、人工升级 | Agent/运维 | 指标平台 | 每次调用可追踪、失败可降级 |
| P2 | 运营指标和异常团分析 Agent | 数据/观测 | 事件埋点 | 指标口径审核通过 |
| P2 | 活动配置草案+模拟，不直接发布 | activity/Agent | 规则 schema | 极端价格校验通过 |
| P3 | 合规召回、DevOps 诊断、受控通知重试 | Agent/运维 | 用户同意/审批 | 频控、审批、回滚完备 |

# 17. 面试项目叙事素材

项目背景：营销系统需要把商品、渠道、活动、人群、优惠和拼团交易解耦，并在高并发下控制团名额、保证支付结算与成团通知一致。

业务难点：活动规则组合、同团并发参团、支付回调幂等、最后一人成团、通知可靠投递、跨系统问题解释。

原系统问题：核心正向链路存在，但逆向生命周期、安全边界、可观测性和自动化测试不足；客服排障需要跨多张表和多个状态。

为什么引入 Agent：自然语言问题多样、诊断需要动态选择多个只读工具并组织证据；单一规则页面体验差。Agent 不接管确定性交易，只负责意图理解、工具编排和解释。

LLM 使用范围：问题分类、查询计划、FAQ/RAG、证据化解释、人工工单摘要。确定性代码保留：身份/权限、价格计算、库存、状态迁移、支付验签、幂等、退款、限流和审批。

安全与可评估性：最小权限只读 Tools、服务端注入身份、参数 schema、脱敏、证据引用、HITL、金标集、越权/注入/故障评测、全链路审计。

面试追问：

1. **为什么不用一个大 Prompt？** 回答要点：交易事实必须实时读取，Tool schema 和确定性规则可审计；RAG 只承载规则说明，不能替代订单真相。
2. **如何防止 Agent 越权退款？** 回答要点：MVP 根本不提供退款 Tool；未来仅创建审批请求，资金执行由确定性服务二次鉴权、幂等和人工批准。
3. **如何证明 Agent 有价值？** 回答要点：与固定 FAQ/人工基线对比一次解决率、诊断准确率、引用完整率、升级召回率和处理时长，同时设置零越权目标。

# 18. 证据索引与待确认问题

关键证据：

- 技术栈/模块：根 `pom.xml`、各模块 `pom.xml`。
- 启动配置：`group-buy-market-app/src/main/java/cn/bugstack/Application.java`、`resources/application*.yml`。
- HTTP 接口：`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/*Controller.java`。
- 活动试算：`group-buy-market-domain/.../activity/service/trial`、`service/discount`。
- 锁单与结算：`TradeLockOrderService.java`、`TradeSettlementOrderService.java`、`TradeRepository.java`。
- 并发 SQL：`group_buy_order_mapper.xml`、`group_buy_order_list_mapper.xml`。
- 通知：`TradePort.java`、`GroupBuyNotifyService.java`、`GroupBuyNotifyJob.java`、`nofify_task_mapper.xml`。
- 数据模型：`docs/dev-ops/mysql/sql/2-19-group_buy_market.sql`、`docs/tag/v2.0/mysql/sql/group_buy_market.sql`。
- 测试：`group-buy-market-app/src/test/java`、App POM Surefire 配置。
- 部署：`group-buy-market-app/Dockerfile`、`docs/dev-ops/docker-compose-*.yml`。

未知/待确认：

- 当前分支对应的权威 SQL 文件是哪一份。
- 生产是否在网关层另有鉴权、验签、限流和 CORS 限制。
- 外部支付系统是否保证 outTradeNo 全局唯一、金额核对和重试语义。
- 商品实物库存由哪个系统负责。
- 未支付订单、失败团和退款是否在其他仓库实现。
- HTTP 回调 URL 是否只来自受信渠道。
- Maven/JDK 的标准构建环境及 CI 是否在仓库外。
- 生产监控、备份、密钥管理和发布回滚是否由外部平台提供。

需要作者回答：

1. 该仓库是完整项目还是课程阶段分支？是否有更晚分支包含关单/退款？
2. `biz_id` 预期是否应为唯一键？为何当前 SQL 没有？
3. 支付结算接口的调用方和可信边界是什么？
4. 团名额与商品库存分别由谁持有真相？
5. HTTP 通知地址是否应由活动/商户注册，而不是终端锁单请求提供？
6. Agent MVP 面向终端用户还是内部客服？现有身份系统如何接入？

建议下一步安全验证命令（本次危险项不执行）：

```bash
java -version
mvn -version
mvn -DskipTests compile
mvn test -DskipTests=false
docker compose -f docs/dev-ops/docker-compose-environment.yml config
rg -n "@CrossOrigin|RequestMapping|Transactional|Scheduled|RabbitListener" .
```

集成测试只能在本地隔离基础设施和脱敏测试数据准备完毕后执行；不得直接使用现有 dev/prod 配置连接共享环境。
