# 电商智能售后 Agent

基于 Java、Spring AI 和 Vue 的个人学习与简历项目。通过 DeepSeek 理解用户需求，调用订单与售后工具，结合政策知识库生成有来源的回答；正式申请由用户确认，资格、权限、金额和状态流转由后端校验。

**当前范围：可演示的退货退款业务闭环。订单与物流为测试数据，退款为模拟执行，未接入真实支付或换货。**

## 交付入口

- [启动、架构与演示手册](docs/DELIVERY_GUIDE.md)：环境配置、启动顺序、完整演示、验收范围和限制。
- [功能完成清单](docs/DEVELOPMENT_CHECKLIST.md)：已实现能力与后续工作。
- [接口设计](docs/API_DESIGN.md)：REST 约定；运行后访问 `/swagger-ui.html`，当前 OpenAPI 包含 52 个路径。
- [代码结构](docs/CODE_STRUCTURE.md)：按业务模块分包，内部使用 controller/service/mapper/domain 分层。

## 功能

| 模块 | 能力 |
| --- | --- |
| 身份与订单 | MySQL + Redis 可撤销会话、买家/客服权限、订单商品与模拟物流查询 |
| 售后办理 | 资格与剩余量校验、分批申请金额计算、审核/撤销、退回物流与收货确认 |
| 模拟退款 | 幂等流水、成功/失败/超时未知、状态回查与失败重试 |
| Agent | DeepSeek 工具调用、多轮会话、SSE、任务取消、草稿生成与用户确认 |
| 聚合资格查询 | 一次工具调用筛选可售后商品，按商品项分页，减少逐订单调用 |
| 政策 RAG | 政策管理与发布、Ollama 向量化、混合检索、原文与来源追溯 |
| 人工工单 | 用户分享会话快照、客服领取与解决、处理进度查询 |
| 图片凭证 | 待审核申请上传、客服查看、鉴权与去重，图片保存在 MySQL |
| 执行监控 | 任务状态、耗时、Token、工具顺序、脱敏摘要及失败原因 |

## 技术栈

Java 21 编译目标、Spring Boot 4.0.8、Spring AI 2.0.1、MyBatis、MySQL 8.0.43、Redis、Flyway；Vue 3、TypeScript、Vite；DeepSeek 文本模型与 Ollama `bge-m3` Embedding。

采用模块化单体，前后端独立运行。向量以 JSON 保存在 MySQL，Java 执行相似度与关键词混合检索，适合小规模知识库；没有部署专用向量数据库。

## 本地启动摘要

1. 启动 MySQL 和 Redis，在专用开发库执行 `deploy/mysql/01-create-database.sql`。
2. 在 IDEA 的 `AftersalesApplication` 运行配置中设置 `SPRING_PROFILES_ACTIVE=local`、`DB_USERNAME`、`DB_PASSWORD`；其他变量见 [.env.example](.env.example)。Spring Boot **不会自动加载 .env**。
3. 启动后端，Flyway 自动应用未执行的 V1–V10 迁移。默认地址 `http://127.0.0.1:8080`。
4. 首次空库执行 `deploy/mysql/02-seed-demo-data.sql`。三个演示账号 `demo_customer`、`demo_other`、`demo_staff` 的公开密码均为 `123`；旧账号使用 `03-reset-demo-passwords.sql`。不要在生产环境使用这些账号。
5. 在 `frontend` 执行 `npm ci`、`npm run dev`，打开 `http://127.0.0.1:5173`。
6. AI 与 RAG 配置、政策发布及演示步骤见[交付手册](docs/DELIVERY_GUIDE.md)。未配置模型不影响普通售后业务。

默认 `scaffold` 只用于健康与接口契约检查；正常业务使用 `local`，不要同时启用两者。

## 验证记录

2026-09-29 最近一次隔离回归：**57 项后端测试通过，0 失败/错误/跳过**，包含售后、Agent、工单、图片、接口契约和异常提示；测试使用隔离 MySQL/Redis与模拟模型。图片功能阶段前端构建、类型检查和现有 5 项测试通过。

历史真实模型测试与上述回归分开记录：[Agent 基线](docs/evaluations/baseline-v1-20260929/REPORT.md)、[RAG 基线](docs/evaluations/rag-baseline-v1-20260929/REPORT.md)。历史结果不代表当前所有改动的模型表现；**新增聚合工具尚需真实 DeepSeek 联调**。目前未宣称生产验收、压力测试或全新机器部署验收通过。

隔离业务测试：`./scripts/Run-AgentBaseline.ps1 -AftersaleTest`。脚本默认依赖本机 MySQL/Redis 安装路径，可通过参数覆盖，详见[交付手册](docs/DELIVERY_GUIDE.md)。

## 专项说明

[认证](docs/AUTHENTICATION.md) · [订单](docs/ORDER_QUERIES.md) · [售后](docs/AFTERSALES.md) · [Agent](docs/AGENT.md) · [RAG](docs/KNOWLEDGE_RAG.md) · [退回收货](docs/RETURN_SHIPMENT.md) · [模拟退款](docs/SIMULATED_REFUND.md) · [人工工单](docs/SUPPORT_TICKETS.md) · [图片凭证](docs/AFTERSALE_EVIDENCE.md) · [执行监控](docs/AGENT_MONITORING.md) · [聚合资格查询](docs/ELIGIBLE_AFTERSALE_QUERY.md)
