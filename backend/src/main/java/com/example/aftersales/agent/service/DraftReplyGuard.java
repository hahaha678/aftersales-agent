package com.example.aftersales.agent.service;

import com.example.aftersales.aftersales.domain.vo.DraftVO;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/** 每次模型回答独立记录写工具证据，历史文字不能作为本轮创建成功的依据。 */
public final class DraftReplyGuard {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SUCCESS = Pattern.compile(
        "(?:已.{0,16}(?:生成|创建|准备)|(?:生成|创建|准备)(?:好了|完成|了)|草稿.{0,12}(?:已生成|已创建|已准备)|草稿如下|确认卡片)",
        Pattern.DOTALL
    );
    private final Map<String, DraftVO> created = new LinkedHashMap<>();

    public void record(String toolName, String result) {
        if (!toolName.equals("createAftersaleDraft")) return;
        // 错误 JSON 也属于工具正常返回，必须明确检查成功结构，不能只看工具是否被调用。
        var node = JSON.readTree(result);
        if (
            node.has("error") || !node.path("status").asString().equals("READY") || node.path("id").asString().isBlank()
        ) return;
        var draft = JSON.treeToValue(node, DraftVO.class);
        created.put(draft.id(), draft);
    }

    public String finish(String answer) {
        if (!created.isEmpty()) {
            var text = new StringBuilder("本轮已生成以下待确认草稿（尚未提交）：\n");
            for (var draft : created.values()) {
                text.append("\n订单：")
                    .append(draft.orderNumber())
                    .append("\n商品：")
                    .append(draft.productName())
                    .append("\n申请数量：")
                    .append(draft.quantity())
                    .append(" 件")
                    .append("\n本次申请金额：")
                    .append(draft.amount())
                    .append(" 元")
                    .append("\n问题描述：")
                    .append(draft.description())
                    .append("\n");
            }
            return text
                .append("\n请在下方草稿卡片核对信息并确认提交。每份草稿需要单独确认；已有草稿不会被新草稿替换。")
                .toString();
        }
        if ((answer.contains("草稿") || answer.contains("确认卡片")) && SUCCESS.matcher(answer).find()) {
            return "本轮没有成功生成新的售后草稿。历史回答中的生成提示不代表本轮已创建，已有草稿仍保留。请重新发送生成请求；只有实际显示的草稿卡片才能确认提交。";
        }
        return answer;
    }
}
