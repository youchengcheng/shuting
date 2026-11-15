package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通知分页查询结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationPageRspVO {

    /**
     * 通知列表
     */
    private List<FindNotificationItemRspVO> items;

    /**
     * 下一页游标
     */
    private String nextCursor;

    /**
     * 是否还有下一页
     */
    private boolean hasMore;

    /**
     * 本次查询的快照序号
     */
    private String snapshotSeq;

    /**
     * 通知状态版本
     */
    private String revision;

}
