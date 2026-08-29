# 拼团项目构建与测试验证报告

> 验证日期：2026-07-28  
> 安全边界：未修改业务源码/业务配置/数据库；未启动应用；未连接 MySQL、Redis、RabbitMQ；未调用支付、短信、邮件或回调服务。允许 Maven 读取/下载声明依赖并生成 `target` 构建目录。

# 1. 验证环境摘要

| 项目 | 结果 |
|---|---|
| OS | Windows 10 amd64 |
| Maven | Apache Maven 3.9.9 |
| 系统默认 Java | OpenJDK 23.0.2 |
| 额外可用 Java | Oracle JDK 21.0.6 |
| 项目目标 Java | Java 8（根 POM） |
| JDK 8/11/17 | 在已检查的 `D:\soft` 范围未发现 |
| 分支 | `2-20-xfg-redis-cache` |
| 初始工作区 | `application-dev.yml` 已有用户修改 |

为了绕开本机 Maven 全局 settings 的语法错误，本次增加审计专用最小配置：`docs/codex-audit/audit-maven-settings.xml`。它只指定本地仓库，不包含凭据或业务配置。

# 2. 执行过的命令

| 序号 | 命令 | Maven/命令实际结果 | 说明 |
|---|---|---|---|
| 1 | `java -version` | 0 | 默认 JDK 23.0.2 |
| 2 | `mvn -version` | 0 | Maven 3.9.9，默认使用 JDK 23 |
| 3 | `mvn -DskipTests compile` | 1 | 在读取项目之前失败：全局 settings mirror 配置无效 |
| 4 | `mvn -gs docs/codex-audit/audit-maven-settings.xml -s ... -o -DskipTests compile` | 1 | 离线仓库缺 Spring Boot parent，未进入编译 |
| 5 | 同上但允许联网 | 1 | JDK 23 进入 `group-buy-market-api` 编译后失败 |
| 6 | 切换 JDK 21，离线 compile | 1 | 仍受本地依赖仓库的 remote-id/offline 状态影响 |
| 7 | 切换 JDK 21，联网 compile | 1 | API 模块被 Maven 判为 up-to-date；types 模块出现 Lombok/javac 兼容错误 |

注：PowerShell 包装命令在最后输出退出码，因此外层 shell 曾显示进程退出 0；表中记录的是命令内捕获的 Maven `LASTEXITCODE`，均有 Maven `BUILD FAILURE` 证据。

# 3. 构建结果

**结论：当前环境无法完成全量编译，不能宣称项目可构建。**

## 3.1 默认 Maven 配置失败

本机 `D:\soft\apache-maven-3.9.9\conf\settings.xml` 存在无效 mirror：

- `Unrecognised tag: repository`
- `mirrors.mirror.url is missing`
- `mirrors.mirror.mirrorOf is missing`

这是本机工具链问题，发生在读取项目模型前，不是业务源码错误。

## 3.2 JDK 23 编译失败

使用最小 Maven settings 并下载依赖后，Reactor 在 `group-buy-market-api` 失败：

```text
LockMarketPayOrderRequestDTO.java:[33,23] 找不到方法 setNotifyType(String)
LockMarketPayOrderRequestDTO.java:[34,23] 找不到方法 setNotifyUrl(String)
LockMarketPayOrderRequestDTO.java:[41,23] 找不到方法 setNotifyType(String)
```

这些 setter 预期由内部类 `NotifyConfigVO` 的 Lombok `@Data` 生成。JDK 23 改变了注解处理默认行为，而项目使用旧 `maven-compiler-plugin:3.0` 和 `lombok:1.18.26`（API 模块显式版本），因此高度怀疑是工具链兼容问题。该判断是推断，不等同于证明源码在 Java 8 下一定成功。

## 3.3 JDK 21 编译失败

切换 JDK 21 后，Reactor 在 `group-buy-market-types` 失败：

```text
Fatal error compiling:
java.lang.NoSuchFieldError:
JCTree$JCImport does not have member field ... qualid
```

这是旧 Lombok 访问新版 javac 内部 API 的典型兼容症状。项目目标是 Java 8，但本机未发现匹配的 JDK 8。因安全约束，本次未修改 POM、未升级 Lombok、未安装系统级 JDK。

# 4. 测试结果

**未执行测试。**

原因：

