package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.security.TokenCodec;
import com.example.aftersales.identity.service.SessionService;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
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
        "app.auth.cache.namespace=aftersales-business-it",
    }
)
class AftersaleIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService auth;

    @Autowired
    StringRedisTemplate redis;

    final JsonMapper json = JsonMapper.builder().build();
    final List<Long> users = new ArrayList<>();
    final List<String> tokens = new ArrayList<>();
    long order, item, expired, foreign, staffOrder, staffItem;

    @BeforeEach
    void fixture() {
        for (String role : List.of("CUSTOMER", "CUSTOMER", "STAFF")) {
            String name = "after_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,?)",
                name,
                "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80",
                "售后测试",
                role
            );
            users.add(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, name));
            tokens.add(auth.login(new CreateSessionDTO(name, "DemoPass123!")).accessToken());
        }
        order = makeOrder(users.get(0), 2);
        expired = makeOrder(users.get(0), 8);
        foreign = makeOrder(users.get(1), 2);
        staffOrder = makeOrder(users.get(2), 2);
        item = itemOf(order);
        staffItem = itemOf(staffOrder);
    }

    long makeOrder(long user, int age) {
        String number = "AFTER-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO trade_order(user_id,order_number,status,paid_amount,created_at,paid_at,signed_at) VALUES(?,?,'COMPLETED',10.00,UTC_TIMESTAMP()-INTERVAL 15 DAY,UTC_TIMESTAMP()-INTERVAL 14 DAY,DATE_SUB(UTC_TIMESTAMP(),INTERVAL ? DAY))",
            user,
            number,
            age
        );
        long id = jdbc.queryForObject("SELECT id FROM trade_order WHERE order_number=?", Long.class, number);
        jdbc.update(
            "INSERT INTO order_item(order_id,sku_id,product_name,quantity,paid_amount) VALUES(?,101,'三件分摊商品',3,10.00)",
            id
        );
        return id;
    }

    long itemOf(long oid) {
        return jdbc.queryForObject("SELECT id FROM order_item WHERE order_id=?", Long.class, oid);
    }

    @AfterEach
    void cleanup() {
        for (long uid : users) {
            jdbc.update(
                "DELETE e FROM aftersale_evidence e JOIN aftersale_request a ON a.id=e.request_id WHERE a.user_id=?",
                uid
            );
            jdbc.update(
                "DELETE r FROM aftersale_refund r JOIN aftersale_request a ON a.id=r.request_id WHERE a.user_id=?",
                uid
            );
            jdbc.update(
                "DELETE e FROM aftersale_event e JOIN aftersale_request a ON a.id=e.request_id WHERE a.user_id=?",
                uid
            );
            jdbc.update("DELETE FROM aftersale_request WHERE user_id=?", uid);
            jdbc.update("DELETE i FROM order_item i JOIN trade_order o ON o.id=i.order_id WHERE o.user_id=?", uid);
            jdbc.update("DELETE FROM trade_order WHERE user_id=?", uid);
            jdbc.update("DELETE FROM user_session WHERE user_id=?", uid);
            jdbc.update("DELETE FROM app_user WHERE id=?", uid);
        }
        for (String token : tokens) {
            String prefix = "aftersales-business-it:{" + TokenCodec.hash(token) + "}:";
            redis.delete(List.of(prefix + "session", prefix + "revoked"));
        }
    }

    HttpResponse<String> call(String method, String path, Object body, int actor) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
        if (actor >= 0) builder.header("Authorization", "Bearer " + tokens.get(actor));
        builder
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
            );
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    Map<String, Object> body(long oid, long iid, int qty, String key) {
        return Map.of(
            "orderId",
            String.valueOf(oid),
            "orderItemId",
            String.valueOf(iid),
            "quantity",
            qty,
            "reason",
            "QUALITY",
            "description",
            "商品有损坏",
            "requestKey",
            key
        );
    }

    JsonNode expect(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(code);
        return json.readTree(response.body());
    }

    String create(int qty) throws Exception {
        return expect(call("POST", "/aftersales", body(order, item, qty, UUID.randomUUID().toString()), 0), 201)
            .path("id")
            .asString();
    }

    int available() throws Exception {
        return expect(call("GET", "/orders/" + order + "/aftersale-eligibility", null, 0), 200)
            .path("items")
            .get(0)
            .path("availableQuantity")
            .asInt();
    }

    void approve(String id) throws Exception {
        expect(
            call("POST", "/staff/aftersales/" + id + "/review", Map.of("decision", "APPROVED", "note", "同意退回"), 2),
            200
        );
    }

    void receive(String id) throws Exception {
        approve(id);
        expect(
            call(
                "PUT",
                "/aftersales/" + id + "/return-shipment",
                Map.of("carrier", "顺丰速运", "trackingNumber", "SF1234567890"),
                0
            ),
            200
        );
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "实物已核对"), 2), 200);
    }

    String refundPath(String id, String key) {
        return "/staff/aftersales/" + id + "/refunds/" + key;
    }

    @Test
    void successfulRefundIsIdempotentAndCompletedQuantityCannotBeReused() throws Exception {
        String id = create(3),
            key = UUID.randomUUID().toString();
        receive(id);
        var body = Map.of("mode", "SUCCESS");
        var completed = expect(call("PUT", refundPath(id, key), body, 2), 200);
        assertThat(completed.path("status").asString()).isEqualTo("COMPLETED");
        var refund = completed.path("refunds").get(0);
        assertThat(refund.path("amount").asString()).isEqualTo("10.00");
        assertThat(refund.path("operationNumber").asString()).startsWith("SIM-");
        assertThat(refund.path("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(refund.path("updatedAt").asString()).endsWith("Z");
        for (int i = 0; i < 2; i++) {
            var again = expect(call("PUT", refundPath(id, key), body, 2), 200);
            assertThat(again.path("refunds").size()).isEqualTo(1);
            assertThat(again.path("events").size()).isEqualTo(5);
        }
        expect(call("PUT", refundPath(id, key), Map.of("mode", "FAILURE"), 2), 409);
        expect(call("PUT", refundPath(id, UUID.randomUUID().toString()), body, 2), 409);
        expect(call("POST", refundPath(id, key) + "/reconciliation", null, 2), 200);
        expect(call("POST", "/aftersales/" + id + "/cancellation", null, 0), 409);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "实物已核对"), 2), 200);
        expect(call("POST", "/aftersales", body(order, item, 1, UUID.randomUUID().toString()), 0), 409);
        assertThat(available()).isZero();
        var eligibility = expect(call("GET", "/orders/" + order + "/aftersale-eligibility", null, 0), 200);
        assertThat(eligibility.path("items").get(0).path("remainingAmount").asString()).isEqualTo("0.00");
        assertThat(
            expect(call("GET", "/staff/aftersales?status=COMPLETED", null, 2), 200)
                .path("total")
                .asInt()
        ).isEqualTo(1);
        expect(call("GET", "/aftersales/" + id, null, 1), 404);
    }

    @Test
    void timeoutMustBeReconciledBeforeRetryAndBothOutcomesArePersisted() throws Exception {
        for (String mode : List.of("TIMEOUT_SUCCESS", "TIMEOUT_FAILURE")) {
            String id = create(1),
                key = UUID.randomUUID().toString();
            receive(id);
            var pending = expect(call("PUT", refundPath(id, key), Map.of("mode", mode), 2), 200);
            assertThat(pending.path("status").asString()).isEqualTo("REFUND_PENDING");
            assertThat(pending.path("refunds").get(0).path("status").asString()).isEqualTo("UNKNOWN");
            expect(
                call(
                    "PUT",
                    refundPath(id, UUID.randomUUID().toString()),
                    Map.of("mode", "SUCCESS", "previousKey", key),
                    2
                ),
                409
            );
            // 普通详情查询不偷偷推进退款。
            assertThat(
                expect(call("GET", "/aftersales/" + id, null, 0), 200)
                    .path("status")
                    .asString()
            ).isEqualTo("REFUND_PENDING");
            String target = mode.equals("TIMEOUT_SUCCESS") ? "COMPLETED" : "REFUND_FAILED";
            for (int i = 0; i < 2; i++) {
                var result = expect(call("POST", refundPath(id, key) + "/reconciliation", null, 2), 200);
                assertThat(result.path("status").asString()).isEqualTo(target);
                assertThat(result.path("events").size()).isEqualTo(6);
                assertThat(result.path("refunds").size()).isEqualTo(1);
            }
            if (target.equals("REFUND_FAILED")) {
                var result = expect(
                    call(
                        "PUT",
                        refundPath(id, UUID.randomUUID().toString()),
                        Map.of("mode", "SUCCESS", "previousKey", key),
                        2
                    ),
                    200
                );
                assertThat(result.path("status").asString()).isEqualTo("COMPLETED");
                assertThat(result.path("refunds").size()).isEqualTo(2);
                // 旧失败流水的晚到请求只返回现有状态，不能再发起退款。
                assertThat(
                    expect(call("PUT", refundPath(id, key), Map.of("mode", mode), 2), 200)
                        .path("refunds")
                        .size()
                ).isEqualTo(2);
            }
        }
        assertThat(available()).isEqualTo(1);
    }

    @Test
    void concurrentFailedRetriesRequireTheLatestFailedAttempt() throws Exception {
        String id = create(1),
            key = UUID.randomUUID().toString();
        receive(id);
        expect(call("PUT", refundPath(id, key), Map.of("mode", "FAILURE"), 2), 200);
        expect(call("PUT", refundPath(id, UUID.randomUUID().toString()), Map.of("mode", "SUCCESS"), 2), 409);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> retry = () -> {
                gate.await();
                return call(
                    "PUT",
                    refundPath(id, UUID.randomUUID().toString()),
                    Map.of("mode", "FAILURE", "previousKey", key),
                    2
                ).statusCode();
            };
            var first = pool.submit(retry);
            var second = pool.submit(retry);
            gate.countDown();
            assertThat(
                List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(200, 409);
        }
        var detail = expect(call("GET", "/aftersales/" + id, null, 0), 200);
        assertThat(detail.path("refunds").size()).isEqualTo(2);
        assertThat(detail.path("status").asString()).isEqualTo("REFUND_FAILED");
        assertThat(available()).isEqualTo(2);
        String previous = detail.path("refunds").get(0).path("requestKey").asString();
        String successKey = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> submit = () -> {
                gate.await();
                return call(
                    "PUT",
                    refundPath(id, successKey),
                    Map.of("mode", "SUCCESS", "previousKey", previous),
                    2
                ).statusCode();
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            gate.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactly(
                200,
                200
            );
        }
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM aftersale_refund WHERE request_id=? AND status='SUCCEEDED'",
                Integer.class,
                Long.parseLong(id)
            )
        ).isEqualTo(1);
    }

    @Test
    void refundPermissionsStatesAndValidationCannotBeBypassed() throws Exception {
        String id = create(1),
            key = UUID.randomUUID().toString();
        String path = refundPath(id, key);
        var body = Map.of("mode", "SUCCESS");
        expect(call("PUT", path, body, -1), 401);
        expect(call("PUT", path, body, 0), 403);
        expect(call("POST", path + "/reconciliation", null, 0), 403);
        expect(call("PUT", path, body, 2), 409);
        expect(call("PUT", path, Map.of("mode", "INVALID"), 2), 400);
        expect(call("PUT", path, Map.of(), 2), 400);
        expect(call("PUT", refundPath(id, "bad"), body, 2), 400);
        receive(id);
        expect(call("POST", path + "/reconciliation", null, 2), 404);
        String own = expect(
            call("POST", "/aftersales", body(staffOrder, staffItem, 1, UUID.randomUUID().toString()), 2),
            201
        )
            .path("id")
            .asString();
        expect(call("PUT", refundPath(own, key), body, 2), 403);
        expect(call("POST", refundPath(own, key) + "/reconciliation", null, 2), 403);
    }

    @Test
    void partialRefundsSettleRoundingAndNeverExceedPaidAmount() throws Exception {
        String first = create(1);
        receive(first);
        expect(call("PUT", refundPath(first, UUID.randomUUID().toString()), Map.of("mode", "SUCCESS"), 2), 200);
        String second = create(2);
        receive(second);
        var completed = expect(
            call("PUT", refundPath(second, UUID.randomUUID().toString()), Map.of("mode", "SUCCESS"), 2),
            200
        );
        assertThat(completed.path("refunds").get(0).path("amount").asString()).isEqualTo("6.67");
        assertThat(
            jdbc.queryForObject(
                "SELECT SUM(r.amount) FROM aftersale_refund r JOIN aftersale_request a ON a.id=r.request_id WHERE a.order_id=? AND r.status='SUCCEEDED'",
                java.math.BigDecimal.class,
                order
            )
        ).isEqualByComparingTo("10.00");
        assertThat(available()).isZero();
    }

    @Test
    void returnShipmentAndReceiptAreIdempotentAndKeepReservation() throws Exception {
        String id = create(3);
        var shipment = Map.of("carrier", "顺丰速运", "trackingNumber", "SF1234567890");
        expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 0), 409);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "收货"), 2), 409);
        approve(id);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "收货"), 2), 409);
        var registered = expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 0), 200);
        assertThat(registered.path("status").asString()).isEqualTo("RETURN_SHIPPED");
        assertThat(registered.path("returnShipment").path("carrier").asString()).isEqualTo("顺丰速运");
        assertThat(registered.path("returnShipment").path("registeredAt").asString()).endsWith("Z");
        assertThat(registered.path("receipt").isNull()).isTrue();
        assertThat(
            expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 0), 200)
                .path("events")
                .size()
        ).isEqualTo(3);
        expect(
            call(
                "PUT",
                "/aftersales/" + id + "/return-shipment",
                Map.of("carrier", "顺丰速运", "trackingNumber", "SF9999999999"),
                0
            ),
            409
        );
        assertThat(available()).isZero();
        var received = expect(
            call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "商品数量和配件已核对"), 2),
            200
        );
        assertThat(received.path("status").asString()).isEqualTo("RETURN_RECEIVED");
        assertThat(received.path("receipt").path("receivedAt").asString()).endsWith("Z");
        assertThat(received.path("receipt").path("note").asString()).isEqualTo("商品数量和配件已核对");
        assertThat(
            expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "商品数量和配件已核对"), 2), 200)
                .path("events")
                .size()
        ).isEqualTo(4);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "不同备注"), 2), 409);
        assertThat(
            expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 0), 200)
                .path("status")
                .asString()
        ).isEqualTo("RETURN_RECEIVED");
        expect(call("POST", "/aftersales/" + id + "/cancellation", null, 0), 409);
        expect(call("POST", "/aftersales", body(order, item, 1, UUID.randomUUID().toString()), 0), 409);
        assertThat(available()).isZero();
        var eligibility = expect(call("GET", "/orders/" + order + "/aftersale-eligibility", null, 0), 200);
        assertThat(eligibility.path("items").get(0).path("remainingAmount").asString()).isEqualTo("0.00");
        assertThat(
            expect(call("GET", "/orders/" + order, null, 0), 200)
                .path("items")
                .get(0)
                .path("availableAftersalesQuantity")
                .asInt()
        ).isZero();
        assertThat(
            expect(call("GET", "/staff/aftersales?status=RETURN_RECEIVED", null, 2), 200)
                .path("total")
                .asInt()
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "SELECT actor_id FROM aftersale_event WHERE request_id=? AND action='RETURN_RECEIVED'",
                Long.class,
                Long.parseLong(id)
            )
        ).isEqualTo(users.get(2));
    }

    @Test
    void returnPermissionsAndInputValidation() throws Exception {
        String id = create(1);
        approve(id);
        var shipment = Map.of("carrier", "顺丰速运", "trackingNumber", "SF1234567890");
        expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, -1), 401);
        expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 1), 404);
        expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 2), 404);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "已收货"), 0), 403);
        for (var invalid : List.of(
            Map.of("carrier", " ", "trackingNumber", "SF1234567890"),
            Map.of("carrier", "顺丰速运", "trackingNumber", "abc"),
            Map.of("carrier", "顺丰速运", "trackingNumber", "https://evil.test")
        ))
            expect(call("PUT", "/aftersales/" + id + "/return-shipment", invalid, 0), 400);
        expect(call("PUT", "/aftersales/" + id + "/return-shipment", shipment, 0), 200);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "   "), 2), 400);
        expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "字".repeat(1001)), 2), 400);
        assertThat(
            expect(call("GET", "/aftersales/" + id, null, 0), 200)
                .path("status")
                .asString()
        ).isEqualTo("RETURN_SHIPPED");
        expect(call("GET", "/aftersales/" + id, null, 1), 404);
        // 设置自有申请的已审核测试夹具，避免通过审核接口绕过既有自审限制。
        String own = expect(
            call("POST", "/aftersales", body(staffOrder, staffItem, 1, UUID.randomUUID().toString()), 2),
            201
        )
            .path("id")
            .asString();
        jdbc.update("UPDATE aftersale_request SET status='APPROVED' WHERE id=?", Long.parseLong(own));
        expect(call("PUT", "/aftersales/" + own + "/return-shipment", shipment, 2), 200);
        expect(call("PUT", "/staff/aftersales/" + own + "/receipt", Map.of("note", "自己收货"), 2), 403);
    }

    @Test
    void concurrentReturnChangesHaveOneWinnerAndReceiptRetryCreatesOneEvent() throws Exception {
        String id = create(1);
        approve(id);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> {
                gate.await();
                return call(
                    "PUT",
                    "/aftersales/" + id + "/return-shipment",
                    Map.of("carrier", "顺丰速运", "trackingNumber", "SF11111111"),
                    0
                ).statusCode();
            });
            var second = pool.submit(() -> {
                gate.await();
                return call(
                    "PUT",
                    "/aftersales/" + id + "/return-shipment",
                    Map.of("carrier", "顺丰速运", "trackingNumber", "SF22222222"),
                    0
                ).statusCode();
            });
            gate.countDown();
            assertThat(
                List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(200, 409);
        }
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> receipt = () -> {
                gate.await();
                return call(
                    "PUT",
                    "/staff/aftersales/" + id + "/receipt",
                    Map.of("note", "核对实物已收货"),
                    2
                ).statusCode();
            };
            var first = pool.submit(receipt);
            var second = pool.submit(receipt);
            gate.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactly(
                200,
                200
            );
        }
        assertThat(
            expect(call("GET", "/aftersales/" + id, null, 0), 200)
                .path("events")
                .size()
        ).isEqualTo(4);
        assertThat(available()).isEqualTo(2);
    }

    @Test
    void cancelledAndRejectedRequestsCannotRegisterReturns() throws Exception {
        String cancelled = create(1),
            rejected = create(1);
        expect(call("POST", "/aftersales/" + cancelled + "/cancellation", null, 0), 200);
        expect(
            call(
                "POST",
                "/staff/aftersales/" + rejected + "/review",
                Map.of("decision", "REJECTED", "note", "拒绝申请"),
                2
            ),
            200
        );
        for (String id : List.of(cancelled, rejected)) {
            expect(
                call(
                    "PUT",
                    "/aftersales/" + id + "/return-shipment",
                    Map.of("carrier", "顺丰速运", "trackingNumber", "SF1234567890"),
                    0
                ),
                409
            );
            expect(call("PUT", "/staff/aftersales/" + id + "/receipt", Map.of("note", "收货"), 2), 409);
        }
        assertThat(available()).isEqualTo(3);
    }

    @Test
    void ownershipRolesAndInputValidation() throws Exception {
        expect(call("GET", "/aftersales", null, -1), 401);
        expect(call("GET", "/orders/" + foreign + "/aftersale-eligibility", null, 0), 404);
        expect(call("GET", "/staff/aftersales", null, 0), 403);
        String id = create(1);
        expect(call("GET", "/aftersales/" + id, null, 1), 404);
        expect(call("POST", "/aftersales/" + id + "/cancellation", null, 1), 404);
        expect(
            call("POST", "/staff/aftersales/" + id + "/review", Map.of("decision", "APPROVED", "note", "同意"), 0),
            403
        );
        expect(call("POST", "/aftersales", body(order, item, 0, UUID.randomUUID().toString()), 0), 400);
        expect(call("POST", "/aftersales", body(order, itemOf(foreign), 1, UUID.randomUUID().toString()), 0), 404);
        expect(call("GET", "/aftersales?page=0", null, 0), 400);
        expect(call("GET", "/aftersales?status=UNKNOWN", null, 0), 400);
        expect(call("GET", "/aftersales/9999999999999999999", null, 0), 400);
    }

    @Test
    void eligibilityExpiryStateAndZeroAmount() throws Exception {
        var result = expect(call("GET", "/orders/" + expired + "/aftersale-eligibility", null, 0), 200);
        assertThat(result.path("items").get(0).path("eligible").asBoolean()).isFalse();
        expect(call("POST", "/aftersales", body(expired, itemOf(expired), 1, UUID.randomUUID().toString()), 0), 409);
        jdbc.update("UPDATE trade_order SET status='SHIPPED',signed_at=NULL WHERE id=?", order);
        expect(call("POST", "/aftersales", body(order, item, 1, UUID.randomUUID().toString()), 0), 409);
        jdbc.update(
            "UPDATE trade_order SET status='COMPLETED',signed_at=UTC_TIMESTAMP()-INTERVAL 1 DAY WHERE id=?",
            order
        );
        jdbc.update("UPDATE order_item SET paid_amount=0 WHERE id=?", item);
        expect(call("POST", "/aftersales", body(order, item, 1, UUID.randomUUID().toString()), 0), 409);
    }

    @Test
    void idempotencyCancellationAndQuantityRelease() throws Exception {
        var body = body(order, item, 2, UUID.randomUUID().toString());
        var first = expect(call("POST", "/aftersales", body, 0), 201);
        var again = expect(call("POST", "/aftersales", body, 0), 201);
        assertThat(again.path("id").asString()).isEqualTo(first.path("id").asString());
        assertThat(again.path("events").size()).isEqualTo(1);
        assertThat(available()).isEqualTo(1);
        expect(call("POST", "/aftersales", body(order, item, 1, (String) body.get("requestKey")), 0), 409);
        String id = first.path("id").asString();
        expect(call("POST", "/aftersales/" + id + "/cancellation", null, 0), 200);
        var cancelled = expect(call("POST", "/aftersales/" + id + "/cancellation", null, 0), 200);
        assertThat(cancelled.path("events").size()).isEqualTo(2);
        assertThat(available()).isEqualTo(3);
        assertThat(
            expect(call("POST", "/aftersales", body, 0), 201)
                .path("status")
                .asString()
        ).isEqualTo("CANCELLED");
        var details = expect(call("GET", "/orders/" + order, null, 0), 200);
        assertThat(details.path("items").get(0).path("availableAftersalesQuantity").asInt()).isEqualTo(3);
    }

    @Test
    void reviewAuditAndRoundingNeverOverRefund() throws Exception {
        String a = create(1),
            b = create(2);
        assertThat(available()).isZero();
        assertThat(
            jdbc.queryForObject(
                "SELECT SUM(amount) FROM aftersale_request WHERE order_id=?",
                java.math.BigDecimal.class,
                order
            )
        ).isEqualByComparingTo("10.00");
        var approve = Map.of("decision", "APPROVED", "note", "同意申请，请等待退货指引");
        var approved = expect(call("POST", "/staff/aftersales/" + a + "/review", approve, 2), 200);
        assertThat(approved.path("status").asString()).isEqualTo("APPROVED");
        assertThat(
            expect(call("POST", "/staff/aftersales/" + a + "/review", approve, 2), 200)
                .path("events")
                .size()
        ).isEqualTo(2);
        expect(call("POST", "/aftersales/" + a + "/cancellation", null, 0), 409);
        expect(
            call("POST", "/staff/aftersales/" + a + "/review", Map.of("decision", "REJECTED", "note", "变更"), 2),
            409
        );
        expect(
            call("POST", "/staff/aftersales/" + b + "/review", Map.of("decision", "REJECTED", "note", "凭据不足"), 2),
            200
        );
        assertThat(available()).isEqualTo(2);
        String replacement = create(2);
        assertThat(
            expect(call("GET", "/aftersales/" + replacement, null, 0), 200)
                .path("amount")
                .asString()
        ).isEqualTo("6.67");
    }

    @Test
    void staffCannotReviewOwnApplicationAndListIsScoped() throws Exception {
        create(1);
        String own = expect(
            call("POST", "/aftersales", body(staffOrder, staffItem, 1, UUID.randomUUID().toString()), 2),
            201
        )
            .path("id")
            .asString();
        expect(
            call("POST", "/staff/aftersales/" + own + "/review", Map.of("decision", "APPROVED", "note", "自己审核"), 2),
            403
        );
        assertThat(
            expect(call("GET", "/aftersales", null, 1), 200)
                .path("total")
                .asLong()
        ).isZero();
        assertThat(
            expect(call("GET", "/staff/aftersales?status=PENDING", null, 2), 200)
                .path("total")
                .asLong()
        ).isEqualTo(2);
        assertThat(
            expect(call("GET", "/aftersales?page=999", null, 0), 200)
                .path("items")
                .size()
        ).isZero();
        assertThat(
            expect(call("GET", "/aftersales?page=999", null, 0), 200)
                .path("total")
                .asLong()
        ).isEqualTo(1);
    }

    @Test
    void eligibleItemsFiltersOwnershipExpiryAndOccupiedQuantities() throws Exception {
        String request = create(1);
        var result = expect(call("GET", "/aftersale-eligible-items", null, 0), 200);
        assertThat(result.path("items").size()).isEqualTo(1);
        var first = result.path("items").get(0);
        assertThat(first.path("orderId").asString()).isEqualTo(String.valueOf(order));
        assertThat(first.path("availableQuantity").asInt()).isEqualTo(2);
        var single = expect(call("GET", "/orders/" + order + "/aftersale-eligibility", null, 0), 200)
            .path("items")
            .get(0);
        assertThat(first.path("remainingAmount").asString()).isEqualTo(single.path("remainingAmount").asString());
        create(2);
        assertThat(
            expect(call("GET", "/aftersale-eligible-items", null, 0), 200)
                .path("items")
                .size()
        ).isZero();
        expect(call("POST", "/aftersales/" + request + "/cancellation", null, 0), 200);
        assertThat(
            expect(call("GET", "/aftersale-eligible-items", null, 0), 200)
                .path("items")
                .get(0)
                .path("availableQuantity")
                .asInt()
        ).isEqualTo(1);
        expect(call("GET", "/aftersale-eligible-items?page=0", null, 0), 400);
        expect(call("GET", "/aftersale-eligible-items", null, -1), 401);
    }

    @Test
    void eligibleItemsPaginatesWithoutTruncatingOrRepeatingRows() throws Exception {
        for (int i = 0; i < 12; i++) makeOrder(users.get(0), 1);
        var first = expect(call("GET", "/aftersale-eligible-items?page=1", null, 0), 200);
        var second = expect(call("GET", "/aftersale-eligible-items?page=2", null, 0), 200);
        assertThat(first.path("items").size()).isEqualTo(10);
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        assertThat(first.path("nextPage").asInt()).isEqualTo(2);
        assertThat(second.path("items").size()).isEqualTo(3);
        assertThat(second.path("hasMore").asBoolean()).isFalse();
        var ids = new HashSet<String>();
        first.path("items").forEach(row -> assertThat(ids.add(row.path("orderItemId").asString())).isTrue());
        second.path("items").forEach(row -> assertThat(ids.add(row.path("orderItemId").asString())).isTrue());
    }

    byte[] photo(int color) throws Exception {
        var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    HttpResponse<String> uploadPhoto(String id, byte[] bytes, int actor) throws Exception {
        String boundary = "boundary-evidence-test";
        var body = new java.io.ByteArrayOutputStream();
        body.write(
            (
                "--" +
                boundary +
                "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n"
            ).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var builder = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api/aftersales/" + id + "/evidence")
        )
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (actor >= 0) builder.header("Authorization", "Bearer " + tokens.get(actor));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void evidenceRequiresOwnershipAndRemainsReadableAfterReview() throws Exception {
        String id = create(1);
        byte[] photo = photo(100);
        expect(uploadPhoto(id, photo, -1), 401);
        expect(uploadPhoto(id, photo, 1), 404);
        String imageId = expect(uploadPhoto(id, photo, 0), 200)
            .path("id")
            .asString();
        assertThat(
            expect(uploadPhoto(id, photo, 0), 200)
                .path("id")
                .asString()
        ).isEqualTo(imageId);
        expect(call("GET", "/aftersales/" + id + "/evidence", null, 1), 404);
        expect(call("GET", "/staff/aftersales/" + id + "/evidence", null, 0), 403);
        assertThat(expect(call("GET", "/staff/aftersales/" + id + "/evidence", null, 2), 200).size()).isEqualTo(1);
        String contentPath = "/aftersales/" + id + "/evidence/" + imageId + "/content";
        expect(call("GET", contentPath, null, 1), 404);
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/staff" + contentPath))
                    .header("Authorization", "Bearer " + tokens.get(2))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("image/png");
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body())).getWidth()
            ).isEqualTo(4);
        }
        String other = create(1);
        expect(call("GET", "/aftersales/" + other + "/evidence/" + imageId + "/content", null, 0), 404);
        approve(id);
        expect(uploadPhoto(id, photo(200), 0), 409);
        expect(uploadPhoto(id, photo, 0), 200);
        assertThat(expect(call("GET", "/aftersales/" + id + "/evidence", null, 0), 200).size()).isEqualTo(1);
    }

    @Test
    void evidenceRejectsInvalidFilesAndConcurrentOverflow() throws Exception {
        String id = create(1);
        expect(uploadPhoto(id, "<svg>not a PNG</svg>".getBytes(), 0), 400);
        expect(uploadPhoto(id, new byte[0], 0), 400);
        expect(uploadPhoto(id, new byte[5 * 1024 * 1024 + 1], 0), 413);
        for (int i = 0; i < 4; i++) expect(uploadPhoto(id, photo(i), 0), 200);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> {
                gate.await();
                return uploadPhoto(id, photo(101), 0).statusCode();
            });
            var second = pool.submit(() -> {
                gate.await();
                return uploadPhoto(id, photo(102), 0).statusCode();
            });
            gate.countDown();
            assertThat(
                List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(expect(call("GET", "/aftersales/" + id + "/evidence", null, 0), 200).size()).isEqualTo(5);
    }

    @Test
    void concurrentRequestsCannotOverAllocate() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> submit = () -> {
                gate.await();
                return call("POST", "/aftersales", body(order, item, 2, UUID.randomUUID().toString()), 0).statusCode();
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            gate.countDown();
            assertThat(
                List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(available()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyCreatesOneRequest() throws Exception {
        var data = body(order, item, 1, UUID.randomUUID().toString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<String> submit = () -> {
                gate.await();
                return expect(call("POST", "/aftersales", data, 0), 201)
                    .path("id")
                    .asString();
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            gate.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(available()).isEqualTo(2);
    }

    @Test
    void reviewAndCancellationRaceHasOneWinner() throws Exception {
        String id = create(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var cancel = pool.submit(() -> {
                gate.await();
                return call("POST", "/aftersales/" + id + "/cancellation", null, 0).statusCode();
            });
            var review = pool.submit(() -> {
                gate.await();
                return call(
                    "POST",
                    "/staff/aftersales/" + id + "/review",
                    Map.of("decision", "APPROVED", "note", "审核通过"),
                    2
                ).statusCode();
            });
            gate.countDown();
            assertThat(
                List.of(cancel.get(15, TimeUnit.SECONDS), review.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(
            expect(call("GET", "/aftersales/" + id, null, 0), 200)
                .path("events")
                .size()
        ).isEqualTo(2);
    }
}
