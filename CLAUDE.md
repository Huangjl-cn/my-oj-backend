# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

在线判题系统（Online Judge）后端。技术栈：**Spring Boot 3.5.16 + Java 21 + MyBatis-Plus 3.5.17**（spring-boot3 starter + mybatis-plus-jsqlparser 分页插件）+ Hutool 5.8.47 + Knife4j 4.4.0（OpenAPI 3 + Jakarta）。无 Spring Security，鉴权用自定义 session + AOP 切面。Redis 依赖已注释且在 `MainApplication` 中 `exclude RedisAutoConfiguration`，无需 Redis 即可启动。

## 常用命令

```bash
mvn clean package          # 构建
mvn spring-boot:run        # 本地运行，pom 中配置自动激活 dev profile，127.0.0.1:8102，context-path /api
mvn test                   # 全部测试，不依赖数据库和沙箱
mvn test -Dtest=CodeSandboxTest   # 单个测试类
```

- 前置条件：JDK 21、Maven 3.6.3+。clone 后无需任何配置文件即可运行。
- 开发库：`docker compose -f deploy/docker-compose.dev.yml up -d`（MySQL 8.4，root / oj_dev，首次启动自动执行 `sql/create_table.sql`）。无 Docker 时手动执行该脚本：创建 `oj_db`、四张表（user / question / question_starter_code / question_submit）、默认 admin 账号（密码 12345678，仅开发用；脚本可重复执行）。
- 接口文档（dev）：`http://localhost:8102/api/doc.html`（Knife4j）。
- 测试注意：加载 Spring 上下文的测试统一用 `@ActiveProfiles({"dev", "test"})`；test profile（`src/test/resources/application-test.yml`）只覆盖与 dev 不同的项，目前是关闭判题调度器（`judge.queue.enabled=false`），上下文不连数据库。`CodeSandboxTest.executeCode` 调用真实沙箱，默认跳过，需 `RUN_SANDBOX_TESTS=true`（沙箱地址用 `CODESANDBOX_URL`）。

## 架构

经典分层：controller → service → mapper；`model/` 下分 entity / dto / vo / enums；`common/` 统一返回（BaseResponse + ResultUtils + ErrorCode）；`exception/` 全局异常（BusinessException + GlobalExceptionHandler）。

### 判题模块（核心，`judge/`）— 一条设计模式流水线

1. **提交即入队 + 有界调度**：`QuestionSubmitServiceImpl.doQuestionSubmit` 校验语言/题目，落 WAITING 记录后直接返回提交 ID，不做异步派发——`question_submit` 表即队列（WAITING 按 id 升序 = 提交顺序，雪花 id 单调递增）。`judge/queue/JudgeTaskDispatcher` 每轮（`judge.queue.poll-interval-ms` 默认 1s）按 id 升序领取 WAITING 记录，`Semaphore`（`judge.queue.concurrency` 默认 4）限制同时在跑的沙箱调用数（削峰填谷，保护沙箱侧容器内存），执行仍走专用 Java 21 虚拟线程 `judgeExecutor`；轮询与排队计数依赖 `idx_status_id (status, id)` 索引（见 `sql/add_question_submit_status_id_index.sql`）。排队人数经 `/question/question_submit/queue/status` 查询（`aheadCount`=前方 WAITING 数、`queueLength`=队列总长，与实际派发顺序严格一致，登录即可，Long 字段序列化为字符串）。
2. **编排**：`JudgeServiceImpl.processSubmission` 用条件更新原子抢占 `WAITING → RUNNING`（抢占失败 = 已被其他判题线程处理，属正常竞争，log.debug 后返回 null），`runJudgePipeline` 调沙箱、构建上下文、调用 `JudgeManager.applyStrategy` 并回写结果；成功时 `RUNNING → SUCCEED`，异常时尝试 `RUNNING → FAILED`。多实例部署安全：重复派发靠原子抢占收敛，不会重复判。
3. **崩溃恢复（at-least-once）**：启动后首轮轮询自动续判遗留的 WAITING 记录；每 60s 将 RUNNING 超过 `judge.queue.stale-running-minutes`（默认 10 分钟，须远大于判题最大耗时）的卡死记录重置回 WAITING 重判。
4. **沙箱**（工厂 + 代理模式）：`CodeSandbox` 接口，`CodeSandboxFactory` 按 `codesandbox.type` 配置实例化，`CodeSandboxProxy` 包一层做请求/响应日志。三个实现：
   - `RemoteCodeSandbox` — 真实调用外部沙箱 HTTP 接口，带 `auth: secretKey` 请求头；所有语言统一走 `/executeCode`，由沙箱按语言选择执行环境；URL 来自 `codesandbox.url`；
   - `ExampleCodeSandbox` — 本地模拟，不联网；
   - `ThirdPartyCodeSandbox` — 占位骨架。
