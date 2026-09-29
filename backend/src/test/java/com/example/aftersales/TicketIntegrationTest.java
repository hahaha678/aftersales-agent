package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.conversation.mapper.ConversationMapper;
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
        "app.auth.cache.namespace=tickets-it",
    }
)
class TicketIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService auth;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ConversationMapper conversations;

    final JsonMapper json = JsonMapper.builder().build();
    final List<Long> users = new ArrayList<>();
    final List<String> tokens = new ArrayList<>(),
        chats = new ArrayList<>();

    @BeforeEach
    void setup() {
        for (String role : List.of("CUSTOMER", "CUSTOMER", "STAFF", "STAFF")) {
            String name = "ticket_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,?)",
                name,
                "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80",
                "工单测试",
                role
            );
            long uid = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, name);
            users.add(uid);
            tokens.add(auth.login(new CreateSessionDTO(name, "DemoPass123!")).accessToken());
            String chat = UUID.randomUUID().toString();
            conversations.create(chat, uid, "测试会话");
            chats.add(chat);
        }
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("support_ticket_context", "support_ticket_submission", "support_ticket_event")) {
            for (long uid : users)
                jdbc.update(
                    "DELETE x FROM " + table + " x JOIN support_ticket t ON t.id=x.ticket_id WHERE t.user_id=?",
                    uid
                );
        }
        for (long uid : users) jdbc.update("DELETE FROM support_ticket WHERE user_id=?", uid);
        for (long uid : users) {
            jdbc.update(
                "DELETE m FROM conversation_message m JOIN conversation c ON c.id=m.conversation_id WHERE c.user_id=?",
                uid
            );
            jdbc.update("DELETE FROM agent_run WHERE user_id=?", uid);
            jdbc.update("DELETE FROM conversation WHERE user_id=?", uid);
            jdbc.update(
                "DELETE e FROM aftersale_event e JOIN aftersale_request a ON a.id=e.request_id WHERE a.user_id=?",
                uid
            );
            jdbc.update("DELETE FROM aftersale_request WHERE user_id=?", uid);
            jdbc.update("DELETE i FROM order_item i JOIN trade_order o ON o.id=i.order_id WHERE o.user_id=?", uid);
            jdbc.update("DELETE FROM trade_order WHERE user_id=?", uid);
            jdbc.update("DELETE FROM user_session WHERE user_id=?", uid);
        }
        for (long uid : users) jdbc.update("DELETE FROM app_user WHERE id=?", uid);
        for (String token : tokens) {
            String prefix = "tickets-it:{" + TokenCodec.hash(token) + "}:";
            redis.delete(List.of(prefix + "session", prefix + "revoked"));
        }
    }

    HttpResponse<String> call(String method, String path, Object body, int actor) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path)).header(
            "Content-Type",
            "application/json"
        );
        if (actor >= 0) builder.header("Authorization", "Bearer " + tokens.get(actor));
        builder.method(
            method,
            body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
        );
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    JsonNode expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }

    Map<String, Object> input(int owner) {
        return new HashMap<>(
            Map.of(
                "conversationId",
                chats.get(owner),
                "requestKey",
                UUID.randomUUID().toString(),
                "problem",
                "请确认退回地址"
            )
        );
    }

    String create() throws Exception {
        return expect(call("POST", "/tickets", input(0), 0), 200)
            .path("id")
            .asString();
    }

    void message(String content) {
        String run = UUID.randomUUID().toString();
        conversations.begin(run, chats.get(0), users.get(0), run, content, "test");
        conversations.message(chats.get(0), run, "USER", content, "SUCCEEDED");
    }

    @Test
    void lifecycleSnapshotAndReusedSubmissionRemainIdempotentAfterResolution() throws Exception {
        message("提交前的消息");
        var body = input(0);
        String id = expect(call("POST", "/tickets", body, 0), 200)
            .path("id")
            .asString();
        var alias = input(0);
        alias.put("problem", "另一段问题");
        assertThat(
            expect(call("POST", "/tickets", alias, 0), 200)
                .path("id")
                .asString()
        ).isEqualTo(id);
        message("提交后的私人消息");
        var context = expect(call("GET", "/staff/tickets/" + id + "/context", null, 2), 200);
        assertThat(context.size()).isEqualTo(1);
        assertThat(context.toString()).contains("提交前的消息").doesNotContain("私人消息");
        expect(call("PUT", "/staff/tickets/" + id + "/resolution", Map.of("resolution", "已答复"), 2), 403);
        for (int i = 0; i < 2; i++) expect(call("PUT", "/staff/tickets/" + id + "/assignment", null, 2), 200);
        expect(call("PUT", "/staff/tickets/" + id + "/resolution", Map.of("resolution", "其他人"), 3), 403);
        for (int i = 0; i < 2; i++) {
            var done = expect(
                call("PUT", "/staff/tickets/" + id + "/resolution", Map.of("resolution", "已核实并告知退回地址"), 2),
                200
            );
            assertThat(done.path("status").asString()).isEqualTo("RESOLVED");
            assertThat(done.path("events").size()).isEqualTo(3);
        }
        expect(call("PUT", "/staff/tickets/" + id + "/resolution", Map.of("resolution", "覆盖结果"), 2), 409);
        assertThat(
            expect(call("POST", "/tickets", alias, 0), 200)
                .path("id")
                .asString()
        ).isEqualTo(id);
        assertThat(
            expect(call("POST", "/tickets", body, 0), 200)
                .path("id")
                .asString()
        ).isEqualTo(id);
        body.put("problem", "请求键冲突");
        expect(call("POST", "/tickets", body, 0), 409);
        assertThat(create()).isNotEqualTo(id);
        assertThat(expect(call("GET", "/tickets?status=RESOLVED", null, 0), 200).size()).isEqualTo(1);
    }

    @Test
    void permissionsAndInvalidInputsProtectConversationAndLinks() throws Exception {
        expect(call("POST", "/tickets", input(0), -1), 401);
        expect(call("POST", "/tickets", input(1), 0), 404);
        var body = input(0);
        body.put("problem", "  ");
        expect(call("POST", "/tickets", body, 0), 400);
        body = input(0);
        body.put("problem", "字".repeat(2001));
        expect(call("POST", "/tickets", body, 0), 400);
        String id = create();
        for (String suffix : List.of("", "/context")) expect(call("GET", "/tickets/" + id + suffix, null, 1), 404);
        expect(call("GET", "/staff/tickets", null, 0), 403);
        expect(call("GET", "/staff/tickets/" + id + "/context", null, 0), 403);
        expect(call("PUT", "/staff/tickets/" + id + "/assignment", null, 0), 403);
        expect(call("GET", "/tickets?before=0", null, 0), 400);
        expect(call("GET", "/tickets?status=INVALID", null, 0), 400);
        expect(call("GET", "/tickets/9999999999999999999", null, 0), 400);
        String own = expect(call("POST", "/tickets", input(2), 2), 200)
            .path("id")
            .asString();
        expect(call("PUT", "/staff/tickets/" + own + "/assignment", null, 2), 403);
        assertThat(expect(call("GET", "/tickets", null, 1), 200).size()).isZero();
        long oid = order(users.get(1));
        body = input(0);
        body.put("orderId", Long.toString(oid));
        expect(call("POST", "/tickets", body, 0), 404);
    }

    long order(long uid) {
        String number = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO trade_order(user_id,order_number,status,paid_amount,created_at,paid_at) VALUES(?,?,'PAID',10,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            uid,
            number
        );
        return jdbc.queryForObject("SELECT id FROM trade_order WHERE order_number=?", Long.class, number);
    }

    @Test
    void associationMustBelongToUserAndMatchOrder() throws Exception {
        long first = order(users.get(0)),
            second = order(users.get(0)),
            other = order(users.get(1));
        jdbc.update(
            "INSERT INTO order_item(order_id,sku_id,product_name,quantity,paid_amount) VALUES(?,101,'测试商品',1,10)",
            first
        );
        long item = jdbc.queryForObject("SELECT id FROM order_item WHERE order_id=?", Long.class, first);
        jdbc.update(
            "INSERT INTO aftersale_request(user_id,order_id,order_item_id,request_key,quantity,amount,reason,description,status) VALUES(?,?,?,'ticket-fixture',1,10,'QUALITY','测试','PENDING')",
            users.get(0),
            first,
            item
        );
        long sale = jdbc.queryForObject("SELECT id FROM aftersale_request WHERE order_id=?", Long.class, first);
        var body = input(1);
        body.put("aftersaleId", Long.toString(sale));
        expect(call("POST", "/tickets", body, 1), 404);
        body = input(0);
        body.put("aftersaleId", Long.toString(sale));
        body.put("orderId", Long.toString(second));
        expect(call("POST", "/tickets", body, 0), 409);
        body.remove("orderId");
        var created = expect(call("POST", "/tickets", body, 0), 200);
        assertThat(created.path("orderId").asString()).isEqualTo(Long.toString(first));
        assertThat(created.path("aftersaleId").asString()).isEqualTo(Long.toString(sale));
    }

    @Test
    void concurrentCreateAndClaimHaveOneTicketAndOneAssignee() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<String> create = () -> {
                gate.await();
                return create();
            };
            var a = pool.submit(create);
            var b = pool.submit(create);
            gate.countDown();
            String id = a.get(15, TimeUnit.SECONDS);
            assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(id);
            var claimGate = new CountDownLatch(1);
            var c = pool.submit(() -> {
                claimGate.await();
                return call("PUT", "/staff/tickets/" + id + "/assignment", null, 2).statusCode();
            });
            var d = pool.submit(() -> {
                claimGate.await();
                return call("PUT", "/staff/tickets/" + id + "/assignment", null, 3).statusCode();
            });
            claimGate.countDown();
            assertThat(List.of(c.get(15, TimeUnit.SECONDS), d.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(
                200,
                409
            );
            assertThat(
                expect(call("GET", "/tickets/" + id, null, 0), 200)
                    .path("events")
                    .size()
            ).isEqualTo(2);
        }
    }

    @Test
    void snapshotPaginationHasNoDuplicatesAndDoesNotReadLaterMessages() throws Exception {
        for (int i = 0; i < 55; i++) message("历史" + i);
        String id = create();
        message("之后的消息");
        var first = expect(call("GET", "/staff/tickets/" + id + "/context", null, 2), 200);
        assertThat(first.size()).isEqualTo(50);
        var second = expect(
            call("GET", "/staff/tickets/" + id + "/context?before=" + first.get(0).path("id").asString(), null, 2),
            200
        );
        assertThat(second.size()).isEqualTo(5);
        assertThat(first.toString() + second.toString()).doesNotContain("之后的消息");
        assertThat(expect(call("GET", "/tickets?before=" + id, null, 0), 200).size()).isZero();
    }
}
