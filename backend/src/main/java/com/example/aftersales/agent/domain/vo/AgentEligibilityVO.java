package com.example.aftersales.agent.domain.vo;

import com.example.aftersales.aftersales.domain.vo.EligibilityVO;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 只改变模型工具的展示值；业务 API 仍保留原有时间类型。 */
public record AgentEligibilityVO(String orderId, String ruleVersion, String deadline, List<EligibilityVO.Item> items) {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss '（北京时间 UTC+08:00）'"
    ).withZone(ZoneId.of("Asia/Shanghai"));

    public static AgentEligibilityVO from(EligibilityVO value) {
        return new AgentEligibilityVO(
            value.orderId(),
            value.ruleVersion(),
            value.deadline() == null ? null : DISPLAY.format(value.deadline()),
            value.items()
        );
    }
}
