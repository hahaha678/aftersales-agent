# 电商智能售后 Agent

最新进展：已补齐客服收货后的模拟退款闭环，支持成功、失败、超时回查、幂等重试和退款流水展示，见 [模拟退款联调说明](docs/SIMULATED_REFUND.md)。local 重启会自动应用 V7；当前不接入真实支付。

个人学习与简历项目。已包含 Vue 3 + TypeScript 页面、MyBatis 数据访问、MySQL + Redis 会话认证、订单物流、售后申请及审核、DeepSeek Agent 与草稿确认。新增政策知识库：客服录入发布、本地 Embedding、原文检索与回答来源追溯。Ollama 安装及使用见 [RAG 启动说明](docs/KNOWLEDGE_RAG.md)；已完成 15 个场景的真实模型评测，结果见 [RAG 基线报告](docs/evaluations/rag-baseline-v1-20260929/REPORT.md)。退回物流登记与客服收货确认已实现，见 [联调说明](docs/RETURN_SHIPMENT.md)；实际退款仍待实现。

## 技术选择

| 组件 | 版本/方案 |
| --- | --- |
| JDK | 编译目标 Java 21；本机已有 JDK 25 可用于构建和运行 |
| Maven | 本机 3.9.11 |
| Spring Boot | 4.0.8 |
| Spring AI | 2.0.1 BOM + ChatClient 核心依赖 |
| MySQL | 用户现有 8.0.43 |
| 数据访问 | MyBatis，按业务模块组织 PO/Mapper |
| 数据库迁移 | Flyway，版本由 Spring Boot 管理 |