5. **策略**（策略模式 + 模板方法）：`AbstractJudgeStrategy.evaluate` 统一执行错误和资源限制；`JudgeManager` 用语言枚举注册表解析策略，再由共享的 `JudgeOutputComparator` 解析模板输出的 JSON 并按声明类型比较。C++ 使用基准限制，Go 额外 16 MB，Java 额外 64 MB / 2000 ms，Python 额外 32 MB / 2000 ms，JavaScript 额外 32 MB / 1000 ms。
6. **结构化用例**：题目 `judgeCase` 保存参数定义、输出定义和结构化用例。`JudgeInputEncoder` 将每条用例编码为 `cases[].args[]`，代码沙箱只原样传递独立参数并返回 stdout，不理解 OJ 值类型或预期答案。

### 提交归档与统计（`QuestionSubmitServiceImpl` + `QuestionSubmitMapper.xml`）

1. **全部提交视图** `list/page`：请求用独立的 `QuestionSubmitArchiveQueryRequest`（不继承 PageRequest，sortOrder 缺省 descend，与 my/list/page 共用的旧 `QuestionSubmitQueryRequest` 保持不动）；`judgeResult`/`sortField`/`sortOrder` 白名单校验（非法抛 PARAMS_ERROR），判题结果语义唯一来源是 `JudgeResultEnum`（含 SQL 片段常量 + `from(status, message)` 派生）；排序表达式（含 JSON_EXTRACT）整体经 `wrapper.last("ORDER BY ...")` 拼接，缺失指标垫底、同值按 createTime 倒序；响应平铺 questionTitle/questionTags/userName/userAvatar 与派生 judgeResult，题目/用户摘要用批量 `listByIds` 预取（勿改回 N+1）。
2. **按题聚合视图** `group/page`：项目首批自定义 SQL（XML 的 `selectQuestionGroupPage`/`countQuestionGroup`，窗口函数 `ROW_NUMBER` + `MIN(...) OVER`）；total 是题目数（`setSearchCount(false)` + 显式 count）；bestTime/bestMemory 分别独立取最小（可能来自不同提交），人群随 `judgeResult` 等筛选变化，无有效指标的行自动排除、null 垫底。
3. **指标击败率** `rank`：对比人群为同题同语言 + 仅 Accepted（复用 `JudgeResultEnum.ACCEPTED.getSqlFragment()`）+ 含本人；"超过"= 指标 `>=` 本次提交（持平计入，首次通过提交 1/1=100%）；只返回 Total/Beaten 计数，比例由前端计算。
4. **SQL 前提**：`judgeInfo` 列永远是合法 JSON（WAITING 行写 `"{}"`，见 `doQuestionSubmit`），以上 SQL 的 `JSON_EXTRACT` 依赖这一点；自定义 SQL 列名直接用 camelCase（同 `map-underscore-to-camel-case: false`）；窗口函数要求 MySQL 8.0+。

### 鉴权

- 登录态存 session：`UserService.getLoginUser(request)` 读 session 属性 `user_login`（`UserConstant.USER_LOGIN_STATE`），登出即删除该属性。
- 接口权限：`@AuthCheck(mustRole = "admin")` 注解 + `AuthInterceptor` AOP 切面校验，无 Spring Security。
- 提交浏览与代码公开（产品决策"公开代码促进学习"）：`list/page`、`group/page`、`rank`、`get` 详情均为登录即可访问；`get` 不再要求本人或管理员，提交 `code` 在列表/聚合/详情中对所有登录用户可见；`userAccount` 维持脱敏不返回（与 UserVO 一致）。

### 重要约定 / 坑

