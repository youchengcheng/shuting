package com.quanxiaoha.xiaohashu.notification.biz.util;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;

/*
 * 通知请求参数解析工具
 * */
public final class NotificationParamUtils {

    private NotificationParamUtils() {
    }

    /*
     * 将分类参数转换为通知分类枚举
     * */
    public static NotificationCategoryEnum parseCategory(String value) {
        try {
            return NotificationCategoryEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BizException(ResponseCodeEnum.INVALID_CATEGORY);
        }
    }

    /*
     * 解析通知 ID 或序号，按场景决定是否允许零值
     * */
    public static long parseDecimal(String value, boolean positive) {
        // ID 使用字符串传输，避免 JavaScript 对大整数截断；服务端只接受十进制数字
        if (value == null || !value.matches("[0-9]{1,19}")) {
            throw new BizException(ResponseCodeEnum.INVALID_IDENTIFIER);
        }
        try {
            long parsed = Long.parseLong(value);
            // 通知 ID 必须大于零，空通知状态的序号和水位可以为零
            if (positive && parsed == 0) {
                throw new BizException(ResponseCodeEnum.INVALID_IDENTIFIER);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            // 位数合规仍可能超过 Long 上限，统一转换为中文参数异常
            throw new BizException(ResponseCodeEnum.INVALID_IDENTIFIER);
        }
    }
}
