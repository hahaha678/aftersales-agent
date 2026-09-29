package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.agent.config.DeepSeekGateway;
import com.example.aftersales.agent.service.ModelGateway;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tools.jackson.databind.json.JsonMapper;

class DeepSeekProtocolTest {

    record OrderInput(String id) {}

    @Test
    void streamsTextAndExecutesToolRoundTripWithRealAdapter() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var chunks = new CopyOnWriteArrayList<ModelGateway.Chunk>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(body);
            String delta =
                requests.size() == 1
                    ? "{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"call_order\",\"type\":\"function\",\"function\":{\"name\":\"getMyOrder\",\"arguments\":\"{\\\"id\\\":\\\"1001\\\"}\"}}]}"
                    : "{\"role\":\"assistant\",\"content\":\"订单已签收，请核对商品。\"}";
            String finish = requests.size() == 1 ? "tool_calls" : "stop";
            String preamble =
                requests.size() == 1
                    ? "data: {\"id\":\"mock\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"I'll look up the order.\"},\"finish_reason\":null}]}\n\n"
                    : "";
            String stream =
                preamble +
                "data: {\"id\":\"mock\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":" +
                delta +
                ",\"finish_reason\":null}]}\n\n" +
                "data: {\"id\":\"mock\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"" +
                finish +
                "\"}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":15}}\n\n" +
                "data: [DONE]\n\n";
            byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new DeepSeekGateway(
                true,
                "local-test-key",
                "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort()
            );
            var tool = FunctionToolCallback.<OrderInput, Map<String, String>>builder("getMyOrder", input -> {
                assertThat(input.id()).isEqualTo("1001");
                return Map.of("status", "COMPLETED");
            })
                .description("Query current user's order")
                .inputType(OrderInput.class)
                .build();
            gateway.stream(List.of(), "查询订单 1001", List.of(tool), chunks::add);
            assertThat(requests).hasSize(2);
            var json = JsonMapper.builder().build();
            assertThat(json.readTree(requests.get(0)).path("tools").size()).isEqualTo(1);
            assertThat(requests.get(1)).contains("tool", "call_order", "COMPLETED");
            assertThat(chunks.stream().map(ModelGateway.Chunk::text).reduce("", String::concat)).isEqualTo(
                "订单已签收，请核对商品。"
            );
            assertThat(chunks.stream().mapToInt(ModelGateway.Chunk::inputTokens).sum()).isEqualTo(24);
            assertThat(chunks.stream().mapToInt(ModelGateway.Chunk::outputTokens).sum()).isEqualTo(6);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replacesModelOnlyDraftSuccessBeforeItReachesTheChat() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String stream =
                "data: {\"id\":\"mock\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"已为你生成退货退款申请草稿：鼠标垫70元，请到确认卡片提交。\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
            byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new DeepSeekGateway(
                true,
                "test-key",
                "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort()
            );
            var chunks = new ArrayList<ModelGateway.Chunk>();
            gateway.stream(List.of(), "再给鼠标垫生成草稿", List.of(), chunks::add);
            String answer = chunks.stream().map(ModelGateway.Chunk::text).reduce("", String::concat);
            assertThat(answer).contains("没有成功生成").doesNotContain("鼠标垫70元");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingKeyDoesNotCreatePretendAnswer() {
        var gateway = new DeepSeekGateway(true, "", "test", "http://127.0.0.1:1");
        assertThat(gateway.available()).isFalse();
        assertThatThrownBy(() -> gateway.stream(List.of(), "test", List.of(), chunk -> {})).isInstanceOf(
            IllegalStateException.class
        );
    }
}
