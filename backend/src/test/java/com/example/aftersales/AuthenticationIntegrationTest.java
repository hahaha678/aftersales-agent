package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.aftersales.identity.cache.SessionCache;
import com.example.aftersales.identity.mapper.UserMapper;
import com.example.aftersales.identity.mapper.UserSessionMapper;
import com.example.aftersales.identity.security.TokenCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "AUTH_TEST_ENABLED", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=${MAPPER_TEST_URL}",
        "spring.datasource.username=${MAPPER_TEST_USERNAME}",
        "spring.datasource.password=${MAPPER_TEST_PASSWORD}",
        "spring.data.redis.port=${REDIS_TEST_PORT}",
        "app.auth.cache.namespace=aftersales-auth-it",
    }
)
class AuthenticationIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HASH =
        "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80";

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @MockitoSpyBean
    UserMapper users;

    @MockitoSpyBean
    UserSessionMapper sessions;

    @MockitoSpyBean
    SessionCache cache;

    private String username;
    private long userId;
    private final List<String> tokens = new ArrayList<>();

    @BeforeEach
    void prepare() {
        username = "auth_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
            "INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,?)",
            username,
            HASH,
            "认证测试用户",
            "CUSTOMER"
        );
        userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM user_session WHERE user_id=?", userId);
        jdbc.update("DELETE FROM app_user WHERE id=?", userId);
        for (String token : tokens) {
            String prefix = "aftersales-auth-it:{" + TokenCodec.hash(token) + "}:";
            redis.delete(List.of(prefix + "session", prefix + "revoked"));
        }
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body != null) builder.header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(
            method,
            body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)
        );
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private String login() throws Exception {
        var response = request(
            "POST",
            "/api/sessions",
            "{\"username\":\"" + username + "\",\"password\":\"DemoPass123!\"}",
            null
        );
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).contains("/api/sessions/current");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        String token = JSON.readTree(response.body()).path("accessToken").asString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        tokens.add(token);
        return token;
    }

    @Test
    void loginCachesIdentityWithoutReadingMysqlOnEveryRequest() throws Exception {
        String token = login();
        clearInvocations(users, sessions);
        for (int i = 0; i < 2; i++) {
            var response = request("GET", "/api/users/me", null, token);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body()).path("id").asString()).isEqualTo(Long.toString(userId));
            assertThat(response.body()).doesNotContain("passwordHash", "tokenHash", "DemoPass123!");
        }
        verifyNoInteractions(users, sessions);
        redis.delete("aftersales-auth-it:{" + TokenCodec.hash(token) + "}:session");
        assertThat(request("GET", "/api/users/me", null, token).statusCode()).isEqualTo(200);
        verify(sessions).findByTokenHash(any(byte[].class));
        verify(users).findById(userId);
    }

    @Test
    void logoutIsIdempotentAndDoesNotAffectAnotherSession() throws Exception {
        String first = login();
        String second = login();
        var response = request("DELETE", "/api/sessions/current", null, first);
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(request("DELETE", "/api/sessions/current", null, first).statusCode()).isEqualTo(204);
        assertThat(request("GET", "/api/users/me", null, first).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/users/me", null, second).statusCode()).isEqualTo(200);
        // 即使撤销标记丢失，数据库也不会让已注销会话再次认证。
        redis.delete("aftersales-auth-it:{" + TokenCodec.hash(first) + "}:revoked");
        assertThat(request("GET", "/api/users/me", null, first).statusCode()).isEqualTo(401);
    }

    @Test
    void credentialsErrorsAndInvalidTokensUseUniformErrors() throws Exception {
        for (String name : List.of(username, "missing_" + username)) {
            var response = request(
                "POST",
                "/api/sessions",
                "{\"username\":\"" + name + "\",\"password\":\"wrong\"}",
                null
            );
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo("AUTHENTICATION_FAILED");
        }
        for (String token : new String[] { null, "malformed", TokenCodec.generate() }) {
            var response = request("GET", "/api/users/me", null, token);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JSON.readTree(response.body()).path("requestId").asString()).isEqualTo(
                response.headers().firstValue("X-Request-Id").orElseThrow()
            );
            assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
            assertThat(request("DELETE", "/api/sessions/current", null, token).statusCode()).isEqualTo(401);
        }
        assertThat(request("GET", "/api/orders", null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/orders", null, login()).statusCode()).isEqualTo(200);
    }

    @Test
    void expiredAndDisabledUsersAreRejectedAfterCacheMiss() throws Exception {
        String token = login();
        jdbc.update(
            "UPDATE user_session SET created_at=UTC_TIMESTAMP(3)-INTERVAL 2 HOUR, expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 HOUR WHERE user_id=?",
            userId
        );
        redis.delete("aftersales-auth-it:{" + TokenCodec.hash(token) + "}:session");
        assertThat(request("GET", "/api/users/me", null, token).statusCode()).isEqualTo(401);
        assertThat(request("DELETE", "/api/sessions/current", null, token).statusCode()).isEqualTo(401);
        String another = login();
        jdbc.update("UPDATE app_user SET enabled=0 WHERE id=?", userId);
        redis.delete("aftersales-auth-it:{" + TokenCodec.hash(another) + "}:session");
        assertThat(request("GET", "/api/users/me", null, another).statusCode()).isEqualTo(401);
        assertThat(
            request(
                "POST",
                "/api/sessions",
                "{\"username\":\"" + username + "\",\"password\":\"DemoPass123!\"}",
                null
            ).statusCode()
        ).isEqualTo(401);
    }

    @Test
    void cacheFailureReturns503AndFailedLogoutCanBeRetried() throws Exception {
        String token = login();
        doThrow(new RedisConnectionFailureException("test outage")).when(cache).lookup(anyString());
        assertThat(request("GET", "/api/users/me", null, token).statusCode()).isEqualTo(503);
        doCallRealMethod().when(cache).lookup(anyString());
        doThrow(new RedisConnectionFailureException("test outage")).when(cache).revoke(anyString(), any());
        assertThat(request("DELETE", "/api/sessions/current", null, token).statusCode()).isEqualTo(503);
        assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NULL FROM user_session WHERE token_hash=?",
                Boolean.class,
                TokenCodec.bytes(TokenCodec.hash(token))
            )
        ).isTrue();
        doCallRealMethod().when(cache).revoke(anyString(), any());
        assertThat(request("DELETE", "/api/sessions/current", null, token).statusCode()).isEqualTo(204);
        assertThat(request("GET", "/api/users/me", null, token).statusCode()).isEqualTo(401);
    }

    @Test
    void cacheFillFailureAfterLoginCommitRevokesUndeliveredSession() throws Exception {
        doThrow(new RedisConnectionFailureException("test write outage"))
            .when(cache)
            .putIfNotRevoked(anyString(), any());
        var response = request(
            "POST",
            "/api/sessions",
            "{\"username\":\"" + username + "\",\"password\":\"DemoPass123!\"}",
            null
        );
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain("accessToken", "DemoPass123!");
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE user_id=? AND revoked_at IS NOT NULL",
                Long.class,
                userId
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE user_id=? AND revoked_at IS NULL",
                Long.class,
                userId
            )
        ).isZero();
    }

    @Test
    void databaseFailureDuringLogoutKeepsRedisBlockedAndRetryCompletes() throws Exception {
        String token = login();
        doThrow(new org.springframework.dao.CannotAcquireLockException("test database outage"))
            .when(sessions)
            .revokeByTokenHash(any(byte[].class), any());
        assertThat(request("DELETE", "/api/sessions/current", null, token).statusCode()).isEqualTo(503);
        assertThat(request("GET", "/api/users/me", null, token).statusCode()).isEqualTo(401);
        assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NULL FROM user_session WHERE token_hash=?",
                Boolean.class,
                TokenCodec.bytes(TokenCodec.hash(token))
            )
        ).isTrue();
        reset(sessions); // Mapper 是代理接口，恢复委托，不能调用抽象接口的 real method。
        assertThat(request("DELETE", "/api/sessions/current", null, token).statusCode()).isEqualTo(204);
        assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM user_session WHERE token_hash=?",
                Boolean.class,
                TokenCodec.bytes(TokenCodec.hash(token))
            )
        ).isTrue();
    }
}
