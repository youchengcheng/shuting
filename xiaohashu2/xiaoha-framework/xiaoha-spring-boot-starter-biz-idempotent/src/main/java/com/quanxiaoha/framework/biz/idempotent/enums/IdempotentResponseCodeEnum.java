package com.quanxiaoha.framework.biz.idempotent.enums;

import com.quanxiaoha.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 幂等组件响应异常码
 */
@AllArgsConstructor
@Getter
public enum IdempotentResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    DUPLICATE_SUBMIT("IDEMPOTENT-10000", "请勿重复提交"),
    TOKEN_APPLY_FAIL("IDEMPOTENT-10001", "幂等令牌申请失败，请稍后重试"),
    REPEAT_REQUEST("IDEMPOTENT-10002", "操作过于频繁，请稍后再试"),
    ;

    // 异常码
    private final String errorCode;
    // 异常信息
    private final String errorMessage;
}
