package com.example.aftersales.identity.controller;

import com.example.aftersales.identity.domain.vo.UserVO;
import com.example.aftersales.common.api.ApiError;
import com.example.aftersales.common.api.ContractNotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/users", produces = "application/json")
@Tag(name = "02 当前用户")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    @GetMapping("/me")
    @Operation(summary = "查询当前用户", description = "目标：从认证上下文获取用户，不接收 userId；不返回密码或密码摘要。当前鉴权未实现，返回 501。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "目标：当前用户资料"),
        @ApiResponse(responseCode = "401", description = "目标：未登录或会话失效", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "501", description = "当前实现：契约占位", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserVO me() {
        throw new ContractNotImplementedException();
    }
}
