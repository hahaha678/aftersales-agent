package com.example.aftersales.order.controller;

import com.example.aftersales.order.domain.query.OrderPageQuery;
import com.example.aftersales.order.domain.vo.OrderDetailVO;
import com.example.aftersales.order.domain.vo.OrderPageVO;
import com.example.aftersales.order.domain.vo.ShipmentVO;
import com.example.aftersales.common.domain.vo.ApiError;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springdoc.core.annotations.ParameterObject;
import java.util.List;
import com.example.aftersales.order.service.OrderService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/orders", produces = "application/json")
@Tag(name = "03 我的订单", description = "始终按当前身份过滤；用户传入的 ID 不能改变归属范围")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "参数格式、分页范围或枚举值错误", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "401", description = "未登录或会话失效", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "503", description = "认证依赖不可用", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "501", description = "scaffold 模式：契约占位", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class OrderController {
    private final ObjectProvider<OrderService> services;
    public OrderController(ObjectProvider<OrderService> services) { this.services = services; }
    private OrderService service() {
        var service = services.getIfAvailable();
        if (service == null) throw new ContractNotImplementedException();
        return service;
    }
    @GetMapping
    @Operation(summary = "分页查询我的订单", description = "按 createdAt DESC、id DESC 稳定排序；空结果和超出末页返回空 items。身份来自认证上下文。scaffold 返回 501。")
    @ApiResponse(responseCode = "200", description = "目标：分页结果")
    public OrderPageVO list(@Valid @ModelAttribute @ParameterObject OrderPageQuery query) {
        return service().list(query);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "查询我的订单详情", description = "包含全部订单商品项；对不存在和不属于当前用户的订单统一返回 404。scaffold 返回 501。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "目标：订单详情"),
        @ApiResponse(responseCode = "404", description = "目标：订单不存在或不可访问", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrderDetailVO get(
            @Parameter(description = "正整数形式的字符串 ID，最多 19 位", example = "2001")
            @PathVariable @Pattern(regexp = "[1-9][0-9]{0,18}") String orderId) {
        return service().detail(orderId);
    }

    @GetMapping("/{orderId}/shipments")
    @Operation(summary = "查询订单物流", description = "目标：先校验订单归属；未发货返回空数组。首版最多一个原始发货包裹，按发货时间升序。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "目标：物流数组；未发货为 []"),
        @ApiResponse(responseCode = "404", description = "目标：订单不存在或不可访问", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<ShipmentVO> shipments(
            @Parameter(description = "订单 ID", example = "2001")
            @PathVariable @Pattern(regexp = "[1-9][0-9]{0,18}") String orderId) {
        return service().shipments(orderId);
    }
}
