package com.example.aftersales.knowledge.service;

import com.example.aftersales.common.exception.ApiRequestException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 独立于对话模型的向量服务；显式配置后才向配置的端点发送政策文本。 */
@Component
public class PolicyEmbedding {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String provider, baseUrl, model, key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public PolicyEmbedding(
        @Value("${app.knowledge.provider:disabled}") String provider,
        @Value("${app.knowledge.base-url:http://127.0.0.1:11434}") String baseUrl,
        @Value("${app.knowledge.model:}") String model,
        @Value("${app.knowledge.api-key:}") String key
    ) {
        this.provider = provider;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.key = key;
    }

    public boolean available() {
        return !model.isBlank() && (provider.equals("ollama") || (provider.equals("openai") && !key.isBlank()));
    }

    public String identity() {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    (provider + ":" + baseUrl + ":" + model).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<double[]> embed(List<String> input) {
        if (!available()) throw unavailable("请先配置知识库 Embedding 服务");
        try {
            var body = new HashMap<String, Object>();
            body.put("model", model);
            body.put("input", input);
            if (provider.equals("ollama")) body.put("truncate", false);
            var request = HttpRequest.newBuilder(
                URI.create(baseUrl + (provider.equals("ollama") ? "/api/embed" : "/embeddings"))
            )
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (provider.equals("openai")) request.header("Authorization", "Bearer " + key);
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable("向量服务调用失败，请检查模型与配置");
            var root = JSON.readTree(response.body());
            List<double[]> vectors = new ArrayList<>();
            if (provider.equals("ollama")) {
                for (var node : root.path("embeddings")) vectors.add(JSON.treeToValue(node, double[].class));
            } else {
                // 兼容接口可能乱序返回，按 index 还原输入和向量的对应关系。
                double[][] ordered = new double[input.size()][];
                for (var node : root.path("data")) {
                    if (!node.path("index").isInt()) throw unavailable("向量返回索引无效");
                    int index = node.path("index").asInt();
                    if (index < 0 || index >= ordered.length || ordered[index] != null) throw unavailable(
                        "向量返回索引无效"
                    );
                    ordered[index] = JSON.treeToValue(node.path("embedding"), double[].class);
                }
                vectors.addAll(Arrays.asList(ordered));
            }
            if (vectors.size() != input.size()) throw unavailable("向量返回数量不匹配");
            int dimension = vectors.isEmpty() || vectors.getFirst() == null ? 0 : vectors.getFirst().length;
            for (double[] vector : vectors) {
                if (
                    vector == null || dimension == 0 || dimension > 8192 || vector.length != dimension
                ) throw unavailable("向量维度无效");
                double norm = 0;
                for (double value : vector) {
                    if (!Double.isFinite(value)) throw unavailable("向量数值无效");
                    norm += value * value;
                }
                if (!Double.isFinite(norm) || norm == 0) throw unavailable("向量数值无效");
            }
            return vectors;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable("向量请求已中断");
        } catch (ApiRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("向量服务暂不可用，请检查配置后重试");
        }
    }

    private static ApiRequestException unavailable(String message) {
        return new ApiRequestException(503, "EMBEDDING_UNAVAILABLE", message);
    }
}
