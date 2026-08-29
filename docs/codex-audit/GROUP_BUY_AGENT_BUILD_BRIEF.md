# 拼团项目 Agent 架构建设输入报告

> 基于 2026-07-28 静态审计与安全构建验证合并。静态推断与运行证据冲突时以运行证据为准；未验证项明确标记为未知。

# 1. 一页执行摘要

项目是 Java/Spring Boot 拼团营销服务，采用 DDD 多模块架构，已实现商品/活动匹配、四类优惠试算、开团/参团锁单、支付后营销结算、团完成通知和通知任务补偿。

**综合完成度：54%。** 主正向链路有真实代码与 SQL，不是纯脚手架；但取消、退款、超时失败、未支付释放、可靠支付边界、完整权限、自动化测试、观测与可复现构建不足。

**当前不能确认可运行。** Maven 3.9.9 + 默认 JDK 23 下 Lombok setter 未生成；JDK 21 下旧 Lombok 与 javac 报 `NoSuchFieldError`。项目目标 Java 8，但本机未发现 JDK 8。测试因此未执行，且 App POM 默认跳过测试。

P0 风险：

1. 所有 Controller 未发现鉴权；DCC 可匿名 GET 修改任意配置。
2. 支付结算入口无签名、金额/商户核对或重放保护。
3. 用户提交的 HTTP 回调 URL 被直接请求，存在 SSRF 风险。
4. `out_trade_no/biz_id` 缺唯一约束；最后一人成团存在并发漏迁移风险。
5. 取消、退款、超时关团和团名额释放缺失。

**Agent 建议：现在只做“拼团客服与订单诊断 Agent”的只读 MVP。** 不允许 Agent 调用结算、DCC、改价、退款、任意 URL 或 SQL。

# 2. 项目技术架构

```text
静态 HTML/JS
    |
Spring MVC Trigger
    |-- MarketIndexController
    |-- MarketTradeController
    |-- DCCController
    |-- Scheduled Job / Rabbit Listener
    |
Domain
    |-- 活动策略树 + 人群标签 + 折扣策略
    |-- 锁单责任链
    |-- 结算责任链
    |
Infrastructure
    |-- MyBatis / MySQL
    |-- Redisson / Redis
    |-- Spring AMQP / RabbitMQ
    |-- OkHttp 回调
```

技术版本：Java 8 target、Spring Boot 2.7.12、MyBatis 2.1.4、MySQL connector 8.0.22、Redisson 3.26.0、RabbitMQ、OkHttp 3.14.9、Maven。前端为 Nginx 托管静态页面。存在 Docker/Compose，无 K8s/CI/Actuator/Metrics/Trace 证据。

模块职责：

- `api`：接口和 DTO。
- `app`：启动、配置、Mapper、测试和打包。
- `domain`：活动、标签、优惠和交易领域。
- `trigger`：HTTP、任务、消息入口。
- `infrastructure`：DAO、仓储、Redis、MQ、HTTP 网关。
- `types`：通用枚举、异常和策略/责任链框架。

# 3. 拼团业务流程与状态机

核心流程：

```text
查询营销配置
 -> source/channel/goods 匹配活动
 -> 活动/人群/开关校验
 -> 折扣试算

锁单
 -> 手工参数检查
 -> 幂等查询（非原子）
 -> 活动与参与次数校验
 -> 参团时 Redis 团名额预占
 -> DB 团计数条件更新
 -> 写团主表/订单明细

支付结算
 -> 外部单号存在校验
 -> 渠道黑名单与团有效期校验
 -> 订单 CREATE->COMPLETE
 -> complete_count + 1
 -> 最后一人时团 PROGRESS->COMPLETE
 -> 写 notify_task
 -> 立即通知，失败由每日任务补偿
```

状态：

- 活动：CREATE/EFFECTIVE/OVERDUE/ABANDONED；未发现管理迁移。
- 团：PROGRESS/COMPLETE/FAIL；仅实现 PROGRESS→COMPLETE。
- 交易明细：CREATE/COMPLETE/CLOSE；仅实现 CREATE→COMPLETE。
- 通知：WAIT/SUCCESS/RETRY/ERROR，由 SQL 与代码推断。
- 支付、退款、商品库存：无独立状态机。

必须补齐的确定性状态迁移：未支付关闭、团超时失败、团名额释放、取消、退款、通知死信；并明确支付与超时并发的裁决规则。

