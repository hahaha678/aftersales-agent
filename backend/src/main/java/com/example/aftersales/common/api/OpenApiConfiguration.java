package com.example.aftersales.common.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "电商售后 Agent · 接口契约草案",
        version = "0.1.0-draft",
        description = "本轮仅设计用户与订单接口，无数据库读写及认证实现。合法业务请求统一返回 HTTP 501；参数错误返回 400。"
                + "文档中的 2xx/401/404 是目标契约，Security 注解不代表鉴权已实现。"
                + "ID 为字符串，金额为两位小数人民币字符串，时间为带时区 ISO-8601。"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer",
        description = "规划中的服务端可撤销会话令牌，非 JWT 承诺。待登录模块实现后使用。")
public class OpenApiConfiguration {
}
