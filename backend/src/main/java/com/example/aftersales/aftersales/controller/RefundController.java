package com.example.aftersales.aftersales.controller;

import com.example.aftersales.aftersales.domain.dto.SimulateRefundDTO;
import com.example.aftersales.aftersales.domain.vo.AftersaleVO;
import com.example.aftersales.aftersales.service.RefundService;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/staff/aftersales/{id}/refunds", produces = "application/json")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "04 售后申请", description = "包含模拟退款演示，不涉及真实资金")
public class RefundController {

    private final ObjectProvider<RefundService> services;

    public RefundController(ObjectProvider<RefundService> services) {
        this.services = services;
    }

    private RefundService service() {
        var value = services.getIfAvailable();
        if (value == null) throw new ContractNotImplementedException();
        return value;
    }

    @PutMapping(value = "/{requestKey}", consumes = "application/json")
    @Operation(
        summary = "客服发起或重试模拟退款",
        description = "requestKey 为小写 UUID，相同键和内容幂等。金额取自售后单。首次 previousKey 为空；明确失败后新建请求键并携带最近失败的 previousKey。未知结果禁止重试。仅客服可操作且不能处理自己的申请。"
    )
    public AftersaleVO submit(
        @PathVariable String id,
        @PathVariable String requestKey,
        @Valid @RequestBody SimulateRefundDTO body
    ) {
        return service().submit(id, requestKey, body);
    }

    @PostMapping("/{requestKey}/reconciliation")
    @Operation(
        summary = "查询并同步模拟渠道退款结果",
        description = "仅查询原退款流水，不创建新退款。模拟超时场景在本次查询时返回预设成功或失败，重复查询幂等；普通 GET 详情不改变状态。"
    )
    public AftersaleVO reconcile(@PathVariable String id, @PathVariable String requestKey) {
        return service().reconcile(id, requestKey);
    }
}
