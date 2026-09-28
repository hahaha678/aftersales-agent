# MyBatis 数据访问层

本项目采用原生 MyBatis Starter 4.0.0 + XML SQL，适配 Spring Boot 4.0.x，版本依据见 [MyBatis 官方兼容说明](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。当前查询需要明确订单归属、联表和会话状态条件，使用显式 SQL 便于检查与学习，暂不引入 MyBatis-Plus。

## 文件如何对应

每个业务模块保留 `controller / service / mapper / domain` 分层，PO 放在 `domain/po`。

| 表 | PO | Mapper | 当前能力 |
| --- | --- | --- | --- |
| app_user | identity/domain/po/UserPO | UserMapper | 按 ID、精确用户名读取 |
| user_session | identity/domain/po/UserSessionPO | UserSessionMapper | 新建并回填 ID、摘要查询、条件撤销、分批清理过期记录 |
| trade_order | order/domain/po/OrderPO | OrderMapper | 当前用户分页、计数、详情 |
| order_item | order/domain/po/OrderItemPO | OrderItemMapper | 当前用户单订单及批量订单商品查询 |
| order_shipment | order/domain/po/OrderShipmentPO | OrderShipmentMapper | 当前用户订单包裹查询 |
| shipment_event | order/domain/po/ShipmentEventPO | ShipmentEventMapper | 当前用户包裹轨迹查询 |

SQL 文件放在 `backend/src/main/resources/mapper/identity` 和 `mapper/order`。XML 的 namespace 对应 Mapper 全限定类名，SQL 的 id 对应方法名。Mapper 上的 `@Mapper` 由 Starter 在存在数据源时扫描；没有在启动类添加无条件 `@MapperScan`，因此 scaffold 和现有 test 配置仍能无数据库启动。

PO 是普通 JavaBean，有 getter/setter，不依赖 Lombok。数据库下划线字段通过配置映射到驼峰属性；BIGINT 对应 Long，DECIMAL 对应 BigDecimal，DATETIME(3) 对应 LocalDateTime（约定 UTC），BINARY(32) 对应 byte[]。OrderPO.status 使用已有 OrderStatus 枚举；用户角色和物流状态暂以数据库字符串保存，后续 Service 转换为 VO 枚举。

## 调用约定

- Mapper 只处理数据库访问。登录是否允许、会话是否有效、HTTP 状态码、业务事务和 PO→VO 转换由后续 Service 负责。
- 查询单条记录不存在时返回 null，列表不存在时为空集合。订单和子表查询必须传入认证上下文中的 userId；不能使用客户端提交的身份值。
- 所有动态值使用 `#{}` 参数绑定；没有 `${}` 字符串拼接。订单筛选与计数共用同一个 WHERE 片段；排序固定为 created_at DESC、id DESC。
- Service 先校验 page ≥ 1、1 ≤ size ≤ 100，再用 `((long) page - 1) * size` 计算 offset。空白订单号的处理应由 Service 与接口契约统一；Mapper 把非 null 字符串当作精确过滤条件。
- 订单分页先取订单，再用 findByOwnedOrders 批量取商品并在 Service 汇总数量，避免每个订单发起一次查询。空/null ID 集合返回空列表，不能退化为全量查询。
- 物流/商品列表为空无法区分订单不存在和没有子记录，Service 应先用 findOwnedById 判断订单存在及归属，再决定 404 或空列表。
- 新会话仅写入用户、摘要、创建/过期时间，数据库主键回填到传入 PO。Service 必须验证摘要恰好 32 字节、时间为 UTC，不能将明文令牌直接交给 Mapper。
- findByTokenHash 保留过期/撤销记录，便于重复注销。revokeByTokenHash 只修改未撤销且未过期的记录，重复调用不会覆盖第一次撤销时间；更新行数为 0 时，Service 需要根据查询结果判定业务结果。
- deleteExpired 是内部维护方法，Service/任务提供固定正数批大小。已撤销但尚未到原过期时间的记录不会被清理。
- 禁用 MyBatis 二级缓存，一级缓存限制在单条语句，避免会话状态在后续查询中复用旧值。SQL 参数日志关闭，PO 不生成包含敏感字段的 toString，也不能直接作为 HTTP 响应。

目前仅添加首批接口需要的访问方法，不开放用户删除、任意订单状态修改等通用 CRUD。identity Controller 已接入认证 Service，order Controller 已接入订单查询 Service，详见 AUTHENTICATION.md、ORDER_QUERIES.md。

## 测试

普通 `mvn test` 运行原有 5 项测试。新增 MapperIntegrationTest 的 4 项 MySQL 集成测试默认跳过；只有显式提供 MAPPER_TEST_URL 才会启动。它们使用 Flyway 迁移，测试数据由事务回滚，不需要手工导入演示数据，不启动 HTTP 服务。

必须使用专用的空测试库，不能指向日常开发或生产库：Flyway 的 DDL 不会随测试数据回滚。需要具有建表权限的测试账号。可在 IDEA 的该测试运行配置中设置：

```text
MAPPER_TEST_URL=jdbc:mysql://127.0.0.1:33307/aftersales_mapper_validation?serverTimezone=UTC
MAPPER_TEST_USERNAME=你的测试账号
MAPPER_TEST_PASSWORD=你的测试密码
```

端口应改为你自己的隔离实例端口；本次验证用的临时 33307 实例在验证后关闭，不作为长期开发数据库。

在已设置上述环境变量的终端执行：

```text
mvn -pl backend -Dtest=MapperIntegrationTest test
```

覆盖六组 Mapper 的实际 SQL 执行，以及精确用户名与停用状态、主键回填、摘要二进制映射、重复摘要、会话过期和幂等撤销、分批清理、订单筛选与稳定分页、跨用户访问、空集合、BigDecimal 精度、毫秒时间与 NULL 映射。测试使用 MySQL 8.0.43，不以 H2 模拟 MySQL。
