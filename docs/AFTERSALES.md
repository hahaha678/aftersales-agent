# 售后申请第一版

范围：单商品项退货退款、资格查询、用户提交/撤销、记录与详情、客服审核。暂不提供换货、退回物流或实际支付退款，也未接入 Agent 草稿。

## 启动与验收

1. 在 IDEA 重启 Spring Boot local 配置，Flyway 自动执行 V3 新建 aftersale_request、aftersale_event；保留现有账号和订单，不要修改已执行的 V1/V2。
2. 前端 `npm run dev`，打开 http://127.0.0.1:5173 。后端与前端需分别启动。
3. 使用 demo_customer / DemoPass123! 登录，打开 DEMO-1001 → 查询资格并申请 → 填写商品、数量、原因和描述 → 勾选确认后提交。
4. 在售后记录查看金额、状态和时间线。待审核申请可以勾选确认后撤销，商品数量立即释放。
5. 退出后用 demo_staff / DemoPass123! 登录，进入“客服审核”，查看申请、填写审核意见并确认通过或拒绝。
6. 通过后显示“审核通过 · 待退货”，不显示退款成功；拒绝释放数量。用户只能查看自己的记录。

演示账号需先导入 deploy/mysql/02-seed-demo-data.sql。DEMO-1001 的资格取决于首次导入时生成的签收时间，超过 7 天后会自然失效；重复导入不会刷新时间。

## REST API

所有接口使用 Bearer 认证和统一错误格式，Swagger 注解位于 aftersales/controller/AftersaleController。

| 方法 | 路径 | 权限与功能 |
| --- | --- | --- |
| GET | /api/orders/{orderId}/aftersale-eligibility | 本人订单商品资格、数量、剩余金额、截止时间及原因 |
| POST | /api/aftersales | 创建本人申请；201 和 Location；同键同内容重试返回原记录 |
| GET | /api/aftersales | 本人申请分页，page/size/status |
| GET | /api/aftersales/{id} | 本人申请详情与事件 |
| POST | /api/aftersales/{id}/cancellation | 撤销本人待审核申请；200，重复撤销返回原记录 |
| GET | /api/staff/aftersales | STAFF 跨用户查询申请分页 |
| GET | /api/staff/aftersales/{id} | STAFF 查看申请详情 |
| POST | /api/staff/aftersales/{id}/review | STAFF 审核；200；禁止审核自己的申请 |

创建请求示例：

```json
{"orderId":"1001","orderItemId":"2002","quantity":1,"reason":"QUALITY","description":"商品有损坏","requestKey":"550e8400-e29b-41d4-a716-446655440000"}
```

reason 可选 QUALITY、DAMAGED、WRONG_ITEM、NO_LONGER_NEEDED、OTHER。type 固定 RETURN_REFUND。不接受客户端指定身份、状态、金额或规则版本。审核请求：`{"decision":"APPROVED","note":"审核通过，请等待退货指引"}`，拒绝使用 REJECTED。意见必填，最多 1000 字符。

不存在与越权统一 404；非客服返回 403；资格失效、数量不足、重复键内容不同或状态冲突返回 409；非法参数返回 400。scaffold 合法请求返回 501。

## 规则、并发与金额

RETURN_7D_V1 是本项目演示规则，不代表通用电商法律政策：仅 COMPLETED、已签收、签收时间到达且严格早于签收后 7 天、商品实付大于 0、剩余数量大于 0 时可以申请。当前没有商品类别规则，所有演示商品按同一规则处理。资格查询不占用，提交时重新校验。

PENDING → APPROVED / REJECTED / CANCELLED；其他跳转拒绝。APPROVED 表示待退货。PENDING 和 APPROVED 持续占用数量与金额；REJECTED 和 CANCELLED 释放。订单详情 availableAftersalesQuantity 已扣除占用，但仍不代表满足期限和状态规则。

申请以 (user_id, request_key) 唯一约束防重。同用户同键同内容返回原申请，包括已撤销或已拒绝的原记录，不重新占用；新申请必须使用新键。同键不同内容返回 409。前端请求失败且结果不确定时冻结原内容并复用请求键，允许先查看记录。离开或刷新表单不会持久化待提交内容，请先检查记录再重新申请。

所有提交、审核和撤销先锁 trade_order 对应行（FOR UPDATE），再读取占用和更新记录；写事务为 READ_COMMITTED，避免等待锁后读到旧快照。状态更新另外要求 WHERE status='PENDING'。申请和事件在同一事务内写入。独立订单可并发处理，同一订单串行检查。订单归属和商品归属由后端校验。

金额采用 BigDecimal 与 DECIMAL(12,2)，API 返回两位小数字符串。非最后一批按 `floor(整行实付 / 购买数量, 2) × 本次数量` 计算；申请全部剩余数量时取未占用金额，避免尾差累计超额。例如 10 元 / 3 件，先申请 1 件为 3.33 元，余下 2 件为 6.67 元。拒绝或撤销释放对应金额；金额始终只是退款申请额，不代表支付动作。尚未提供配送费退款或优惠券返还规则。

## 数据结构

- aftersale_request：用户、订单、商品项外键，幂等请求键，数量、申请金额、原因/描述、状态、规则版本和时间。
- aftersale_event：申请外键、操作人、动作、意见和时间；对外返回动作/意见/时间，不暴露操作人 ID。
- 列表按 created_at DESC、id DESC 排序，返回 items/page/size/total；详情返回事件。业务内不使用 Redis 缓存售后数量，以数据库事务保证一致性。

## 验证

2026-09-28 验证结果：独立 MySQL 33308 和 Redis 16380 上，新增 8 项售后集成测试全部通过。全量测试共 32 项，初始唯一失败为旧 Swagger 路径数量断言；更新为当前 13 个路径后，单独重跑 4 项契约测试全部通过，并完成 verify 打包。最终各测试类均通过，集成测试未跳过。

浏览器联调使用 5174 前端、18082 真实后端和独立测试库，已验证用户提交、35.00 元申请金额、客服审核、状态与事件更新、刷新恢复、审核后剩余数量，以及待审核申请撤销。浏览器控制台未发现运行错误。未修改日常开发库或重启用户的 8080 服务。验证后临时前端、后端、MySQL、Redis 均已停止，临时数据库凭据已删除；正常前端入口仍为 5173。

AftersaleIntegrationTest 在显式 AUTH_TEST_ENABLED=true 和隔离 MySQL/Redis 环境运行，沿用认证测试的 MAPPER_TEST_URL、MAPPER_TEST_USERNAME、MAPPER_TEST_PASSWORD、REDIS_TEST_PORT。覆盖权限隔离、超期/状态/零金额、幂等、释放、金额尾差、自审禁止、分页、并发超量、并发同键、审核与撤销竞争。没有测试环境变量时跳过集成测试，不连接开发数据库。
