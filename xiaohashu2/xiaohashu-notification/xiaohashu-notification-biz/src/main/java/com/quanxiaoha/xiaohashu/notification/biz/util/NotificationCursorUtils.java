package com.quanxiaoha.xiaohashu.notification.biz.util;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.NotificationCursorDTO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * 通知分页游标编解码工具
 * */
public final class NotificationCursorUtils {

    private NotificationCursorUtils() {
    }

    /*
     * 编码用户、分类、查询快照和下一页位置
     * */
    public static String encode(NotificationCursorDTO cursor) {
        // 同时绑定用户与查询快照，后续请求不能用其他账号或分类复用游标
        String value = cursor.getRecipientId() + ":" + cursor.getCategory() + ":"
                + cursor.getSnapshotSeq() + ":" + cursor.getBeforeSeq();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 解码游标并校验用户、分类、快照与分页位置
     * */
    public static NotificationCursorDTO decode(String value, long recipientId,
                                               NotificationCategoryEnum category, long snapshot) {
        if (value == null || value.length() > 256) {
            throw new BizException(ResponseCodeEnum.INVALID_CURSOR);
        }
        try {
            // 固定四个字段，不接受字段缺失或额外内容
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 4) {
                throw new BizException(ResponseCodeEnum.INVALID_CURSOR);
            }
            NotificationCursorDTO cursor = new NotificationCursorDTO(Long.parseLong(parts[0]),
                    NotificationCategoryEnum.valueOf(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3]));
            // 下一页位置必须落在该快照内，防止跨账号、跨分类或跨快照翻页
            if (cursor.getRecipientId() != recipientId || cursor.getCategory() != category
                    || cursor.getSnapshotSeq() != snapshot || cursor.getBeforeSeq() <= 0
                    || cursor.getBeforeSeq() > snapshot) {
                throw new BizException(ResponseCodeEnum.INVALID_CURSOR);
            }
            return cursor;
        } catch (IllegalArgumentException ex) {
            throw new BizException(ResponseCodeEnum.INVALID_CURSOR);
        }
    }
}