Spring AI 2.0 与 Spring Boot 4.0/4.1 的兼容关系见[官方文档](https://docs.spring.io/spring-ai/reference/getting-started.html)。
启用 Agent 并配置密钥后会调用真实模型；真实模型评测需显式启用，可能产生 API 费用。
当前使用 DeepSeek 生成回答、Ollama 生成政策向量。第一版向量以 JSON 存在 MySQL，由 Java 计算相似度，适合小规模知识库；后续可迁移专用向量索引。

## IDEA 导入

1. 使用 IDEA 打开项目根目录的 `pom.xml`，作为 Maven 项目导入。
2. Project SDK 和 Maven Runner JRE 选择本机 JDK 25（JDK 21 亦可）；源码目标为 Java 21。
3. Maven Home 可使用已安装的 Maven 3.9.11，点击 Reload All Maven Projects。
4. 运行 `backend` 中的 `com.example.aftersales.AftersalesApplication`。
5. 访问 http://127.0.0.1:8080/actuator/health ，预期 JSON 中包含 `"status":"UP"`。

默认 scaffold 模式不连接数据库或模型。健康检查成功只说明应用骨架启动成功。
默认 scaffold 模式只用于检查接口契约，不提供真实认证。使用 local 模式并配置 MySQL、Redis 后可验证登录与注销，见 [认证说明](docs/AUTHENTICATION.md)。服务默认仅监听本机地址。

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
local 启动会执行尚未应用的 V1–V7 迁移，创建业务、会话、草稿、政策与向量来源表，并扩展退回物流、收货确认字段和模拟退款流水。
数据库不可用或迁移失败时启动应失败；不会自动切换到无数据库模式。
不要同时启用 scaffold 和 local。已在隔离的 MySQL 8.0.43 实例验证迁移，尚未读取或使用你日常数据库的密码，也未迁移 3306 上的数据库。
数据库使用 UTC 存储时间，API 返回带偏移的 ISO-8601 时间。

## 目录与职责

```text
pom.xml                 Maven 聚合与版本管理
backend/                单个 Spring Boot 应用
frontend/               Vue 3 + TypeScript 独立前端
deploy/mysql/           手动建库和开发演示数据脚本
docs/                   开发清单、环境和验证记录
```

后端按 identity、order、aftersales、agent、conversation、knowledge、common 分包，包说明记录后续职责。
业务模块内部统一使用 controller、service、mapper、domain；domain 分 dto、po、query、vo。identity/order 已实现六组 PO/Mapper，以及认证与订单查询 Service。详见 [代码结构规范](docs/CODE_STRUCTURE.md) 和 [MyBatis 数据访问层](docs/PERSISTENCE.md)。
根项目仅用于聚合，业务仍为一个应用，不拆分微服务。

## 验证范围

已通过 42 项后端测试和打包检查，包含隔离 MySQL / Redis 上的身份、订单、售后、Agent 集成测试和本地 DeepSeek 协议测试。
ScaffoldSmokeTest 使用随机端口启动真实 HTTP 服务，验证无数据库和模型凭据时健康接口可用。
已在隔离 MySQL 8.0.43 中验证 V1–V4 迁移；2026-09-29 已完成首轮真实 DeepSeek 基线评测，10 个场景的自动业务检查通过，回复审阅仍发现语言和金额措辞问题，见 [基线报告](docs/evaluations/baseline-v1-20260929/REPORT.md)。这不代表完整的模型质量验收。

开发范围见 [开发清单](docs/DEVELOPMENT_CHECKLIST.md)。

## 前后端同时开发

后端在 IDEA 中运行 AftersalesApplication，默认 8080 端口。
另开终端进入 frontend，首次执行 npm ci，然后执行 npm run dev。
浏览器打开 http://127.0.0.1:5173 ，进入“我的订单”后登录。业务功能需要后端 local 模式、MySQL、Redis及已创建的用户；演示数据导入说明见 frontend/README.md。
前端不放入 backend/src/main/resources/static，也不参与 Maven 打包，两个工程独立构建和启动。
详细命令、目录职责与部署说明见 [前端 README](frontend/README.md)。

## RESTful 接口

- 设计说明与接口清单：[docs/API_DESIGN.md](docs/API_DESIGN.md)。
- Swagger UI：http://127.0.0.1:8080/swagger-ui.html 。
- OpenAPI JSON：http://127.0.0.1:8080/v3/api-docs 。
- 身份、订单、售后、客服审核、Agent 会话、草稿、知识库和模拟退款接口已实现，OpenAPI 当前包含 36 个路径。
- 契约以 Controller、DTO 和 Swagger v3 注解为准；scaffold 合法业务请求返回 501，local 启用业务实现。
- local 登录会写入真实会话；请使用专用开发库和按文档手工导入的演示账号。
- 构建验证：Maven verify 通过，接口契约测试覆盖健康、文档结构、占位响应和输入校验。
- 生产部署前通过 springdoc.api-docs.enabled=false、springdoc.swagger-ui.enabled=false 关闭文档，或纳入访问控制。

## 数据库设计（待检查）

- 表关系、字段口径、索引、演示账号和导入步骤：[数据库设计](docs/DATABASE_DESIGN.md)。
- 建表脚本：[V2 迁移](backend/src/main/resources/db/migration/V2__create_identity_and_order_tables.sql)，保持 V1 不变。
- 演示数据：[手动导入脚本](deploy/mysql/02-seed-demo-data.sql)，包含 3 个用户、7 个订单、8 条商品项、4 个包裹和 8 条轨迹，不创建会话。
- 仅在专用开发库停机导入；必须显式设置 `@allow_demo_seed = 1`，首次要求业务表为空，重复执行保留已有数据。
- 认证、订单查询、售后申请与审核及对应 Vue 页面已完成；订单测试方法见 [订单查询说明](docs/ORDER_QUERIES.md)。

## Redis 会话缓存准备

已接入 Spring Data Redis + Lettuce，并提供带撤销标记的会话缓存组件。local 配置默认使用 127.0.0.1:6379，可通过 REDIS_HOST、REDIS_PORT、REDIS_PASSWORD 等环境变量调整。
本机路径、配置与测试方法见 [Redis 接入说明](docs/REDIS.md)。缓存已接入登录与认证，故障策略和测试步骤见 [认证说明](docs/AUTHENTICATION.md)；scaffold 模式不要求 Redis 可用。

售后第一版的规则、接口和联调步骤见 [售后说明](docs/AFTERSALES.md)。重启 local 后端会自动迁移 V3，新建售后申请和处理记录表。

## DeepSeek 智能售后

真实模型基线评测已提供 10 个场景、一键隔离运行脚本和自动检查报告，见 [评测说明](docs/AGENT_EVALUATION.md)。运行前需要当前终端可读取的 DeepSeek 密钥；模拟演练结果不作为真实模型成绩。

已实现工具查询、多轮会话、SSE 回复、历史恢复、任务取消以及草稿确认建单。配置方式、执行边界和联调示例见 [Agent 接入说明](docs/AGENT.md)。后端设置 AI_ENABLED=true 和 DEEPSEEK_API_KEY 后重启，从前端“智能售后”进入；密钥不能放到 Vue 环境变量或 Git。未配置模型不影响普通业务。RAG 政策检索已接入，需配置本地 Embedding 并发布资料，见 [RAG 说明](docs/KNOWLEDGE_RAG.md)。换货和实际退款尚未实现。
