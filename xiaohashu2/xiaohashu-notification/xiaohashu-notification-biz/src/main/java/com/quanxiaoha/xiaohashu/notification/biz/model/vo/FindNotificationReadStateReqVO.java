package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通知已读状态查询参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationReadStateReqVO {

    /**
     * 通知 ID 列表
     */
    private List<String> notificationIds;

}
