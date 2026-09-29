package com.example.aftersales.agent.controller;

import com.example.aftersales.agent.domain.vo.*;
import com.example.aftersales.agent.service.AgentMonitorService;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/staff/agent-runs", produces = "application/json")
@SecurityRequirement(name = "bearerAuth")
@Tag(
    name = "08 Agent 执行监控",
    description = "客服只读执行元数据；原始输入和回答仅任务本人可见，其他会话通过用户提交的工单快照查看"
)
public class AgentMonitorController {

    private final ObjectProvider<AgentMonitorService> services;

    public AgentMonitorController(ObjectProvider<AgentMonitorService> services) {
        this.services = services;
    }

    private AgentMonitorService service() {
        var s = services.getIfAvailable();
        if (s == null) throw new ContractNotImplementedException();
        return s;
    }

    @GetMapping
    @Operation(
        summary = "筛选任务，时间区间为开始时间包含from、不含until",
        description = "EXPIRED是已超出租约的RUNNING记录的只读展示状态；读取不修改任务、不重放模型。"
    )
    public MonitorPageVO list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String conversationId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service().list(status, conversationId, from, until, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务详情、工具数量和错误分类；耗时含排队，不含后续用户确认")
    public MonitorRunVO detail(@PathVariable String id) {
        return service().detail(id);
    }

    @GetMapping("/{id}/tool-calls")
    @Operation(summary = "按调用顺序读取工具审计，旧记录可能缺少摘要和序号")
    public List<ToolCallVO> tools(@PathVariable String id) {
        return service().tools(id);
    }
}
