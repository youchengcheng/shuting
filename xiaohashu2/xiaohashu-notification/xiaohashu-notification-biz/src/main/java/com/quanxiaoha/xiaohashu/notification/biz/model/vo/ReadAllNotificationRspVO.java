package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全部通知已读结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReadAllNotificationRspVO {

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
     * 通知分类
     */
    private String category;

    /**
     * 本次查询的快照序号
     */
    private String snapshotSeq;

}
