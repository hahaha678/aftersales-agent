package com.example.aftersales.support.controller;

import com.example.aftersales.common.exception.ContractNotImplementedException;
import com.example.aftersales.conversation.domain.vo.MessageVO;
import com.example.aftersales.support.domain.dto.*;
import com.example.aftersales.support.domain.query.TicketStatus;
import com.example.aftersales.support.domain.vo.TicketVO;
import com.example.aftersales.support.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api", produces = "application/json")
@Tag(name = "07 人工工单", description = "异步人工处理；只分享提交时已有对话，不自动执行售后或退款")
@SecurityRequirement(name = "bearerAuth")
public class TicketController {

    private final ObjectProvider<TicketService> services;

    public TicketController(ObjectProvider<TicketService> services) {
        this.services = services;
    }

    private TicketService service() {
        var s = services.getIfAvailable();
        if (s == null) throw new ContractNotImplementedException();
        return s;
    }

    @PostMapping("/tickets")
    @Operation(
        summary = "提交或复用本人会话未关闭工单",
        description = "返回 200。相同 requestKey 内容幂等；同一会话已有未关闭工单时返回原工单，不覆盖其问题及关联信息。"
    )
    public TicketVO create(@Valid @RequestBody CreateTicketDTO body) {
        return service().create(body);
    }

    @GetMapping("/tickets")
    @Operation(summary = "我的工单，每页最多50条，before为上页末条ID")
    public List<TicketVO> list(
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(defaultValue = "9223372036854775807") long before
    ) {
        return service().list(false, status, before);
    }

    @GetMapping("/staff/tickets")
    @Operation(summary = "客服工单队列，每页最多50条")
    public List<TicketVO> staffList(
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(defaultValue = "9223372036854775807") long before
    ) {
        return service().list(true, status, before);
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "查看本人工单与处理记录")
    public TicketVO detail(@PathVariable String id) {
        return service().detail(id, false);
    }

    @GetMapping("/staff/tickets/{id}")
    @Operation(summary = "客服查看工单详情")
    public TicketVO staffDetail(@PathVariable String id) {
        return service().detail(id, true);
    }

    @GetMapping("/tickets/{id}/context")
    @Operation(summary = "查看工单提交时已有的对话，每页50条")
    public List<MessageVO> context(
        @PathVariable String id,
        @RequestParam(defaultValue = "9223372036854775807") long before
    ) {
        return service().context(id, false, before);
    }

    @GetMapping("/staff/tickets/{id}/context")
    @Operation(summary = "客服通过工单读取提交时已有对话，每页50条，before为最早消息ID")
    public List<MessageVO> staffContext(
        @PathVariable String id,
        @RequestParam(defaultValue = "9223372036854775807") long before
    ) {
        return service().context(id, true, before);
    }

    @PutMapping("/staff/tickets/{id}/assignment")
    @Operation(summary = "当前客服领取工单，同一领取人重试幂等")
    public TicketVO claim(@PathVariable String id) {
        return service().claim(id);
    }

    @PutMapping("/staff/tickets/{id}/resolution")
    @Operation(summary = "领取客服回复并解决工单，相同结果重试幂等")
    public TicketVO resolve(@PathVariable String id, @Valid @RequestBody ResolveTicketDTO body) {
        return service().resolve(id, body);
    }
}
