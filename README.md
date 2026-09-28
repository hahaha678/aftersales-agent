# 电商智能售后 Agent

个人学习与简历项目。当前包含后端和 Vue 3 + TypeScript 前端骨架，以及首批用户/订单 RESTful 接口契约；登录、订单数据访问、售后业务、RAG 和 Agent 对话尚未实现。

## 技术选择

| 组件 | 版本/方案 |
| --- | --- |
| JDK | 编译目标 Java 21；本机已有 JDK 25 可用于构建和运行 |
| Maven | 本机 3.9.11 |
| Spring Boot | 4.0.8 |
| Spring AI | 2.0.1 BOM + ChatClient 核心依赖 |
| MySQL | 用户现有 8.0.43 |
| 数据访问 | Spring JDBC，后续按业务需要评估 MyBatis |
| 数据库迁移 | Flyway，版本由 Spring Boot 管理 |

Spring AI 2.0 与 Spring Boot 4.0/4.1 的兼容关系见[官方文档](https://docs.spring.io/spring-ai/reference/getting-started.html)。
模型供应商未确定，当前未添加供应商 Starter、未创建 ChatModel，也不会发送付费 API 请求。
后续选择供应商时接入对应 Starter 和环境变量；MySQL 业务库不承担向量检索，向量存储在 RAG 阶段单独选择。

## IDEA 导入

1. 使用 IDEA 打开项目根目录的 `pom.xml`，作为 Maven 项目导入。
2. Project SDK 和 Maven Runner JRE 选择本机 JDK 25（JDK 21 亦可）；源码目标为 Java 21。
3. Maven Home 可使用已安装的 Maven 3.9.11，点击 Reload All Maven Projects。
4. 运行 `backend` 中的 `com.example.aftersales.AftersalesApplication`。
5. 访问 http://127.0.0.1:8080/actuator/health ，预期 JSON 中包含 `"status":"UP"`。

默认 scaffold 模式不连接数据库或模型。健康检查成功只说明应用骨架启动成功。
当前未实现认证，服务默认仅监听本机地址；不要将其直接作为业务服务公开部署。

## 命令行

在项目根目录运行：

```powershell
mvn clean verify
mvn -pl backend spring-boot:run
```

也可构建后运行：

```powershell
java -jar backend/target/aftersales-agent-backend-0.0.1-SNAPSHOT.jar
```

## 连接本机 MySQL

1. 在数据库客户端执行 `deploy/mysql/01-create-database.sql`。
2. 配置专用开发账号，允许其操作 `aftersales_agent` 库。Flyway 初始化需要建表权限。
3. 在 IDEA 的 Run Configuration 中设置环境变量：

```text
SPRING_PROFILES_ACTIVE=local
DB_USERNAME=你的开发账号
DB_PASSWORD=你的本地密码
```

默认连接 127.0.0.1:3306/aftersales_agent，可通过 DB_HOST、DB_PORT、DB_NAME 修改。
`.env.example` 是配置说明，Spring Boot 不会自动读取 `.env`；请通过 IDEA 或终端设置变量。
首次 local 启动会执行 V1 迁移，创建 project_metadata 和 Flyway 历史表。
数据库不可用或迁移失败时启动应失败；不会自动切换到无数据库模式。
不要同时启用 scaffold 和 local。当前没有自动读取你的数据库密码，也没有执行本机数据库迁移。

## 目录与职责

```text
pom.xml                 Maven 聚合与版本管理
backend/                单个 Spring Boot 应用
frontend/               Vue 3 + TypeScript 独立前端
deploy/mysql/           手动建库脚本
docs/                   开发清单、环境和验证记录
```

后端按 identity、order、aftersales、agent、conversation、knowledge、common 分包，包说明记录后续职责。
业务模块内部统一使用 controller、service、mapper、domain；domain 分 dto、po、query、vo。当前 Service、Mapper、PO 仅保留包结构，等待业务与数据库设计。详见 [代码结构规范](docs/CODE_STRUCTURE.md)。
根项目仅用于聚合，业务仍为一个应用，不拆分微服务。

## 验证范围

已完成 Maven verify，1 项测试通过，并直接运行打包 JAR 验证默认 scaffold 模式健康接口返回 UP。验证进程已停止。
ScaffoldSmokeTest 使用随机端口启动真实 HTTP 服务，验证无数据库和模型凭据时健康接口可用。
它不替代 MySQL 集成测试或模型工具调用测试。真实数据库、业务及模型测试在对应模块开发时补齐。

开发范围见 [开发清单](docs/DEVELOPMENT_CHECKLIST.md)。

## 前后端同时开发

后端在 IDEA 中运行 AftersalesApplication，默认 8080 端口。
另开终端进入 frontend，首次执行 npm ci，然后执行 npm run dev。
浏览器打开 http://127.0.0.1:5173 ，点击“检测连接”验证开发代理与后端健康接口。
前端不放入 backend/src/main/resources/static，也不参与 Maven 打包，两个工程独立构建和启动。
详细命令、目录职责与部署说明见 [前端 README](frontend/README.md)。

## RESTful 接口设计（待检查）

- 设计说明与接口清单：[docs/API_DESIGN.md](docs/API_DESIGN.md)。
- Swagger UI：http://127.0.0.1:8080/swagger-ui.html 。
- OpenAPI JSON：http://127.0.0.1:8080/v3/api-docs 。
- 首批 6 个接口：创建/撤销会话、当前用户、订单分页、订单详情、物流查询。
- 契约以 Controller、DTO 和 Swagger v3 注解为准；合法请求当前返回 501，参数错误返回 400。
- Swagger 中的认证方案和成功响应是目标设计，尚未实现鉴权，也不会产生业务数据。
- 构建验证：Maven verify 通过，5 项测试验证健康接口、文档结构、占位响应和输入校验。
- 生产部署前通过 springdoc.api-docs.enabled=false、springdoc.swagger-ui.enabled=false 关闭文档，或纳入访问控制。
