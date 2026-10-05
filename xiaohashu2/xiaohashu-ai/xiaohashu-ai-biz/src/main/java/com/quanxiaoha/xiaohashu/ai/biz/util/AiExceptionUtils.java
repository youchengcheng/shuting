package com.quanxiaoha.xiaohashu.ai.biz.util;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;

/**
 * 异常信息提取工具
 *
 * <p>SSE 流式接口不走 {@code @ControllerAdvice}，需要自己把异常转成用户可读的文案。</p>
 */
public final class AiExceptionUtils {

    private AiExceptionUtils() {
    }

    /**
     * 提取可展示给用户的错误信息
     */
    public static String resolveMessage(Throwable e) {
        if (e == null) {
            return ResponseCodeEnum.SYSTEM_ERROR.getErrorMessage();
        }
        if (e instanceof BizException) {
            String message = ((BizException) e).getErrorMessage();
            return AiStringUtils.isBlank(message) ? ResponseCodeEnum.SYSTEM_ERROR.getErrorMessage() : message;
        }
        return ResponseCodeEnum.SYSTEM_ERROR.getErrorMessage();
    }
}