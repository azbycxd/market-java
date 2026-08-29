# 拼团规则知识目录

`group_buy_rules_v1.json` 是从 `group-buy-market-jiusi` 已验证的业务源码整理出的、面向客服解释的规则知识目录。它不是 Java Runtime 配置，也不是订单、团队或活动的实时数据源。

## 用途与版本

当前目录版本为 `v1`。后续 Python Agent 可在通过静态校验后加载此文件用于检索；它只能解释一般业务规则，不能据此直接执行锁单、支付、退单、退款或数据写入。

每个条目都包含稳定的 `knowledge_id`、简明中文 `content`、来源类型、来源等级、源码文件与符号、版本、标签，以及 `requires_realtime_facts`。来源字段用于审核和变更追溯，不应作为面向终端用户的回答内容。

## 来源等级与更新规则

- `LEVEL_1`：正式业务文档明确说明。
- `LEVEL_2`：Enum、领域规则、Service 或 Strategy 明确表达。
- `LEVEL_3`：由多个实现路径审慎归纳，正文必须使用“当前项目实现体现”等非承诺性措辞。

任何相关 Java Enum、Rule、Strategy、Service、API Contract 变更后，都必须复核受影响条目的 `content`、`source_files`、`source_symbols` 和版本。没有可追溯源码依据的内容不得加入目录；不把通用电商经验当作项目规则。

## Facts、Rule 与检索边界

- **Facts**：某订单状态、当前团队人数、活动是否仍有效、可加入团队和统计，必须通过 Java Facts API 实时查询。
- **Rule**：资格、容量、库存、标签、价格、锁单和退单策略由 Java Rule/Service/Strategy 最终裁决与执行。
- **检索知识**：只解释一般规则和状态含义。检索结果不得替代 Facts 或 Java 的确定性业务判断。

## 安全边界

目录不得包含用户标识、订单号、手机号、地址、Header、Token、密码、API Key、连接信息、Redis 键、真实订单数据、通知参数、SQL、Mapper 或基础设施配置。不要把整个仓库或原始源码切块入库。

尤其是：`CLOSE` 仅表示本项目交易订单进入用户退单关闭状态；目录不能把它解释为资金已到账或支付渠道退款已完成。

## 静态校验

运行以下命令进行目录治理校验：

```powershell
python docs/agent/knowledge/validate_catalog.py
```

校验使用 Python 标准库，不引入 Runtime 依赖。它检查 JSON 结构、条目数、唯一 ID、来源字段、来源等级、标签、敏感模式、测试订单号和绝对路径。
