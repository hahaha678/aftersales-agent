package com.example.aftersales.support.service;

import com.example.aftersales.aftersales.mapper.AftersaleMapper;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.conversation.domain.vo.MessageVO;
import com.example.aftersales.conversation.mapper.ConversationMapper;
import com.example.aftersales.identity.service.CurrentUserService;
import com.example.aftersales.order.mapper.OrderMapper;
import com.example.aftersales.support.domain.dto.*;
import com.example.aftersales.support.domain.po.TicketPO;
import com.example.aftersales.support.domain.query.TicketStatus;
import com.example.aftersales.support.domain.vo.TicketVO;
import com.example.aftersales.support.mapper.TicketMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class TicketService {

    private final TicketMapper tickets;
    private final ConversationMapper conversations;
    private final CurrentUserService user;
    private final OrderMapper orders;
    private final AftersaleMapper aftersales;

    public TicketService(
        TicketMapper tickets,
        ConversationMapper conversations,
        CurrentUserService user,
        OrderMapper orders,
        AftersaleMapper aftersales
    ) {
        this.tickets = tickets;
        this.conversations = conversations;
        this.user = user;
        this.orders = orders;
        this.aftersales = aftersales;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TicketVO create(CreateTicketDTO body) {
        long uid = user.requireUserId();
        // 同一用户创建串行，数据库另以生成列唯一约束保证单会话最多一个未关闭工单。
        conversations.lockUser(uid);
        if (conversations.owned(body.conversationId(), uid) == null) throw ApiRequestException.notFound();
        String fingerprint = fingerprint(body);
        Long submitted = tickets.submitted(uid, body.requestKey(), fingerprint);
        if (submitted != null) return view(tickets.find(submitted), true);
        if (tickets.keyExists(uid, body.requestKey()) > 0) throw conflict("同一请求键不能用于不同的问题或关联信息");
        Long oid = body.orderId() == null ? null : id(body.orderId());
        Long aid = body.aftersaleId() == null ? null : id(body.aftersaleId());
        if (oid != null && orders.findOwnedById(uid, oid) == null) throw ApiRequestException.notFound();
        if (aid != null) {
            var sale = aftersales.find(aid);
            if (sale == null || sale.getUserId() != uid) throw ApiRequestException.notFound();
            if (oid != null && !oid.equals(sale.getOrderId())) throw conflict("关联售后单不属于所选订单");
            oid = sale.getOrderId();
        }
        var row = tickets.active(body.conversationId());
        if (row == null) {
            tickets.insert(
                new TicketPO(
                    null,
                    uid,
                    body.conversationId(),
                    body.requestKey(),
                    body.problem(),
                    oid,
                    aid,
                    tickets.contextEnd(body.conversationId()),
                    "OPEN",
                    null,
                    null,
                    null,
                    null
                )
            );
            row = tickets.byKey(uid, body.requestKey());
            tickets.snapshot(row.id(), row.conversationId(), row.contextEndId());
            tickets.event(row.id(), uid, "OPEN", "用户提交人工处理工单");
        }
        tickets.submission(uid, body.requestKey(), fingerprint, row.id());
        return view(row, true);
    }

    public List<TicketVO> list(boolean staff, TicketStatus status, long before) {
        long uid = user.requireUserId();
        if (staff) user.requireStaff();
        if (before < 1) throw ApiRequestException.invalid("游标必须为正整数");
        return tickets
            .list(staff ? null : uid, status == null ? null : status.name(), before)
            .stream()
            .map(r -> view(r, false))
            .toList();
    }

    public TicketVO detail(String id, boolean staff) {
        return view(access(id, staff, false), true);
    }

    public List<MessageVO> context(String id, boolean staff, long before) {
        var row = access(id, staff, false);
        if (before < 1) throw ApiRequestException.invalid("游标必须为正整数");
        var result = new ArrayList<>(tickets.context(row.id(), before));
        Collections.reverse(result);
        return result.stream().map(MessageVO::of).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TicketVO claim(String id) {
        var row = access(id, true, true);
        long uid = user.requireUserId();
        if (row.userId() == uid) throw forbidden("不能领取自己的工单");
        if (row.status().equals("IN_PROGRESS") && Objects.equals(row.assigneeId(), uid)) return view(row, true);
        if (!row.status().equals("OPEN") || tickets.claim(row.id(), uid) != 1) throw conflict(
            "工单已被领取或解决，请刷新"
        );
        tickets.event(row.id(), uid, "IN_PROGRESS", "客服已领取工单");
        return view(tickets.find(row.id()), true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TicketVO resolve(String id, ResolveTicketDTO body) {
        var row = access(id, true, true);
        long uid = user.requireUserId();
        if (!Objects.equals(row.assigneeId(), uid) || row.userId() == uid) throw forbidden(
            "仅领取此工单的客服可以提交处理结果"
        );
        if (row.status().equals("RESOLVED") && Objects.equals(row.resolution(), body.resolution())) return view(
            row,
            true
        );
        if (!row.status().equals("IN_PROGRESS") || tickets.resolve(row.id(), body.resolution()) != 1) throw conflict(
            "工单已解决，不能覆盖原处理结果"
        );
        tickets.event(row.id(), uid, "RESOLVED", body.resolution());
        return view(tickets.find(row.id()), true);
    }

    private TicketPO access(String id, boolean staff, boolean lock) {
        long uid = user.requireUserId();
        if (staff) user.requireStaff();
        var row = lock ? tickets.lock(id(id)) : tickets.find(id(id));
        if (row == null || (!staff && row.userId() != uid)) throw ApiRequestException.notFound();
        return row;
    }

    private TicketVO view(TicketPO row, boolean detail) {
        return new TicketVO(
            row.id().toString(),
            row.conversationId(),
            row.problem(),
            str(row.orderId()),
            str(row.aftersaleId()),
            row.status(),
            str(row.assigneeId()),
            row.resolution(),
            row.createdAt().atOffset(ZoneOffset.UTC),
            row.updatedAt().atOffset(ZoneOffset.UTC),
            detail
                ? tickets
                      .events(row.id())
                      .stream()
                      .map(e -> new TicketVO.Event(e.action(), e.note(), e.occurredAt().atOffset(ZoneOffset.UTC)))
                      .toList()
                : List.of()
        );
    }

    private String str(Long value) {
        return value == null ? null : value.toString();
    }

    private long id(String value) {
        try {
            if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiRequestException.invalid("工单或关联资源 ID 不合法");
        }
    }

    private String fingerprint(CreateTicketDTO body) {
        try {
            String value =
                body.conversationId() + "|" + body.orderId() + "|" + body.aftersaleId() + "|" + body.problem();
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ApiRequestException conflict(String message) {
        return new ApiRequestException(409, "STATE_CONFLICT", message);
    }

    private ApiRequestException forbidden(String message) {
        return new ApiRequestException(403, "FORBIDDEN", message);
    }
}
