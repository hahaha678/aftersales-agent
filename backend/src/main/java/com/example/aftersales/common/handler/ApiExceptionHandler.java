package com.example.aftersales.common.handler;

import com.example.aftersales.common.domain.vo.ApiError;
import com.example.aftersales.common.exception.ContractNotImplementedException;
import com.example.aftersales.common.filter.RequestIdFilter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(com.example.aftersales.common.exception.ApiRequestException.class)
    ResponseEntity<Object> businessRequest(
        com.example.aftersales.common.exception.ApiRequestException ex,
        WebRequest request
    ) {
        return ResponseEntity.status(ex.status()).body(
            error(ex.status(), ex.code(), ex.getMessage(), request, List.of())
        );
    }

    @ExceptionHandler(com.example.aftersales.identity.service.AuthFailure.class)
    ResponseEntity<Object> authentication(com.example.aftersales.identity.service.AuthFailure ex, WebRequest request) {
        var builder = ResponseEntity.status(ex.status());
        if (ex.status() == 401) builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return builder.body(error(ex.status(), ex.code(), ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(ContractNotImplementedException.class)
    ResponseEntity<Object> notImplemented(ContractNotImplementedException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
            error(501, "NOT_IMPLEMENTED", ex.getMessage(), request, List.of())
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        var fields = ex
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(item -> new ApiError.FieldViolation(item.getField(), item.getDefaultMessage()))
            .toList();
        boolean queryBinding = ex.getParameter().hasParameterAnnotation(ModelAttribute.class);
        return new ResponseEntity<>(
            error(400, queryBinding ? "INVALID_REQUEST" : "VALIDATION_ERROR", "请求字段不符合约束", request, fields),
            headers,
            status
        );
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "HTTP_ERROR";
        };
        return new ResponseEntity<>(
            error(
                status.value(),
                code,
                status.value() == 413
                    ? "上传内容超过大小限制，请压缩文件后重试"
                    : "请求无法处理，请检查路径、参数和请求格式",
                request,
                List.of()
            ),
            headers,
            status
        );
    }

    private ApiError error(
        int status,
        String code,
        String message,
        WebRequest request,
        List<ApiError.FieldViolation> fields
    ) {
        var servletRequest = ((ServletWebRequest) request).getRequest();
        return new ApiError(
            status,
            code,
            message,
            servletRequest.getRequestURI(),
            (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE),
            fields
        );
    }
}