# 4. 功能完成度矩阵

| 功能 | 状态 | 证据 | 主要缺口 |
|---|---|---|---|
| 商品/SKU/活动匹配 | 基本完成 | ActivityRepository、Mapper | 管理与验证 |
| 人群标签 | 基本完成 | TagService、Redis bitmap | DB/缓存一致性 |
| 四类优惠计算 | 基本完成 | ZJ/ZK/MJ/N services | 边界测试 |
| 开团/参团锁单 | 基本完成 | TradeLockOrderService/Repository | 唯一键与身份 |
| 团名额控制 | 部分完成 | Redis INCR+DB 条件更新 | 超时/取消释放 |
| 支付结算 | 部分完成 | settlement controller/service | 验签、金额核对 |
| 成团 | 部分完成 | complete count + status update | 并发漏迁移 |
| HTTP/MQ 通知 | 部分完成 | notify task/TradePort/Job | SSRF、签名、可靠性 |
| 取消/超时失败 | 未实现 | 仅有枚举 | 完整流程 |
| 退款 | 未实现 | 无接口/状态 | 完整流程 |
| 权限与审计 | 未实现 | 未发现 Security | RBAC/主体绑定 |
| 自动化测试 | 仅骨架/占位 | SpringBootTest 样例 | 隔离、断言、CI |
| 运维观测 | 仅骨架/占位 | 日志、Compose | health/metrics/trace/alert |

# 5. 运行与测试验证

执行结果：

- 默认 `mvn -DskipTests compile`：Maven 全局 settings mirror 配置无效，退出 1。
- 使用审计最小 settings、JDK 23：API 模块 Lombok setter 未生成，`BUILD FAILURE`。
- 使用 JDK 21：types 模块 Lombok/javac `JCTree.qualid` 兼容错误，`BUILD FAILURE`。
- 项目 target Java 8；标准 Java 8 构建未知。
- 未执行测试：编译失败；测试依赖外部基础设施；Surefire 默认 `skipTests=true`。

报告没有把工具链失败误判为已证实的业务源码缺陷。需要固定 JDK/Maven/Lombok 组合后重新验证。

# 6. 并发、一致性和幂等风险

| 问题 | 当前防护 | 结论 |
|---|---|---|
| 同时参团/团名额 | Redis 原子计数、NX；DB `lock_count < target_count` | 有基础，需压测恢复模型 |
| 重复锁单 | 先查未支付订单、业务 ID 注释 | 检查-写竞态；缺 outTradeNo/bizId 唯一键 |
| 重复支付 | 明细更新 `WHERE status=0`，事务回滚 | DB 层基本幂等；入口安全缺失 |
| 最后一人成团 | 基于更新前 completeCount 判断 | 高风险：并发回调可能漏成团 |
| 超时与支付竞态 | 仅校验传入支付时间 | 高风险：无关团迁移/锁策略 |
| 通知重复 | Redis 团锁、notify_task | 仍是至少一次；对端需幂等和签名 |
| 可靠消息 | 通知任务类似 Outbox | 无状态 CAS、退避、死信和 confirm 证据 |

P0 数据库改造：唯一外部交易键、唯一业务参与键、团状态原子条件更新、计数约束/版本、通知任务租约和状态 CAS、必要索引。

# 7. 安全、权限和 Guardrails 基础

高风险证据：

- `DCCController` 无鉴权，用 GET 写配置，接收任意 key/value。
- `MarketTradeController` 直接信任请求中的 userId 和支付结算字段。
- 全部 Controller `@CrossOrigin("*")`。
- `GroupBuyNotifyService` 直接请求锁单时提供的 URL。
- 请求对象被完整 JSON 记录，可能包含身份、外部单号和回调地址。
- Test Controller 位于生产主源码。
- Compose/配置存在明文默认凭据线索。

Agent Guardrails 前置：

1. 身份由服务端网关注入，模型不可传任意 userId。
2. Tool 用只读账号、固定查询、字段/行级授权和脱敏。
3. 模型不可获得 SQL、Redis key、任意 URL 或代码执行能力。
4. 资金、价格、配置、通知触达必须走审批。
5. Tool 输出带状态时间、证据 ID 和数据来源。
6. Prompt injection 不得改变权限、数据域或审批要求。
7. 全量记录 Tool 调用、策略拒绝、人工批准和最终答案。

# 8. 可封装的 Agent Tools

