package com.example.aftersales.aftersales.service;

import com.example.aftersales.aftersales.domain.dto.*;
import com.example.aftersales.aftersales.domain.po.AftersalePO;
import com.example.aftersales.aftersales.domain.query.AftersalePageQuery;
import com.example.aftersales.aftersales.domain.vo.*;
import com.example.aftersales.aftersales.mapper.AftersaleMapper;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.identity.service.CurrentUserService;
import com.example.aftersales.order.domain.OrderStatus;
import com.example.aftersales.order.domain.po.*;
import com.example.aftersales.order.mapper.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AftersaleService {

    private final CurrentUserService user;
    private final AftersaleMapper requests;
    private final OrderMapper orders;
    private final OrderItemMapper items;

    public AftersaleService(
        CurrentUserService user,
        AftersaleMapper requests,
        OrderMapper orders,
        OrderItemMapper items
    ) {
        this.user = user;
        this.requests = requests;
        this.orders = orders;
        this.items = items;
    }

    public EligibilityVO eligibility(String orderId) {
        long uid = user.requireUserId();
        var order = owned(uid, id(orderId));
        return new EligibilityVO(
            orderId,
            "RETURN_7D_V1",
            utc(deadline(order)),
            items
                .findByOwnedOrder(uid, order.getId())
                .stream()
                .map(item -> {
                    int available = item.getQuantity() - requests.occupied(item.getId());
                    String reason = denial(order, item, available);
                    return new EligibilityVO.Item(
                        item.getId().toString(),
                        item.getProductName(),
                        available,
                        money(item.getPaidAmount().subtract(requests.reservedAmount(item.getId()))),
                        reason == null,
                        reason
                    );
                })
                .toList()
        );
    }

    public AftersalePageVO list(AftersalePageQuery query, boolean staff) {
        long uid = user.requireUserId();
        if (staff) user.requireStaff();
        Long owner = staff ? null : uid;
        String status = query.getStatus() == null ? null : query.getStatus().name();
        long total = requests.count(owner, status);
        long offset = ((long) query.getPage() - 1) * query.getSize();
        var records =
            offset >= total
                ? List.<AftersaleVO>of()
                : requests
                      .page(owner, status, offset, query.getSize())
                      .stream()
                      .map(row -> view(row, false))
                      .toList();
        return new AftersalePageVO(records, query.getPage(), query.getSize(), total);
    }

    public AftersaleVO detail(String requestId, boolean staff) {
        long uid = user.requireUserId();
        if (staff) user.requireStaff();
        var row = requests.find(id(requestId));
        checkAccess(row, uid, staff);
        return view(row, true);
    }

    /** 草稿金额由后端计算；不创建申请、不占用数量。确认事务调用时订单锁保持至事务结束。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AftersaleQuoteVO quote(CreateAftersaleDTO body) {
        long uid = user.requireUserId(),
            oid = id(body.orderId()),
            iid = id(body.orderItemId());
        if (body.quantity() < 1) throw ApiRequestException.invalid("申请数量必须大于零");
        var order = requests.lockOrder(oid);
        if (order == null || order.getUserId() != uid) throw ApiRequestException.notFound();
        var item = items
            .findByOwnedOrder(uid, oid)
            .stream()
            .filter(i -> i.getId() == iid)
            .findFirst()
            .orElseThrow(ApiRequestException::notFound);
        int available = item.getQuantity() - requests.occupied(iid);
        String reason = denial(order, item, available);
        if (reason != null) throw conflict(reason);
        if (body.quantity() > available) throw conflict("申请数量超过剩余可申请数量，请重新选择");
        BigDecimal remaining = item.getPaidAmount().subtract(requests.reservedAmount(iid));
        return new AftersaleQuoteVO(
            allocation(item, body.quantity(), available, remaining),
            available,
            item.getProductName(),
            order.getOrderNumber(),
            "RETURN_7D_V1"
        );
    }

    private BigDecimal allocation(OrderItemPO item, int quantity, int available, BigDecimal remaining) {
        return quantity == available
            ? remaining
            : item
                  .getPaidAmount()
                  .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.DOWN)
                  .multiply(BigDecimal.valueOf(quantity))
                  .min(remaining);
    }

    // 所有售后写入均先锁同一订单行，再读取数量/金额；READ_COMMITTED 避免等待锁后读到旧快照。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AftersaleVO create(CreateAftersaleDTO body) {
        long uid = user.requireUserId(),
            oid = id(body.orderId()),
            iid = id(body.orderItemId());
        var order = requests.lockOrder(oid);
        if (order == null || order.getUserId() != uid) throw ApiRequestException.notFound();
        var existing = requests.findByKey(uid, body.requestKey());
        if (existing != null) {
            if (
                existing.getOrderId() != oid ||
                existing.getOrderItemId() != iid ||
                existing.getQuantity() != body.quantity() ||
                !existing.getReason().equals(body.reason().name()) ||
                !existing.getDescription().equals(body.description())
            ) throw conflict("同一个请求键不能用于不同申请内容，请重新发起申请");
            return view(existing, true);
        }
        var item = items
            .findByOwnedOrder(uid, oid)
            .stream()
            .filter(i -> i.getId() == iid)
            .findFirst()
            .orElseThrow(ApiRequestException::notFound);
        int available = item.getQuantity() - requests.occupied(iid);
        String reason = denial(order, item, available);
        if (reason != null) throw conflict(reason);
        if (body.quantity() > available) throw conflict("申请数量超过剩余可申请数量，请刷新后重试");
        BigDecimal remaining = item.getPaidAmount().subtract(requests.reservedAmount(iid));
        // 非最后一批按单件金额向下取分；最后一批结清剩余分币，累计不超过原实付。
        BigDecimal amount = allocation(item, body.quantity(), available, remaining);
        var row = new AftersalePO();
        row.setUserId(uid);
        row.setOrderId(oid);
        row.setOrderItemId(iid);
        row.setRequestKey(body.requestKey());
        row.setQuantity(body.quantity());
        row.setAmount(amount);
        row.setReason(body.reason().name());
        row.setDescription(body.description());
        try {
            requests.insert(row);
        } catch (DuplicateKeyException ex) {
            throw conflict("请求键已被另一申请使用，请查看售后记录");
        }
        requests.event(row.getId(), uid, "SUBMITTED", "用户提交退货退款申请");
        return view(requests.find(row.getId()), true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AftersaleVO cancel(String requestId) {
        long uid = user.requireUserId();
        var row = locked(id(requestId), uid, false);
        if (row.getStatus().equals("CANCELLED")) return view(row, true);
        transition(row, "CANCELLED", uid, "用户撤销申请");
        return view(requests.find(row.getId()), true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AftersaleVO review(String requestId, ReviewAftersaleDTO body) {
        long uid = user.requireUserId();
        user.requireStaff();
        var row = locked(id(requestId), uid, true);
        if (row.getUserId() == uid) throw new ApiRequestException(403, "FORBIDDEN", "不能审核自己的售后申请");
        String decision = body.decision().name();
        if (row.getStatus().equals(decision)) {
            var events = requests.events(row.getId());
            if (!events.isEmpty() && events.getLast().note().equals(body.note())) return view(row, true);
        }
        transition(row, decision, uid, body.note());
        return view(requests.find(row.getId()), true);
    }

    private void transition(AftersalePO row, String target, long actor, String note) {
        if (!row.getStatus().equals("PENDING") || requests.transition(row.getId(), target) != 1) throw conflict(
            "申请已被处理，无法执行此操作，请刷新详情"
        );
        requests.event(row.getId(), actor, target, note);
    }

    private AftersalePO locked(long requestId, long uid, boolean staff) {
        var row = requests.find(requestId);
        checkAccess(row, uid, staff);
        requests.lockOrder(row.getOrderId());
        row = requests.find(requestId);
        checkAccess(row, uid, staff);
        return row;
    }

    private void checkAccess(AftersalePO row, long uid, boolean staff) {
        if (row == null || (!staff && row.getUserId() != uid)) throw new ApiRequestException(
            404,
            "RESOURCE_NOT_FOUND",
            "售后申请不存在或不可访问"
        );
    }

    private OrderPO owned(long uid, long oid) {
        var order = orders.findOwnedById(uid, oid);
        if (order == null) throw ApiRequestException.notFound();
        return order;
    }

    private LocalDateTime deadline(OrderPO order) {
        return order.getSignedAt() == null ? null : order.getSignedAt().plusDays(7);
    }

    private String denial(OrderPO order, OrderItemPO item, int available) {
        if (
            order.getStatus() != OrderStatus.COMPLETED || order.getSignedAt() == null
        ) return "仅已签收完成的订单支持退货退款";
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isBefore(order.getSignedAt())) return "签收时间尚未到达，请核对订单";
        if (!now.isBefore(deadline(order))) return "已超过签收后 7 天的申请期限";
        if (item.getPaidAmount().signum() <= 0) return "零实付商品暂不支持退货退款";
        if (available <= 0) return "商品数量已被其他待审核或已通过申请占用";
        return null;
    }

    private AftersaleVO view(AftersalePO row, boolean detail) {
        var events = detail
            ? requests
                  .events(row.getId())
                  .stream()
                  .map(e -> new AftersaleVO.Event(e.action(), e.note(), utc(e.occurredAt())))
                  .toList()
            : List.<AftersaleVO.Event>of();
        return new AftersaleVO(
            row.getId().toString(),
            row.getOrderId().toString(),
            row.getOrderItemId().toString(),
            row.getOrderNumber(),
            row.getProductName(),
            row.getSpecification(),
            row.getQuantity(),
            money(row.getAmount()),
            "CNY",
            "RETURN_REFUND",
            row.getReason(),
            row.getDescription(),
            row.getStatus(),
            row.getRuleVersion(),
            utc(row.getCreatedAt()),
            utc(row.getUpdatedAt()),
            events
        );
    }

    private static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw ApiRequestException.invalid("ID 格式不正确");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw ApiRequestException.invalid("ID 超出有效范围");
        }
    }

    private static ApiRequestException conflict(String message) {
        return new ApiRequestException(409, "STATE_CONFLICT", message);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
