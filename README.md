# 在线判题系统 (Online Judge)-backend

## 项目介绍

这是一个基于Spring Boot开发的在线判题系统（Online Judge）-backend，用于管理编程题目、接收用户代码提交并进行自动判题。系统支持用户注册登录、题目管理、代码提交和判题等核心功能。

### 主要功能

- **用户管理**：注册、登录、个人信息管理
- **题目管理**：题目列表、题目详情、题目编辑
- **代码提交**：支持 Java、C++、Go、Python 和 JavaScript
- **自动判题**：调用远程代码沙箱执行用户代码并进行判题
- **判题结果**：返回执行结果、运行时间、内存消耗等详细信息

## 项目使用的工具栈

| 技术/框架 | 版本     | 用途 |
|---------|--------|------|
| Spring Boot | 3.5.16 | 基础框架 |
| Java | 21     | 开发语言 |
| MyBatis-Plus | 3.5.17 | ORM框架（spring-boot3 starter + jsqlparser分页） |
| MySQL | -      | 数据库 |
| Hutool | 5.8.47 | 工具库 |
| Knife4j | 4.4.0  | 接口文档（OpenAPI 3 + Jakarta） |
| Lombok | -      | 代码简化 |
| Spring AOP | -      | 面向切面编程（权限校验） |

## 项目结构

```
src/main/java/com/hjl/oj/
├── annotation/        # 注解定义（@AuthCheck）
├── aop/               # 切面实现（权限校验、日志）
├── common/            # 通用类（统一返回、错误码）
├── config/            # 配置类（CORS、JSON、MyBatis-Plus）
├── constant/          # 常量定义
├── controller/        # 控制器
├── exception/         # 异常处理
├── judge/             # 判题模块
│   ├── codesandbox/   # 代码沙箱（工厂 + 代理模式）
│   └── strategy/      # 判题策略（策略模式）
├── mapper/            # 数据访问层
├── model/             # 数据模型
│   ├── dto/           # 数据传输对象
│   ├── entity/        # 实体类
│   ├── enums/         # 枚举类
│   └── vo/            # 视图对象
├── service/           # 服务层
├── utils/             # 工具类
└── MainApplication.java  # 应用入口
```

## 配置文件说明

配置文件位于 `src/main/resources/`，公共项在 `application.yml`，环境差异按 profile 拆分（profile 文件覆盖同名公共项）：

| 文件 | 端口 | 数据库 | 说明 |
|------|------|--------|------|
| `application.yml` | - | - | 公共配置（默认激活 dev） |
| `application-dev.yml` | 8102 | localhost:3306/oj_db | 开发环境（默认） |
| `application-prod.yml` | 8101 | my-oj-db:3306/oj_db | 生产环境（docker 网络内） |
| `application-test.yml` | 8101 | localhost:3306/my_db | 测试环境（占位配置，需替换） |

### 核心配置项（application.yml 公共部分）

```yaml
spring:
  application:
    name: my-oj
  profiles:
    active: dev          # 默认 dev 环境（部署时用 --spring.profiles.active=prod 覆盖）
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
server:
  address: 0.0.0.0
  servlet:
    context-path: /api   # 统一接口前缀
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: false   # 字段名与列名保持一致
  global-config:
    db-config:
      logic-delete-field: isDelete        # 全局逻辑删除字段
```

环境差异见 `application-dev.yml` / `application-prod.yml`：

```yaml
# application-dev.yml（本地运行，宽松优先）
server:
  port: 8102
spring:
  session:
    timeout: 2592000     # session 30 天过期
  datasource:
    url: jdbc:mysql://localhost:3306/oj_db
    username: root
    password: your_password
codesandbox:
  type: remote           # remote / example / thirdParty
  url: http://172.25.66.109:8100   # 沙箱需 Linux 环境，dev 指向 WSL；prod 指向沙箱服务器内网地址
```

**配置说明**

1. **数据库配置**：`url` 格式为 `jdbc:mysql://{host}:{port}/{database}`，需先在本地启动 MySQL 并执行建表脚本。
2. **服务器配置**：dev 端口 8102，prod 端口 8101，统一前缀 `/api`。
3. **会话配置**：dev 环境 session 与 cookie 均 30 天过期（方便），prod 24 小时（安全）；Redis 相关配置已注释（`MainApplication` 排除了 Redis 自动配置）。
4. **MyBatis-Plus 配置**：`map-underscore-to-camel-case: false`，实体字段必须与数据库列名完全一致（如 `userAccount`）；SQL 日志 dev 开启、prod 关闭。
5. **接口文档**：dev 开启 Knife4j（`/api/doc.html`）；prod 关闭 knife4j 和 springdoc（`/v3/api-docs` 也不可访问，防止泄露接口结构）。
6. **代码沙箱配置**：
   - `codesandbox.type`：`remote`（调用外部沙箱接口）、`example`（本地模拟）、`thirdParty`（占位）；
   - `codesandbox.url`：仅 remote 生效且不含 `/executeCode` 后缀（沙箱调用方会自行拼接）；所有语言统一请求 `/executeCode`，由代码沙箱按语言选择执行环境，请求头带 `auth: secretKey` 鉴权。
   - `codesandbox.timeout`：远程沙箱 HTTP 连接和读取超时，默认 `60000` ms。
