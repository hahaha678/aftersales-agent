# 环境与实现记录

## 本机检查

- 检查日期：2026-09-28。
- Java：Oracle JDK 25，路径 C:\Program Files\java25\Java\jdk-25。
- Maven：3.9.11，路径 D:\apache-maven-3.9.11-bin\apache-maven-3.9.11。
- MySQL：用户确认版本 8.0.43；127.0.0.1:3306 TCP 可连接，未验证账号、SQL 执行或数据库版本返回值。
- 没有修改本机 JDK、Maven 全局配置，也没有读取/使用数据库凭据。
- 直接通过 PowerShell 请求 Maven Central 遇到 TLS 错误；使用现有 Maven 仓库配置执行构建。
- Maven 验证使用项目内 .m2-local 缓存，不修改用户已有依赖缓存；该目录不提交。
- Maven 启动脚本出现 Access is denied 和 JDK 25 下的 Unsafe 弃用警告；应以最终 Maven 退出状态判断构建结果。

## 本次选择

- Spring Boot 4.0.8、Spring AI 2.0.1，使用稳定版本，兼容关系以官方文档为依据。
- 源码/字节码目标 Java 21，使用已有 JDK 25 构建运行，不安装额外 JDK。
- 单应用模块 backend；根 pom 管理依赖和模块导入。
- Spring JDBC + Flyway 管理 MySQL 数据访问和迁移基础设施。
- Spring AI 当前仅引入 ChatClient 核心依赖，无供应商 Starter 和真实模型调用。
- scaffold/test 模式不连接数据库；local 模式必须提供数据库账号并执行迁移。
- 无登录和业务接口；后端当前只有 Actuator 基础健康检查，前端骨架已接入该接口。

## 验证结果

- Maven verify：通过，生成 backend/target/aftersales-agent-backend-0.0.1-SNAPSHOT.jar。
- 测试：1 项通过，0 失败、0 错误；随机端口 HTTP 健康接口返回 200 和 UP。
- 默认启动：直接运行打包 JAR，确认自动使用 scaffold 配置，健康接口返回 UP；验证后已停止检查进程。
- 沙箱内 javac 读取依赖出现 AccessDeniedException；经批准在沙箱外执行相同 Maven 命令后通过，未修改业务代码规避该问题。
- MySQL 登录与 Flyway 实际迁移：未执行，需要用户在本机配置凭据并准备开发库。
- 模型连接与工具调用：未执行，需要后续确定供应商和本地密钥配置。
- Docker：未检查，未宣称通过。

## 前端骨架验证

- Node.js 22.20.0、npm 10.9.3。
- Vue 3.5.43、Vue Router 4.6.4、Vite 8.3.1、TypeScript 5.9.3，准确版本由 package-lock.json 锁定。
- npm run build 通过（含 vue-tsc 类型检查）；生成 frontend/dist/。
- 本地页面可渲染，浏览器验证订单占位页路由及返回首页。
- 浏览器检测按钮及 HTTP 请求均验证：/api/system/health 经 Vite 转发至后端 /actuator/health，返回 UP。
- 停止测试后端后，页面正确提示 HTTP 502；没有将离线状态显示为成功。
- 检查所启动的后端进程已停止，前端开发服务器保留在 http://127.0.0.1:5173 供用户预览。
- npm 缓存使用项目内 .npm-cache；没有修改全局 npm 配置。
- 首页之外的业务页面仅是占位，未实现业务数据或模型对话。

## RESTful 接口契约阶段

- 新增 springdoc-openapi-starter-webmvc-ui 3.0.3，匹配 Spring Boot 4.0.x。
- 新增 6 个用户/订单契约端点和 DTO，Swagger v3 注解生成 OpenAPI 文档。
- 未新增或运行数据库迁移，未实现会话授权、订单数据访问。
- Maven verify 通过：共 5 项测试，0 失败、0 错误。
- 验证包含 OpenAPI 安全声明及资源结构、所有端点的 501 占位、分页/ID/枚举校验、JSON 与登录字段校验、请求 ID 和不回显密码。
- 完整设计与用户检查项见 API_DESIGN.md。

## 分层结构调整

- 业务模块下统一 controller/service/mapper/domain，domain 分 dto/po/query/vo；公共技术包 common/api 保留。
- 迁移 Controller，Request/Response 类分别改名为 DTO/VO；新增 OrderPageQuery 承接查询参数。
- service、mapper、po 等未实现层通过 package-info.java 保留目录，未提前添加数据库实体或 MyBatis 依赖。
- 接口路径及 JSON 字段保持不变；OpenAPI schema 名称随 DTO/VO 重命名。
- 重构后执行 clean verify 清除旧包产物，并完成最终 verify：5 项测试全部通过；覆盖 Query 的默认值、可选参数、非法分页及枚举绑定。