- **提交接口已合并**：`QuestionSubmitController` 整体 @Deprecated、路由被注释（为微服务拆分预留）；题目提交相关接口在 `QuestionController` 下：`/question/question_submit/do`、`/question/question_submit/list/page`（归档-全部提交视图）、`/question/question_submit/group/page`（归档-按题聚合视图）、`/question/question_submit/rank`（指标击败率）、`/question/question_submit/get`（详情）、`/question/question_submit/queue/status`（排队人数，判题等待期间前端轮询）。改提交逻辑去 QuestionController / QuestionSubmitServiceImpl，不要动废弃 Controller。
- **JSON 以文本存库**：`question.tags` / `judgeCase` / `judgeConfig`、`question_submit.judgeInfo` 是 text 列存 JSON 字符串；结构化 `judgeCase` 统一通过 `JudgeCaseDataService` 校验和转换，其他字段沿用 Hutool `JSONUtil`。
- **Long 精度**：`JsonConfig` 全局把 Long 序列化为字符串（防前端 JS 精度丢失），返回 Long 的接口无需单独处理。
- **`map-underscore-to-camel-case: false`**：实体字段名必须与数据库列名完全一致（如 `userAccount`）。
- 逻辑删除：全局 `isDelete` 字段（MyBatis-Plus 全局配置）。
- **状态与结果分离**：提交状态 `SUCCEED` 表示判题流程完成，不代表 Accepted；最终 verdict、首个错误用例或沙箱诊断在 `judgeInfo.message`。消息可能为多行文本。
- **判题并发与失败收敛**：`updateStatusIfCurrent` 用单条条件 SQL 保证 `WAITING → RUNNING → SUCCEED|FAILED`；并发上限由 `JudgeTaskDispatcher` 的许可数控制，无分布式锁（多实例靠原子抢占收敛）；卡死的 RUNNING 由调度器按 `judge.queue.stale-running-minutes` 超时重置回 WAITING 重判。

## 配置（`src/main/resources/`）

公共项在 `application.yml`，环境差异拆在 profile 文件，profile 覆盖同名公共项。**`application.yml` 不设默认 profile**，必须显式指定环境：`mvn spring-boot:run` 由 pom 的 spring-boot-maven-plugin 自动激活 dev，IDE 加 `-Dspring.profiles.active=dev`，Docker 镜像固定 `SPRING_PROFILES_ACTIVE=prod`。

- `application-dev.yml`：本地运行，**写全开发默认值，clone 即可运行**。监听 127.0.0.1:**8102**（`SERVER_ADDRESS` 可覆盖），MySQL `localhost:3306/oj_db` root / oj_dev（`MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` 可覆盖），SQL 日志开、Knife4j 开、session/cookie 30 天。沙箱默认 `example`（模拟，结果是假的），联调真实沙箱设 `CODESANDBOX_TYPE=remote` + `CODESANDBOX_URL`（默认 `http://localhost:8100`）。**内置开发专用测试密钥对**（key-id `sandbox-key-dev-2026`，与 my-oj-sandbox 的 dev 公钥配对；已公开，生产沙箱不信任）。本机差异用环境变量或项目根目录 `config/application-dev.yml`（已 gitignore）覆盖，不要改仓库里的文件。新密钥对用 `SandboxAuthKeygenTool` 生成，协议见 `docs/sandbox-auth/README.md`（两端 key-id 必须一致）。
- `application-prod.yml`：上线部署。端口 **8101**，MySQL `my-oj-db`（docker 网络内），SQL 日志和接口文档（knife4j + springdoc）全关、session/cookie 24 小时、HikariCP 池 10、响应压缩开、codesandbox.url 指向沙箱服务器内网地址（如 CODESANDBOX_PROD_URL_PLACEHOLDER:8100，部署时可按需覆盖）。
- `src/test/resources/application-test.yml`：测试专用，叠加在 dev 之上，只关闭判题调度器。

日志由 `logback-spring.xml` 管理：dev 控制台 DEBUG；prod 控制台 INFO + 滚动文件（`logs/`，按天滚、留 30 天）+ ERROR 单独文件；其他情况（单独 test、未指定 profile）仅控制台。请求日志切面 `LogInterceptor` 会打印接口入参的 toString，**含密码的字段必须加 `@ToString.Exclude`**（见 `PasswordToStringTest`）。

`codesandbox.type` 可选 `remote` / `example` / `thirdParty`（注意 Factory 里 case 是 `thirdParty` 驼峰）；`codesandbox.url` 仅 remote 生效，且**不含 `/executeCode` 后缀**（RemoteCodeSandbox 会自行拼接路径）。`codesandbox.timeout` 位于公共配置，默认 60000 ms，同时用于 HTTP 连接和读取超时。`judge.queue.*` 也在公共配置：`concurrency`（判题并发上限）、`poll-interval-ms`（轮询间隔）、`stale-running-minutes`（卡死 RUNNING 重置阈值）。

## Docker 部署

- `deploy/oj-backend/Dockerfile`：amazoncorretto:21-alpine，镜像固定 `SPRING_PROFILES_ACTIVE=prod`。构建前把 `target/my-oj.jar`（pom 固定 finalName）复制为 `deploy/oj-backend/my-oj.jar`。
- `deploy/docker-compose.yml`：mysql:8.4（宿主机 127.0.0.1:3307）+ 后端 + 前端 nginx（前端目录 `deploy/oj-frontend/` 不在本仓库），command 激活 prod profile 并用 CLI 参数覆盖数据源和 codesandbox 配置。
- `deploy/docker-compose.dev.yml`：仅本地开发用的 MySQL，与生产无关。
