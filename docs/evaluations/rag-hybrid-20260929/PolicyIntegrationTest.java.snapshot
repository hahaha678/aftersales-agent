package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.service.SessionService;
import com.example.aftersales.knowledge.service.PolicyEmbedding;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 隔离 MySQL/Redis 验证真实迁移和 HTTP 权限；向量使用可预测夹具，不计为语义效果评测。 */
@EnabledIfEnvironmentVariable(named = "AGENT_EVAL_ENABLED", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=${EVAL_DB_URL}",
        "spring.datasource.username=${EVAL_DB_USERNAME}",
        "spring.datasource.password=${EVAL_DB_PASSWORD}",
        "spring.data.redis.port=${EVAL_REDIS_PORT}",
        "spring.data.redis.password=",
        "app.auth.cache.namespace=policy-it",
        "app.agent.enabled=false",
    }
)
class PolicyIntegrationTest {

    @Autowired
    com.example.aftersales.agent.service.AgentTools tools;

    @Autowired
    com.example.aftersales.conversation.service.ConversationService conversations;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService auth;

    @MockitoBean
    PolicyEmbedding embedding;

    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient client = HttpClient.newHttpClient();
    String staff, customer;

    @BeforeEach
    void prepare() {
        when(embedding.identity()).thenReturn("a".repeat(64));
        when(embedding.available()).thenReturn(true);
        when(embedding.embed(anyList())).thenAnswer(call ->
            ((List<?>) call.getArgument(0))
                .stream()
                .map(x -> new double[] { 1, 0 })
                .toList()
        );
        String hash =
            "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80";
        for (String role : List.of("STAFF", "CUSTOMER")) {
            String name = "policy_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,?)",
                name,
                hash,
                "评测用户",
                role
            );
            String token = auth.login(new CreateSessionDTO(name, "DemoPass123!")).accessToken();
            if (role.equals("STAFF")) staff = token;
            else customer = token;
        }
    }

    Map<String, Object> body(String key, int version, String scope, String content) {
        return Map.of(
            "policyKey",
            key,
            "version",
            version,
            "scope",
            scope,
            "title",
            "演示材料政策",
            "content",
            content,
            "effectiveFrom",
            LocalDate.now().minusDays(1).toString(),
            "effectiveUntil",
            LocalDate.now().plusDays(30).toString()
        );
    }

    HttpResponse<String> call(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path)).timeout(
            Duration.ofSeconds(30)
        );
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
            );
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    String create(String key, int version, String scope) throws Exception {
        var r = call(
            "POST",
            "/staff/policies",
            body(key, version, scope, "鼠标按键失灵需提供故障描述，可附故障视频。"),
            staff
        );
        assertThat(r.statusCode()).isEqualTo(201);
        return json.readTree(r.body()).path("id").asString();
    }

    JsonNode search(String scope) throws Exception {
        var r = call("POST", "/knowledge/searches", Map.of("question", "故障材料", "scope", scope), customer);
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body()).path("sources");
    }

    @Test
    void lexicalRecallStillRespectsScopeAndArchival() throws Exception {
        String scope = "T" + UUID.randomUUID().toString().replace("-", "");
        var created = call(
            "POST",
            "/staff/policies",
            body("hybrid", 1, scope, "本演示政策没有终身免费换新承诺，保修期限以购买凭证为准。"),
            staff
        );
        assertThat(created.statusCode()).isEqualTo(201);
        String id = json.readTree(created.body()).path("id").asString();
        assertThat(call("POST", "/staff/policies/" + id + "/publication", null, staff).statusCode()).isEqualTo(200);
        // 发布向量为 [1,0]，查询为 [0,1]，确保这里只能通过词面通路召回。
        when(embedding.embed(anyList())).thenReturn(List.of(new double[] { 0, 1 }));
        var query = Map.of("question", "终身免费换新是否有承诺", "scope", scope);
        var found = json.readTree(call("POST", "/knowledge/searches", query, customer).body()).path("sources");
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).path("policyId").asString()).isEqualTo(id);
        var other = json
            .readTree(
                call(
                    "POST",
                    "/knowledge/searches",
                    Map.of("question", query.get("question"), "scope", "OTHER"),
                    customer
                ).body()
            )
            .path("sources");
        assertThat(other.toString()).doesNotContain(id);
        assertThat(call("POST", "/staff/policies/" + id + "/archival", null, staff).statusCode()).isEqualTo(200);
        var archived = json.readTree(call("POST", "/knowledge/searches", query, customer).body()).path("sources");
        assertThat(archived.toString()).doesNotContain(id);
    }

    @Test
    void toolPersistsSourcesAndOtherUserCannotReadThem() throws Exception {
        String scope = "T" + UUID.randomUUID().toString().replace("-", "");
        String policy = create("policy", 1, scope);
        assertThat(call("POST", "/staff/policies/" + policy + "/publication", null, staff).statusCode()).isEqualTo(200);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                auth.authenticate(customer),
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
            )
        );
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        String run;
        try {
            var conversation = conversations.create(
                new com.example.aftersales.conversation.domain.dto.ConversationDTO("政策评测")
            );
            run = conversations
                .begin(
                    conversation.id(),
                    new com.example.aftersales.conversation.domain.dto.SendMessageDTO(
                        "材料",
                        UUID.randomUUID().toString()
                    ),
                    "script-test"
                )
                .run()
                .id();
            var events = new ArrayList<String>();
            var tool = tools
                .forRun(run, customer, () -> true, (name, data) -> events.add(name))
                .stream()
                .filter(t -> t.getToolDefinition().name().equals("searchPolicies"))
                .findFirst()
                .orElseThrow();
            String result = tool.call(json.writeValueAsString(Map.of("question", "材料", "scope", scope)));
            assertThat(json.readTree(result).path("sources").size()).isEqualTo(1);
            assertThat(events).contains("status", "sources");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        var response = call("GET", "/agent-runs/" + run + "/sources", null, customer);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get(0).path("policyId").asString()).isEqualTo(policy);
        assertThat(call("GET", "/agent-runs/" + run + "/sources", null, staff).statusCode()).isEqualTo(404);
    }

    @Test
    void permissionsVersionReplacementAndArchivedSource() throws Exception {
        String scope = "T" + UUID.randomUUID().toString().replace("-", "");
        assertThat(call("POST", "/staff/policies", body("policy", 1, scope, "材料"), customer).statusCode()).isEqualTo(
            403
        );
        assertThat(
            call("POST", "/knowledge/searches", Map.of("question", "材料", "scope", scope), null).statusCode()
        ).isEqualTo(401);
        String first = create("policy", 1, scope);
        assertThat(call("GET", "/policies/" + first, null, customer).statusCode()).isEqualTo(403);
        assertThat(search(scope).size()).isZero();
        assertThat(call("POST", "/staff/policies/" + first + "/publication", null, staff).statusCode()).isEqualTo(200);
        assertThat(search(scope).get(0).path("policyId").asString()).isEqualTo(first);
        String second = create("policy", 2, scope);
        assertThat(call("POST", "/staff/policies/" + second + "/publication", null, staff).statusCode()).isEqualTo(200);
        assertThat(search(scope).size()).isEqualTo(1);
        assertThat(search(scope).get(0).path("version").asInt()).isEqualTo(2);
        assertThat(call("GET", "/policies/" + first, null, customer).statusCode()).isEqualTo(200);
        assertThat(search("UNRELATED").size()).isZero();
        jdbc.update("UPDATE knowledge_policy SET effective_until=? WHERE id=?", LocalDate.now().minusDays(1), second);
        assertThat(search(scope).size()).isZero();
    }

    @Test
    void draftEditsDetectConflictsAndCannotRewritePublishedText() throws Exception {
        String scope = "T" + UUID.randomUUID().toString().replace("-", "");
        String id = create("policy", 1, scope);
        var row = json.readTree(call("GET", "/policies/" + id, null, staff).body());
        var edit = Map.of(
            "expectedFingerprint",
            row.path("fingerprint").asString(),
            "title",
            "修改后的演示政策",
            "content",
            "修改后的原文",
            "effectiveFrom",
            row.path("effectiveFrom").asString(),
            "effectiveUntil",
            row.path("effectiveUntil").asString()
        );
        assertThat(call("PUT", "/staff/policies/" + id, edit, customer).statusCode()).isEqualTo(403);
        assertThat(call("PUT", "/staff/policies/" + id, edit, staff).statusCode()).isEqualTo(200);
        assertThat(call("PUT", "/staff/policies/" + id, edit, staff).statusCode()).isEqualTo(409);
        assertThat(call("POST", "/staff/policies/" + id + "/publication", null, staff).statusCode()).isEqualTo(200);
        assertThat(call("PUT", "/staff/policies/" + id, edit, staff).statusCode()).isEqualTo(409);
        assertThat(search(scope).get(0).path("excerpt").asString()).isEqualTo("修改后的原文");
    }

    @Test
    void editDuringEmbeddingPreventsStaleIndexPublication() throws Exception {
        String scope = "T" + UUID.randomUUID().toString().replace("-", "");
        String id = create("policy", 1, scope);
        var row = json.readTree(call("GET", "/policies/" + id, null, staff).body());
        doAnswer(invocation -> {
            var edit = Map.of(
                "expectedFingerprint",
                row.path("fingerprint").asString(),
                "title",
                "新标题",
                "content",
                "生成向量期间修改的正文",
                "effectiveFrom",
                row.path("effectiveFrom").asString(),
                "effectiveUntil",
                row.path("effectiveUntil").asString()
            );
            assertThat(call("PUT", "/staff/policies/" + id, edit, staff).statusCode()).isEqualTo(200);
            return List.of(new double[] { 1, 0 });
        })
            .when(embedding)
            .embed(anyList());
        assertThat(call("POST", "/staff/policies/" + id + "/publication", null, staff).statusCode()).isEqualTo(409);
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE policy_id=?", Integer.class, id)
        ).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM knowledge_policy WHERE id=?", String.class, id)).isEqualTo(
            "DRAFT"
        );
    }

    @Test
    void embeddingFailureKeepsPreviousAndNoEvidenceWithoutEitherRoute() throws Exception {
        String scope = "T" + UUID.randomUUID().toString().replace("-", "");
        String first = create("policy", 1, scope);
        assertThat(call("POST", "/staff/policies/" + first + "/publication", null, staff).statusCode()).isEqualTo(200);
        String second = create("policy", 2, scope);
        when(embedding.embed(anyList())).thenThrow(new ApiRequestException(503, "EMBEDDING_UNAVAILABLE", "模拟故障"));
        assertThat(call("POST", "/staff/policies/" + second + "/publication", null, staff).statusCode()).isEqualTo(503);
        assertThat(
            jdbc.queryForObject("SELECT status FROM knowledge_policy WHERE id=?", String.class, first)
        ).isEqualTo("PUBLISHED");
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE policy_id=?", Integer.class, second)
        ).isZero();
        doReturn(List.of(new double[] { 0, 1 }))
            .when(embedding)
            .embed(anyList());
        // 低向量分仍可通过词面召回；改用无关问题验证两条通路都无依据时返回空。
        var unrelated = call(
            "POST",
            "/knowledge/searches",
            Map.of("question", "明天天气温度", "scope", scope),
            customer
        );
        assertThat(unrelated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(unrelated.body()).path("sources").size()).isZero();
        when(embedding.identity()).thenReturn("b".repeat(64));
        assertThat(
            call("POST", "/knowledge/searches", Map.of("question", "故障", "scope", scope), customer).statusCode()
        ).isEqualTo(409);
    }
}
