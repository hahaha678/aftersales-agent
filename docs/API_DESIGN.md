# RESTful API 契约 · 用户、订单与售后

售后第一版已实现，新增 8 个接口与规则见 [AFTERSALES.md](AFTERSALES.md)。下文第一批契约保留为用户和订单接口说明；后续规划中的售后资格、申请及审核现已落地，Agent 草稿尚未实现。

状态：待用户检查。Controller/DTO/OpenAPI 注解是字段契约来源；本文记录统一规则与业务语义。
代码现按 controller、service、mapper、domain 分层；domain 内区分 dto、po、query、vo。响应对象使用 VO 后缀，订单列表条件封装为 OrderPageQuery；接口 URL 和 JSON 字段不变。详见 CODE_STRUCTURE.md。
用户、会话、订单和物流表及六组 Mapper/PO 已实现。local 模式已实现登录、当前用户、注销、Bearer 认证，以及订单分页、详情和物流查询，见 AUTHENTICATION.md、ORDER_QUERIES.md。
scaffold 模式的合法业务请求仍返回 501。local 模式身份接口返回实际 201/200/204，订单查询成功返回 200；认证失败为 401，认证依赖不可用为 503，订单不存在或越权为 404。

## 一、检查入口

- 启动后端后访问 http://127.0.0.1:8080/swagger-ui.html 。
- 机器可读文档：http://127.0.0.1:8080/v3/api-docs 。
- 无需数据库或模型密钥，使用 scaffold 配置即可检查。
- 文档注解采用 Swagger/OpenAPI v3，springdoc 3.0.3 与 Spring Boot 4.0.x 对齐。
- 参考兼容矩阵：https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot

## 二、统一规则

| 项目 | 约定 |
| --- | --- |
| 路径 | /api 前缀；小写复数名词；嵌套路径表达归属关系 |
| 方法 | GET 读取；POST 创建；DELETE 删除/撤销资源；后续 PATCH 修改允许变更的字段 |
| 成功响应 | 直接返回资源或分页对象，不额外嵌套 code=200/data；204 无响应体 |
| 创建资源 | 返回 201；可寻址资源通过 Location 指出地址 |
| 身份 | Authorization: Bearer <accessToken>；身份来自认证上下文，不接收用于授权的 userId/role |
| ID | JSON 字符串；路径 ID 为 1~19 位正整数文本，仍视为不透明标识，客户端不做数学运算 |
| 金额 | JSON 字符串，两位小数，单位元，例如 "199.00"；币种 CNY；后端用 BigDecimal/DECIMAL 计算 |
| 时间 | 带时区 ISO-8601，例如 2026-09-28T10:00:00+08:00；无时间值返回 null |
| 枚举 | 固定英文大写值，未知值返回 400；中文名称由前端映射 |
| 分页 | page 从 1 开始，size 默认 20、最大 100；items/page/size/total |
| 排序 | 订单列表固定 createdAt DESC, id DESC；首版不开放任意字段排序 |
| 空结果 | 列表成功返回空数组；不把空列表当成 404 |
| 缓存 | API 响应 Cache-Control: no-store，防止会话凭据及个人数据被中间缓存 |
| 请求标识 | 服务端生成 X-Request-Id，错误体带相同 requestId；不回显用户传入的追踪头 |

GET 不执行退款、提交申请或修改状态。业务状态转换以后设计为审核、取消、退款等记录资源，避免允许任意 PATCH status 跳转。

## 三、首批接口清单

| 方法 | 路径 | 目标功能 | 目标成功状态 | 目标权限 |
| --- | --- | --- | --- | --- |
| POST | /api/sessions | 创建登录会话 | 201 | 无需已登录 |
| DELETE | /api/sessions/current | 撤销当前会话 | 204 | 当前令牌 |
| GET | /api/users/me | 当前用户资料 | 200 | 已登录 |
| GET | /api/orders | 当前用户订单分页 | 200 | 已登录，按归属过滤 |
| GET | /api/orders/{orderId} | 订单详情及商品项 | 200 | 已登录且拥有订单 |
| GET | /api/orders/{orderId}/shipments | 原订单物流 | 200 | 已登录且拥有订单 |

### 1. 创建和撤销会话

POST /api/sessions，请求：

```json
{"username":"customer01","password":"仅为请求字段示意"}
```

目标响应为 accessToken、tokenType=Bearer、expiresAt 和 user。密码只出现在请求中，不回显、不记录原值；不在此接口注册账号或接受 role。
会话方案建议采用服务端可撤销的不透明令牌；数据库仅保存令牌摘要，具体存储与有效期在下一轮设计确认。
目标 Location 为 /api/sessions/current。首版不支持刷新令牌；过期重新登录。
用户名区分大小写；用户名和密码错误使用相同的 401/AUTHENTICATION_FAILED 提示，避免泄露账号存在性。
DELETE 只撤销当前设备会话。对可识别的已撤销令牌重复请求返回 204；缺少或伪造令牌返回 401。实现时需保留撤销记录至令牌原过期时间。

