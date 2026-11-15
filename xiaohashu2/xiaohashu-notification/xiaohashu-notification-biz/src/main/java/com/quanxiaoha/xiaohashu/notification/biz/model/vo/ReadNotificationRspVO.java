package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条通知已读结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReadNotificationRspVO {

    /**
     * 已读状态是否发生变化
     */
    private boolean changed;

    /**
     * 通知状态版本
     */
    private String revision;

    /**
     * 通知摘要
     */
    private FindNotificationSummaryRspVO summary;

    /**
     * 通知 ID
     */
    private String notificationId;

    /**
     * 是否已读
     */
    private boolean read;

}
