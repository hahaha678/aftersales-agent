# 后端代码组织规范

采用按业务模块分包、模块内部按层划分的模块化单体；所有模块仍在一个 backend 应用中。

```text
com.example.aftersales
├── identity                 用户与会话
├── order                    订单与物流
├── aftersales               售后业务
├── agent                    模型和工具适配
├── conversation             会话消息与任务
├── knowledge                政策知识库
└── common                   跨模块公共技术能力

每个业务模块
├── controller               HTTP 路由、入参校验，调用 Service
├── service                  业务规则、事务及跨模块服务调用
├── mapper                   MyBatis 数据库访问接口
└── domain
    ├── dto                  创建、修改等操作入参：CreateSessionDTO
    ├── po                   与数据库表对应的持久化对象
    ├── query                查询条件：OrderPageQuery
    └── vo                   对外响应：OrderDetailVO
```

## 类型与依赖约定

- Controller 依赖 Service，Service 访问本模块 Mapper；跨业务模块通过公开 Service 能力协作，不直接操作其他模块 Mapper。
- PO 不作为 Controller 入参或响应，避免暴露数据库字段。
- DTO 描述写操作输入，不承载查询分页；Query 只包含客户端允许设置的过滤条件，不接受用于授权的 userId/role。
- VO 仅返回客户端需要的数据；ID 和金额仍按现有接口契约使用字符串。
- 领域枚举可直接位于 domain，例如 OrderStatus，避免绑定到特定 DTO/VO 或引入不必要的子层。
- common 是公共技术包，按技术职责分类，不强制建立 controller/service/mapper。结构如下：

```text
common
├── config                  OpenApiConfiguration：公共配置
├── domain/vo               ApiError：公共错误响应
├── exception               ContractNotImplementedException：公共异常类型
├── handler                 ApiExceptionHandler：全局异常处理
└── filter                  RequestIdFilter：请求 ID 与响应缓存控制
```

- identity/security 的 Token 与认证配置、identity/cache 的会话缓存仍归身份模块，不迁入 common。
- Service、Mapper、PO 按实际功能添加。identity/order 已有六组 PO/Mapper 和 XML；SessionService 负责认证流程，OrderService 负责订单查询，CurrentUserService 提供跨模块身份入口。identity/security 负责认证技术适配，identity/cache 封装 Redis，不改变业务层职责。
- package-info.java 用于记录/保留分层目录，不代表这一层已经实现；暂无类的包会在 IDEA 中以空包形式显示。

## 本轮迁移

| 原类位置/名称 | 新位置/名称 |
| --- | --- |
| identity/api/SessionController | identity/controller/SessionController |
| identity/api/UserController | identity/controller/UserController |
| CreateSessionRequest | identity/domain/dto/CreateSessionDTO |
| SessionResponse、UserResponse | identity/domain/vo/SessionVO、UserVO |
| order/api/OrderController | order/controller/OrderController |
| OrderStatus | order/domain/OrderStatus |
| OrderDetailResponse、OrderItemResponse | order/domain/vo/OrderDetailVO、OrderItemVO |
| OrderPageResponse、OrderSummaryResponse | order/domain/vo/OrderPageVO、OrderSummaryVO |
| ShipmentResponse | order/domain/vo/ShipmentVO |
| Controller 中的分页/筛选参数 | order/domain/query/OrderPageQuery |

OrderPageQuery 采用 JavaBean，便于 Spring MVC 将 URL 查询参数绑定到对象；DTO 和 VO 继续使用 Java record。
接口路径、JSON 字段名、分页默认值和错误码保持原契约；OpenAPI schema 名称由 Request/Response 更新为 DTO/VO，后续生成客户端时应重新生成。
