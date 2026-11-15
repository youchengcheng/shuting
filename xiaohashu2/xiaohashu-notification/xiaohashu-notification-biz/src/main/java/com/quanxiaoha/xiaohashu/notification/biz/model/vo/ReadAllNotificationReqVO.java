package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全部通知已读参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReadAllNotificationReqVO {

    /**
     * 通知分类
     */
    private String category;

    /**
     * 本次查询的快照序号
     */
    private String snapshotSeq;

}
