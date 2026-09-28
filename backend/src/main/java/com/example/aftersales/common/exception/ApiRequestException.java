package com.example.aftersales.common.exception;

/** 可预期的业务请求错误，使用统一 API 错误体返回。 */
public class ApiRequestException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiRequestException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiRequestException invalid(String message) {
        return new ApiRequestException(400, "INVALID_REQUEST", message);
    }
    public static ApiRequestException notFound() {
        return new ApiRequestException(404, "RESOURCE_NOT_FOUND", "订单不存在或不可访问");
    }
}