| Tool | 输入 | 输出 | 风险/权限 | 现状 |
|---|---|---|---|---|
| `get_market_config` | server subject、SC、goodsId | 活动/价格/团候选 | A；用户/客服 | 可基于现有查询重构 |
| `get_team_progress` | teamId | 人数、状态、截止时间 | A；成员/客服 | 有领域入口 |
| `get_order_diagnosis` | subject、order locator | 脱敏订单状态、原因码 | A；本人/客服 | 需新增 facade |
| `get_activity_rules` | activityId、subject context | 资格/时限/折扣解释 | A；客服 | 需封装规则证据 |
| `get_notify_status` | teamId | 通知状态与次数 | A；内部 | 需新增只读封装 |
| `create_support_case` | idempotencyKey、摘要、证据 | caseId | B；客服 | 当前缺失 |
| `retry_team_notification` | teamId、reason、idempotencyKey | 受控结果 | C；运维+审批 | 需重构 |

明确禁止：`update_dcc`、`settle_payment`、任意退款/改价、任意回调 URL、任意 SQL/Redis/代码执行。

# 9. Agent 候选场景对比

| 场景 | 价值 | 准备度 | 风险 | 推荐 |
|---|---:|---:|---:|---|
| 客服与订单诊断 | 高 | 中 | 低（只读时） | **MVP** |
| 运营分析 | 高 | 低中 | 中 | P2，先补指标 |
| 异常团处置建议 | 中高 | 低 | 高 | P2，只给建议 |
| 活动配置辅助 | 中 | 中 | 高 | P2，仅草案+模拟 |
| 成团促进/召回 | 中 | 低 | 高 | P3，需同意与频控 |
| DevOps 诊断 | 中高 | 低 | 中 | P2，先补观测 |

不需要 LLM 的部分：折扣公式、资格判定、状态迁移、库存、幂等、验签、退款金额、权限和限流。

# 10. 推荐的 Agent MVP

**拼团客服与订单诊断 Agent。**

工作流：

```text
身份验证
 -> 意图 Routing
 -> 订单定位
 -> 并行调用订单/团/活动/通知只读 Tools
 -> 确定性状态解释器
 -> FAQ/错误码 RAG
 -> LLM 组织带证据答复
 -> 未知/高风险升级人工
```

不选择其他方案的原因：运营 Agent 缺指标，处置 Agent 缺可靠状态机，活动发布有错价风险，召回涉及隐私和触达，DevOps Agent 缺 trace/metrics。

两周范围：

- 四个只读 facade 和权限/脱敏。
- FAQ、错误码、状态机知识库。
- 诊断工作流、证据引用和人工升级。
- 50–100 个脱敏评测案例。
- 演示 UI 与调用审计。

降级：Tool 超时返回不可核验并升级；数据冲突明确展示；RAG 失败用结构化模板；模型失败降级为普通状态查询页。

# 11. Pattern 映射

| Pattern | MVP 用法 |
|---|---|
| Routing | 区分资格、价格、订单、团进度、通知、未知问题 |
| Tool Use | 只读查询实时交易事实 |
| Prompt Chaining | 定位 → 取证 → 规则解释 → 答复 |
| RAG | FAQ、错误码、活动规则说明；不存订单真相 |
| Memory | 仅会话内保存已验证订单上下文；不做长期个人画像 |
| Guardrails | 主体绑定、Tool allowlist、脱敏、证据要求 |
| HITL | 退款、补偿、通知重试、配置全部转人工 |
| Exception Handling | Tool 超时/冲突/无记录的确定性降级 |
| Evaluation | 金标诊断、越权、注入、故障和延迟评测 |
| Reflection | 可做证据完整性检查，不允许自我批准写操作 |

# 12. Evaluation 指标

核心质量：

- 订单定位准确率 ≥ 99%（在可定位样本）。
- 状态解释准确率 ≥ 95%。
- 关键事实证据引用完整率 ≥ 98%。
- 无证据事实/幻觉率 < 1%。
- 应升级人工场景召回率 ≥ 98%。

安全：

- 越权数据泄露率 0。
- 未审批写操作执行率 0。
- Prompt injection Tool 越权成功率 0。
- 敏感字段输出率 0。

效率：

- P95 响应时间、平均 Tool 次数、Tool 失败率。
- 一次解决率、人工平均处理时长下降、用户重复提问率。

