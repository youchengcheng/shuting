package com.quanxiaoha.framework.notification.outbox.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已领取的通知事件
 */
@AllArgsConstructor
@Getter
public class NotificationOutboxClaimDTO {

    /**
     * 通知事件内容
     */
    private final String payload;

    /**
     * 当前租约版本
     */
    private final long version;

    /**
     * 已尝试发送的次数
     */
    private final int attempts;
}
