# AGENTS.md

本文件是 AI 编码助手在本仓库工作的说明，也是项目开发约定的唯一来源（CLAUDE.md 只引用本文件）。面向人的介绍和快速开始见 README.md。

## 项目概述

在线判题系统（Online Judge）后端。技术栈：**Spring Boot 3.5 + Java 21 + MyBatis-Plus 3.5**（spring-boot3 starter + mybatis-plus-jsqlparser 分页插件）+ Hutool + Knife4j（OpenAPI 3 + Jakarta）。没有 Spring Security，鉴权用自定义 session + AOP 切面；不依赖 Redis。

用户代码在独立的代码沙箱 [my-oj-sandbox](https://github.com/Huangjl-cn/my-oj-sandbox) 中执行，接口约定见 `docs/sandbox-contract.md`。**不要在本后端里运行用户提交的代码。**

## 常用命令

```bash
docker compose -f deploy/docker-compose.dev.yml up -d   # 开发库：MySQL 8.4，root / oj_dev，首次启动自动执行 sql/create_table.sql
mvn spring-boot:run          # 本地运行：pom 自动激活 dev profile，127.0.0.1:8102，context-path /api
mvn test                     # 全部测试，不依赖数据库和沙箱
mvn test -Dtest=类名          # 单个测试类
mvn clean package            # 构建，产物 target/my-oj.jar
```

- 前置条件：JDK 21、Maven 3.6.3+。clone 后不需要任何配置文件。
- 在 IDE 里运行 `MainApplication` 时加 VM 参数 `-Dspring.profiles.active=dev`。
- 接口文档（仅 dev）：`http://localhost:8102/api/doc.html`。
- 建表脚本 `sql/create_table.sql` 创建 `oj_db`、各张表和开发用管理员 admin / 12345678（只在 user 表为空时插入）。它是表结构的唯一来源，改表结构时同步修改它。每次上线部署脚本都会在线上库重新执行它，所以只能写"不存在才建"的语句；给已有的表加字段、改字段，它覆盖不到，需要单独处理。

## 架构

经典分层：controller → service → mapper；`model/` 下分 entity / dto / vo / enums；`common/` 统一返回（BaseResponse + ResultUtils + ErrorCode）；`exception/` 全局异常（BusinessException + GlobalExceptionHandler）；`aop/` 请求日志和权限切面。

### 判题模块（`judge/`）

1. **提交即入队 + 有界调度**：`QuestionSubmitServiceImpl.doQuestionSubmit` 校验语言和题目，写入 WAITING 记录后直接返回提交 ID，不做异步派发——`question_submit` 表就是队列（WAITING 按 id 升序即提交顺序，雪花 id 单调递增）。`judge/queue/JudgeTaskDispatcher` 每轮（`judge.queue.poll-interval-ms`，默认 1s）按 id 升序领取 WAITING 记录，用 `Semaphore`（`judge.queue.concurrency`，默认 4）限制同时进行的沙箱调用数（削峰填谷，保护沙箱侧容器内存），任务在专用的 Java 21 虚拟线程 `judgeExecutor` 上执行。轮询和排队计数依赖 `idx_status_id (status, id)` 索引。排队人数通过 `/question/question_submit/queue/status` 查询（`aheadCount` = 前方 WAITING 数，`queueLength` = 队列总长，与实际派发顺序一致；登录即可访问；Long 字段序列化为字符串）。
2. **编排**：`JudgeServiceImpl.processSubmission` 用条件更新原子抢占 `WAITING → RUNNING`（抢占失败说明已被其他线程处理，属正常竞争，记 debug 后返回 null）；`runJudgePipeline` 调用沙箱、构建上下文、调用 `JudgeManager.applyStrategy` 并回写结果。成功时 `RUNNING → SUCCEED`，异常时尝试 `RUNNING → FAILED`。多实例部署时重复派发靠原子抢占收敛，不会重复判题。
3. **崩溃恢复（at-least-once）**：启动后首轮轮询自动续判遗留的 WAITING 记录；每 60s 把 RUNNING 超过 `judge.queue.stale-running-minutes`（默认 10 分钟，必须远大于判题最长耗时）的记录重置回 WAITING 重判。
4. **沙箱**（工厂 + 代理模式）：`CodeSandboxFactory` 按 `codesandbox.type` 创建实现，`CodeSandboxProxy` 包一层，在 DEBUG 级别记录完整的请求和响应。两个实现：
   - `RemoteCodeSandbox`：调用真实沙箱的 `/executeCode`，所有语言走同一个接口；每个请求用 ECDSA 签名（`SandboxAuthSigner`，协议见 `docs/sandbox-auth/README.md`）；
   - `ExampleCodeSandbox`：本地模拟，不联网，判题结果是假的，只用于跑通流程。
5. **策略**（策略模式 + 模板方法）：`AbstractJudgeStrategy.evaluate` 统一处理执行错误和资源限制；`JudgeManager` 用语言枚举注册表选择策略，再由共享的 `JudgeOutputComparator` 解析模板输出的 JSON 并按声明的类型比较。C++ 使用题目的基准限制，Go 额外 16 MB，Java 额外 64 MB / 2000 ms，Python 额外 32 MB / 2000 ms，JavaScript 额外 32 MB / 1000 ms。
6. **结构化用例**：题目的 `judgeCase` 保存参数定义、输出定义和结构化用例。`JudgeInputEncoder` 把每条用例编码为 `cases[].args[]`；沙箱只原样传递参数并返回 stdout，不理解 OJ 的值类型，也不接收预期答案。

### 提交归档与统计（`QuestionSubmitServiceImpl` + `QuestionSubmitMapper.xml`）

1. **全部提交视图** `list/page`：请求用独立的 `QuestionSubmitArchiveQueryRequest`（不继承 PageRequest，sortOrder 默认 descend；`my/list/page` 使用的 `QuestionSubmitQueryRequest` 保持不动）。`judgeResult` / `sortField` / `sortOrder` 走白名单校验（非法抛 PARAMS_ERROR）；判题结果语义的唯一来源是 `JudgeResultEnum`（含 SQL 片段常量和 `from(status, message)` 派生）。排序表达式（含 JSON_EXTRACT）通过 `wrapper.last("ORDER BY ...")` 整体拼接，缺失指标排在最后，同值按 createTime 倒序。响应平铺 questionTitle / questionTags / userName / userAvatar 和派生的 judgeResult，题目和用户摘要用批量 `listByIds` 预取（不要改回 N+1）。
2. **按题聚合视图** `group/page`：XML 中的自定义 SQL `selectQuestionGroupPage` / `countQuestionGroup`（窗口函数 `ROW_NUMBER` + `MIN(...) OVER`）；total 是题目数（`setSearchCount(false)` + 显式 count）；bestTime / bestMemory 分别独立取最小值（可能来自不同提交），统计范围随 `judgeResult` 等筛选变化，没有有效指标的行自动排除，null 排在最后。
3. **指标击败率** `rank`：对比人群是同题、同语言、仅 Accepted（复用 `JudgeResultEnum.ACCEPTED.getSqlFragment()`）、含本人；"超过"按指标 `>=` 本次提交计（持平计入，首次通过为 1/1 = 100%）。只返回 Total / Beaten 计数，比例由前端计算。
4. **SQL 前提**：`judgeInfo` 列永远是合法 JSON（WAITING 行写 `"{}"`，见 `doQuestionSubmit`），以上 SQL 的 `JSON_EXTRACT` 依赖这一点；自定义 SQL 的列名直接用 camelCase（因为 `map-underscore-to-camel-case: false`）；窗口函数要求 MySQL 8.0+。

### AI 陪练（`ai/` + `companion/`）

LangChain4j 调用 DeepSeek（OpenAI 兼容接口），费用走用户自己的 API Key。前端对接见 `docs/frontend/ai-companion.md`。

- **分层**：`ai/` 是通用 AI 基础设施（配置与校验、按用户 Key 创建模型、模拟模型、Key 加解密与校验、调用许可、错误翻译、用量日志），不依赖任何业务代码，以后的 AI 功能直接复用；`companion/` 是陪练的 AI 编排（AI Service 接口、分级规则、事实计算、提示词、工具、护栏、记忆、兜底话术）；接口和持久化沿用 controller / service / mapper 分层。依赖方向：controller → service → companion → ai。
- **AI Service**（`companion/assistant/`）：聊天（流式、记忆、工具）、看答案（流式、思考模式、记忆）、提示和提交后反应（结构化输出 + 输出护栏）、记忆压缩。每次请求按用户的 Key、模型和改代码权限新建，不做单例。**系统提示词和用户消息只能通过 `@V` 变量代入**：框架会把模板当模板渲染，题目或代码里的 `{{` 直接作为模板会报错（见 `CompanionAssistantWiringTest`）。
- **提示档位**（`companion/hint/HintLadder`）：0-5 档，表示"最多透露多少"，用户调 0-4，第 5 档只能通过看答案进入；提示接口按当前档位出，不改档位。用户只看到每档的名字和说明；每档按方向（找思路 IDEA / 找 bug BUG）各有一条给模型的规则，方向由模型判断、存在会话里，不展示给用户。改代码权限随档位放开（只修语法 → 可批注 → 每处 ≤3 行 → 不限），由 `CodeEditPolicy` 在 `edit_code` 工具里强制执行；文字回答的分寸由提示词约束。有问必答模式等同第 5 档。
- **改代码**（`companion/edit/`）：修改只作用于请求带上来的代码快照，原文必须唯一匹配；返回 `CodeEditProposal`（baseHash + 每处明细 + resultCode），前端预览后由用户决定是否应用。
- **事实由代码算，表达交给模型**（`companion/context/`）：提交后反应用到的"第几次、进步还是退步、连续同错、离上次多久"由 `SubmissionFactsBuilder` 计算；判题结果兼容 judgeInfo.message 中英文两种写法（`VerdictClassifier`）。
- **会话与对话**：`companion_session` 每人每题一行（档位、方向、当前对话）；`companion_conversation` 是同一题下的多段对话，各有记忆和上下文占用；消息带 `conversationId`。聊天、提示、看答案、整理记忆都按请求里的 `conversationId` 操作，并把它设为当前对话，提交后的反应记在当前对话里。
- **记忆**（`companion/memory/`）：每段对话一份，存 `companion_conversation.memory`（LangChain4j 序列化格式，写入前去掉思考内容）。上下文占用达到窗口的 `ai.companion.compact-ratio`（默认 0.8）时发送前自动压缩，也可手动压缩：较早的对话整理成分节笔记、用户提问原文保留、最近 `keep-recent-rounds` 轮原样保留，超长时分段滚动压缩。提示和反应不挂记忆，提示的结果由服务层补写进记忆，反应在下一轮作为"最近的提交与你的反应"带进用户消息。
- **调用约束**：同一用户同一时间只有一个进行中的 AI 调用（`AiCallGuard`，单实例内生效）；提交后反应在没配 Key 或调用失败时用兜底话术（只表达情绪，不给解题信息），同一次提交重复调用返回同一结果。
- **上下文占用**：流式结束时 LangChain4j 给的是整轮累计用量，当前上下文取最后一次调用的输入加输出（总数减去中间各次）。

### 鉴权

- 登录态存在 session：`UserService.getLoginUser(request)` 读 session 属性 `user_login`（`UserConstant.USER_LOGIN_STATE`），登出即删除该属性。
- 接口权限：`@AuthCheck(mustRole = "admin")` 注解 + `AuthInterceptor` 切面校验。
- 提交代码公开（产品决策"公开代码促进学习"）：`list/page`、`group/page`、`rank`、`get` 登录即可访问，提交的 `code` 对所有登录用户可见；`userAccount` 脱敏不返回（与 UserVO 一致）。

### 重要约定

- **提交相关接口都在 `QuestionController`**：`/question/question_submit/do`、`list/page`、`group/page`、`rank`、`get`、`queue/status`、`my/list/page`、`my/get`。
- **JSON 以文本存库**：`question.tags` / `judgeCase` / `judgeConfig` 和 `question_submit.judgeInfo` 是 text 列存 JSON 字符串；结构化 `judgeCase` 统一经 `JudgeCaseDataService` 校验和转换，其他字段沿用 Hutool `JSONUtil`。
- **Long 精度**：`JsonConfig` 全局把 Long 序列化为字符串（防止前端 JS 精度丢失）。
- **`map-underscore-to-camel-case: false`**：实体字段名必须与数据库列名完全一致（如 `userAccount`）。
- **逻辑删除**：全局 `isDelete` 字段（MyBatis-Plus 全局配置）。
- **状态与结果分离**：提交状态 `SUCCEED` 表示判题流程完成，不代表 Accepted；最终结果、首个错误用例或沙箱诊断在 `judgeInfo.message`，可能是多行文本。
- **判题并发**：`updateStatusIfCurrent` 用单条条件 SQL 保证 `WAITING → RUNNING → SUCCEED|FAILED`；并发上限由 `JudgeTaskDispatcher` 的许可数控制，没有分布式锁。修改并发、重试或失败处理时，必须测试重复执行和终态。

## 配置（`src/main/resources/`）

公共项在 `application.yml`，环境差异在 profile 文件中覆盖。**`application.yml` 不设默认 profile**：`mvn spring-boot:run` 由 pom 自动激活 dev，IDE 运行需加 `-Dspring.profiles.active=dev`，Docker 镜像固定 `SPRING_PROFILES_ACTIVE=prod`。不指定 profile 时应用因缺少数据库配置而启动失败，不会退回开发配置。

- `application-dev.yml`：写全开发默认值，clone 即可运行。监听 127.0.0.1:8102（`SERVER_ADDRESS` 可覆盖）；MySQL `localhost:3306/oj_db`，root / oj_dev（`MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` 可覆盖）；SQL 日志和 Knife4j 打开；沙箱默认 `example`，联调真实沙箱设 `CODESANDBOX_TYPE=remote` 和 `CODESANDBOX_URL`（默认 `http://localhost:8100`）。内置**开发专用**签名密钥对（key-id `sandbox-key-dev-2026`，与沙箱 dev 公钥配对；私钥已公开，生产沙箱不信任）。本机差异用环境变量或项目根目录的 `config/application-dev.yml`（已 gitignore）覆盖，不要改仓库里的文件。
- `application-prod.yml`：生产配置的唯一来源。端口 8101，数据库 `my-oj-db:3306/oj_db`，账号 `oj_app`；SQL 日志和接口文档关闭，session 24 小时，HikariCP 池 10，响应压缩开。密码、沙箱地址和签名密钥从环境变量读取（`MYSQL_PASSWORD`、`CODESANDBOX_URL`、`CODESANDBOX_AUTH_KEY_ID`、`CODESANDBOX_AUTH_PRIVATE_KEY`），缺失时启动失败。
- `src/test/resources/application-test.yml`：叠加在 dev 之上，只关闭判题调度器（`judge.queue.enabled=false`）。
- AI 配置（`ai.*`，绑定到 `AiProperties`，启动时由 `AiConfig` 校验）：公共部分在 `application.yml`（DeepSeek 地址、可选模型及上下文窗口、超时、陪练参数）；dev 的 `ai.mode` 默认 `mock`（模拟模型，不需要任何 Key，`AI_MODE=live` 切到真实模型），并内置**开发专用**的 Key 加密主密钥；prod 为 `live`，主密钥从 `AI_KEY_ENCRYPTION_KEY` 读取，缺失时启动失败。用户 Key 用 AES-GCM 加密存进 `user_ai_setting`，接口只返回后 4 位。

公共配置中的其他项：`codesandbox.timeout`（毫秒，同时用作 HTTP 连接和读取超时）；`codesandbox.url` 不带 `/executeCode` 后缀；`judge.queue.*`（`enabled`、`concurrency`、`poll-interval-ms`、`stale-running-minutes`）。`codesandbox.type` 只有 `remote` 和 `example` 两个取值。

日志由 `logback-spring.xml` 管理：dev 控制台、`com.hjl.oj` 为 DEBUG；prod 控制台 INFO + 滚动文件（`logs/`，按天滚动，保留 30 天）+ 单独的 ERROR 文件；其他情况（单独的 test、未指定 profile）只输出到控制台。

## 测试

- JUnit 5 + Spring Boot Test + Mockito。测试放在与被测代码相同的包下，覆盖成功和校验失败两类路径。
- 加载 Spring 上下文的测试统一用 `@ActiveProfiles({"dev", "test"})`，上下文不连数据库。
- `CodeSandboxTest` 调用真实沙箱，默认跳过；需要时设 `RUN_SANDBOX_TESTS=true`，沙箱地址用 `CODESANDBOX_URL`。其他测试用 mock 或本机临时端口（参考 `RemoteCodeSandboxTest`）。
- 测试类命名 `*Test`，方法名描述被测行为。
- Mockito 由 surefire 以 `-javaagent` 加载（见 pom）。在 IDE 里直接运行测试报 MockMaker 初始化失败时，给 JVM 加同样的 `-javaagent:<mockito-core jar>`。

## 编码约定

- 四空格缩进，沿用现有的 Spring 和 Lombok 写法；类名 PascalCase，方法和字段 camelCase，常量 UPPER_SNAKE_CASE。
- 请求模型放在 `model/dto/<功能>`；接口返回统一用 `BaseResponse` / `ResultUtils`，错误抛 `BusinessException`。
- **密码字段必须加 `@ToString.Exclude`**：`LogInterceptor` 会以 INFO 级别打印接口入参的 toString（见 `PasswordToStringTest`）。API Key、用户代码和对话内容同样排除（见 `SensitiveToStringTest`）。
- 业务异常（4xxxx）记 warn 不打堆栈，系统错误（5xxxx）记 error。

## 安全与凭据

- 仓库里只允许出现开发专用、只能访问本机开发资源的凭据，并注释标明（见 `application-dev.yml`）。
- 生产的数据库密码、沙箱签名私钥、AI Key 加密主密钥只通过服务器上的 `deploy/.env` 注入，不进仓库、镜像和日志。主密钥部署后不要更换，否则已保存的用户 Key 全部无法解密。
- 与环境有关的值放在 profile 配置中，不写进公共配置或代码。

## 部署（`deploy/` 和 `.github/workflows/`）

- **流水线**：`ci.yml` 在每次 push 和 PR 时跑 `mvn verify`；`deploy.yml` 在合进 master 后构建 jar，经 GitHub `production` 环境批准，用 SSH 把 jar、Dockerfile、`docker-compose.yml` 和 `sql/create_table.sql` 打包送到服务器。部署所需的 `DEPLOY_HOST` / `DEPLOY_PORT` / `DEPLOY_SSH_KEY` / `DEPLOY_KNOWN_HOSTS` 是 production 环境的密钥，应用的秘密不进 GitHub。
- **`server/deploy.sh`**：服务器上的部署脚本，前后端共用，装在 `/opt/my-oj-deploy/`，部署钥匙在 authorized_keys 里被限定为只能执行它（参数来自 `SSH_ORIGINAL_COMMAND`）。
  - 后端：构建 `my-oj-backend:<提交号>` → 执行建表脚本 → 标成 `current` 并重建容器 → 等 healthcheck 通过 → reload nginx（容器 IP 可能变）；不通过就切回上一版本。
  - 前端：放进 `oj-frontend/www/releases/<时间-提交号>/`（补上上一版本自己的 js/assets，防止开着的旧页面加载失败）→ 原子切换 `www/current` → nginx.conf 原地写入并 reload（单文件挂载，不能换 inode）。
  - 各保留最近 5 个版本，`rollback frontend|backend` 切回上一个。仓库公开、Actions 日志所有人可见，所以脚本输出不含容器日志和任何配置值，容器日志只写进服务器的 `.deploy/deploy.log`。
- **`server/setup-server.sh`**：服务器一次性准备（建 `deploy` 账号、钥匙、版本目录，把正在运行的后端记为 `initial`），可重复执行，`--check` 只检查不改。
- `oj-backend/Dockerfile`：amazoncorretto:21-alpine，固定 `SPRING_PROFILES_ACTIVE=prod`，以非 root 用户运行。
- `docker-compose.yml`：MySQL 8.4（宿主机 127.0.0.1:3307）+ 后端（127.0.0.1:8101，镜像固定 `my-oj-backend:current`，不写 build，healthcheck 打 `/api/actuator/health`）+ 前端 nginx（挂载 `oj-frontend/www`，nginx.conf 来自前端仓库）。只通过环境变量传值，变量模板见 `deploy/.env.example`。应用使用 `oj_app` 账号，root 只允许容器内登录；这两项只在首次初始化数据卷时生效。
- 健康检查：actuator 只开放 `health`，不返回组件明细（`application.yml` 的 `management.*`，见 `ActuatorEndpointTest`）；nginx 对外屏蔽 `/api/actuator/`。
- `docker-compose.dev.yml`：只用于本地开发的 MySQL，与生产无关。

## 提交与合并请求

- 提交信息用中文约定式提交：`<类型>(<范围>): <说明>`，类型如 feat、fix、docs、test、refactor、build、chore。
- 合并请求说明行为变化、配置或表结构的影响、执行过的命令；接口契约有变化时附示例，并同步更新 `docs/sandbox-contract.md` 或 `docs/frontend/` 下的文档。
