package com.example.aftersales.order.service;

import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.identity.service.CurrentUserService;
import com.example.aftersales.order.domain.po.OrderPO;
import com.example.aftersales.order.domain.query.OrderPageQuery;
import com.example.aftersales.order.domain.vo.*;
import com.example.aftersales.order.mapper.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OrderService {
    private final com.example.aftersales.aftersales.mapper.AftersaleMapper aftersales;
    private final CurrentUserService currentUser;
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final OrderShipmentMapper shipments;
    private final ShipmentEventMapper events;

    public OrderService(CurrentUserService currentUser, OrderMapper orders, OrderItemMapper items,
            OrderShipmentMapper shipments, ShipmentEventMapper events, com.example.aftersales.aftersales.mapper.AftersaleMapper aftersales) {
        this.aftersales = aftersales;
        this.currentUser = currentUser; this.orders = orders; this.items = items;
        this.shipments = shipments; this.events = events;
    }

    public OrderPageVO list(OrderPageQuery query) {
        long userId = currentUser.requireUserId();
        if (query == null || query.getPage() < 1 || query.getSize() < 1 || query.getSize() > 100) {
            throw ApiRequestException.invalid("分页参数不符合约束");
        }
        String number = query.getOrderNumber();
        if (number != null && number.length() > 64) throw ApiRequestException.invalid("订单号不能超过 64 个字符");
        // 空字符串及纯空白不筛选，非空订单号保持精确匹配，不擅自裁剪。
        if (number != null && number.isBlank()) number = null;
        long total = orders.countByUser(userId, query.getStatus(), number);
        long offset = ((long) query.getPage() - 1) * query.getSize();
        if (offset >= total) return new OrderPageVO(List.of(), query.getPage(), query.getSize(), total);
        var page = orders.findPageByUser(userId, query.getStatus(), number, offset, query.getSize());
        Map<Long, Integer> quantities = new HashMap<>();
        if (!page.isEmpty()) {
            for (var item : items.findByOwnedOrders(userId, page.stream().map(OrderPO::getId).toList())) {
                quantities.merge(item.getOrderId(), item.getQuantity(), Math::addExact);
            }
        }
        var views = page.stream().map(order -> new OrderSummaryVO(
                order.getId().toString(), order.getOrderNumber(), order.getStatus(),
                quantities.getOrDefault(order.getId(), 0), money(order.getPaidAmount()),
                order.getCurrency(), utc(order.getCreatedAt()))).toList();
        return new OrderPageVO(views, query.getPage(), query.getSize(), total);
    }

    public OrderDetailVO detail(String orderId) {
        long userId = currentUser.requireUserId();
        var order = owned(userId, parseId(orderId));
        var views = items.findByOwnedOrder(userId, order.getId()).stream().map(item -> new OrderItemVO(
                item.getId().toString(), item.getSkuId().toString(), item.getProductName(), item.getSpecification(),
                item.getQuantity(), money(item.getPaidAmount()), item.getQuantity() - aftersales.occupied(item.getId()))).toList();
        // 剩余数量扣除待审核及已通过申请的占用；期限和订单状态由资格接口判断。
        return new OrderDetailVO(order.getId().toString(), order.getOrderNumber(), order.getStatus(),
                money(order.getPaidAmount()), order.getCurrency(), utc(order.getCreatedAt()),
                utc(order.getPaidAt()), utc(order.getSignedAt()), views);
    }

    public List<ShipmentVO> shipments(String orderId) {
        long userId = currentUser.requireUserId();
        var order = owned(userId, parseId(orderId));
        // 首版数据库唯一约束保证一个订单至多一个包裹，查询轨迹不会随商品数量增加。
        return shipments.findByOwnedOrder(userId, order.getId()).stream().map(shipment -> {
            var timeline = events.findByOwnedShipment(userId, shipment.getId()).stream()
                    .map(event -> new ShipmentVO.ShipmentEvent(utc(event.getOccurredAt()), event.getDescription())).toList();
            return new ShipmentVO(shipment.getId().toString(), shipment.getCarrier(), shipment.getTrackingNumber(),
                    ShipmentVO.ShipmentStatus.valueOf(shipment.getStatus()), timeline);
        }).toList();
    }

    private OrderPO owned(long userId, long orderId) {
        var order = orders.findOwnedById(userId, orderId);
        if (order == null) throw ApiRequestException.notFound();
        return order;
    }
    private long parseId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw ApiRequestException.invalid("订单 ID 格式不正确");
        try { return Long.parseLong(id); }
        catch (NumberFormatException ex) { throw ApiRequestException.invalid("订单 ID 超出有效范围"); }
    }
    private String money(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
    private OffsetDateTime utc(LocalDateTime value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
}