1. 主源码编译前置条件未通过。
2. App POM 的 Surefire 配置为 `<skipTests>true</skipTests>`，默认命令不会真实执行测试。
3. 现有测试几乎全部使用 `@SpringBootTest`，会加载真实数据源、Redis、RabbitMQ 或 HTTP 客户端；本次未准备隔离基础设施。
4. 静态扫描未发现 `assert`/`Assertions`/Mockito 等断言或 Mock 证据，当前测试更接近手工演示脚本。

测试盘点：

- 约 13 个测试类。
- 约 24 个 `@Test` 方法。
- 覆盖活动试算、标签、DAO、锁单、结算、Controller、DCC、MQ 和 HTTP 回调样例。
- 未发现并发、状态机、支付验签/重放、退款、超时关团、安全或 E2E 自动化测试。

# 5. 失败根因

按层次区分：

1. **本机 Maven 全局配置损坏**：无效 mirror 直接阻止构建。
2. **JDK 与项目目标不匹配**：项目目标 Java 8，本机可用 JDK 为 21/23。
3. **构建插件和 Lombok 过旧**：compiler 3.0、API Lombok 1.18.26 与现代 JDK 不兼容。
4. **可复现构建未固化**：仓库未提供 Maven Wrapper、toolchains、CI 或明确 JDK 镜像。
5. **测试默认跳过且非隔离**：即使编译成功，默认 Maven 生命周期也不能证明测试通过。

未证实的源码问题：本次错误都与 Lombok/现代 JDK 兼容直接相关；在标准 Java 8 环境下是否仍有其他编译错误，未知。

# 6. 是否可启动

**未验证，当前不能启动。**

静态上具备 `cn.bugstack.Application`、Spring Boot Maven plugin、Dockerfile 和配置；但编译未完成，且启动需要 MySQL、Redis、RabbitMQ 与正确 profile。为避免连接未知环境，本次没有尝试 `spring-boot:run` 或运行现有 JAR。

# 7. 尚未验证的外部依赖

- MySQL schema 与当前分支代码是否一致。
- Redis 连接、Lua/原子操作和分布式锁行为。
- RabbitMQ exchange/queue/binding 与 producer confirm。
- HTTP 回调超时、重试和真实对端契约。
- 静态前端与后端 API 地址、CORS 和联调。
- Docker 镜像能否在当前 CPU/OS 上构建。
- 生产/共享环境的任何配置。

# 8. 对完成度评分的修正

静态报告评分为 58%。实际验证后修正为 **54%**：

- 核心源码证据仍然成立。
- 扣 4 分：没有可复现的兼容构建环境，默认 Maven 配置损坏，现代 JDK 下编译失败，测试无法运行。
- 若在干净 Java 8 环境编译和隔离测试通过，可恢复 3–6 分；若仍有源码/集成失败，应继续下调。

# 9. 接入 Agent 前必须解决的 P0

1. 固化 JDK 版本：Maven Wrapper + toolchains 或基于固定 JDK 的 CI/容器。
2. 升级 Lombok、maven-compiler-plugin、Surefire，并明确现代 JDK 支持；或严格使用 Java 8 构建镜像。
3. 修复/移除不安全 HTTP Maven 仓库地址和本机 settings 依赖。
4. 取消 `skipTests=true` 的默认行为，区分 unit/integration profile。
5. 将外部依赖测试改为 Testcontainers/Mock，并加入真实断言。
6. 在 CI 中执行 compile、unit test、integration test、SCA、secret scan。
7. 完成静态报告列出的鉴权、回调验签、幂等唯一键、并发成团和生命周期 P0。

# 10. 证据路径与日志位置

- 构建定义：`pom.xml`、`group-buy-market-api/pom.xml`、`group-buy-market-app/pom.xml`
- 编译错误源文件：`group-buy-market-api/src/main/java/cn/bugstack/api/dto/LockMarketPayOrderRequestDTO.java:30-59`
- 测试目录：`group-buy-market-app/src/test/java`
- 启动入口：`group-buy-market-app/src/main/java/cn/bugstack/Application.java`
- 审计 Maven 配置：`docs/codex-audit/audit-maven-settings.xml`
- Maven 生成物：各模块 `target/` 目录

本次终端输出未写入包含潜在环境信息的永久原始日志；关键命令、退出码与错误已脱敏摘录在本报告中。
