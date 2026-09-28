package com.example.aftersales.identity.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 会话缓存原语：读/回填/撤销均通过 Lua 原子执行。
 * Redis 异常向调用方传播，绝不把故障伪装为正常缓存未命中。
 * MySQL 事务、认证判断、故障补偿由后续 Service 负责。
 */
@Component
@ConditionalOnProperty(name = "app.auth.cache.enabled", havingValue = "true")
public class SessionCache {
    private static final DefaultRedisScript<String> READ = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return '!revoked' end
            return redis.call('GET', KEYS[1])
            """, String.class);
    private static final DefaultRedisScript<Long> PUT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            local remaining = redis.call('PTTL', KEYS[2])
            if remaining ~= -1 and remaining < tonumber(ARGV[1]) then
                redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String namespace;
    private final Duration ttl;

    public SessionCache(StringRedisTemplate redis,
            @Value("${app.auth.cache.namespace:aftersales:auth}") String namespace,
            @Value("${app.auth.cache.ttl:30s}") Duration ttl) {
        this.redis = redis;
        if (!namespace.matches("[A-Za-z0-9:_-]+")) {
            throw new IllegalArgumentException("Invalid auth cache namespace");
        }
        if (ttl.toMillis() < 1 || ttl.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Auth cache TTL must be between 1ms and 30s");
        }
        this.namespace = namespace;
        this.ttl = ttl;
    }

    public Lookup lookup(String tokenHash) {
        String value = redis.execute(READ, keys(tokenHash));
        if (value == null) return new Lookup(State.MISS, null);
        if (value.equals("!revoked")) return new Lookup(State.REVOKED, null);
        CachedSession session = json.readValue(value, CachedSession.class);
        if (!session.expiresAt().isAfter(Instant.now())) return new Lookup(State.MISS, null);
        return new Lookup(State.HIT, session);
    }

    /** 只能在数据库事务提交后回填；返回 false 表示已撤销或过期。 */
    public boolean putIfNotRevoked(String tokenHash, CachedSession session) {
        List<String> keys = keys(tokenHash);
        Objects.requireNonNull(session);
        long millis = Math.min(ttl.toMillis(), Duration.between(Instant.now(), session.expiresAt()).toMillis());
        if (millis <= 0) return false;
        return Long.valueOf(1).equals(redis.execute(PUT, keys, json.writeValueAsString(session), Long.toString(millis)));
    }

    /** expiresAt 必须来自数据库，不能由客户端提供；重复撤销不会缩短已有标记 TTL。 */
    public void revoke(String tokenHash, Instant expiresAt) {
        List<String> keys = keys(tokenHash);
        long millis = Math.max(1, Duration.between(Instant.now(), expiresAt).toMillis());
        redis.execute(REVOKE, keys, Long.toString(millis));
    }

    private List<String> keys(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
        }
        // 相同 hash tag 使两把键在 Redis Cluster 中落在同一个 slot。
        String prefix = namespace + ":{" + hash + "}:";
        return List.of(prefix + "session", prefix + "revoked");
    }

    public enum State { HIT, MISS, REVOKED }
    public record Lookup(State state, CachedSession session) {}
}
