package com.quanxiaoha.xiaohashu.notification.biz.exception;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/*
 * 通知模块统一异常处理
 * */
@Slf4j
@RestControllerAdvice(basePackages = "com.quanxiaoha.xiaohashu.notification.biz.controller")
public class GlobalExceptionHandler {

    /*
     * 捕获项目通用业务异常，按通知异常枚举返回响应
     * */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Response<Object>> handleBizException(HttpServletRequest request,
                                                               HttpServletResponse response, BizException ex) {
        if (response.isCommitted()) {
            // SSE 已经开始推送后不能再改写响应状态和 JSON 内容
            return null;
        }
        log.warn("通知请求失败，路径：{}，异常码：{}，异常信息：{}",
                request.getRequestURI(), ex.getErrorCode(), ex.getErrorMessage());
        return error(ResponseCodeEnum.httpStatusOf(ex.getErrorCode()), Response.fail(ex));
    }

    /*
     * 捕获请求体格式异常和字段校验异常
     * */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Response<Object>> handleRequestParamException(HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        // 不返回解析器内部信息或被拒绝的 Token 等原始输入
        return error(HttpStatus.BAD_REQUEST, Response.fail(ResponseCodeEnum.PARAM_NOT_VALID));
    }

    /*
     * 捕获参数取值异常
     * */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Object>> handleIllegalArgumentException(HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        return error(HttpStatus.BAD_REQUEST, Response.fail(ResponseCodeEnum.PARAM_NOT_VALID));
    }

    /*
     * 捕获其余异常，返回可重试的服务异常
     * */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Object>> handleOtherException(HttpServletRequest request,
                                                                 HttpServletResponse response, Exception ex) {
        if (response.isCommitted()) {
            return null;
        }
        log.error("通知请求发生系统异常，路径：{}", request.getRequestURI(), ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, Response.fail(ResponseCodeEnum.SYSTEM_ERROR));
    }

    /*
     * 构建统一响应，同时保留登录校验和 SSE 重连需要的 HTTP 状态
     * */
    private static ResponseEntity<Response<Object>> error(HttpStatus status, Response<Object> result) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
