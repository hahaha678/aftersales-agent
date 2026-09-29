package com.example.aftersales.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "电商售后 Agent · 接口文档",
        version = "0.2.0",
        description = "local 模式已实现登录、当前用户和注销，使用 MySQL + Redis 及 Bearer 认证。" +
            "local 模式支持当前用户订单分页、详情及物流查询，须先通过认证。scaffold 模式保留所有接口的契约占位。" +
            "ID 为字符串，金额为两位小数人民币字符串，时间为带时区 ISO-8601。"
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    description = "登录返回的服务端可撤销随机令牌（非 JWT），在此填写 accessToken。"
)
public class OpenApiConfiguration {}
