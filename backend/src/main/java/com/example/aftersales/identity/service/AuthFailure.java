package com.example.aftersales.identity.service;

public class AuthFailure extends RuntimeException {
    private final int status;
    private final String code;
    public AuthFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static AuthFailure unauthenticated() {
        return new AuthFailure(401, "UNAUTHENTICATED", "未登录或会话已失效");
    }
    public static AuthFailure unavailable() {
        return new AuthFailure(503, "AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用，请稍后重试");
    }
}
