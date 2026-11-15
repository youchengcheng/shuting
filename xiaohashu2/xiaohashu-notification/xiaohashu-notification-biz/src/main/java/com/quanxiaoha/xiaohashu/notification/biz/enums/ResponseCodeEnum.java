package com.quanxiaoha.xiaohashu.notification.biz.enums;

import com.quanxiaoha.framework.common.exception.BaseExceptionInterface;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

/**
 * 通知响应异常码
 */
@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    PARAM_NOT_VALID("NOTIFICATION_400", "参数错误", HttpStatus.BAD_REQUEST),
    LOGIN_REQUIRED("NOTIFICATION_401", "请先登录", HttpStatus.UNAUTHORIZED),
    LOGIN_EXPIRED("NOTIFICATION_401", "登录已失效，请重新登录", HttpStatus.UNAUTHORIZED),

    // ----------- 通知参数异常 -----------
    INVALID_PAGE_SIZE("NOTIFICATION_400", "每页数量必须在 1 到 50 之间", HttpStatus.BAD_REQUEST),
    CURSOR_SNAPSHOT_REQUIRED("NOTIFICATION_400", "使用分页游标时必须携带查询快照", HttpStatus.BAD_REQUEST),
    INVALID_CURSOR("NOTIFICATION_400", "通知分页游标无效", HttpStatus.BAD_REQUEST),
    INVALID_CATEGORY("NOTIFICATION_400", "通知分类无效", HttpStatus.BAD_REQUEST),
    INVALID_IDENTIFIER("NOTIFICATION_400", "通知 ID 或序号格式不正确", HttpStatus.BAD_REQUEST),
    SNAPSHOT_OUT_OF_RANGE("NOTIFICATION_400", "查询快照不能超过当前用户的通知序号", HttpStatus.BAD_REQUEST),
    INVALID_READ_STATE_IDS("NOTIFICATION_400", "一次查询的通知 ID 数量必须在 1 到 100 之间", HttpStatus.BAD_REQUEST),

    // ----------- 业务异常状态码 -----------
    NOTIFICATION_NOT_FOUND("NOTIFICATION_404", "通知不存在", HttpStatus.NOT_FOUND),
    STREAM_LIMIT_REACHED("NOTIFICATION_429", "通知连接数量已达上限", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE("NOTIFICATION_503", "通知服务暂时不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_NOT_ENABLED("NOTIFICATION_503", "通知服务尚未启用", HttpStatus.SERVICE_UNAVAILABLE),
    AUTHENTICATION_UNAVAILABLE("NOTIFICATION_503", "登录验证暂时不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    STREAM_CAPACITY_REACHED("NOTIFICATION_503", "通知服务的连接容量已达上限，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    NOTIFICATION_STATE_UNAVAILABLE("NOTIFICATION_503", "通知状态暂时无法读取，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    USER_RPC_QUERY_FAILED("NOTIFICATION_503", "用户资料查询失败，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    NOTE_RPC_QUERY_FAILED("NOTIFICATION_503", "笔记信息查询失败，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    COMMENT_RPC_QUERY_FAILED("NOTIFICATION_503", "评论信息查询失败，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_ERROR("NOTIFICATION_503", "系统异常，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);

    // 异常码
    private final String errorCode;

    // 异常信息
    private final String errorMessage;

    // HTTP 响应状态，用于前端鉴权及 SSE 重连
    private final HttpStatus httpStatus;

    /*
     * 根据业务异常码确定 HTTP 状态，供统一异常处理使用
     * */
    public static HttpStatus httpStatusOf(String errorCode) {
        for (ResponseCodeEnum responseCodeEnum : values()) {
            if (responseCodeEnum.getErrorCode().equals(errorCode)) {
                return responseCodeEnum.getHttpStatus();
            }
        }
        // 未识别的业务异常使用可重试状态，避免 SSE 客户端误认为连接成功
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
