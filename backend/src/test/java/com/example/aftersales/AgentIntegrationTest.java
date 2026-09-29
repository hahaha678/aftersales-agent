package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.aftersales.agent.domain.po.RunPO;
import com.example.aftersales.agent.service.ModelGateway;
import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.security.TokenCodec;
import com.example.aftersales.identity.service.SessionService;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
        "app.auth.cache.namespace=aftersales-agent-it",
    }
)
class AgentIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService auth;

    @Autowired
    StringRedisTemplate redis;

    @MockitoBean
    ModelGateway model;

    final JsonMapper json = JsonMapper.builder().build();
    final List<Long> users = new ArrayList<>();
    final List<String> tokens = new ArrayList<>();
    long order, item, foreign;
    String conversation;
    final AtomicInteger invocations = new AtomicInteger();
    volatile String draftId;
    CountDownLatch entered = new CountDownLatch(1),
        release = new CountDownLatch(1);

    @BeforeEach
    void fixture() throws Exception {
        for (int i = 0; i < 2; i++) {
            String name = "agent_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,'CUSTOMER')",
                name,
                "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80",
                "Agent 测试"
            );
            users.add(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, name));
            tokens.add(auth.login(new CreateSessionDTO(name, "DemoPass123!")).accessToken());
        }
        order = makeOrder(users.get(0));
        foreign = makeOrder(users.get(1));
        item = jdbc.queryForObject("SELECT id FROM order_item WHERE order_id=?", Long.class, order);
        when(model.available()).thenReturn(true);
        when(model.model()).thenReturn("local-test");
        doAnswer(invocation -> {
            invocations.incrementAndGet();
            String message = invocation.getArgument(1);
            List<ToolCallback> tools = invocation.getArgument(2);
            Consumer<ModelGateway.Chunk> sink = invocation.getArgument(3);
            assertThat(tools.stream().map(t -> t.getToolDefinition().name())).containsExactly(
                "searchPolicies",
                "listMyOrders",
                "getMyOrder",
                "getMyShipments",
                "getAftersaleEligibility",
                "listMyAftersales",
                "getMyAftersale",
                "createAftersaleDraft"
            );
            if (message.equals("wait") || message.equals("revoked")) {
                entered.countDown();
                release.await(15, TimeUnit.SECONDS);
            }
            if (message.equals("revoked")) tool(tools, "getMyOrder").call("{\"id\":\"" + order + "\"}");
            if (message.equals("fail")) throw new IllegalStateException("provider-secret-must-not-leak");
            if (message.equals("draft") || message.equals("draft-second")) {
                long targetItem = message.equals("draft-second")
                    ? jdbc.queryForObject(
                          "SELECT id FROM order_item WHERE order_id=? AND sku_id=102",
                          Long.class,
                          order
                      )
                    : item;
                String result = tool(tools, "createAftersaleDraft").call(
                    json.writeValueAsString(
                        Map.of(
                            "orderId",
                            "" + order,
                            "orderItemId",
                            "" + targetItem,
                            "quantity",
                            1,
                            "reason",
                            "QUALITY",
                            "description",
                            "商品破损"
                        )
                    )
                );
                draftId = json.readTree(result).path("id").asString();
                assertThat(draftId).isNotBlank();
            }
            if (message.equals("order-number")) {
                String number = jdbc.queryForObject(
                    "SELECT order_number FROM trade_order WHERE id=?",
                    String.class,
                    order
                );
                String input = json.writeValueAsString(Map.of("id", number));
                var detail = json.readTree(tool(tools, "getMyOrder").call(input));
                assertThat(detail.path("id").asString()).isEqualTo("" + order);
                assertThat(detail.path("orderNumber").asString()).isEqualTo(number);
                assertThat(json.readTree(tool(tools, "getMyShipments").call(input)).isArray()).isTrue();
                assertThat(
                    json.readTree(tool(tools, "getAftersaleEligibility").call(input)).path("orderId").asString()
                ).isEqualTo("" + order);
                String other = jdbc.queryForObject(
                    "SELECT order_number FROM trade_order WHERE id=?",
                    String.class,
                    foreign
                );
                assertThat(tool(tools, "getMyOrder").call(json.writeValueAsString(Map.of("id", other))))
                    .contains("RESOURCE_NOT_FOUND")
                    .doesNotContain("private product");
                assertThat(tool(tools, "getMyOrder").call("{\"id\":\"DEMO-DOES-NOT-EXIST\"}")).contains(
                    "RESOURCE_NOT_FOUND"
                );
            }
            if (message.equals("foreign")) {
                String result = tool(tools, "getMyOrder").call("{\"id\":\"" + foreign + "\"}");
                assertThat(result).contains("RESOURCE_NOT_FOUND").doesNotContain("private product");
            }
            if (message.equals("budget")) for (int i = 0; i < 9; i++) tool(tools, "getMyOrder").call(
                "{\"id\":\"" + order + "\"}"
            );
            if (message.equals("invalid")) assertThat(tool(tools, "listMyOrders").call("{\"page\":0}")).contains(
                "INVALID_REQUEST"
            );
            if (message.equals("history")) {
                List<RunPO> rows = invocation.getArgument(0);
                assertThat(rows).hasSize(1);
                assertThat(rows.get(0).userContent()).isEqualTo("hello");
            }
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            sink.accept(new ModelGateway.Chunk("查询完成。", 12, 5));
            return null;
        })
            .when(model)
            .stream(anyList(), anyString(), anyList(), any());
        conversation = expect(call("POST", "/conversations", Map.of("title", "售后咨询"), 0), 201)
            .path("id")
            .asString();
    }

    static ToolCallback tool(List<ToolCallback> tools, String name) {
        return tools
            .stream()
            .filter(t -> t.getToolDefinition().name().equals(name))
            .findFirst()
            .orElseThrow();
    }

    long makeOrder(long user) {
        String number = "AGENT-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO trade_order(user_id,order_number,status,paid_amount,created_at,paid_at,signed_at) VALUES(?,?,'COMPLETED',10,UTC_TIMESTAMP()-INTERVAL 3 DAY,UTC_TIMESTAMP()-INTERVAL 3 DAY,UTC_TIMESTAMP()-INTERVAL 1 DAY)",
            user,
            number
        );
        long id = jdbc.queryForObject("SELECT id FROM trade_order WHERE order_number=?", Long.class, number);
        jdbc.update(
            "INSERT INTO order_item(order_id,sku_id,product_name,quantity,paid_amount) VALUES(?,101,'private product',3,10)",
            id
        );
        return id;
    }

    @AfterEach
    void cleanup() throws Exception {
        release.countDown();
        for (long user : users) {
            var runs = jdbc.queryForList(
                "SELECT id FROM agent_run WHERE user_id=? AND status='RUNNING'",
                String.class,
                user
            );
            for (String id : runs) call("POST", "/agent-runs/" + id + "/cancellation", null, users.indexOf(user));
            jdbc.update("DELETE FROM aftersale_draft WHERE user_id=?", user);
            jdbc.update("DELETE t FROM agent_tool_call t JOIN agent_run r ON r.id=t.run_id WHERE r.user_id=?", user);
            jdbc.update(
                "DELETE m FROM conversation_message m JOIN conversation c ON c.id=m.conversation_id WHERE c.user_id=?",
                user
            );
            jdbc.update("DELETE FROM agent_run WHERE user_id=?", user);
            jdbc.update("DELETE FROM conversation WHERE user_id=?", user);
            jdbc.update(
                "DELETE e FROM aftersale_event e JOIN aftersale_request a ON a.id=e.request_id WHERE a.user_id=?",
                user
            );
            jdbc.update("DELETE FROM aftersale_request WHERE user_id=?", user);
            jdbc.update("DELETE i FROM order_item i JOIN trade_order o ON o.id=i.order_id WHERE o.user_id=?", user);
            jdbc.update("DELETE FROM trade_order WHERE user_id=?", user);
            jdbc.update("DELETE FROM user_session WHERE user_id=?", user);
            jdbc.update("DELETE FROM app_user WHERE id=?", user);
        }
        for (String token : tokens) {
            String prefix = "aftersales-agent-it:{" + TokenCodec.hash(token) + "}:";
            redis.delete(List.of(prefix + "session", prefix + "revoked"));
        }
    }

    HttpResponse<String> call(String method, String path, Object body, int actor) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path)).timeout(
            Duration.ofSeconds(20)
        );
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

    JsonNode expect(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(code);
        return json.readTree(response.body());
    }

    Map<String, String> message(String content, String key) {
        return Map.of("content", content, "requestKey", key);
    }

    String send(String content) throws Exception {
        return expect(
            call(
                "POST",
                "/conversations/" + conversation + "/messages",
                message(content, UUID.randomUUID().toString()),
                0
            ),
            202
        )
            .path("id")
            .asString();
    }

    JsonNode await(String id) throws Exception {
        for (int i = 0; i < 100; i++) {
            var row = expect(call("GET", "/agent-runs/" + id, null, 0), 200);
            if (!row.path("status").asString().equals("RUNNING")) return row;
            Thread.sleep(50);
        }
        throw new AssertionError("run did not finish");
    }

    String draft() throws Exception {
        assertThat(await(send("draft")).path("status").asString()).isEqualTo("SUCCEEDED");
        return draftId;
    }

    @Test
    void historySseIdempotencyAndOwnership() throws Exception {
        var body = message("hello", UUID.randomUUID().toString());
        String path = "/conversations/" + conversation + "/messages";
        String id = expect(call("POST", path, body, 0), 202)
            .path("id")
            .asString();
        assertThat(await(id).path("inputTokens").asInt()).isEqualTo(12);
        assertThat(
            expect(call("POST", path, body, 0), 202)
                .path("id")
                .asString()
        ).isEqualTo(id);
        assertThat(invocations.get()).isEqualTo(1);
        expect(call("POST", path, message("different", body.get("requestKey")), 0), 409);
        var stream = call("GET", "/agent-runs/" + id + "/events", null, 0);
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.body()).contains("event:done", "查询完成");
        assertThat(expect(call("GET", path, null, 0), 200).size()).isEqualTo(2);
        for (String other : List.of(
            path,
            "/agent-runs/" + id,
            "/agent-runs/" + id + "/events",
            "/conversations/" + conversation + "/drafts"
        ))
            expect(call("GET", other, null, 1), 404);
        expect(call("GET", path, null, -1), 401);
        assertThat(await(send("history")).path("status").asString()).isEqualTo("SUCCEEDED");
    }

    @Test
    void sameConversationKeepsFirstConfirmedAndSecondReadyDraft() throws Exception {
        jdbc.update(
            "INSERT INTO order_item(order_id,sku_id,product_name,quantity,paid_amount) VALUES(?,102,'第二件商品',2,70)",
            order
        );
        jdbc.update("UPDATE trade_order SET paid_amount=80 WHERE id=?", order);
        String first = draft();
        expect(
            call("POST", "/aftersale-drafts/" + first + "/confirmation", Map.of("version", 1, "confirmed", true), 0),
            200
        );
        assertThat(await(send("draft-second")).path("status").asString()).isEqualTo("SUCCEEDED");
        String second = draftId;
        assertThat(second).isNotEqualTo(first);
        var cards = expect(call("GET", "/conversations/" + conversation + "/drafts", null, 0), 200);
        assertThat(cards.size()).isEqualTo(2);
        assertThat(cards.get(0).path("id").asString()).isEqualTo(second);
        assertThat(cards.get(0).path("status").asString()).isEqualTo("READY");
        assertThat(cards.get(0).path("productName").asString()).isEqualTo("第二件商品");
        assertThat(cards.get(1).path("id").asString()).isEqualTo(first);
        assertThat(cards.get(1).path("status").asString()).isEqualTo("CONFIRMED");
        expect(call("GET", "/conversations/" + conversation + "/drafts", null, 1), 404);
        var confirmation = expect(
            call("POST", "/aftersale-drafts/" + second + "/confirmation", Map.of("version", 1, "confirmed", true), 0),
            200
        );
        assertThat(confirmation.path("confirmed").asBoolean()).isTrue();
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM aftersale_request WHERE order_id=?", Integer.class, order)
        ).isEqualTo(2);
    }

    @Test
    void toolsPreserveOwnershipValidationAndBudget() throws Exception {
        assertThat(await(send("foreign")).path("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(await(send("invalid")).path("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(await(send("budget")).path("status").asString()).isEqualTo("FAILED");
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_tool_call t JOIN agent_run r ON r.id=t.run_id WHERE r.user_id=?",
                Integer.class,
                users.get(0)
            )
        ).isEqualTo(10);
    }

    @Test
    void resolvesFullOrderNumbersForAllOrderTools() throws Exception {
        assertThat(await(send("order-number")).path("status").asString()).isEqualTo("SUCCEEDED");
    }

    @Test
    void messageCursorUsesNumericOrderAcrossDigitBoundaries() throws Exception {
        long max = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM conversation_message", Long.class);
        long boundary = 10;
        while (boundary <= max + 100) boundary *= 10;
        long start = boundary - 30;
        for (int i = 0; i < 30; i++) {
            String id = UUID.randomUUID().toString();
            jdbc.update(
                "INSERT INTO agent_run(id,conversation_id,user_id,request_key,status,user_content,assistant_content,model,expires_at) VALUES(?,?,?,?,'SUCCEEDED','question','answer','test',UTC_TIMESTAMP())",
                id,
                conversation,
                users.get(0),
                id
            );
            jdbc.update(
                "INSERT INTO conversation_message(id,conversation_id,run_id,role,content,status) VALUES(?,?,?,'USER',?,'SUCCEEDED'),(?,?,?,'ASSISTANT',?,'SUCCEEDED')",
                start + i * 2,
                conversation,
                id,
                "question " + i,
                start + i * 2 + 1,
                conversation,
                id,
                "answer " + i
            );
        }
        var latest = expect(call("GET", "/conversations/" + conversation + "/messages", null, 0), 200);
        assertThat(latest.size()).isEqualTo(50);
        for (int i = 0; i < 50; i++) assertThat(latest.get(i).path("id").asString()).isEqualTo("" + (start + 10 + i));
        var earlier = expect(
            call("GET", "/conversations/" + conversation + "/messages?before=" + (start + 10), null, 0),
            200
        );
        assertThat(earlier.size()).isEqualTo(10);
        for (int i = 0; i < 10; i++) assertThat(earlier.get(i).path("id").asString()).isEqualTo("" + (start + i));
    }

    @Test
    void failuresCancellationAndMissingConfiguration() throws Exception {
        var failed = await(send("fail"));
        assertThat(failed.path("status").asString()).isEqualTo("FAILED");
        assertThat(failed.toString()).doesNotContain("provider-secret");
        String id = send("wait");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        expect(
            call(
                "POST",
                "/conversations/" + conversation + "/messages",
                message("another", UUID.randomUUID().toString()),
                0
            ),
            409
        );
        expect(call("POST", "/agent-runs/" + id + "/cancellation", null, 1), 404);
        assertThat(
            expect(call("POST", "/agent-runs/" + id + "/cancellation", null, 0), 200)
                .path("status")
                .asString()
        ).isEqualTo("CANCELLED");
        release.countDown();
        when(model.available()).thenReturn(false);
        expect(
            call(
                "POST",
                "/conversations/" + conversation + "/messages",
                message("disabled", UUID.randomUUID().toString()),
                0
            ),
            503
        );
    }

    @Test
    void draftRequiresExplicitConfirmationAndIsIdempotentUnderConcurrency() throws Exception {
        String id = draft();
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM aftersale_request WHERE user_id=?", Integer.class, users.get(0))
        ).isZero();
        expect(call("GET", "/aftersale-drafts/" + id, null, 1), 404);
        expect(
            call("POST", "/aftersale-drafts/" + id + "/confirmation", Map.of("version", 1, "confirmed", true), 1),
            404
        );
        expect(
            call("POST", "/aftersale-drafts/" + id + "/confirmation", Map.of("version", 1, "confirmed", false), 0),
            400
        );
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<JsonNode> submit = () ->
                expect(
                    call(
                        "POST",
                        "/aftersale-drafts/" + id + "/confirmation",
                        Map.of("version", 1, "confirmed", true),
                        0
                    ),
                    200
                );
            var a = pool.submit(submit);
            var b = pool.submit(submit);
            assertThat(a.get(10, TimeUnit.SECONDS).path("application").path("id").asString()).isEqualTo(
                b.get(10, TimeUnit.SECONDS).path("application").path("id").asString()
            );
        }
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM aftersale_request WHERE user_id=?", Integer.class, users.get(0))
        ).isEqualTo(1);
    }

    @Test
    void changedQuoteRequiresANewConfirmationAndExpiredDraftCannotSubmit() throws Exception {
        String id = draft();
        expect(
            call(
                "POST",
                "/aftersales",
                Map.of(
                    "orderId",
                    "" + order,
                    "orderItemId",
                    "" + item,
                    "quantity",
                    1,
                    "reason",
                    "QUALITY",
                    "description",
                    "manual",
                    "requestKey",
                    UUID.randomUUID().toString()
                ),
                0
            ),
            201
        );
        var updated = expect(
            call("POST", "/aftersale-drafts/" + id + "/confirmation", Map.of("version", 1, "confirmed", true), 0),
            200
        );
        assertThat(updated.path("confirmed").asBoolean()).isFalse();
        assertThat(updated.path("draft").path("version").asInt()).isEqualTo(2);
        expect(
            call("POST", "/aftersale-drafts/" + id + "/confirmation", Map.of("version", 1, "confirmed", true), 0),
            409
        );
        assertThat(
            expect(
                call("POST", "/aftersale-drafts/" + id + "/confirmation", Map.of("version", 2, "confirmed", true), 0),
                200
            )
                .path("confirmed")
                .asBoolean()
        ).isTrue();
        String expired = draft();
        jdbc.update("UPDATE aftersale_draft SET expires_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE id=?", expired);
        expect(
            call("POST", "/aftersale-drafts/" + expired + "/confirmation", Map.of("version", 1, "confirmed", true), 0),
            409
        );
    }

    @Test
    void liveStreamAndRevokedSessionDoNotBypassAuthentication() throws Exception {
        String id = send("wait");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/agent-runs/" + id + "/events")
            )
                .header("Authorization", "Bearer " + tokens.get(0))
                .timeout(Duration.ofSeconds(15))
                .build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            release.countDown();
            var completed = response.get(10, TimeUnit.SECONDS);
            assertThat(completed.statusCode()).isEqualTo(200);
            assertThat(completed.body()).contains("event:done", "查询完成");
        }
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        String revoked = send("revoked");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(call("DELETE", "/sessions/current", null, 0).statusCode()).isEqualTo(204);
        release.countDown();
        for (int i = 0; i < 100; i++) {
            if (
                !jdbc.queryForObject("SELECT status FROM agent_run WHERE id=?", String.class, revoked).equals("RUNNING")
            ) break;
            Thread.sleep(50);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM agent_run WHERE id=?", String.class, revoked)).isEqualTo(
            "FAILED"
        );
    }

    @Test
    void limitsNewRunsButAllowsIdempotentRetry() throws Exception {
        var first = message("hello", UUID.randomUUID().toString());
        String id = expect(call("POST", "/conversations/" + conversation + "/messages", first, 0), 202)
            .path("id")
            .asString();
        await(id);
        for (int i = 1; i < 10; i++) assertThat(await(send("hello")).path("status").asString()).isEqualTo("SUCCEEDED");
        expect(
            call(
                "POST",
                "/conversations/" + conversation + "/messages",
                message("extra", UUID.randomUUID().toString()),
                0
            ),
            429
        );
        assertThat(
            expect(call("POST", "/conversations/" + conversation + "/messages", first, 0), 202)
                .path("id")
                .asString()
        ).isEqualTo(id);
    }

    @Test
    void cancelledDraftAndExpiredLeaseAreNotReplayed() throws Exception {
        String id = draft();
        expect(call("POST", "/aftersale-drafts/" + id + "/cancellation", null, 0), 200);
        expect(
            call("POST", "/aftersale-drafts/" + id + "/confirmation", Map.of("version", 1, "confirmed", true), 0),
            409
        );
        String run = send("wait");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE agent_run SET expires_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE id=?", run);
        assertThat(
            expect(call("GET", "/agent-runs/" + run, null, 0), 200)
                .path("status")
                .asString()
        ).isEqualTo("FAILED");
        release.countDown();
        assertThat(invocations.get()).isEqualTo(2);
    }
}
