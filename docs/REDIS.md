# Redis 接入与会话缓存

本机可执行文件位于 `E:\Redis-x64-5.0.14.1`，检测版本为 5.0.14.1。项目通过 Spring Data Redis + Lettuce 连接；使用的 GET、SET PX、PTTL 和 Lua 脚本在本机实例验证。

## 当前实现范围

已添加 Redis 连接配置、CachedSession、SessionCache，并接入登录 Service 和 Spring Security 认证过滤器。完整请求链路与测试入口见 AUTHENTICATION.md；已验证缓存命中免查库，尚未进行性能压测。

- local 配置启用缓存，默认连接 127.0.0.1:6379，连接/命令超时均为 500ms。
- scaffold 和普通 test 配置不启用缓存组件与 Redis 健康检查，无 Redis 时仍可启动。
- `.env.example` 只作为说明；在 IDEA 运行配置中设置 REDIS_HOST、REDIS_PORT、REDIS_DATABASE、REDIS_PASSWORD。密码为空表示无密码，实际有密码时在本地设置。
- `AUTH_CACHE_NAMESPACE` 区分项目/环境，同一个应用的多个实例应使用相同值。
- 缓存 TTL 默认 30 秒，可通过 `app.auth.cache.ttl` 缩短，不能超过 30 秒；还会受 Token 剩余有效期限制。

## 本机连接检查

在 PowerShell 中执行：

```powershell
& 'E:\Redis-x64-5.0.14.1\redis-cli.exe' -h 127.0.0.1 -p 6379 PING
```

PONG 表示该地址已经有可用实例，无须重复启动。若实例要求认证，通过本地环境变量 REDISCLI_AUTH 配置客户端认证，不在命令行中填写密码。项目没有改写 Redis 安装目录下的配置、dump.rdb 或启动服务。

本次验证使用独立 16379 端口，工作目录在被 Git 忽略的 `backend/target/redis-validation`，关闭 RDB/AOF 持久化，仅绑定 127.0.0.1。测试实例结束后关闭；日常开发配置仍为 6379，不依赖这个临时进程。

## 缓存结构与原子操作

每个会话使用两把键，输入必须是 Token 文本 SHA-256 的 64 位小写十六进制摘要：

```text
aftersales:auth:{摘要}:session
aftersales:auth:{摘要}:revoked
```

花括号保证未来 Redis Cluster 中两把键位于同一 slot。session 值是 JSON，只包含 sessionId、userId、username、displayName、role、expiresAt；不含密码摘要或明文 Token。revoked 是撤销标记，保留至会话原始到期时间。

SessionCache 的方法：

| 方法 | 语义 |
| --- | --- |
| lookup | 同一个 Lua 脚本优先检查撤销标记，再读缓存；返回 HIT、MISS 或 REVOKED |
| putIfNotRevoked | 原子检查撤销标记后写入，阻止注销前开始、注销后才结束的旧查询重新回填；只应在 MySQL 事务提交后调用 |
| revoke | 原子写撤销标记并删除缓存；重复调用不会缩短已有标记的剩余时间 |

读取还会校验 JSON 内的绝对过期时间。Redis 连接异常、脚本失败、缓存格式错误向上抛出，不伪装为 MISS，也不擅自允许请求通过。

## Service 协调与故障策略

缓存组件只解决 Redis 内部的原子性，不能使 MySQL 和 Redis 自动成为一个事务。当前 SessionService 已实现：

1. 登录事务提交后回填，缓存未命中时查库并检查会话、用户状态。
2. 注销先写 Redis 撤销标记再提交数据库，全部完成才返回 204；部分失败返回 503，可重试。
3. Redis 故障时认证返回 503，不启用自动回源；避免恢复后误读故障期间旧缓存。
4. 用户停用/角色改变的管理接口尚未实现，直接改库的可见延迟最多为缓存 TTL。未来管理接口需要主动失效。
5. 数据库保留撤销状态；Redis 缓存丢失后回源重新核验。恢复旧快照时要先更换缓存命名空间或清理旧认证缓存。

已完成正常链路与故障注入的联合测试。跨存储更新不具备原子事务，当前使用可重试的失败语义；后台自动补偿、登录限流和性能压测仍是后续增强项。

## 测试入口

准备隔离 Redis 后设置 REDIS_TEST_PORT（测试限定连接 127.0.0.1），执行：

```powershell
$env:REDIS_TEST_PORT = '16379'
mvn -pl backend -Dtest=SessionCacheIntegrationTest test
```

未设置该环境变量时，四项 Redis 集成测试默认跳过。测试使用随机键命名空间和短 TTL，不执行 FLUSHDB/FLUSHALL；覆盖 JSON/中文/时间往返、缓存 TTL、会话过期限制、撤销后的回填拒绝、重复撤销与非法摘要拒绝。
