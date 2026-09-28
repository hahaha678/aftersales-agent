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

## 用户与订单数据库设计验证（2026-09-28）

- 保持 V1 不变，新增 V2：app_user、user_session、trade_order、order_item、order_shipment、shipment_event。
- 使用本机 mysqld 8.0.43 创建独立临时数据目录 `backend/target/mysql-validation/data`，仅监听 127.0.0.1:33307；使用独立随机测试密码，没有读取或修改日常 3306 实例。
- 隔离库 `aftersales_validation` 直接执行 V1、V2 和演示数据；首次导入得到 3 个用户、0 个会话、7 个订单、8 条商品项、4 个包裹、8 条轨迹；第二次导入跳过，数量不变。
- 数据核对通过：订单金额等于商品行合计、发货/签收时间一致、用户名大小写敏感；公开演示密码通过 PBKDF2 验证，错误密码不匹配。
- 负向验证通过：零购买数量、负金额、非法状态/角色、不存在的用户外键、重复订单包裹、支付早于下单、完成订单缺少签收时间、非法会话过期/撤销时间、删除被订单引用的用户，均由数据库拒绝。
- 种子保护验证通过：未设置显式开关拒绝导入；非空业务库拒绝且保留原记录；在写入结束前注入错误，五张种子业务表数据全部回滚。
- 另建空库 `aftersales_flyway_validation`，直接运行打包 JAR，local 配置连接上述隔离端口、HTTP 使用 18080。Flyway 成功应用 V1 和 V2，历史表 success 均为 1，健康接口返回 UP。
- Maven verify 的 5 项测试全部通过；首次打包被之前用于预览的 JAR 进程锁定，停止该进程后执行 `mvn -DskipTests verify`，打包成功。没有因打包锁定重复运行已通过的测试。
- 临时数据库与 local 验证应用已停止。临时数据目录在被 Git 忽略的 target 下，可由后续 Maven clean 清理。没有向仓库写入测试数据库密码。
- 本轮不包含登录、鉴权或订单 Mapper 的业务验证；接口合法请求仍为 501。下一步见 DATABASE_DESIGN.md。

## MyBatis 与 PO/Mapper 验证（2026-09-28）

- 接入 MyBatis Starter 4.0.0，保持 Spring Boot 4.0.8；补齐 identity/order 六组 PO、Mapper、XML，配置驼峰映射和 UTC 时间约定。
- 普通 Maven test：原有 5 项测试全部通过；新增 4 项集成测试在没有 MAPPER_TEST_URL 时按设计跳过，无数据库模式仍可运行。
- 在隔离 MySQL 8.0.43 的 127.0.0.1:33307 新建 aftersales_mapper_validation 测试库；显式开启 MapperIntegrationTest，4 项全部通过，0 跳过，并完成 verify 打包。
- 真实数据库验证六组 XML 解析、Mapper 扫描、会话插入主键回填、摘要唯一性、撤销幂等、过期边界、批量清理、按归属读取、分页筛选与稳定排序、空集合、金额和毫秒时间映射。
- 测试数据通过事务回滚，结束后用户、会话、订单行数均为 0；测试库保留迁移结构。临时 MySQL 已关闭，临时凭据文件已删除；未连接日常 3306 数据库，未启动 8080 后端进程。
- 测试入口和后续 Service 调用约定见 PERSISTENCE.md。Controller 尚未调用数据层，业务请求仍返回 501。

## Redis 缓存准备验证（2026-09-28）

- 检测 `E:\Redis-x64-5.0.14.1\redis-server.exe`，版本 5.0.14.1。未改动安装目录内的配置和 dump.rdb。
- 使用独立目录 backend/target/redis-validation、127.0.0.1:16379 启动无持久化测试实例，PING 返回 PONG。
- 接入 Spring Data Redis/Lettuce，添加 SessionCache 与 CachedSession；缓存上限 30 秒，撤销标记保留到原始会话到期，Lua 防止撤销后的旧查询重新回填。
- 普通 Maven test：原有 5 项测试通过，4 项 MySQL 和 4 项 Redis 集成测试按环境开关跳过。
- 显式设置 REDIS_TEST_PORT 后运行 SessionCacheIntegrationTest：4 项全部通过，无跳过；verify 打包成功。没有进行性能压测，没有宣称实际接口查询量或延迟已改善。
- 临时 16379 Redis 已通过 SHUTDOWN NOSAVE 停止，没有启动 8080 后端进程；日常 local 默认连接地址仍为 127.0.0.1:6379。
- 本阶段只完成缓存基础组件；登录 Service、认证过滤器、跨 MySQL/Redis 的注销协调与故障恢复尚待实现，详见 REDIS.md。

