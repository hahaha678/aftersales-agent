package com.example.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractTest {

    @Value("${local.server.port}")
    private int port;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(
                method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)
            );
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void openApiDescribesIdentityOrdersAndAftersales() throws Exception {
        var response = request("GET", "/v3/api-docs", null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode document = mapper.readTree(response.body());
        var paths = document.path("paths");
        assertThat(paths.size()).isEqualTo(36);
        assertThat(
            paths.path("/api/staff/aftersales/{id}/refunds/{requestKey}").path("put").path("security").isArray()
        ).isTrue();
        assertThat(paths.path("/api/staff/aftersales/{id}/refunds/{requestKey}/reconciliation").has("post")).isTrue();
        assertThat(
            document.path("components").path("schemas").path("AftersaleVO").path("properties").has("refunds")
        ).isTrue();
        assertThat(paths.path("/api/aftersales/{id}/return-shipment").has("put")).isTrue();
        assertThat(paths.path("/api/staff/aftersales/{id}/receipt").path("put").path("security").isArray()).isTrue();
        assertThat(paths.path("/api/knowledge/searches").path("post").path("security").isArray()).isTrue();
        assertThat(paths.path("/api/staff/policies/{id}/publication").has("post")).isTrue();
        assertThat(paths.path("/api/agent-runs/{id}/sources").has("get")).isTrue();
        assertThat(paths.path("/api/aftersales").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/staff/aftersales/{id}/review").path("post").path("security").isArray()).isTrue();
        assertThat(
            document
                .path("components")
                .path("schemas")
                .path("AftersaleVO")
                .path("properties")
                .path("amount")
                .path("type")
                .asString()
        ).isEqualTo("string");
        assertThat(paths.path("/api/orders").path("get").path("security").isArray()).isTrue();
        assertThat(paths.path("/api/sessions").path("post").path("security").isMissingNode()).isTrue();
        assertThat(paths.path("/api/sessions").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/sessions/current").path("delete").path("responses").has("204")).isTrue();
        assertThat(
            document
                .path("components")
                .path("schemas")
                .path("OrderDetailVO")
                .path("properties")
                .path("paidAmount")
                .path("type")
                .asString()
        ).isEqualTo("string");
        assertThat(paths.path("/api/orders").path("get").path("responses").has("501")).isTrue();
        assertThat(
            paths.path("/api/orders").path("get").path("responses").path("200").path("content").has("application/json")
        ).isTrue();
        var parameters = paths.path("/api/orders").path("get").path("parameters");
        assertThat(parameters.size()).isEqualTo(4);
        var names = new java.util.ArrayList<String>();
        for (var parameter : parameters) {
            assertThat(parameter.path("in").asString()).isEqualTo("query");
            assertThat(parameter.path("required").asBoolean(false)).isFalse();
            names.add(parameter.path("name").asString());
            if (parameter.path("name").asString().equals("page")) {
                assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(1);
            }
        }
        assertThat(names).containsExactlyInAnyOrder("page", "size", "status", "orderNumber");
    }

    @Test
    void validContractRequestsNeverPretendToSucceed() throws Exception {
        String[][] requests = {
            { "GET", "/api/users/me", null },
            { "GET", "/api/orders", null },
            { "GET", "/api/orders?page=&size=", null },
            { "GET", "/api/orders?page=2&size=10&status=PAID&orderNumber=ORD001", null },
            { "GET", "/api/orders/2001", null },
            { "GET", "/api/orders/2001/shipments", null },
            { "DELETE", "/api/sessions/current", null },
            { "POST", "/api/sessions", "{\"username\":\"customer01\",\"password\":\"private-example\"}" },
        };
        for (var input : requests) {
            var response = request(input[0], input[1], input[2]);
            assertThat(response.statusCode()).as(input[1]).isEqualTo(501);
            var error = mapper.readTree(response.body());
            assertThat(error.path("code").asString()).isEqualTo("NOT_IMPLEMENTED");
            assertThat(error.path("requestId").asString()).isEqualTo(
                response.headers().firstValue("X-Request-Id").orElseThrow()
            );
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.body()).doesNotContain("private-example");
        }
    }

    @Test
    void rejectsInvalidPaginationIdsAndEnumValues() throws Exception {
        for (String path : new String[] {
            "/api/orders?page=0",
            "/api/orders?size=101",
            "/api/orders?status=OTHER",
            "/api/orders/not-an-id",
            "/api/orders?page=abc",
        }) {
            var response = request("GET", path, null);
            assertThat(response.statusCode()).as(path).isEqualTo(400);
            assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    void validatesLoginAndHandlesMalformedJsonWithoutEchoingSecrets() throws Exception {
        var empty = request("POST", "/api/sessions", "{\"username\":\"\",\"password\":\"\"}");
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(empty.body()).path("code").asString()).isEqualTo("VALIDATION_ERROR");
        var malformed = request("POST", "/api/sessions", "{\"password\":\"private-example\"");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformed.body()).doesNotContain("private-example");
    }
}
