package com.quanxiaoha.xiaohashu.notification.biz.model.dto;

import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通知快照分页数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationSnapshotPageDTO {

    /**
     * 当前页通知记录
     */
    private List<NotificationDO> records;

    /**
     * 用户通知状态
     */
    private NotificationUserStateDO state;

    /**
     * 本次查询的快照序号
     */
    private long snapshot;

    /**
     * 分页游标
     */
    private String cursor;

    /**
     * 是否还有下一页
     */
    private boolean more;

}
