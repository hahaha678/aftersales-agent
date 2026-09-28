# 当前用户订单查询

local 模式已实现三个订单查询接口，使用 MyBatis 读取 MySQL。认证继续通过 Redis 会话缓存完成，订单数据本轮不缓存。scaffold 模式仍保留 501 占位。

## 验证入口

配置 local、MySQL 和 Redis，按 DATABASE_DESIGN.md 手工导入演示数据。通过 POST /api/sessions 登录 demo_customer，再在 Swagger 的 Authorize 中填写 accessToken。

| 请求 | 行为 |
| --- | --- |
| GET /api/orders?page=1&size=20 | 仅返回当前用户订单，按 created_at DESC、id DESC 排序 |
| GET /api/orders?status=COMPLETED | 按状态精确筛选 |
| GET /api/orders?orderNumber=DEMO-1001 | 按订单号精确筛选，仍限制用户归属 |
| GET /api/orders/1001 | 订单详情及全部商品项 |
| GET /api/orders/1001/shipments | 原订单包裹及轨迹 |
| GET /api/orders/1004/shipments | 已支付未发货，返回空数组 |
| GET /api/orders/1007 | demo_customer 访问 demo_other 的订单，返回 404 |

身份只来自认证上下文。URL 中额外传入 userId、role 不会改变查询范围。STAFF 使用这些接口同样只查询自己的订单；后台跨用户查询需要后续独立权限接口。

## 实现位置

- identity/service/CurrentUserService：公开请求级用户身份入口，订单模块不直接操作身份模块的 Mapper。
- order/service/OrderService：分页、归属验证和 PO→VO 转换。
- order/controller/OrderController：参数校验及调用 Service，无数据库访问代码。
- common/exception/ApiRequestException 与 common/handler/ApiExceptionHandler：统一 400/404 业务错误。

OrderService 使用只读、REPEATABLE_READ 事务，使一次调用中的总数、订单和商品读取使用一致快照。列表先统计数量，再查询当前页并一次批量加载商品，最多三次订单模块 SELECT，不会按订单数量逐条查询商品。超出末页返回空 items 和真实 total，并跳过后续查询。

详情和物流先验证订单归属，再读取子记录；不存在和越权统一 RESOURCE_NOT_FOUND / 404。物流包裹为空时，只有确认订单属于当前用户后才返回 []。轨迹按 occurred_at、id 升序；数据库约束首版每个订单至多一个原始包裹。

## 字段口径和边界

- page 从 1 开始，size 为 1～100；偏移量使用 long 计算，避免 int 乘法溢出。
- orderNumber 为空或纯空白时不筛选，非空值精确匹配，不自动去除首尾字符；最大 64 字符。
- orderId 为正整数字符串，还要满足 Java Long 上界；19 位但超出 Long 上界的值返回 400，合法范围内不存在的值返回 404。
- ID、SKU ID 输出为字符串；金额用 BigDecimal 转为两位小数字符串，保持整行/整单实付口径。
- DATETIME 按 UTC 转为带偏移 ISO-8601；未支付、未签收的时间返回 null。
- totalQuantity 为当前订单商品购买数量合计。
- availableAftersalesQuantity 当前等于购买数量，因为尚未建立售后申请和占用记录。该字段不是资格判断结果；接入售后后必须扣除有效占用和已处理数量。

## 验证范围

OrderQueryIntegrationTest 使用真实 HTTP、隔离 MySQL/Redis 及随机测试用户，共四项：分页与筛选、详情映射、用户和客服越权、物流及非法输入。覆盖超过 JavaScript 安全整数范围的 ID、极大页码、Long 溢出、UTC 毫秒时间、null 时间、两位金额、商品批量查询次数和空物流。

沿用 AUTHENTICATION.md 中的集成测试环境变量，执行 `mvn verify` 可运行完整回归；没有 AUTH_TEST_ENABLED=true 时这些订单集成测试跳过。测试结束会清理自己创建的订单、物流、用户和会话，必须使用专用测试库。

下一步可以联调 Vue 登录、订单列表、订单详情和物流展示，再开展售后资格、草稿和申请模块。
