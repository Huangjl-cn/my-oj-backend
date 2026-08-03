# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

在线判题系统（Online Judge）后端。技术栈：**Spring Boot 3.5.4 + Java 21 + MyBatis-Plus 3.5.17**（spring-boot3 starter + mybatis-plus-jsqlparser 分页插件）+ Hutool 5.8.47 + Knife4j 4.4.0（OpenAPI 3 + Jakarta）。无 Spring Security，鉴权用自定义 session + AOP 切面。Redis 依赖已注释且在 `MainApplication` 中 `exclude RedisAutoConfiguration`，无需 Redis 即可启动。

## 常用命令

```bash
mvn clean package          # 构建
mvn spring-boot:run        # 本地运行，默认 dev profile，端口 8102，context-path /api
mvn test                   # 全部测试
mvn test -Dtest=CodeSandboxTest   # 单个测试类
```

- 前置条件：JDK 21、Maven 3.6+。
- 首次运行先执行 `sql/create_table.sql`：创建 `oj_db`、三张表（user / question / question_submit）、默认 admin 账号（密码 12345678）。
- 接口文档（dev）：`http://localhost:8102/api/doc.html`（Knife4j）。
- 测试注意：`CodeSandboxTest.executeCode` 直接 new `RemoteCodeSandbox`，外部沙箱没启动会失败；`executeCodeByValue/executeCodeByProxy` 走 `codesandbox.type` 配置（默认 example，可离线跑）。

## 架构

经典分层：controller → service → mapper；`model/` 下分 entity / dto / vo / enums；`common/` 统一返回（BaseResponse + ResultUtils + ErrorCode）；`exception/` 全局异常（BusinessException + GlobalExceptionHandler）。

### 判题模块（核心，`judge/`）— 一条设计模式流水线

1. **异步入口**：`QuestionSubmitServiceImpl.doQuestionSubmit` 校验语言/题目，落 WAITING 状态的提交记录，然后 `CompletableFuture.runAsync` 异步调判题——HTTP 请求不阻塞在判题上。
2. **编排**：`JudgeServiceImpl.doJudge` 依次：校验提交为 WAITING → 置为 RUNNING（防重复判题）→ 调沙箱执行 → 策略判题 → 回写 SUCCEED + judgeInfo。
3. **沙箱**（工厂 + 代理模式）：`CodeSandbox` 接口，`CodeSandboxFactory` 按 `codesandbox.type` 配置实例化，`CodeSandboxProxy` 包一层做请求/响应日志。三个实现：
   - `RemoteCodeSandbox` — 真实调用外部沙箱 HTTP 接口，带 `auth: secretKey` 请求头；Java 走 `/executeCode`，其他语言走 `/executeCodeByAI`；URL 来自 `codesandbox.url`；
   - `ExampleCodeSandbox` — 本地模拟，不联网；
   - `ThirdPartyCodeSandbox` — 占位骨架。
4. **策略**（策略模式）：`JudgeStrategy` 接口 + `DefaultJudgeStrategy` / `JavaLanguageJudgeStrategy`，`JudgeManager` 按提交语言选择。策略内对比输入输出、按 `question.judgeConfig`（时间/内存限制）判超时超内存。

### 鉴权

- 登录态存 session：`UserService.getLoginUser(request)` 读 session 属性 `user_login`（`UserConstant.USER_LOGIN_STATE`），登出即删除该属性。
- 接口权限：`@AuthCheck(mustRole = "admin")` 注解 + `AuthInterceptor` AOP 切面校验，无 Spring Security。

### 重要约定 / 坑

- **提交接口已合并**：`QuestionSubmitController` 整体 @Deprecated、路由被注释（为微服务拆分预留）；题目提交相关接口在 `QuestionController` 下：`/question/question_submit/do`、`/question/question_submit/list/page`、`/question/question_submit/get`。改提交逻辑去 QuestionController / QuestionSubmitServiceImpl，不要动废弃 Controller。
- **JSON 以文本存库**：`question.tags` / `judgeCase` / `judgeConfig`、`question_submit.judgeInfo` 是 text 列存 JSON 字符串；DTO 侧是 List / JudgeConfig 对象，出入库用 Hutool `JSONUtil` 转换（参考 QuestionController.addQuestion 与 JudgeServiceImpl）。
- **Long 精度**：`JsonConfig` 全局把 Long 序列化为字符串（防前端 JS 精度丢失），返回 Long 的接口无需单独处理。
- **`map-underscore-to-camel-case: false`**：实体字段名必须与数据库列名完全一致（如 `userAccount`）。
- 逻辑删除：全局 `isDelete` 字段（MyBatis-Plus 全局配置）。
- 判题并发保障只有 WAITING → RUNNING 的状态流转，无分布式锁。

## 配置（`src/main/resources/`）

公共项在 `application.yml`（默认激活 dev），环境差异拆在 profile 文件，profile 覆盖同名公共项：

- `application-dev.yml`：本地运行。端口 **8102**，MySQL localhost:3306/oj_db，SQL 日志开（StdOutImpl）、Knife4j 开、session/cookie 30 天、codesandbox.url 指向 WSL 地址 172.25.66.109:8100（沙箱需要 Linux 环境）。
- `application-prod.yml`：上线部署。端口 **8101**，MySQL `my-oj-db`（docker 网络内），SQL 日志和接口文档（knife4j + springdoc）全关、session/cookie 24 小时、HikariCP 池 10、响应压缩开、codesandbox.url 指向沙箱服务器内网地址（如 CODESANDBOX_PROD_URL_PLACEHOLDER:8100，部署时可按需覆盖）。
- `application-test.yml`：占位配置（my_db 等），实际使用需替换。

日志由 `logback-spring.xml` 管理：dev 控制台 DEBUG；prod 控制台 INFO + 滚动文件（`logs/`，按天滚、留 30 天）+ ERROR 单独文件。

`codesandbox.type` 可选 `remote` / `example` / `thirdParty`（注意 Factory 里 case 是 `thirdParty` 驼峰）；`codesandbox.url` 仅 remote 生效，且**不含 `/executeCode` 后缀**（RemoteCodeSandbox 会自行拼接路径）。

## Docker 部署

- `Dockerfile`：amazoncorretto:21-alpine。**构建前需先把 `target/my-oj-0.0.1.jar` 复制到项目根目录**（Dockerfile `COPY my-oj-0.0.1.jar app.jar` 从构建上下文根取 jar）。
- `docker-compose.yml`：mysql:8（宿主机 3307）+ 后端服务，command 激活 prod profile（`--spring.profiles.active=prod`）并用 CLI 参数覆盖 codesandbox 配置（`--codesandbox.url` / `--codesandbox.type`）。
- `nginx.conf`：反代到后端 8101。