## 登录、会话认证与注销验证（2026-09-28）

- 接入 Spring Security，local 模式启用 SessionService、请求头 Bearer 认证、/users/me 和注销。scaffold 仍保留无数据库契约占位，不使用默认 Spring Security 测试账号。
- 密码编码显式使用 PBKDF2-HMAC-SHA256 / 600000 次 / 16 字节盐，已验证与演示种子密码兼容。Token 为 32 字节安全随机值的 Base64URL，仅摘要入库。
- 在独立 MySQL 8.0.43（33307）和 Redis 5.0.14.1（16379）上执行 Maven verify：20 项测试，0 失败、0 错误、0 跳过，打包成功。
- 其中 AuthenticationIntegrationTest 7 项，使用随机 HTTP 端口：验证登录、公开用户资料、注销幂等、多会话隔离、错误/停用账号、缺失/伪造/过期令牌、统一错误体、缓存免查库及未命中回填。
- Redis 故障、登录缓存写失败、注销数据库更新失败采用故障注入验证；失败不返回成功状态，注销可重试完成。Redis 不可用时当前策略返回 503，不启用自动数据库回退。
- 使用 Mapper spy 验证连续两次缓存命中的 /users/me 请求不调用会话/用户 Mapper；并未进行吞吐或延迟压测。
- 一次新增故障测试因 Mockito 对 Mapper 抽象接口使用 real-method 恢复而失败；修正为恢复代理委托后，最终完整 20 项验证通过。
- 测试用户和会话清理后均为 0；独立 MySQL/Redis 进程已关闭，临时凭据文件已删除，未启动或占用 8080 后端服务。
- 运行方式、缓存延迟及跨存储部分失败的边界见 AUTHENTICATION.md。下一步为订单查询 Service 和前端联调。

## common 包职责整理（2026-09-28）

- 原 common/api 拆分为 config、domain/vo、exception、handler、filter，更新所有 Java 引用；Token 生成和认证行为不变。
- 执行 clean verify 清理旧包字节码，避免组件重复注册：5 项默认测试通过，15 项依赖 MySQL/Redis 的集成测试按环境开关跳过，打包成功。本轮未重新启动外部依赖或 8080 服务。

## 订单查询 Service 验证（2026-09-28）

- 新增 OrderService 和 CurrentUserService，接通当前用户订单分页、详情及物流接口；跨用户与不存在统一 404，scaffold 仍为契约占位。
- 一次列表查询在同一个只读 REPEATABLE_READ 事务中读取总数、订单和批量商品；金额输出两位字符串，时间使用 UTC 偏移，ID 输出字符串。
- 独立 MySQL 8.0.43 / Redis 5.0.14.1 上执行 Maven verify：24 项测试，0 失败、0 错误、0 跳过，打包成功。
- 新增 OrderQueryIntegrationTest 四项真实 HTTP 测试，覆盖分页排序、订单号/状态过滤、批量商品查询次数、大于 JavaScript 安全整数范围的 ID、Long 溢出、金额/UTC/null 字段、普通用户及客服越权、空物流和轨迹顺序。
- 原认证测试随订单实现更新：已登录访问订单列表从 501 改为 200；未登录仍为 401。
- 测试后六张业务表行数均为 0，独立 33307/16379 实例已关闭，临时数据库凭据已删除；未启动 8080 服务。验证入口见 ORDER_QUERIES.md。
- availableAftersalesQuantity 暂等于购买数量，售后占用与资格判断尚未实现。下一步为 Vue 页面联调。
