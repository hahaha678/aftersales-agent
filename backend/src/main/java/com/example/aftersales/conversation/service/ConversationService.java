package com.example.aftersales.conversation.service;

import com.example.aftersales.agent.domain.po.RunPO;
import com.example.aftersales.agent.domain.vo.RunVO;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.conversation.domain.dto.*;
import com.example.aftersales.conversation.domain.vo.*;
import com.example.aftersales.conversation.mapper.ConversationMapper;
import com.example.aftersales.identity.service.CurrentUserService;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 管理会话归属、消息历史和任务状态，防止重复发送导致重复执行。 */
@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class ConversationService {

    private final ConversationMapper mapper;
    private final CurrentUserService user;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public ConversationService(
        ConversationMapper mapper,
        CurrentUserService user,
        org.springframework.transaction.PlatformTransactionManager manager
    ) {
        this.mapper = mapper;
        this.user = user;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
    }

    public ConversationVO create(ConversationDTO body) {
        String id = UUID.randomUUID().toString();
        long uid = user.requireUserId();
        mapper.create(id, uid, body.title());
        return ConversationVO.of(mapper.owned(id, uid));
    }

    public List<ConversationVO> list() {
        return mapper.list(user.requireUserId()).stream().map(ConversationVO::of).toList();
    }

    public void requireOwned(String id) {
        if (mapper.owned(id, user.requireUserId()) == null) throw missing();
    }

    public List<MessageVO> messages(String id, long before) {
        requireOwned(id);
        if (before < 1) throw ApiRequestException.invalid("消息游标必须为正整数");
        expire(user.requireUserId());
        var result = new ArrayList<>(mapper.messages(id, before));
        Collections.reverse(result);
        return result.stream().map(MessageVO::of).toList();
    }

    public RunVO active(String id) {
        requireOwned(id);
        expire(user.requireUserId());
        var row = mapper.activeRun(id);
        return row == null ? null : RunVO.of(row);
    }

    public RunPO ownedRun(String id) {
        expire(user.requireUserId());
        var row = mapper.run(id);
        if (row == null || row.userId() != user.requireUserId()) throw missing();
        return row;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Start begin(String conversation, SendMessageDTO body, String model) {
        // 锁定当前用户后再检查请求键和活动任务，避免并发请求同时通过检查。
        long uid = user.requireUserId();
        mapper.lockUser(uid);
        requireOwned(conversation);
        expire(uid);
        var previous = mapper.byKey(uid, body.requestKey());
        if (previous != null) {
            if (
                !previous.conversationId().equals(conversation) || !previous.userContent().equals(body.content())
            ) throw conflict("消息请求键已用于其他内容");
            return new Start(previous, false);
        }
        if (mapper.active(uid) > 0) throw conflict("你有一条消息正在处理，请等待完成或停止后再发送");
        if (mapper.recent(uid) >= 10) throw new ApiRequestException(429, "RATE_LIMITED", "发送过于频繁，请稍后再试");
        String id = UUID.randomUUID().toString();
        mapper.begin(id, conversation, uid, body.requestKey(), body.content(), model);
        mapper.message(conversation, id, "USER", body.content(), "SUCCEEDED");
        return new Start(mapper.run(id), true);
    }

    public record Start(RunPO run, boolean fresh) {}

    @Transactional
    public void finish(String id, String status, String content, String error, int input, int output) {
        transactions.executeWithoutResult(tx -> {
            // 只有成功从运行态切换到终态的请求，才能追加助手消息。
            var row = mapper.run(id);
            if (row == null) return;
            if (mapper.finish(id, status, content, error, input, output) == 1) mapper.message(
                row.conversationId(),
                id,
                "ASSISTANT",
                content,
                status
            );
        });
    }

    // 过期任务由数据库租约回收；不在服务器重启后自动重放付费调用。
    @Transactional
    public void expire(long uid) {
        for (var row : mapper.expired(uid))
            finish(
                row.id(),
                "FAILED",
                row.assistantContent(),
                "执行超时或服务已重启，请重新发送",
                row.inputTokens(),
                row.outputTokens()
            );
    }

    public boolean running(String id) {
        var row = mapper.run(id);
        return (
            row != null &&
            row.status().equals("RUNNING") &&
            row.expiresAt().isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
        );
    }

    public List<RunPO> history(String conversation) {
        var list = new ArrayList<>(mapper.history(conversation));
        Collections.reverse(list);
        return list;
    }

    public RunPO internalRun(String id) {
        return mapper.run(id);
    }

    public void audit(String run, String tool, String status, long duration) {
        mapper.audit(run, tool, status, duration);
    }

    private static ApiRequestException missing() {
        return new ApiRequestException(404, "RESOURCE_NOT_FOUND", "会话或任务不存在或不可访问");
    }

    private static ApiRequestException conflict(String text) {
        return new ApiRequestException(409, "STATE_CONFLICT", text);
    }
}
