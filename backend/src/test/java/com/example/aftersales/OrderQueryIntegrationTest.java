package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.security.TokenCodec;
import com.example.aftersales.identity.service.SessionService;
import com.example.aftersales.order.mapper.OrderItemMapper;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
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
        "app.auth.cache.namespace=aftersales-order-it",
    }
)
class OrderQueryIntegrationTest {

    private static final long BASE = 9007199254740993L;
    private static final String HASH =
        "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80";
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 28, 3, 0, 0, 123000000);
    private final JsonMapper json = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService auth;

    @Autowired
    StringRedisTemplate redis;

    @MockitoSpyBean
    OrderItemMapper itemMapper;

    private final List<Long> userIds = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    @BeforeEach
    void fixtures() {
        for (String role : List.of("CUSTOMER", "CUSTOMER", "STAFF")) {
            String name = "order_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,?)",
                name,
                HASH,
                "订单测试用户",
                role
            );
            userIds.add(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, name));
            tokens.add(auth.login(new CreateSessionDTO(name, "DemoPass123!")).accessToken());
        }
        for (int i = 0; i < 4; i++) {
            jdbc.update(
                "INSERT INTO trade_order(id,user_id,order_number,status,paid_amount,created_at,paid_at,signed_at) VALUES(?,?,?,?,?,?,?,?)",
                BASE + i,
                userIds.get(i == 3 ? 1 : 0),
                "ORDER-IT-" + i,
                i == 0 ? "COMPLETED" : i == 2 ? "PENDING_PAYMENT" : "PAID",
                i == 2 ? "0.00" : "19.90",
                TIME,
                i == 2 ? null : TIME.plusMinutes(1),
                i == 0 ? TIME.plusDays(1) : null
            );
            jdbc.update(
                "INSERT INTO order_item(order_id,sku_id,product_name,specification,quantity,paid_amount) VALUES(?,?,?,?,?,?)",
                BASE + i,
                BASE + 10,
                "测试商品",
                "黑色",
                2,
                i == 0 ? "12.90" : i == 2 ? "0.00" : "19.90"
            );
        }
        jdbc.update(
            "INSERT INTO order_item(order_id,sku_id,product_name,specification,quantity,paid_amount) VALUES(?,?,?,?,?,?)",
            BASE,
            BASE + 11,
            "第二件商品",
            "",
            1,
            "7.00"
        );
        jdbc.update(
            "INSERT INTO order_shipment(order_id,carrier,tracking_number,status,shipped_at,delivered_at) VALUES(?,?,?,?,?,?)",
            BASE,
            "模拟物流",
            "ORDER-IT-TRACK",
            "DELIVERED",
            TIME.plusHours(1),
            TIME.plusDays(1)
        );
        long shipmentId = jdbc.queryForObject("SELECT id FROM order_shipment WHERE order_id=?", Long.class, BASE);
        jdbc.update(
            "INSERT INTO shipment_event(shipment_id,occurred_at,description) VALUES(?,?,?)",
            shipmentId,
            TIME.plusDays(1),
            "已签收"
        );
        jdbc.update(
            "INSERT INTO shipment_event(shipment_id,occurred_at,description) VALUES(?,?,?)",
            shipmentId,
            TIME.plusHours(1),
            "已揽收"
        );
    }

    @AfterEach
    void cleanup() {
        jdbc.update(
            "DELETE e FROM shipment_event e JOIN order_shipment s ON s.id=e.shipment_id WHERE s.order_id BETWEEN ? AND ?",
            BASE,
            BASE + 3
        );
        jdbc.update("DELETE FROM order_shipment WHERE order_id BETWEEN ? AND ?", BASE, BASE + 3);
        jdbc.update("DELETE FROM order_item WHERE order_id BETWEEN ? AND ?", BASE, BASE + 3);
        jdbc.update("DELETE FROM trade_order WHERE id BETWEEN ? AND ?", BASE, BASE + 3);
        for (long id : userIds) {
            jdbc.update("DELETE FROM user_session WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_user WHERE id=?", id);
        }
        for (String token : tokens) {
            String prefix = "aftersales-order-it:{" + TokenCodec.hash(token) + "}:";
            redis.delete(List.of(prefix + "session", prefix + "revoked"));
        }
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private JsonNode success(String path, int userIndex) throws Exception {
        var response = get(path, tokens.get(userIndex));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    @Test
    void paginationFiltersAndBatchQuantityAvoidNPlusOne() throws Exception {
        clearInvocations(itemMapper);
        var page = success("/api/orders?size=2&userId=" + userIds.get(1), 0);
        assertThat(page.path("total").asLong()).isEqualTo(3);
        assertThat(page.path("items").size()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id").asString()).isEqualTo(Long.toString(BASE + 2));
        assertThat(page.path("items").get(1).path("id").asString()).isEqualTo(Long.toString(BASE + 1));
        verify(itemMapper, times(1)).findByOwnedOrders(eq(userIds.getFirst()), anyList());
        verify(itemMapper, never()).findByOwnedOrder(anyLong(), anyLong());
        var second = success("/api/orders?page=2&size=2", 0);
        assertThat(second.path("items").get(0).path("totalQuantity").asInt()).isEqualTo(3);
        assertThat(second.path("items").get(0).path("paidAmount").asString()).isEqualTo("19.90");
        assertThat(success("/api/orders?status=PAID", 0).path("total").asLong()).isEqualTo(1);
        assertThat(success("/api/orders?orderNumber=ORDER-IT-0", 0).path("total").asLong()).isEqualTo(1);
        assertThat(success("/api/orders?orderNumber=ORDER-IT-3", 0).path("total").asLong()).isZero();
        assertThat(success("/api/orders?orderNumber=%20", 0).path("total").asLong()).isEqualTo(3);
        var beyond = success("/api/orders?page=2147483647&size=100", 0);
        assertThat(beyond.path("items").isEmpty()).isTrue();
        assertThat(beyond.path("total").asLong()).isEqualTo(3);
        assertThat(success("/api/orders", 2).path("total").asLong()).isZero();
    }

    @Test
    void detailPreservesStringIdsMoneyUtcAndNulls() throws Exception {
        var detail = success("/api/orders/" + BASE, 0);
        assertThat(detail.path("id").asString()).isEqualTo(Long.toString(BASE));
        assertThat(detail.path("paidAmount").asString()).isEqualTo("19.90");
        assertThat(detail.path("createdAt").asString()).isEqualTo("2026-09-28T03:00:00.123Z");
        assertThat(detail.path("items").size()).isEqualTo(2);
        assertThat(detail.path("items").get(0).path("skuId").asString()).isEqualTo(Long.toString(BASE + 10));
        assertThat(detail.path("items").get(0).path("availableAftersalesQuantity").asInt()).isEqualTo(2);
        var pending = success("/api/orders/" + (BASE + 2), 0);
        assertThat(pending.path("paidAt").isNull()).isTrue();
        assertThat(pending.path("signedAt").isNull()).isTrue();
        assertThat(pending.path("paidAmount").asString()).isEqualTo("0.00");
    }

    @Test
    void ownershipIsRequiredForCustomerAndStaffAndForChildResources() throws Exception {
        for (int user : List.of(1, 2)) {
            for (String suffix : List.of("", "/shipments")) {
                var foreign = get("/api/orders/" + BASE + suffix, tokens.get(user));
                var missing = get("/api/orders/9223372036854775807" + suffix, tokens.get(user));
                assertThat(foreign.statusCode()).isEqualTo(404);
                assertThat(missing.statusCode()).isEqualTo(404);
                var error = json.readTree(foreign.body());
                assertThat(error.path("code").asString()).isEqualTo("RESOURCE_NOT_FOUND");
                assertThat(error.path("message")).isEqualTo(json.readTree(missing.body()).path("message"));
                assertThat(error.path("requestId").asString()).isEqualTo(
                    foreign.headers().firstValue("X-Request-Id").orElseThrow()
                );
            }
        }
        assertThat(get("/api/orders", null).statusCode()).isEqualTo(401);
    }

    @Test
    void logisticsAndInvalidInputs() throws Exception {
        assertThat(success("/api/orders/" + (BASE + 1) + "/shipments", 0).isEmpty()).isTrue();
        var shipments = success("/api/orders/" + BASE + "/shipments", 0);
        assertThat(shipments.size()).isEqualTo(1);
        assertThat(shipments.get(0).path("status").asString()).isEqualTo("DELIVERED");
        assertThat(shipments.get(0).path("events").get(0).path("description").asString()).isEqualTo("已揽收");
        assertThat(shipments.get(0).path("events").get(1).path("description").asString()).isEqualTo("已签收");
        for (String path : List.of(
            "/api/orders?size=101",
            "/api/orders?page=0",
            "/api/orders?status=OTHER",
            "/api/orders/9223372036854775808",
            "/api/orders/9223372036854775808/shipments"
        )) {
            assertThat(get(path, tokens.getFirst()).statusCode()).isEqualTo(400);
        }
    }
}
