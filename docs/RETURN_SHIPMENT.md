# 退回物流登记与客服收货确认

本轮实现人工退回物流登记和客服确认实物收货，不对接快递轨迹、不生成退货地址、不执行退款或换货。

## 状态与权限

| 当前状态 | 操作 | 执行人 | 新状态 |
|---|---|---|---|
| APPROVED（待退货） | 登记承运商、单号 | 申请本人 | RETURN_SHIPPED（已登记退回物流，待收货） |
| RETURN_SHIPPED | 确认实物收货并填写记录 | STAFF，且不是申请本人 | RETURN_RECEIVED（客服已收货，尚未退款） |

PENDING、REJECTED、CANCELLED 不允许登记物流；未登记物流不允许确认收货。登记不等于快递已揽收或签收，确认收货不等于退款。仅 PENDING 可撤销的原规则不变。

一张售后单只有一份退回物流。相同承运商、单号重试返回原申请，包括收货后的重试；不同内容返回 409，不覆盖已有记录。收货确认的相同备注重试幂等，不同备注返回 409。登记信息填错目前需人工核实，未实现在线更正流程。

所有写入在 READ_COMMITTED 事务内，先按原有顺序锁订单行，再读取售后状态；SQL 条件更新校验来源状态，事件与业务变更在同一事务提交。并发登记不同单号只有一份成功；重复确认收货只写一条事件。退回与收货后继续占用数量及申请金额，避免再次申请导致超量、超额。

## REST 接口

两个接口均返回更新后的 AftersaleVO（200），已提供 Swagger 注解。

```http
PUT /api/aftersales/{id}/return-shipment
Authorization: Bearer <用户令牌>
Content-Type: application/json

{"carrier":"顺丰速运","trackingNumber":"SF1234567890"}
```

carrier 去除首尾空白后为 2–40 个字母、数字、空格、点、下划线或连字符；trackingNumber 为 6–64 个字母、数字或连字符，首位为字母或数字。格式校验不代表快递单号真实有效。

```http
PUT /api/staff/aftersales/{id}/receipt
Authorization: Bearer <客服令牌>
Content-Type: application/json

{"note":"已核对退回商品数量及配件"}
```

note 去除首尾空白后非空、最多 1000 字符。权限或状态仍由后端重新校验，不能仅依靠前端勾选框。

详情和列表新增可空 returnShipment（carrier、trackingNumber、registeredAt）与 receipt（note、receivedAt）。API 时间带 UTC 偏移，前端按浏览器时区显示；时间由服务端生成。时间线保留操作者审计，事件为 RETURN_SHIPPED 与 RETURN_RECEIVED。已有 Agent 的售后详情工具会带回这些数据；Agent 没有这两项写工具，需引导用户进入详情页、客服进入工作台操作。

## 启动与手动联调

1. 重启 IDEA 中使用 local 配置的后端，Flyway 自动应用新增 V6。不要修改已执行的 V1–V5，也无需重导演示数据。
2. 启动前端（frontend 目录下 `npm run dev`）。
3. 买家进入“我的售后记录”，打开一条审核通过的申请。没有申请时，先对仍符合资格的订单申请并由客服审核。
4. 与人工客服确认退回方式，实际寄出后，在详情页填写承运商、单号并确认登记。开发联调可使用测试数据。
5. 客服进入售后工作台，筛选“已登记退回物流 · 待收货”，查看详情、核对实物、填写收货记录并确认。
6. 买家刷新详情，应看到物流、收货记录、时间线及“客服已收货 · 尚未退款”。

只有真实收到实物才确认收货。模拟退款将另行开发，不应使用本按钮表示退款完成。

## 验证

```powershell
./scripts/Run-AgentBaseline.ps1 -AftersaleTest
```

脚本使用临时 MySQL 33310、Redis 16382，运行结束关闭并恢复环境变量，不调用模型、不连接日常 3306/6379。虽然沿用已有脚本名称，此模式只运行售后业务与 API 契约测试。

2026-09-29：V1–V6 在隔离 MySQL 8.0.43 上执行成功；12 项 AftersaleIntegrationTest、4 项 ApiContractTest 通过。覆盖原有申请/审核流程、新状态流转、权限、字段验证、幂等、并发冲突、审计、状态筛选及数量金额占用。前端 TypeScript 检查与生产构建通过；未宣称已在浏览器完成手动验收，也未对日常库执行 V6。
