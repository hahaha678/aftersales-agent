package com.example.aftersales.aftersales.controller;

import com.example.aftersales.aftersales.domain.dto.*;
import com.example.aftersales.aftersales.domain.query.AftersalePageQuery;
import com.example.aftersales.aftersales.domain.vo.*;
import com.example.aftersales.aftersales.service.AftersaleService;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import com.example.aftersales.common.domain.vo.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping(value="/api",produces="application/json")
@Tag(name="04 售后申请", description="单商品项退货退款；审核通过为待退货，不执行支付退款")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
 @ApiResponse(responseCode="400",description="参数不合法",content=@Content(schema=@Schema(implementation=ApiError.class))),
 @ApiResponse(responseCode="401",description="未登录",content=@Content(schema=@Schema(implementation=ApiError.class))),
 @ApiResponse(responseCode="403",description="非客服或审核自己的申请",content=@Content(schema=@Schema(implementation=ApiError.class))),
 @ApiResponse(responseCode="404",description="不存在或不属于当前用户",content=@Content(schema=@Schema(implementation=ApiError.class))),
 @ApiResponse(responseCode="409",description="数量、资格、幂等键或状态冲突",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class AftersaleController {
 private final ObjectProvider<AftersaleService> services;
 public AftersaleController(ObjectProvider<AftersaleService> services){this.services=services;}
 private AftersaleService service(){var value=services.getIfAvailable();if(value==null)throw new ContractNotImplementedException();return value;}
 @GetMapping("/orders/{orderId}/aftersale-eligibility")
 @Operation(summary="查询订单售后资格",description="项目演示规则 RETURN_7D_V1：完成签收后 7 天内，按商品项返回可申请数量及不能申请原因。提交时重新校验。")
 public EligibilityVO eligibility(@PathVariable String orderId){return service().eligibility(orderId);}
 @PostMapping(value="/aftersales",consumes="application/json")
 @Operation(summary="提交退货退款申请",description="金额由后端计算；同一用户同一 requestKey 和内容重试返回原申请，不重复占用。")
 @ApiResponse(responseCode="201",description="创建或返回同一次提交的申请，Location 指向详情")
 public ResponseEntity<AftersaleVO> create(@Valid @RequestBody CreateAftersaleDTO body){
  var result=service().create(body);return ResponseEntity.created(URI.create("/api/aftersales/"+result.id())).body(result);
 }
 @GetMapping("/aftersales")
 @Operation(summary="分页查询我的售后申请")
 public AftersalePageVO list(@Valid @ModelAttribute @ParameterObject AftersalePageQuery query){return service().list(query,false);}
 @GetMapping("/aftersales/{id}")
 @Operation(summary="查询我的售后详情和处理记录")
 public AftersaleVO detail(@PathVariable String id){return service().detail(id,false);}
 @PostMapping("/aftersales/{id}/cancellation")
 @Operation(summary="撤销待审核申请",description="仅 PENDING 可撤销，已撤销重试返回原结果；历史记录保留。")
 public AftersaleVO cancel(@PathVariable String id){return service().cancel(id);}
 @GetMapping("/staff/aftersales")
 @Operation(summary="客服分页查询售后申请",description="跨用户查询，仅 STAFF 可访问。")
 public AftersalePageVO staffList(@Valid @ModelAttribute @ParameterObject AftersalePageQuery query){return service().list(query,true);}
 @GetMapping("/staff/aftersales/{id}")
 @Operation(summary="客服查看售后详情")
 public AftersaleVO staffDetail(@PathVariable String id){return service().detail(id,true);}
 @PostMapping(value="/staff/aftersales/{id}/review",consumes="application/json")
 @Operation(summary="客服审核申请",description="仅待审核可通过或拒绝，必须填写意见；通过为待退货，不触发退款。相同决定和意见重试不重复写记录。")
 public AftersaleVO review(@PathVariable String id,@Valid @RequestBody ReviewAftersaleDTO body){return service().review(id,body);}
}
