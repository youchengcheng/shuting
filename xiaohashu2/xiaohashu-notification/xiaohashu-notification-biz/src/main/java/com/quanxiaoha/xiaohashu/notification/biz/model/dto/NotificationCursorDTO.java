package com.quanxiaoha.xiaohashu.notification.biz.model.dto;

import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知分页游标
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationCursorDTO {

    // 接收者用户 ID
    private long recipientId;

    // 通知分类
    private NotificationCategoryEnum category;

    // 查询快照序号
    private long snapshotSeq;

    // 下一页起始序号
    private long beforeSeq;

}
