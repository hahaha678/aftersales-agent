package com.example.aftersales.agent.service;

import com.example.aftersales.agent.domain.po.RunPO;
import com.example.aftersales.agent.domain.po.ToolCountPO;
import com.example.aftersales.agent.domain.query.MonitorFilter;
import com.example.aftersales.agent.domain.vo.*;
import com.example.aftersales.agent.mapper.AgentMonitorMapper;
import com.example.aftersales.common.exception.ApiRequestException;
import com.example.aftersales.identity.service.CurrentUserService;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class AgentMonitorService {

    private final AgentMonitorMapper mapper;
    private final CurrentUserService user;

    public AgentMonitorService(AgentMonitorMapper mapper, CurrentUserService user) {
        this.mapper = mapper;
        this.user = user;
    }

    public MonitorPageVO list(
        String status,
        String conversation,
        OffsetDateTime from,
        OffsetDateTime until,
        int page,
        int size
    ) {
        user.requireStaff();
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw ApiRequestException.invalid("分页范围不合法");
        if (
            status != null && !Set.of("RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED").contains(status)
        ) throw ApiRequestException.invalid("任务状态不合法");
        if (conversation != null) uuid(conversation);
        if (from != null && until != null && !from.isBefore(until)) throw ApiRequestException.invalid(
            "结束时间必须晚于开始时间"
        );
        var filter = new MonitorFilter(status, conversation, local(from), local(until), (page - 1) * size, size);
        long total = mapper.count(filter);
        var rows = mapper.page(filter);
        var counts = new HashMap<String, ToolCountPO>();
        if (!rows.isEmpty()) for (var count : mapper.counts(rows.stream().map(RunPO::id).toList()))
            counts.put(count.runId(), count);
        return new MonitorPageVO(
            rows
                .stream()
                .map(r -> view(r, false, counts.get(r.id())))
                .toList(),
            page,
            size,
            total
        );
    }

    public MonitorRunVO detail(String id) {
        user.requireStaff();
        var row = require(id);
        var counts = mapper.counts(List.of(id));
        return view(row, true, counts.isEmpty() ? null : counts.getFirst());
    }

    public List<ToolCallVO> tools(String id) {
        user.requireStaff();
        require(id);
        return mapper
            .tools(id)
            .stream()
            .map(t ->
                new ToolCallVO(
                    t.id().toString(),
                    t.toolName(),
                    t.status(),
                    t.durationMs(),
                    t.callIndex(),
                    utc(t.startedAt()),
                    utc(t.createdAt()),
                    t.inputSummary(),
                    t.resultSummary(),
                    t.errorCode()
                )
            )
            .toList();
    }

    private RunPO require(String id) {
        uuid(id);
        var row = mapper.find(id);
        if (row == null) throw ApiRequestException.notFound();
        return row;
    }

    private MonitorRunVO view(RunPO row, boolean detail, ToolCountPO counts) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean expired = row.status().equals("RUNNING") && !row.expiresAt().isAfter(now);
        String status = expired ? "EXPIRED" : row.status();
        LocalDateTime end = row.finishedAt() != null ? row.finishedAt() : expired ? row.expiresAt() : now;
        boolean own = row.userId() == user.requireUserId();
        // 客服仅共享执行元数据。原始输入/回答仍限本人，用户主动提交的上下文走工单快照。
        return new MonitorRunVO(
            row.id(),
            row.conversationId(),
            status,
            row.status(),
            row.model(),
            row.inputTokens(),
            row.outputTokens(),
            Math.max(0, Duration.between(row.createdAt(), end).toMillis()),
            utc(row.createdAt()),
            utc(row.finishedAt()),
            counts == null ? 0 : counts.total(),
            counts == null ? 0 : counts.failed(),
            error(row, expired),
            own,
            detail && own ? row.userContent() : null,
            detail && own ? row.assistantContent() : null,
            detail ? mapper.tickets(row.conversationId()) : List.of()
        );
    }

    private String error(RunPO row, boolean expired) {
        if (expired) return "LEASE_EXPIRED";
        if (row.status().equals("CANCELLED")) return "USER_CANCELLED";
        if (!row.status().equals("FAILED")) return null;
        return switch (Objects.toString(row.errorMessage(), "")) {
            case "处理超过 90 秒，任务已停止，请稍后重试" -> "RUN_TIMEOUT";
            case "当前请求较多，请稍后重试" -> "QUEUE_FULL";
            case "模型没有返回有效回答，请重试" -> "EMPTY_MODEL_REPLY";
            case "执行超时或服务已重启，请重新发送" -> "LEASE_EXPIRED";
            default -> "EXECUTION_FAILED";
        };
    }

    private void uuid(String value) {
        if (
            !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        ) throw ApiRequestException.invalid("任务或会话 ID 不合法");
    }

    private LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
