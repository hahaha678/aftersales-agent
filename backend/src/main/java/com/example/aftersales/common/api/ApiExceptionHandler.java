package com.example.aftersales.common.api;

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

    @ExceptionHandler(ContractNotImplementedException.class)
    ResponseEntity<Object> notImplemented(ContractNotImplementedException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(error(501, "NOT_IMPLEMENTED", ex.getMessage(), request, List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(item -> new ApiError.FieldViolation(item.getField(), item.getDefaultMessage()))
                .toList();
        boolean queryBinding = ex.getParameter().hasParameterAnnotation(ModelAttribute.class);
        return new ResponseEntity<>(error(400, queryBinding ? "INVALID_REQUEST" : "VALIDATION_ERROR",
                "请求字段不符合约束", request, fields), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "HTTP_ERROR";
        };
        return new ResponseEntity<>(error(status.value(), code, "请求无法处理，请检查路径、参数和请求格式", request, List.of()), headers, status);
    }

    private ApiError error(int status, String code, String message, WebRequest request,
                           List<ApiError.FieldViolation> fields) {
        var servletRequest = ((ServletWebRequest) request).getRequest();
        return new ApiError(status, code, message, servletRequest.getRequestURI(),
                (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE), fields);
    }
}