7. **日志**：由 `logback-spring.xml` 管理，dev 控制台 DEBUG；prod 控制台 INFO + 滚动文件日志（`logs/`，按天滚动保留 30 天）+ ERROR 单独文件。

## 项目启动

### 前置条件

- JDK 21 或以上
- Maven 3.6 或以上
- MySQL 5.7 或以上

### 启动步骤

1. **创建数据库**
   - 执行 `sql/create_table.sql` 文件创建数据库表结构（含默认 admin 账号，密码 12345678）
   - 或使用 `docker-compose.yml` 一键启动 MySQL（宿主机端口 3307）

2. **配置数据库连接**
   - 修改 `application.yml` 文件中的数据库连接信息

3. **构建项目**
   ```bash
   mvn clean package
   ```

4. **启动项目**
   ```bash
   mvn spring-boot:run
   ```
   或运行 `java -jar target/my-oj-0.0.1.jar`

5. **访问接口文档**
   - 浏览器访问: `http://localhost:8102/api/doc.html`

## 核心模块说明

### 1. 判题模块

判题模块是系统的核心，负责执行用户提交的代码并进行判题。采用设计模式组合：

- **代码沙箱**：`CodeSandbox` 接口 + `CodeSandboxFactory` 工厂（按配置创建实例）+ `CodeSandboxProxy` 代理（日志拓展）
- **判题策略**：`AbstractJudgeStrategy.evaluate` 用模板方法统一判题流程，`JudgeManager.applyStrategy` 按语言选择策略；C++ 使用基准限制，Go、Java、Python 和 JavaScript 分别补偿运行时资源开销
- **判题编排**：`JudgeServiceImpl.processSubmission` 原子抢占待判题提交，执行沙箱与策略流水线，并将任务收敛到成功或失败终态
- **异步判题**：提交后通过专用 Java 21 虚拟线程执行器异步判题，不阻塞 HTTP 请求
- **结果语义**：提交状态“成功”表示判题流程完成，最终 verdict、首个失败用例或沙箱错误堆栈保存在 `judgeInfo.message`

### 2. 用户模块

用户模块负责用户的注册、登录和信息管理，确保系统的安全性和用户数据的完整性。登录态基于 Session，接口权限通过 `@AuthCheck` 注解 + AOP 切面校验。

### 3. 题目模块

题目模块负责题目的管理和查询，包括题目的添加、编辑、删除和查询等功能。题目提交相关接口已合并至 `QuestionController`（`/question/question_submit/*`）。

### 4. 提交模块

提交模块负责接收用户的代码提交，将其转发给判题模块进行处理，并返回判题结果。

## 系统流程

1. **用户注册/登录**：用户通过注册或登录获取系统访问权限
2. **浏览题目**：用户浏览系统中的题目列表，查看题目详情
3. **提交代码**：用户选择题目，编写代码并提交
4. **代码判题**：系统异步调用代码沙箱执行用户代码
5. **返回结果**：系统根据执行结果和题目要求进行判题，返回详细的判题结果

## Docker 部署

仓库提供 Dockerfile 与 docker-compose.yml，可一键部署：

```bash
# 1. 构建 jar 包，并复制到项目根目录（Dockerfile 从根目录取 jar）
mvn clean package
cp target/my-oj-0.0.1.jar .

# 2. 启动 MySQL + 后端
docker compose up -d
```

- `docker-compose.yml`：MySQL 8（宿主机 3307） + 后端（8101），command 激活 prod profile（`--spring.profiles.active=prod`）并通过命令行参数覆盖沙箱配置（`--codesandbox.url` / `--codesandbox.type`）
- `nginx.conf`：反向代理到后端 8101

## 技术特点

- **模块化设计**：系统采用模块化设计，各功能模块职责清晰，易于维护和扩展
- **安全执行**：通过远程代码沙箱隔离执行用户代码，确保系统安全
- **灵活的判题策略**：策略模式支持不同编程语言和判题规则的自定义
- **完整的接口文档**：集成Knife4j，提供详细的接口文档
- **统一的异常处理**：实现全局异常处理，提高系统的稳定性

## 未来规划

- [ ] 扩展更多编程语言与沙箱镜像
- [ ] 优化判题逻辑和代码书写方式
- [ ] 添加题目分类和标签功能
- [ ] 接入 Redis 实现 session 共享（为微服务化做准备）
