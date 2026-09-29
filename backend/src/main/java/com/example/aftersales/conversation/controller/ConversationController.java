package com.example.aftersales.conversation.controller;

import com.example.aftersales.aftersales.domain.dto.ConfirmDraftDTO;
import com.example.aftersales.aftersales.domain.vo.DraftVO;
import com.example.aftersales.aftersales.service.AftersaleDraftService;
import com.example.aftersales.agent.domain.vo.RunVO;
import com.example.aftersales.agent.service.AgentService;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import com.example.aftersales.conversation.domain.dto.*;
import com.example.aftersales.conversation.domain.vo.*;
import com.example.aftersales.conversation.service.ConversationService;
import com.example.aftersales.identity.security.TokenCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Agent 的 HTTP 入口：创建任务和订阅结果分开，正式售后确认走独立接口。 */
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "05 智能售后", description = "会话按用户隔离；模型只能查询和生成草稿，确认申请独立鉴权")
public class ConversationController {

    // 业务 Bean 按配置启用，ObjectProvider 允许脚手架模式保留接口而不强制创建业务依赖。
    private final ObjectProvider<ConversationService> conversations;
    private final ObjectProvider<AgentService> agents;
    private final ObjectProvider<AftersaleDraftService> drafts;

    public ConversationController(
        ObjectProvider<ConversationService> conversations,
        ObjectProvider<AgentService> agents,
        ObjectProvider<AftersaleDraftService> drafts
    ) {
        this.conversations = conversations;
        this.agents = agents;
        this.drafts = drafts;
    }

    private <T> T get(ObjectProvider<T> provider) {
        var value = provider.getIfAvailable();
        if (value == null) throw new ContractNotImplementedException();
        return value;
    }

    @GetMapping("/agent/status")
    @Operation(summary = "查询模型是否配置")
    public Map<String, Object> status() {
        return get(agents).status();
    }

    @PostMapping("/conversations")
    @Operation(summary = "创建聊天会话")
    public ResponseEntity<ConversationVO> create(@Valid @RequestBody ConversationDTO body) {
        var value = get(conversations).create(body);
        return ResponseEntity.created(URI.create("/api/conversations/" + value.id() + "/messages")).body(value);
    }

    @GetMapping("/conversations")
    @Operation(summary = "最近 100 个本人聊天会话")
    public List<ConversationVO> list() {
        return get(conversations).list();
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "读取消息，每页最多 50 条；before 为上一页最早消息 ID")
    public List<MessageVO> messages(
        @PathVariable String id,
        @RequestParam(defaultValue = "9223372036854775807") long before
    ) {
        return get(conversations).messages(id, before);
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "发送消息，返回任务；相同 requestKey 重试不重复执行模型")
    public ResponseEntity<RunVO> send(
        @PathVariable String id,
        @Valid @RequestBody SendMessageDTO body,
        HttpServletRequest request
    ) {
        // 将请求令牌交给服务端工具闭包重新鉴权，不把令牌放入用户消息或模型参数。
        var value = get(agents).send(id, body, TokenCodec.fromRequest(request));
        // 202 表示已接受任务，不代表模型已经完成回答；Location 指向后续查询地址。
        return ResponseEntity.accepted()
            .location(URI.create("/api/agent-runs/" + value.id()))
            .body(value);
    }

    @GetMapping("/conversations/{id}/active-run")
    @Operation(summary = "查询尚在执行的任务，无任务返回 204")
    public ResponseEntity<RunVO> active(@PathVariable String id) {
        var value = get(conversations).active(id);
        return value == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(value);
    }

    @GetMapping("/agent-runs/{id}")
    @Operation(summary = "读取任务状态与已完成回答")
    public RunVO run(@PathVariable String id) {
        return get(agents).get(id);
    }

    @GetMapping(value = "/agent-runs/{id}/events", produces = "text/event-stream")
    @Operation(summary = "SSE 结果流，使用 Bearer 请求头；断开不会重新执行任务")
    public SseEmitter events(@PathVariable String id) {
        return get(agents).events(id);
    }

    @PostMapping("/agent-runs/{id}/cancellation")
    @Operation(summary = "停止模型任务，不撤销已生成的草稿或已提交申请")
    public RunVO cancel(@PathVariable String id) {
        return get(agents).cancel(id);
    }

    @GetMapping("/conversations/{id}/drafts")
    @Operation(summary = "读取当前用户会话内最近 50 个申请草稿")
    public List<DraftVO> drafts(@PathVariable String id) {
        return get(drafts).list(id);
    }

    @GetMapping("/aftersale-drafts/{id}")
    @Operation(summary = "读取本人申请草稿")
    public DraftVO draft(@PathVariable String id) {
        return get(drafts).get(id);
    }

    @PostMapping("/aftersale-drafts/{id}/confirmation")
    @Operation(summary = "用户确认后提交；金额或数量变化返回 confirmed=false 和新版本，必须再次确认")
    public com.example.aftersales.aftersales.domain.vo.DraftConfirmationVO confirm(
        @PathVariable String id,
        @Valid @RequestBody ConfirmDraftDTO body
    ) {
        // 用户确认是单独的业务请求，不注册为模型工具，避免模型自行绕过确认步骤。
        return get(drafts).confirm(id, body);
    }

    @PostMapping("/aftersale-drafts/{id}/cancellation")
    @Operation(summary = "取消尚未提交的草稿")
    public DraftVO cancelDraft(@PathVariable String id) {
        return get(drafts).cancel(id);
    }
}
