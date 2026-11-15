package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条通知已读参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReadNotificationReqVO {

    /**
     * 通知 ID
     */
    private String notificationId;

}