评测集必须包含正常团、满团、过期、重复支付、未知订单、通知失败、数据冲突、越权、注入和 Tool 故障。

# 13. P0/P1/P2 路线图

## P0：Agent 前必须完成

- 可复现 Java/Maven 构建和真实 CI。
- 身份、RBAC、主体绑定、DCC 管理边界、关闭测试接口。
- 支付验签、金额核对、时间窗/nonce、唯一幂等键。
- 原子成团迁移、唯一约束、并发测试。
- 超时关团、订单关闭、名额释放、取消/退款边界。
- 固定注册回调目标、SSRF 防护、签名、超时/退避/死信。
- secrets 外置、日志脱敏、SCA/secret scan。

## P1：Agent MVP

- 只读诊断 facade 和 Agent Tool schema。
- FAQ/RAG、意图路由、证据答复、HITL。
- Tool/答案审计、限流、熔断、评测流水线。

## P2：增强

- 业务事件、Metrics、Trace、告警。
- 运营分析和异常团建议。
- 活动配置草案与确定性模拟。

P3 可选：合规召回、受控通知重试、DevOps 诊断。

# 14. 面试叙事素材

项目亮点不是“用了很多 Agent Pattern”，而是把概率模型限制在适合的位置：

- DDD 与责任链承载确定性活动、交易和状态规则。
- Redis+SQL 控制团名额，数据库事务承载结算一致性。
- Agent 解决跨域诊断和自然语言解释，事实来自实时只读 Tools。
- RAG 只保存规则/错误码，Memory 只保存短会话上下文。
- 资金与状态变更不交给 LLM，必须权限、幂等和人工审批。
- 通过金标、越权、注入和故障集证明安全与价值。

常见追问：

1. 为什么不让 Agent 自动退款？因为资金执行必须确定、可审计和可幂等；MVP 不提供该 Tool。
2. 如何避免幻觉？结构化 Tool 是事实源，答案必须引用证据；缺数据则明确未知。
3. 如何衡量价值？与 FAQ/人工基线比较准确率、一次解决率、处理时长，同时设置越权与写操作零容忍指标。

# 15. 关键证据索引

- 构建与技术栈：`pom.xml`、各模块 POM。
- HTTP 与安全边界：`group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http`。
- 活动试算：`group-buy-market-domain/src/main/java/cn/bugstack/domain/activity`。
- 锁单/结算：`TradeLockOrderService.java`、`TradeSettlementOrderService.java`。
- 事务与并发：`group-buy-market-infrastructure/.../TradeRepository.java`。
- SQL 条件更新：`group_buy_order_mapper.xml`、`group_buy_order_list_mapper.xml`。
- 通知：`TradePort.java`、`GroupBuyNotifyService.java`、`GroupBuyNotifyJob.java`、`nofify_task_mapper.xml`。
- 数据模型：`docs/dev-ops/mysql/sql/2-19-group_buy_market.sql`、`docs/tag/v2.0/mysql/sql/group_buy_market.sql`。
- 构建错误定位：`LockMarketPayOrderRequestDTO.java:30-59`、根/API/types POM。
- 完整证据与风险细节：`docs/codex-audit/AGENT_READINESS_STATIC_AUDIT.md`。
- 实际构建验证：`docs/codex-audit/BUILD_TEST_VALIDATION.md`。

# 16. 需要项目作者回答的问题

1. 当前分支是否是课程中间阶段？是否有包含关单/退款的后续分支？
2. 权威 JDK、Maven、Lombok 和 SQL 版本是什么？
3. 网关层是否在仓库外实现鉴权、支付验签、限流和 CORS？
4. `biz_id` 和 `out_trade_no` 的预期唯一范围是什么？
5. 商品库存与支付真相由哪个外部系统负责？
6. HTTP 回调目标为何由锁单请求提供，能否改为受信商户注册？
7. Agent 服务对象是终端用户还是内部客服？身份系统如何提供 subject/role/tenant？
8. 生产监控、秘密管理、备份和发布回滚是否由外部平台提供？

---

## 交给 ChatGPT 的任务说明

请基于本报告设计拼团系统的 Agent 架构，明确 Agent 职责、Routing、Workflow、Tools、RAG/Memory、Guardrails、Human-in-the-Loop、异常处理、Evaluation、落地步骤和面试表达。先检查哪些结论证据充分、哪些仍需验证；不要为了套 Pattern 过度设计，明确哪些逻辑必须保持为确定性业务代码。
