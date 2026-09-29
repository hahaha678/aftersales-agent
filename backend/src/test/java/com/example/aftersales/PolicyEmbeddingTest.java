package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.knowledge.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PolicyEmbeddingTest {

    @Test
    void chunksPreserveUnicodeAndOverlap() {
        String text = "鼠标故障😀".repeat(240);
        var chunks = PolicyText.split(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.getFirst().codePointCount(0, chunks.getFirst().length())).isEqualTo(600);
        int[] first = chunks.getFirst().codePoints().toArray();
        assertThat(chunks.get(1)).startsWith(new String(first, 520, 80));
        assertThat(PolicyText.cosine(new double[] { 1, 0 }, new double[] { 0, 1 })).isZero();
        assertThat(PolicyText.cosine(new double[] { 1, 0 }, new double[] { 1, 0 })).isEqualTo(1);
    }

    @Test
    void ollamaProtocolRejectsMalformedVectorsAndDoesNotTruncate() throws Exception {
        var body = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"embeddings\":[[1,0],[0,1]]}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var embedding = new PolicyEmbedding(
                "ollama",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-model",
                ""
            );
            assertThat(embedding.embed(List.of("鼠标", "材料"))).hasSize(2);
            assertThat(body.get()).contains("\"truncate\":false", "test-model");
            response.set("{\"embeddings\":[[0,0]]}");
            assertThatThrownBy(() -> embedding.embed(List.of("故障"))).hasMessageContaining("向量数值无效");
            response.set("{\"embeddings\":[[1,0]]}");
            assertThatThrownBy(() -> embedding.embed(List.of("故障", "材料"))).hasMessageContaining("数量不匹配");
        } finally {
            server.stop(0);
        }
    }
}
