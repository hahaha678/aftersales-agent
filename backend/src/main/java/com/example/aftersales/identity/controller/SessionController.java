package com.example.aftersales.identity.controller;

import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.domain.vo.SessionVO;
import com.example.aftersales.common.domain.vo.ApiError;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import com.example.aftersales.identity.service.SessionService;
import com.example.aftersales.identity.security.TokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/sessions", produces = "application/json")
@Tag(name = "01 登录会话", description = "local 模式使用 MySQL + Redis；scaffold 模式保留契约占位")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "请求格式或字段错误", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "401", description = "目标：凭据错误或会话失效", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "503", description = "认证依赖不可用，请稍后重试", content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(responseCode = "501", description = "scaffold 模式：契约占位", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class SessionController {
    private final ObjectProvider<SessionService> services;
    public SessionController(ObjectProvider<SessionService> services) { this.services = services; }
    private SessionService service() {
        var service = services.getIfAvailable();
        if (service == null) throw new ContractNotImplementedException();
        return service;
    }
    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建登录会话", description = "local 模式校验账号密码后创建可撤销会话；scaffold 返回 501。依赖服务不可用返回 503。")
    @ApiResponse(responseCode = "201", description = "目标：登录成功；凭据响应禁止缓存",
            headers = @Header(name = "Location", description = "当前会话资源地址", schema = @Schema(example = "/api/sessions/current")))
    public SessionVO create(@Valid @RequestBody CreateSessionDTO request, HttpServletResponse response) {
        SessionVO result = service().login(request);
        response.setHeader("Location", "/api/sessions/current");
        return result;
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "撤销当前登录会话", description = "目标：只撤销当前 Bearer 对应的会话，不影响其他设备。携带已撤销的同一令牌重复请求返回 204；缺少或伪造令牌返回 401。")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "目标：当前会话已撤销，无响应体", content = @Content)
    public void deleteCurrent(HttpServletRequest request) {
        service().logout(TokenCodec.fromRequest(request));
    }
}
