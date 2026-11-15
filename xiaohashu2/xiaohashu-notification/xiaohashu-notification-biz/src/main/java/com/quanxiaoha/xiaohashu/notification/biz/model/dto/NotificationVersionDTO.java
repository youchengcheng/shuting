package com.quanxiaoha.xiaohashu.notification.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知状态版本
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationVersionDTO {

    /**
     * 通知状态版本
     */
    private String revision;

    /**
     * 最新通知序号
     */
    private String lastSeq;

}
