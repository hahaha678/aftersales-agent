package com.example.aftersales.identity.controller;

import com.example.aftersales.common.domain.vo.ApiError;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import com.example.aftersales.identity.cache.CachedSession;
import com.example.aftersales.identity.domain.vo.UserVO;
import com.example.aftersales.identity.service.AuthFailure;
import com.example.aftersales.identity.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/users", produces = "application/json")
@Tag(name = "02 当前用户")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final ObjectProvider<SessionService> services;

    public UserController(ObjectProvider<SessionService> services) {
        this.services = services;
    }

    @GetMapping("/me")
    @Operation(
        summary = "查询当前用户",
        description = "从认证上下文获取用户，不接收 userId，不返回密码摘要。scaffold 返回 501。"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "目标：当前用户资料"),
        @ApiResponse(
            responseCode = "401",
            description = "目标：未登录或会话失效",
            content = @Content(schema = @Schema(implementation = ApiError.class))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "认证依赖不可用",
            content = @Content(schema = @Schema(implementation = ApiError.class))
        ),
        @ApiResponse(
            responseCode = "501",
            description = "scaffold 模式：契约占位",
            content = @Content(schema = @Schema(implementation = ApiError.class))
        ),
    })
    public UserVO me() {
        if (services.getIfAvailable() == null) throw new ContractNotImplementedException();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CachedSession session)) {
            throw AuthFailure.unauthenticated();
        }
        return SessionService.userView(session);
    }
}
