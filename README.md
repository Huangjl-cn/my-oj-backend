# MyOJ 在线判题系统 · 后端

> 一个像 LeetCode 一样的在线编程判题平台后端：用户提交代码，系统在远程沙箱中隔离执行，自动判定结果。

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green) ![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.17-blue) ![MySQL](https://img.shields.io/badge/MySQL-8.4-lightblue) ![Docker](https://img.shields.io/badge/Docker-ready-blue)

---

## 功能特性

- **多语言判题**：支持 Java、C++、Go、Python、JavaScript，按语言自动选择资源限制策略
- **远程沙箱隔离**：用户代码在独立沙箱中编译执行，后端永不接触用户代码的运行环境
- **排队判题**：提交后立即返回，调度器按提交顺序派发，限制同时判题数保护沙箱，进程重启后自动续判
- **结构化用例**：题目定义输入参数与输出类型，沙箱只做原始编译执行，判题结果由后端类型化比较
- **安全鉴权**：沙箱调用采用 ECDSA 请求签名，防伪造、防重放
- **防重复判题**：`WAITING → RUNNING → SUCCEED | FAILED` 状态机 + 条件更新，保证同一提交只判一次
- **AI 陪练**：做题页的陪练伙伴（LangChain4j + DeepSeek，用户自带 API Key）。提交后结合进退步说话；分级提示分「找思路 / 找 bug」两条线，按用户选择的程度透露；能看用户代码、查提交记录、提出代码修改（用户预览后再应用）；记忆按上下文占用自动压缩
- **完整的管理能力**：用户注册登录、题目增删改查、提交记录查询、管理员权限（`@AuthCheck` + AOP）
- **开箱即用的接口文档**：Knife4j 在线文档（dev 环境）

## 判题架构

```mermaid
flowchart LR
    A["用户提交代码<br/>QuestionSubmitService"] --> B["存储 WAITING 提交<br/>（提交表即队列）"]
    B --> C["调度器按序领取<br/>JudgeTaskDispatcher"]
    C --> D{"原子抢占<br/>WAITING → RUNNING"}
    D -- "抢占失败" --> X["丢弃任务<br/>防止重复判题"]
    D -- "抢占成功" --> E["CodeSandboxFactory<br/>remote / example"]
    E --> F["沙箱编译执行<br/>cases[].args[]"]
    F --> G["JudgeManager<br/>按语言选择策略"]
    G --> H["AbstractJudgeStrategy<br/>执行错误 / 资源限制检查"]
    H --> I["JudgeOutputComparator<br/>JSON 输出类型化比较"]
    I --> J{"终态更新<br/>RUNNING → SUCCEED / FAILED"}
```

- **代码沙箱**：`CodeSandbox` 接口 + 工厂模式按配置切换实现 + 代理模式做日志增强
- **判题策略**：策略模式管理各语言资源配额，`AbstractJudgeStrategy` 统一执行错误与超限检查
- **结果语义**：提交状态 `SUCCEED` 表示判题流程完成；最终 verdict、首个失败用例或沙箱诊断保存在 `judgeInfo.message`

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 开发语言（虚拟线程异步判题） |
| Spring Boot | 3.5.16 | 基础框架 |
| MyBatis-Plus | 3.5.17 | ORM |
| MySQL | 8.4 | 数据库 |
| Knife4j | 4.4.0 | 接口文档（OpenAPI 3） |
| Hutool | 5.8.47 | 工具库 |
| Docker | - | 一键部署（MySQL + 后端） |

## 快速开始

**前置条件**：JDK 21、Maven 3.6.3+；开发数据库用 Docker 一键启动（或自备 MySQL 8）。clone 后不需要创建任何配置文件。

```bash
# 1. 启动开发数据库（MySQL 8.4，账号 root / oj_dev；首次启动自动建表，并创建管理员 admin / 12345678）
docker compose -f deploy/docker-compose.dev.yml up -d

# 2. 启动后端（自动激活 dev profile，默认使用模拟沙箱，监听 127.0.0.1:8102）
mvn spring-boot:run
```

- **没有 Docker**：自行安装 MySQL 8，执行 `mysql -uroot -p < sql/create_table.sql`；root 密码不是 `oj_dev` 时，用环境变量 `MYSQL_PASSWORD` 覆盖。
- **在 IDE 里运行** `MainApplication`：加 VM 参数 `-Dspring.profiles.active=dev`。
- **联调真实沙箱**（[my-oj-sandbox](https://github.com/Huangjl-cn/my-oj-sandbox)）：`CODESANDBOX_TYPE=remote CODESANDBOX_URL=http://<沙箱地址>:8100 mvn spring-boot:run`。开发用的签名密钥对已内置，与沙箱的 dev 配置配对。
- **AI 陪练**默认用模拟模型（不需要任何 Key，回复是固定话术）；要联调真实模型，用 `AI_MODE=live mvn spring-boot:run` 启动，再在页面的 AI 设置里保存自己的 DeepSeek Key。
- **其他开发配置**都可以用环境变量覆盖，见 `src/main/resources/application-dev.yml`；也可以在项目根目录建 `config/application-dev.yml`（已被 gitignore）。

接口文档：http://localhost:8102/api/doc.html

## 测试

```bash
mvn test   # 不依赖数据库和沙箱

# 调用真实沙箱的集成测试默认跳过，需要时：
RUN_SANDBOX_TESTS=true CODESANDBOX_URL=http://<沙箱地址>:8100 mvn test -Dtest=CodeSandboxTest
```

## 部署

### 自动部署（日常上线）

由 GitHub Actions 完成，流水线在 `.github/workflows/`：

- **每次 push 和 PR**：`ci.yml` 跑 `mvn verify`。
- **合进 master**：`deploy.yml` 构建 jar，在 GitHub 的 `production` 环境里批准后，通过 SSH 把 jar、Dockerfile、`docker-compose.yml` 和建表脚本送到服务器。服务器上的 `deploy/server/deploy.sh` 接手：
  1. 构建镜像 `my-oj-backend:<提交号>`；
  2. 执行 `sql/create_table.sql`（只建不存在的表，不用再手动执行）；
  3. 把镜像标成 `current` 并重建后端容器；
  4. 等健康检查 `/api/actuator/health` 通过，不通过就切回上一版本。
- **重新部署旧提交**：Actions → Deploy → Run workflow，填提交号。
- **在服务器上立即切回上一版**：`sudo -u deploy /opt/my-oj-deploy/deploy.sh rollback backend`；`status` 查看当前版本。

前端仓库的部署走同一个 `deploy.sh`（`frontend` 命令）。部署钥匙在服务器上被限定为只能执行这个脚本。

服务器第一次接入自动部署时，用 root 运行一次 `deploy/server/setup-server.sh`（先加 `--check` 只看不改）。它会：
- 建部署账号 `deploy` 和部署钥匙；
- 把前端改成版本目录结构；
- 把正在运行的后端记成第一个版本。

### 服务器目录

```
my-oj/
├── docker-compose.yml        # 来自本仓库 deploy/，部署时自动同步
├── .env                      # 秘密，只在服务器上，见下表
├── oj-backend/               # Dockerfile、init.sql（数据库首次初始化用）
├── oj-frontend/
│   ├── nginx.conf            # 来自前端仓库，部署时自动同步
│   ├── www/releases/<时间-提交号>/   # 前端各版本，保留最近 5 个
│   ├── www/current -> releases/...    # 正在用的版本
│   └── certs/、acme-challenge/
└── .deploy/                  # 部署记录和日志（deploy.log）
```

- 启动 MySQL（宿主机 127.0.0.1:3307）、后端（127.0.0.1:8101）和前端 nginx（80/443）。
- 镜像固定使用 prod profile，接口文档和 SQL 日志关闭。
- 应用使用只授权 `oj_db` 的 `oj_app` 账号；root 只能在容器内登录：`docker exec -it my-oj-db mysql -uroot -p`。账号在首次初始化数据卷时创建。

部署所需的环境变量（写在 `deploy/.env`，不入库）：

| 变量 | 说明 | 秘密 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码，只用于运维 | 是 |
| `MYSQL_PASSWORD` | 应用账号 `oj_app` 的密码 | 是 |
| `CODESANDBOX_URL` | 沙箱地址，不带 `/executeCode`，如 `http://10.0.0.2:8100` | 否 |
| `CODESANDBOX_AUTH_KEY_ID` | 签名 key-id，与沙箱生产配置一致 | 否 |
| `CODESANDBOX_AUTH_PRIVATE_KEY` | 签名私钥（Base64 PKCS#8），生成方法见[沙箱调用鉴权](./docs/sandbox-auth/README.md) | 是 |
| `AI_KEY_ENCRYPTION_KEY` | 加密用户 AI API Key 的主密钥，`openssl rand -base64 32` 生成；部署后不要更换 | 是 |

### 手动部署（自动部署不可用时）

```bash
mvn clean package
# 把 target/my-oj.jar 和 deploy/oj-backend/Dockerfile 上传到服务器的同一个目录，在那里执行：
docker build -t my-oj-backend:manual .
docker tag my-oj-backend:manual my-oj-backend:current
# 在服务器的 my-oj 目录执行：
docker exec -i my-oj-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -uoj_app' < create_table.sql
docker compose up -d --no-deps --force-recreate my-oj-backend
docker exec my-oj-frontend nginx -s reload   # 后端换了容器，让 nginx 重新解析地址
```

全新服务器首次部署：上传本仓库的 `deploy/` 目录，复制 `sql/create_table.sql` 为 `oj-backend/init.sql`、`.env.example` 为 `.env` 并填写，按上面的方法构建 `my-oj-backend:current`，执行 `docker compose up -d`，再运行 `setup-server.sh` 接入自动部署。

## 项目结构

```
src/main/java/com/hjl/oj/
├── controller/        # HTTP 接口层
├── service/           # 业务逻辑层
├── ai/                # 通用 AI 基础设施：配置、模型创建、Key 加密与校验、调用许可
├── companion/         # AI 陪练：AI Service、分级提示、工具、护栏、记忆、提示词
├── judge/             # 判题模块（核心）
│   ├── codesandbox/   # 代码沙箱：工厂 + 代理模式、请求签名
│   ├── queue/         # 判题调度器：按序派发、并发限制、卡死恢复
│   └── strategy/      # 判题策略：策略模式
├── mapper/            # MyBatis-Plus 数据访问层
├── model/             # dto / entity / enums / vo
├── aop/               # 请求日志、权限校验切面（@AuthCheck）
├── annotation/        # 自定义注解
├── config/            # 配置类（JSON、MyBatis-Plus、沙箱签名等）
├── common/            # 统一返回、错误码
├── constant/          # 常量
├── exception/         # 业务异常与全局异常处理
└── utils/             # 工具类
```

## 相关仓库

- [my-oj-sandbox](https://github.com/Huangjl-cn/my-oj-sandbox)：代码沙箱，在 Docker 容器中隔离编译、执行用户代码。本地联调见上面的“联调真实沙箱”。
- 前端：单独的仓库，有自己的 CI 和部署流水线，部署时同样调用服务器上的 `deploy.sh`。

## 更多文档

- [代码沙箱接口契约](./docs/sandbox-contract.md)
- [沙箱调用鉴权](./docs/sandbox-auth/README.md)（ECDSA 签名、密钥生成与轮换）
- [前端对接说明](./docs/frontend/)（含 [AI 陪练](./docs/frontend/ai-companion.md)）
- [开发约定](./AGENTS.md)（架构、配置、测试、提交规范）
- [数据库建表脚本](./sql/create_table.sql)

## License

[MIT](./LICENSE)
