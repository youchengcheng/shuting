package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条通知已读状态
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationReadItemRspVO {

    /**
     * 通知 ID
     */
    private String notificationId;

    /**
     * 是否已读
     */
    private boolean read;

}
