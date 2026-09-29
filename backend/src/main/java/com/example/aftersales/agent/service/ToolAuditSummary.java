package com.example.aftersales.agent.service;

import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 只保存白名单结构字段；用户描述、检索问题、未知字段和完整结果均不写入执行日志。 */
public final class ToolAuditSummary {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ToolAuditSummary() {}

    public static String input(String input) {
        if (input == null || input.length() > 10000) return "参数过长或为空，未记录";
        try {
            var node = JSON.readTree(input);
            var safe = new LinkedHashMap<String, Object>();
            for (String field : List.of("id", "orderId", "orderItemId", "page", "quantity", "reason", "scope")) {
                var value = node.path(field);
                if (value.isMissingNode()) continue;
                String text = value.asString();
                boolean allowed = switch (field) {
                    case "page", "quantity", "orderId", "orderItemId" -> text.matches("[0-9]{1,19}");
                    case "id" -> text.matches("[0-9]{1,19}|DEMO-[0-9]{1,12}");
                    case "reason" -> Set.of("QUALITY", "DAMAGED", "WRONG_ITEM", "NO_LONGER_NEEDED", "OTHER").contains(
                        text
                    );
                    case "scope" -> Set.of("GLOBAL", "MOUSE").contains(text);
                    default -> false;
                };
                safe.put(field, allowed ? text : "[已省略]");
            }
            safe.put("note", "仅白名单参数，其余内容未记录");
            return JSON.writeValueAsString(safe);
        } catch (Exception ex) {
            return "参数无法解析，原文未记录";
        }
    }

    public static String errorCode(String result) {
        try {
            var node = JSON.readTree(result);
            if (!node.path("error").isMissingNode() && !node.path("error").isNull()) return code(
                node.path("error").asString()
            );
            return null;
        } catch (Exception ex) {
            return "INVALID_TOOL_RESULT";
        }
    }

    public static String code(String code) {
        // 错误码也采用白名单，避免任意异常正文或凭据被当成错误码保存。
        return Set.of(
            "INVALID_REQUEST",
            "RESOURCE_NOT_FOUND",
            "STATE_CONFLICT",
            "INVALID_ARGUMENT",
            "RESULT_TOO_LARGE",
            "FORBIDDEN",
            "UNAUTHORIZED",
            "SESSION_EXPIRED",
            "AI_NOT_CONFIGURED",
            "RATE_LIMITED"
        ).contains(code)
            ? code
            : "TOOL_ERROR";
    }

    public static String result(String result) {
        try {
            JsonNode node = JSON.readTree(result);
            var safe = new LinkedHashMap<String, Object>();
            safe.put("type", node.isArray() ? "array" : "object");
            String id = node.path("id").asString();
            if (id.matches("[0-9]{1,19}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) safe.put(
                "resourceId",
                id
            );
            String status = node.path("status").asString();
            if (
                Set.of(
                    "READY",
                    "CONFIRMED",
                    "CANCELLED",
                    "EXPIRED",
                    "PENDING",
                    "APPROVED",
                    "REJECTED",
                    "RETURN_SHIPPED",
                    "RETURN_RECEIVED",
                    "REFUND_PENDING",
                    "REFUND_FAILED",
                    "COMPLETED"
                ).contains(status)
            ) safe.put("status", status);
            if (node.isArray()) safe.put("count", node.size());
            for (String field : List.of("items", "sources", "events"))
                if (node.path(field).isArray()) safe.put(field + "Count", node.path(field).size());
            safe.put("characters", result.length());
            return JSON.writeValueAsString(safe);
        } catch (Exception ex) {
            return "结果无法解析，原文未记录";
        }
    }
}
