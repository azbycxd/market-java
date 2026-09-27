# B3-3：Java Agent Facts API 与内部服务鉴权

## 1. 结论

四个 Agent Facts API 均复用已有只读业务事实实现，并由新的内部 JWT Filter 统一保护。JWT 仅作用于 `/api/v1/agent/**`；其他既有业务接口不会被此 Filter 拦截。

内部用户身份只从 `X-Authenticated-User-Id` 获取。开发/测试环境仍需显式开启既有 `agent.facts.dev-header-auth.enabled` 才信任该 Header；生产环境不因 Header 自动建立身份。

## 2. 最终 API

| API | 请求 body | 真实职责 |
| --- | --- | --- |
| `POST /api/v1/agent/order/facts` | `{"outTradeNo":"..."}` | 已认证用户自己的订单、队伍、活动原始 Facts。 |
| `POST /api/v1/agent/activity/facts` | `{"activityId":...}` | 活动状态、时间、限次等 Facts；`withinValidTime` 由 Java 当前时间确定性计算。 |
| `POST /api/v1/agent/activity/eligibility-facts` | `{"activityId":...}` | 真实活动、Tag、参与次数、DCC 降级和切量 Facts。 |
| `POST /api/v1/agent/team/joinable-facts` | `{"activityId":...}` | 已有候选拼团队伍及统计 Facts。 |

四个成功响应均保持既有统一包络：

```json
{"code":"0000","info":"成功","data":{}}
```

本次没有重新设计或变更 Facts DTO；现有 Order、Activity、Eligibility、Joinable Team DTO 直接复用。

## 3. JWT 鉴权实现

实现位置：

- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/AgentInternalJwtAuthenticationFilter.java`
- `group-buy-market-trigger/pom.xml`（显式引入现有版本管理下的 `io.jsonwebtoken:jjwt`）

Filter 只匹配 URI 前缀 `/api/v1/agent/`。它从以下**环境变量**读取配置，缺失任一个即 fail-closed：

```text
AGENT_INTERNAL_JWT_SECRET
AGENT_INTERNAL_JWT_ISSUER
AGENT_INTERNAL_JWT_AUDIENCE
```

请求必须发送 `Authorization: Bearer <internal JWT>`。Filter 验证：

- JWS algorithm 必须为 `HS256`；
- 使用 secret 验证签名；
- token 必须有未来的 `exp`；
- `iss` 必须等于配置 issuer；
- `aud` 必须等于配置 audience。

失败时在进入 Controller 前返回 HTTP `401`：

```json
{"code":"UNAUTHORIZED","info":"内部服务认证失败","data":null}
```

## 4. 用户身份与订单授权

修改 `DevHeaderAuthenticatedUserProvider` 后，唯一身份 Header 为：

```text
X-Authenticated-User-Id
```

所有 Facts request DTO 仍不包含 `userId`。Order Facts 既有调用链继续把可信身份与 `outTradeNo` 一起传入 Repository，底层查询条件保持：

```sql
where out_trade_no = #{outTradeNo} and user_id = #{userId}
```

因此不存在与无权订单统一返回 `ORDER_NOT_FOUND_OR_NOT_AUTHORIZED`，不会泄露其它用户订单事实。

## 5. 修改文件

- `group-buy-market-trigger/pom.xml`
- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/AgentInternalJwtAuthenticationFilter.java`
- `group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/DevHeaderAuthenticatedUserProvider.java`
- `group-buy-market-app/src/test/java/cn/bugstack/test/agent/AgentInternalJwtAuthenticationFilterTest.java`
- `group-buy-market-app/src/test/java/cn/bugstack/test/agent/DevHeaderAuthenticatedUserProviderTest.java`

未修改 Python Agent、原交易主流程、退款写操作、HITL、Trace 或 Phase C 内容。

## 6. 测试

聚焦 Maven 命令：

```text
mvn -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false \
  -Dtest=AgentInternalJwtAuthenticationFilterTest,DevHeaderAuthenticatedUserProviderTest,
         AgentFactsControllerTest,OrderFactsServiceTest,
         AgentActivityFactsControllerTest,AgentActivityFactsServiceTest,
         AgentEligibilityFactsControllerTest,AgentEligibilityFactsServiceTest,
         AgentJoinableTeamFactsControllerTest,AgentJoinableTeamFactsServiceTest test
```

结果：**61 passed，0 failures，0 errors，0 skipped**。

JWT Filter 测试覆盖合法 HS256 JWT、缺失 token、错误签名、过期、issuer 不符、audience 不符、环境变量缺失 fail-closed 和非 Agent 路径不受影响。四类 Facts 与现有身份 Provider 回归测试均通过。

## 7. 真实 HTTP 验收

当前 B3-3 构建以 `dev` profile 在本机 `8091` 运行，Redis、RabbitMQ 与 MySQL 初始化成功。验收 JWT 配置仅以进程环境变量注入，密钥、token 与可信身份值不输出也不写入项目文件。

以下使用合法 JWT 与已认证用户 Header 实测：

| 调用 | HTTP | 结果摘要 |
| --- | --- | --- |
| `POST /api/v1/agent/order/facts` | 200 | `0000`；返回 order/team/activity/references Facts。 |
| `POST /api/v1/agent/activity/facts` | 200 | `0000`；`withinValidTime=true`，并带 Java `evaluatedAt`。 |
| `POST /api/v1/agent/activity/eligibility-facts` | 200 | `0000`；返回真实 tag、次数、DCC/切量 Facts。 |
| `POST /api/v1/agent/team/joinable-facts` | 200 | `0000`；返回候选队伍和统计 Facts。 |

匿名、错误 token、过期 token、issuer 不符、audience 不符均对 Agent API 实测返回 **HTTP 401**。

另一可信用户尝试访问属于原用户的同一 `outTradeNo`，实测返回：

```json
{"code":"ORDER_NOT_FOUND_OR_NOT_AUTHORIZED","info":"订单不存在或无权访问","data":null}
```

可复现 curl 模板（使用部署环境实际配置签发 token；不要将真实 secret/token 提交到代码库）：

```bash
curl -i -X POST http://127.0.0.1:8091/api/v1/agent/order/facts \
  -H 'Authorization: Bearer <valid-hs256-jwt>' \
  -H 'X-Authenticated-User-Id: <trusted-user-id>' \
  -H 'Content-Type: application/json' \
  -d '{"outTradeNo":"644398015396"}'

curl -i -X POST http://127.0.0.1:8091/api/v1/agent/activity/facts \
  -H 'Authorization: Bearer <valid-hs256-jwt>' \
  -H 'X-Authenticated-User-Id: <trusted-user-id>' \
  -H 'Content-Type: application/json' \
  -d '{"activityId":100123}'

curl -i -X POST http://127.0.0.1:8091/api/v1/agent/activity/eligibility-facts \
  -H 'Authorization: Bearer <valid-hs256-jwt>' \
  -H 'X-Authenticated-User-Id: <trusted-user-id>' \
  -H 'Content-Type: application/json' \
  -d '{"activityId":100123}'

curl -i -X POST http://127.0.0.1:8091/api/v1/agent/team/joinable-facts \
  -H 'Authorization: Bearer <valid-hs256-jwt>' \
  -H 'X-Authenticated-User-Id: <trusted-user-id>' \
  -H 'Content-Type: application/json' \
  -d '{"activityId":100123}'
```
