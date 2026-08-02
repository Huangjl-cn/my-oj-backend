# 在线判题系统 (Online Judge)-backend

## 项目介绍

这是一个基于Spring Boot开发的在线判题系统（Online Judge）-backend，用于管理编程题目、接收用户代码提交并进行自动判题。系统支持用户注册登录、题目管理、代码提交和判题等核心功能。

### 主要功能

- **用户管理**：注册、登录、个人信息管理
- **题目管理**：题目列表、题目详情、题目编辑
- **代码提交**：支持Java语言的代码提交
- **自动判题**：使用Docker代码沙箱执行用户代码并进行判题
- **判题结果**：返回执行结果、运行时间、内存消耗等详细信息

## 项目使用的工具栈

| 技术/框架 | 版本 | 用途 |
|---------|------|------|
| Spring Boot | 2.7.2 | 基础框架 |
| Java | 1.8 | 开发语言 |
| MyBatis-Plus | 3.5.2 | ORM框架 |
| MySQL | - | 数据库 |
| Hutool | 5.8.8 | 工具库 |
| Knife4j | 4.4.0 | 接口文档 |
| Lombok | - | 代码简化 |
| Spring AOP | - | 面向切面编程 |

## 项目结构

```
src/main/java/com/hjl/oj/
├── annotation/        # 注解定义
├── aop/               # 切面实现
├── common/            # 通用类
├── config/            # 配置类
├── constant/          # 常量定义
├── controller/        # 控制器
├── exception/         # 异常处理
├── judge/             # 判题模块
│   ├── codesandbox/   # 代码沙箱
│   └── strategy/      # 判题策略
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

## 需要在application.yml文件里的配置

### 基本配置

```yaml
# 公共配置文件
spring:
  application:
    name: oj
  # 默认 dev 环境
  profiles:
    active: dev
  # 支持 swagger3
  mvc:
    pathmatch:
      matching-strategy: ant_path_matcher
  # session 配置
  session:
    # 30 天过期
    timeout: 2592000
  # 数据库配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/oj_db
    username: root
    password: your_password
  # 文件上传
  servlet:
    multipart:
      # 大小限制
      max-file-size: 10MB
server:
  address: 0.0.0.0
  port: 8101
  servlet:
    context-path: /api
    # cookie 30 天过期
    session:
      cookie:
        max-age: 2592000
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: false
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: isDelete # 全局逻辑删除的实体字段名
      logic-delete-value: 1 # 逻辑已删除值（默认为 1）
      logic-not-delete-value: 0 # 逻辑未删除值（默认为 0）
# 接口文档配置
knife4j:
  enable: true
  openapi:
    title: "oj接口文档"
    version: 1.0
    group:
      default:
        api-rule: package
        api-rule-resources:
          - com.hjl.oj.controller
# 代码沙箱类型选择
codesandbox:
  type: remote
```

### 配置说明

1. **数据库配置**
   - `driver-class-name`: MySQL驱动类名
   - `url`: 数据库连接地址，格式为 `jdbc:mysql://{host}:{port}/{database}`
   - `username`: 数据库用户名
   - `password`: 数据库密码

2. **服务器配置**
   - `port`: 服务器端口，默认为8101
   - `context-path`: 上下文路径，默认为/api

3. **会话配置**
   - `session.timeout`: 会话超时时间，单位为秒
   - `server.servlet.session.cookie.max-age`: Cookie过期时间，单位为秒

4. **文件上传配置**
   - `spring.servlet.multipart.max-file-size`: 文件上传大小限制

5. **MyBatis-Plus配置**
   - `map-underscore-to-camel-case`: 是否开启下划线转驼峰命名
   - `log-impl`: 日志实现类
   - `logic-delete-field`: 逻辑删除字段名

6. **代码沙箱配置**
   - `codesandbox.type`: 代码沙箱类型，可选值：remote（远程）、example（示例）、thirdparty（第三方）

## 项目启动

### 前置条件

- JDK 1.8 或以上
- Maven 3.6 或以上
- MySQL 5.7 或以上

### 启动步骤

1. **创建数据库**
   - 执行 `sql/create_table.sql` 文件创建数据库表结构

2. **配置数据库连接**
   - 修改 `application.yml` 文件中的数据库连接信息

3. **构建项目**
   ```bash
   mvn clean package
   ```

4. **启动项目**
   ```bash
   java -jar target/my-oj-0.0.1.jar
   ```

5. **访问接口文档**
   - 浏览器访问: `http://localhost:8101/api/doc.html`

## 核心模块说明

### 1. 判题模块

判题模块是系统的核心，负责执行用户提交的代码并进行判题。主要包含以下组件：

- **代码沙箱**：安全执行用户代码的环境
- **判题策略**：根据不同编程语言和题目要求进行判题
- **判题管理器**：协调判题流程和策略选择

### 2. 用户模块

用户模块负责用户的注册、登录和信息管理，确保系统的安全性和用户数据的完整性。

### 3. 题目模块

题目模块负责题目的管理和查询，包括题目的添加、编辑、删除和查询等功能。

### 4. 提交模块

提交模块负责接收用户的代码提交，将其转发给判题模块进行处理，并返回判题结果。

## 系统流程

1. **用户注册/登录**：用户通过注册或登录获取系统访问权限
2. **浏览题目**：用户浏览系统中的题目列表，查看题目详情
3. **提交代码**：用户选择题目，编写代码并提交
4. **代码判题**：系统接收代码提交，通过代码沙箱执行代码
5. **返回结果**：系统根据执行结果和题目要求进行判题，返回详细的判题结果

## 技术特点

- **模块化设计**：系统采用模块化设计，各功能模块职责清晰，易于维护和扩展
- **安全执行**：使用代码沙箱隔离执行用户代码，确保系统安全
- **灵活的判题策略**：支持不同编程语言和判题规则的自定义
- **完整的接口文档**：集成Knife4j，提供详细的接口文档
- **统一的异常处理**：实现全局异常处理，提高系统的稳定性

## 未来规划

- [ ] 支持更多编程语言
- [ ] 优化判题逻辑和代码书写方式
- [ ] 添加题目分类和标签功能
