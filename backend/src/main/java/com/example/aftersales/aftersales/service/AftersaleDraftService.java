package com.example.aftersales.aftersales.service;

import com.example.aftersales.aftersales.domain.AftersaleReason;
import com.example.aftersales.aftersales.domain.dto.*;
import com.example.aftersales.aftersales.domain.po.AftersaleDraftPO;
import com.example.aftersales.aftersales.domain.vo.*;
import com.example.aftersales.aftersales.mapper.AftersaleDraftMapper;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.conversation.service.ConversationService;
import com.example.aftersales.identity.service.CurrentUserService;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 模型只生成待确认草稿；用户确认后重新核价，再创建正式售后申请。 */
@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AftersaleDraftService {

    private final AftersaleDraftMapper drafts;
    private final AftersaleService sales;
    private final ConversationService conversations;
    private final CurrentUserService user;
    private final Validator validator;

    public AftersaleDraftService(
        AftersaleDraftMapper drafts,
        AftersaleService sales,
        ConversationService conversations,
        CurrentUserService user,
        Validator validator
    ) {
        this.drafts = drafts;
        this.sales = sales;
        this.conversations = conversations;
        this.user = user;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DraftVO create(String runId, DraftDTO body) {
        if (!validator.validate(body).isEmpty()) throw ApiRequestException.invalid(
            "草稿字段不合法，请补充订单、商品、数量、原因及描述"
        );
        var run = conversations.ownedRun(runId);
        if (!conversations.running(runId)) throw conflict("当前任务已结束，不能继续生成草稿");
        String id = UUID.randomUUID().toString();
        var input = new CreateAftersaleDTO(
            body.orderId(),
            body.orderItemId(),
            body.quantity(),
            body.reason(),
            body.description(),
            id
        );
        var quote = sales.quote(input);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var row = new AftersaleDraftPO(
            id,
            user.requireUserId(),
            run.conversationId(),
            runId,
            Long.parseLong(body.orderId()),
            Long.parseLong(body.orderItemId()),
            body.quantity(),
            body.reason().name(),
            body.description(),
            quote.amount(),
            quote.availableQuantity(),
            quote.productName(),
            quote.orderNumber(),
            quote.ruleVersion(),
            1,
            "READY",
            null,
            now,
            now.plusMinutes(15)
        );
        drafts.insert(row);
        return DraftVO.of(row);
    }

    public List<DraftVO> list(String conversation) {
        conversations.requireOwned(conversation);
        return drafts.list(conversation, user.requireUserId()).stream().map(DraftVO::of).toList();
    }

    public DraftVO get(String id) {
        return DraftVO.of(owned(id, false));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DraftConfirmationVO confirm(String id, ConfirmDraftDTO body) {
        // 加锁读取草稿，重复确认返回已有申请，不重复建单。
        var row = owned(id, true);
        if (!body.confirmed()) throw ApiRequestException.invalid("请先确认申请内容");
        if (row.status().equals("CONFIRMED")) return new DraftConfirmationVO(
            true,
            DraftVO.of(row),
            sales.detail(row.aftersaleId().toString(), false),
            "已提交"
        );
        if (
            !row.status().equals("READY") || !row.expiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
        ) throw conflict("草稿已失效或已取消，请重新生成");
        if (row.version() != body.version()) throw conflict("草稿已更新，请刷新后重新核对并确认");
        var input = new CreateAftersaleDTO(
            "" + row.orderId(),
            "" + row.orderItemId(),
            row.quantity(),
            AftersaleReason.valueOf(row.reason()),
            row.description(),
            "draft-" + row.id()
        );
        var quote = sales.quote(input); // 持有订单锁直到确认事务结束，金额检查与创建之间没有并发窗口。
        if (quote.amount().compareTo(row.amount()) != 0 || quote.availableQuantity() != row.availableQuantity()) {
            drafts.refresh(id, quote.amount(), quote.availableQuantity());
            return new DraftConfirmationVO(
                false,
                DraftVO.of(owned(id, false)),
                null,
                "可申请数量或金额已变化，请重新核对后确认"
            );
        }
        var application = sales.create(input);
        drafts.confirm(id, Long.parseLong(application.id()));
        return new DraftConfirmationVO(true, DraftVO.of(owned(id, false)), application, "申请已提交，等待客服审核");
    }

    @Transactional
    public DraftVO cancel(String id) {
        var row = owned(id, true);
        if (row.status().equals("CONFIRMED")) throw conflict("草稿已提交，请在售后记录中处理申请");
        drafts.cancel(id);
        return DraftVO.of(owned(id, false));
    }

    private AftersaleDraftPO owned(String id, boolean lock) {
        var row = lock ? drafts.lock(id, user.requireUserId()) : drafts.owned(id, user.requireUserId());
        if (row == null) throw new ApiRequestException(404, "RESOURCE_NOT_FOUND", "草稿不存在或不可访问");
        return row;
    }

    private static ApiRequestException conflict(String message) {
        return new ApiRequestException(409, "STATE_CONFLICT", message);
    }
}
