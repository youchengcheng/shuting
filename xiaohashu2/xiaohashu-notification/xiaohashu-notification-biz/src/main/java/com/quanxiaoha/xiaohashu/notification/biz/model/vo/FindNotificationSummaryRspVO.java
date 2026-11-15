package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 通知未读汇总
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationSummaryRspVO {

    /**
     * 各分类未读数量
     */
    private Map<String, Long> unreadCounts;

    /**
     * 未读总数
     */
    private long totalUnread;

    /**
     * 最新通知序号
     */
    private String lastSeq;

    /**
     * 通知状态版本
     */
    private String revision;

    /**
     * 各分类全部已读水位
     */
    private Map<String, String> readAllSeq;

}