### 2. 当前用户

GET /api/users/me，目标返回：

```json
{"id":"1001","username":"customer01","displayName":"演示用户","role":"CUSTOMER"}
```

角色暂定 CUSTOMER（普通用户）、STAFF（客服）。STAFF 也不能通过“我的订单”访问其他用户数据；客服跨用户查询接口在后续单独设计。

### 3. 订单分页

GET /api/orders?page=1&size=20&status=COMPLETED&orderNumber=ORD202609280001。
status 和 orderNumber 可选；订单号精确匹配。不提供任意用户查询参数。
分页目标响应示例（仅说明结构，不是当前 API 返回数据）：

```json
{
  "items": [{
    "id": "2001",
    "orderNumber": "ORD202609280001",
    "status": "COMPLETED",
    "totalQuantity": 2,
    "paidAmount": "199.00",
    "currency": "CNY",
    "createdAt": "2026-09-28T10:00:00+08:00"
  }],
  "page": 1,
  "size": 20,
  "total": 1
}
```

total 为经过身份和筛选条件过滤后的总条数。页码超出末页返回空 items，仍保留真实 total。
订单状态：PENDING_PAYMENT、PAID、SHIPPED、COMPLETED、CANCELLED。售后状态不混入订单状态。

### 4. 订单详情和物流

详情包含订单基本信息、paidAt、signedAt 和全部商品项，不单独再建重复的商品项查询接口。
商品项保存下单时的名称和规格快照。paidAmount 为整行实付额，不是单价；原始实付额不会被后续退款覆盖。
availableAftersalesQuantity 只表示数量可用值，仍需售后资格接口检查期限、类别和状态。
不返回收件地址、手机号等当前页面不需要的信息。
不存在和不属于当前用户的订单统一返回 404/RESOURCE_NOT_FOUND；不能通过差异响应探测他人订单。
物流返回数组，未发货为 []；首版最多一个原始包裹，不支持拆分签收。售后退回/换货物流以后单独建模。

## 四、错误约定

```json
{
  "status": 501,
  "code": "NOT_IMPLEMENTED",
  "message": "当前接口仅完成契约设计，业务尚未实现",
  "path": "/api/orders",
  "requestId": "服务端生成的 UUID",
  "errors": []
}
```

| HTTP 状态 | 错误码 | 语义 | 当前状态 |
| --- | --- | --- | --- |
| 400 | VALIDATION_ERROR | 请求体字段校验失败；errors 提供字段和原因 | 已实现 |
| 400 | INVALID_REQUEST | JSON、枚举、路径或查询参数不合法 | 已实现 |
| 401 | AUTHENTICATION_FAILED | 登录凭据错误 | 目标 |
| 401 | UNAUTHENTICATED | 缺少、过期或失效会话 | 目标 |
| 403 | FORBIDDEN | 后续客服操作角色不足 | 后续接口 |
| 404 | RESOURCE_NOT_FOUND | 资源不存在或当前用户不可访问 | local 订单详情与物流查询 |
| 405 | METHOD_NOT_ALLOWED | 不支持的 HTTP 方法 | 已实现 |
| 409 | STATE_CONFLICT | 后续申请状态、数量或版本冲突 | 后续接口 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 请求媒体类型不支持 | 已实现 |
| 501 | NOT_IMPLEMENTED | 契约占位 | scaffold 所有业务接口 |
| 503 | AUTH_SERVICE_UNAVAILABLE | 认证依赖不可用，可重试 | local 认证链路 |

local 模式中 Controller 与认证过滤器使用相同错误结构，并携带请求 ID。scaffold 模式不进行真实鉴权，供检查接口契约。

## 五、之后的设计批次（尚未创建 Controller）

1. 售后资格与草稿：申请数量、类型、规则版本、有效期。
2. 售后单与审核/取消记录：从已确认草稿创建申请，幂等键、事务与状态冲突。
3. 退货物流、退款和换货处理：操作编号、未知结果查询、重复调用。
4. 会话、消息和 Agent 任务：SSE 事件、等待确认和任务取消。
5. 政策文档、版本和检索管理。

## 六、用户检查要点

- REST 路径、方法与资源拆分是否符合习惯。
- 是否接受服务端会话令牌、两个角色及首版不提供注册/刷新令牌。
- page 从 1 开始、直接资源响应、字符串 ID/金额是否合适。
- 订单摘要和详情字段是否满足计划中的前端页面。
- 首版“单商品项售后、单原始包裹、固定排序”的范围是否合适。

以上是供检查的初稿，确认后再设计用户、会话、订单、订单商品与物流表，并按接口逐项实现。
