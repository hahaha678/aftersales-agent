package com.example.aftersales.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "原订单出库物流，不含售后退回/换货物流。首版使用模拟物流数据。")
public record ShipmentVO(
        @Schema(example = "5001") String id,
        @Schema(example = "模拟物流") String carrier,
        @Schema(example = "DEMO202609280001") String trackingNumber,
        ShipmentStatus status,
        @Schema(description = "运输事件按发生时间升序") List<ShipmentEvent> events
) {
    public enum ShipmentStatus { IN_TRANSIT, DELIVERED, EXCEPTION }
    public record ShipmentEvent(OffsetDateTime occurredAt, String description) {}
}
