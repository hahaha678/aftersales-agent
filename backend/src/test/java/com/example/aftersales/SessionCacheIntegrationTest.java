package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.example.aftersales.identity.cache.CachedSession;
import com.example.aftersales.identity.cache.SessionCache;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(named = "REDIS_TEST_PORT", matches = ".+")
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "app.auth.cache.enabled=true",
        "app.auth.cache.namespace=aftersales-test-${random.uuid}",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=${REDIS_TEST_PORT}",
        "spring.data.redis.connect-timeout=500ms",
        "spring.data.redis.timeout=500ms",
    }
)
class SessionCacheIntegrationTest {

    @Autowired
    SessionCache cache;

    @Autowired
    StringRedisTemplate redis;

    private String hash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    private CachedSession session(Instant expiry) {
        return new CachedSession(1, 2, "test-user", "测试用户", "CUSTOMER", expiry);
    }

    @Test
    void roundTripAndCacheExpiryBoundedByConfiguration() {
        String namespace = "ttl-test-" + UUID.randomUUID();
        var shortCache = new SessionCache(redis, namespace, Duration.ofMillis(250));
        String hash = hash();
        var expected = session(Instant.now().plusSeconds(60));
        assertThat(shortCache.lookup(hash).state()).isEqualTo(SessionCache.State.MISS);
        assertThat(shortCache.putIfNotRevoked(hash, expected)).isTrue();
        assertThat(shortCache.lookup(hash).session()).isEqualTo(expected);
        await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(shortCache.lookup(hash).state()).isEqualTo(SessionCache.State.MISS));
    }

    @Test
    void sessionExpiryCapsRedisTtlAndExpiredSessionIsNotCached() {
        String hash = hash();
        var expiresSoon = session(Instant.now().plusMillis(700));
        assertThat(cache.putIfNotRevoked(hash, expiresSoon)).isTrue();
        await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(cache.lookup(hash).state()).isEqualTo(SessionCache.State.MISS));
        assertThat(cache.putIfNotRevoked(hash, session(Instant.now().minusSeconds(1)))).isFalse();
    }

    @Test
    void revocationBlocksLateRefillAndRepeatedRevocationCannotShortenTtl() {
        String hash = hash();
        var pendingDatabaseResult = session(Instant.now().plusSeconds(5));
        assertThat(cache.putIfNotRevoked(hash, pendingDatabaseResult)).isTrue();
        cache.revoke(hash, pendingDatabaseResult.expiresAt());
        assertThat(cache.lookup(hash).state()).isEqualTo(SessionCache.State.REVOKED);
        assertThat(cache.putIfNotRevoked(hash, pendingDatabaseResult)).isFalse();
        cache.revoke(hash, Instant.now().minusSeconds(1));
        await()
            .pollDelay(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(cache.lookup(hash).state()).isEqualTo(SessionCache.State.REVOKED));
    }

    @Test
    void keysContainOnlyDigestAndRejectRawTokens() {
        assertThatIllegalArgumentException().isThrownBy(() -> cache.lookup("raw-token"));
        assertThatIllegalArgumentException().isThrownBy(() -> cache.putIfNotRevoked(null, session(Instant.now())));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new SessionCache(redis, "bad{namespace}", Duration.ofSeconds(30))
        );
    }
}
