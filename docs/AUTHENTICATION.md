# 登录与会话认证

local 模式已实现登录、当前用户和注销，采用 Spring Security + MySQL 会话 + Redis 认证缓存。scaffold 模式仍保留 501 占位，供无依赖环境检查接口文档。订单接口在 local 下要求认证，成功后可查询当前用户的订单和物流，见 ORDER_QUERIES.md。

## 在 IDEA 中使用

1. 启动自己的 MySQL、Redis。在运行配置设置 `SPRING_PROFILES_ACTIVE=local`、DB_USERNAME、DB_PASSWORD；数据库名默认 aftersales_agent，Redis 默认 127.0.0.1:6379。自定义地址/密码参考 `.env.example`，项目不会自动读取该文件。
2. local 启动由 Flyway 执行迁移。演示账号需按 DATABASE_DESIGN.md 手动导入开发种子数据；程序不会自动导入。
3. 打开 `http://127.0.0.1:8080/swagger-ui.html`，调用 POST /api/sessions，输入下面的演示凭据。

```json
{"username":"demo_customer","password":"DemoPass123!"}
```

成功返回 201 和 accessToken、tokenType、expiresAt、user，Location 为 /api/sessions/current。在 Swagger 的 Authorize 中粘贴 accessToken（只填令牌，不重复输入 Bearer），再调用 GET /api/users/me。调用 DELETE /api/sessions/current 注销后，GET /api/users/me 应返回 401；重复注销在原会话到期前返回 204。

演示账号另有 demo_other、demo_staff，密码相同，角色由后端读取。没有注册接口，不会自动创建用户。

| 接口 | 成功 | 常见错误 |
| --- | --- | --- |
| POST /api/sessions | 201，创建当前设备会话 | 400 参数错误；401 AUTHENTICATION_FAILED；503 AUTH_SERVICE_UNAVAILABLE |
| GET /api/users/me | 200，仅返回当前用户公开字段 | 401 UNAUTHENTICATED；503 AUTH_SERVICE_UNAVAILABLE |
| DELETE /api/sessions/current | 204，无响应体 | 缺失/伪造/过期令牌为 401；依赖故障为 503 |

认证错误使用已有 ApiError 格式，带 X-Request-Id，401 带 WWW-Authenticate: Bearer。API 响应禁止缓存，不创建 JSESSIONID。Spring Security 不启用表单登录和 HTTP Basic。当前仅使用请求头 Bearer，不使用自动携带的 Cookie 凭据，因此关闭 CSRF；未来若改用 Cookie，需要重新设计 CSRF 防护。

## 密码与 Token

PasswordConfiguration 显式配置 PBKDF2-HMAC-SHA256、600000 次迭代、16 字节盐、256 位结果，与演示种子摘要一致。用户名大小写敏感，拒绝首尾空白；密码不裁剪。账号不存在、密码错误和停用账号使用相同提示。未知账号也执行摘要验证，以减少账号枚举的时间差。

TokenCodec 使用 SecureRandom 生成 32 字节随机值，编码为 43 字符 Base64URL，无填充。数据库仅保存令牌文本 UTF-8 的 SHA-256 摘要；Redis 键也只包含摘要。会话默认 2 小时，可用 `app.auth.session-ttl` 调整（1 秒至 1 天），不会因访问自动续期。

SessionService 先提交数据库会话，再写 Redis 缓存，成功后才返回明文 Token。缓存写入失败返回 503，并尽力撤销未交付给客户端的会话；数据库再次故障时可能遗留未交付的会话记录，客户端没有其令牌，后续按过期记录清理。当前仅提供 Mapper 清理方法，尚未添加定时清理任务。

## 请求认证与缓存收益

Spring Security 过滤器只从 Authorization 读取 Token，验证格式后交给 SessionService。缓存命中且未过期时直接建立请求级 SecurityContext；未命中才读取 user_session、app_user，检查会话过期/撤销和用户 enabled 状态，再回填。请求结束清理认证上下文。身份不能通过 query/body 的 userId 或 role 指定。

缓存最长 30 秒，且不超过 Token 原过期时间。集成测试使用真实 HTTP 请求和 Mapper spy 验证：登录后的连续两次 /users/me 调用未访问用户和会话 Mapper；移除该测试 Token 的缓存后才各查询一次。这个结果证明认证查询次数减少，没有进行吞吐量或 P95 耗时压测。

当前没有账号管理接口。直接更新数据库中的 enabled、role 或展示信息时，已缓存信息可能保留最多 30 秒；未命中时立即按数据库状态校验。后续账号管理功能需要主动清理相关会话缓存，不能承诺直接改库后立即全端生效。

## 注销顺序与失败语义

注销独立校验 Token 和数据库会话，不通过普通的“未撤销”认证入口，因此支持幂等重试。无论账号当前是否启用，持有有效期内真实会话的用户都可以撤销该会话。

1. 查询数据库，要求令牌真实存在且未过期；允许已经撤销。
2. Redis Lua 原子写撤销标记并删除认证缓存。
3. MySQL 事务更新 revoked_at，已有值不覆盖。
4. 两步成功才返回 204。其他设备的 Token 不受影响。

如果 Redis 步骤失败，返回 503，尚未修改数据库，客户端可以重试。如果数据库步骤失败，返回 503，Redis 中的标记仍阻止访问，重试注销可完成数据库更新。MySQL 与 Redis 没有分布式事务，此版本不提供后台补偿队列。

普通认证遇到 Redis 故障返回 503，不自动回源放行，避免故障切换中使用旧缓存。当前实现选择故障时拒绝认证；先前讨论的“限量回源”不在此版本启用。Redis 恢复正常后客户端重试即可；如果是恢复旧 Redis 快照，应先更换所有应用实例的 AUTH_CACHE_NAMESPACE 或清理旧认证缓存，让请求重新从 MySQL 建立状态，不能直接复用旧会话快照。

撤销标记不是永久事实，数据库 revoked_at 才是。Redis 数据丢失后，缓存未命中会检查数据库，不允许已成功注销的会话重新认证。并发请求若已在注销前完成认证，仍可能完成当前业务请求；后续敏感写操作需在业务层定义是否再次校验身份/权限。

## 测试与后续工作

在专用隔离 MySQL 和 Redis 上，配置 MAPPER_TEST_URL、MAPPER_TEST_USERNAME、MAPPER_TEST_PASSWORD、REDIS_TEST_PORT，再设置 AUTH_TEST_ENABLED=true，运行 `mvn verify`。测试只启动随机 HTTP 端口，不使用 8080。数据库迁移保留，认证测试创建的随机用户及其会话在测试结束时删除；不指向日常数据库。

AuthenticationIntegrationTest 覆盖：真实 PBKDF2 登录、缓存免查库及回填、重复注销、多会话隔离、未知/错误/停用账号、缺失/伪造/过期 Token、统一错误与请求 ID、Redis 失败、登录缓存写入失败、注销数据库更新失败及重试。

订单查询 Service 与 PO→VO 转换已完成，下一步联调前端登录和订单页。登录限流、自动清理、后台补偿、账号管理即时失效和性能压测是后续增强项，当前不把它们计为已完成。
