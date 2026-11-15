package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知列表分页查询参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationPageListReqVO {

    /**
     * 通知分类
     */
    private String category;

    /**
     * 分页游标
     */
    private String cursor;

    /**
     * 本次查询的快照序号
     */
    private String snapshotSeq;

    /**
     * 每页数量
     */
    private Integer pageSize;

}
