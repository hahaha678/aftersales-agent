package com.example.aftersales.aftersales.service;

import com.example.aftersales.aftersales.domain.dto.SimulateRefundDTO;
import com.example.aftersales.aftersales.domain.po.*;
import com.example.aftersales.aftersales.domain.vo.AftersaleVO;
import com.example.aftersales.aftersales.mapper.*;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.identity.service.CurrentUserService;
import com.example.aftersales.order.mapper.OrderItemMapper;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 本地模拟渠道：事务内记录结果，不调用支付接口。真实渠道接入需要独立发送、查询与补偿流程。 */
@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RefundService {

    private final CurrentUserService user;
    private final AftersaleMapper requests;
    private final RefundMapper refunds;
    private final OrderItemMapper items;
    private final AftersaleService aftersales;

    public RefundService(
        CurrentUserService user,
        AftersaleMapper requests,
        RefundMapper refunds,
        OrderItemMapper items,
        AftersaleService aftersales
    ) {
        this.user = user;
        this.requests = requests;
        this.refunds = refunds;
        this.items = items;
        this.aftersales = aftersales;
    }

    public AftersaleVO submit(String id, String key, SimulateRefundDTO body) {
        var row = lock(id);
        validateKey(key);
        var history = refunds.list(row.getId());
        // 网络重试必须复用请求键；旧请求的重复到达也不能产生新退款。
        var existing = history
            .stream()
            .filter(r -> r.requestKey().equals(key))
            .findFirst();
        if (existing.isPresent()) {
            var refund = existing.get();
            if (
                !refund.mode().equals(body.mode().name()) || !Objects.equals(refund.previousKey(), body.previousKey())
            ) throw conflict("同一退款请求键不能改变模拟方式或前序流水");
            return aftersales.detail(id, true);
        }
        if (!Set.of("RETURN_RECEIVED", "REFUND_FAILED").contains(row.getStatus())) throw conflict(
            "仅已收货或明确退款失败的申请可以发起退款；结果未知时请先查询"
        );
        String previous = history.isEmpty() ? null : history.getFirst().requestKey();
        if (
            !Objects.equals(previous, body.previousKey()) ||
            (!history.isEmpty() && !history.getFirst().status().equals("FAILED"))
        ) throw conflict("退款流水已变化，请刷新后核对最近的失败记录再重试");
        var item = items
            .findByOwnedOrder(row.getUserId(), row.getOrderId())
            .stream()
            .filter(i -> i.getId().equals(row.getOrderItemId()))
            .findFirst()
            .orElseThrow(ApiRequestException::notFound);
        if (
            row.getAmount().signum() <= 0 ||
            refunds.committedAmount(item.getId()).add(row.getAmount()).compareTo(item.getPaidAmount()) > 0
        ) throw conflict("退款金额超过商品剩余实付额度");
        String status = switch (body.mode()) {
            case SUCCESS -> "SUCCEEDED";
            case FAILURE -> "FAILED";
            case TIMEOUT_SUCCESS, TIMEOUT_FAILURE -> "UNKNOWN";
        };
        refunds.insert(
            new RefundPO(
                null,
                row.getId(),
                key,
                previous,
                "SIM-" + UUID.randomUUID(),
                row.getAmount(),
                body.mode().name(),
                status,
                user.requireUserId(),
                null,
                null
            )
        );
        advance(row, status);
        return aftersales.detail(id, true);
    }

    /** 超时后的查询只核对原流水，不创建新流水；结果保存在数据库，重启后仍能查询。 */
    public AftersaleVO reconcile(String id, String key) {
        var row = lock(id);
        validateKey(key);
        var refund = refunds
            .list(row.getId())
            .stream()
            .filter(r -> r.requestKey().equals(key))
            .findFirst()
            .orElseThrow(ApiRequestException::notFound);
        if (refund.status().equals("UNKNOWN")) {
            if (!row.getStatus().equals("REFUND_PENDING")) throw conflict("申请状态与退款流水不一致");
            String status = refund.mode().equals("TIMEOUT_SUCCESS") ? "SUCCEEDED" : "FAILED";
            if (refunds.resolve(refund.id(), status) != 1) throw conflict("退款状态已更新，请刷新");
            advance(row, status);
        }
        return aftersales.detail(id, true);
    }

    private void advance(AftersalePO row, String status) {
        String target = switch (status) {
            case "SUCCEEDED" -> "COMPLETED";
            case "FAILED" -> "REFUND_FAILED";
            default -> "REFUND_PENDING";
        };
        if (refunds.transition(row.getId(), row.getStatus(), target) != 1) throw conflict("售后状态已变化");
        String note = switch (status) {
            case "SUCCEEDED" -> "模拟渠道退款成功，售后已完成；未发生真实资金变动";
            case "FAILED" -> "模拟渠道明确退款失败，可核对后重新发起；未发生真实资金变动";
            default -> "模拟渠道响应超时，结果未知；请查询原流水，禁止直接再次退款";
        };
        requests.event(row.getId(), user.requireUserId(), target, note);
    }

    private AftersalePO lock(String id) {
        user.requireStaff();
        // 先复用访问与 ID 校验，再遵守所有售后写操作的订单锁顺序。
        var detail = aftersales.detail(id, true);
        requests.lockOrder(Long.parseLong(detail.orderId()));
        var row = requests.find(Long.parseLong(id));
        if (row.getUserId() == user.requireUserId()) throw new ApiRequestException(
            403,
            "FORBIDDEN",
            "不能处理自己的退款"
        );
        return row;
    }

    private void validateKey(String key) {
        if (
            key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        ) throw ApiRequestException.invalid("退款请求键必须为小写 UUID");
    }

    private ApiRequestException conflict(String message) {
        return new ApiRequestException(409, "STATE_CONFLICT", message);
    }
}
