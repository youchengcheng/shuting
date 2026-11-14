package com.quanxiaoha.xiaohashu.notification.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Response<Void>> status(ResponseStatusException ex, HttpServletResponse response) {
        if (response.isCommitted()) return null;
        return error(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Void>> malformed(HttpServletResponse response) {
        if (response.isCommitted()) return null;
        return error(HttpStatus.BAD_REQUEST, "请求参数格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> unavailable(Exception ex, HttpServletResponse response) {
        if (response.isCommitted()) return null;
        log.warn("Notification request unavailable, exception={}", ex.getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "通知服务暂时不可用，请稍后重试");
    }

    private static ResponseEntity<Response<Void>> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(Response.fail("NOTIFICATION_" + status.value(), message));
    }
}
