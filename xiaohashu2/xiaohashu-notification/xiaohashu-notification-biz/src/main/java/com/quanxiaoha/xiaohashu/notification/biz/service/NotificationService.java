package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.xiaohashu.notification.api.dto.NotificationEvent;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.*;

import java.util.List;

/*
 * 通知服务
 * */
public interface NotificationService {

    /*
     * 消费互动事件并生成通知
     * */
    void consume(NotificationEvent event);

    /*
     * 查询通知分页列表
     * */
    FindNotificationPageRspVO list(long userId, String categoryValue, String cursor, String snapshotValue, Integer pageSize);

    /*
     * 查询未读数量及状态版本
     * */
    FindNotificationSummaryRspVO summary(long userId);

    /*
     * 批量查询通知已读状态
     * */
    FindNotificationReadStateRspVO readState(long userId, List<String> ids);

    /*
     * 将单条通知标为已读
     * */
    ReadNotificationRspVO markRead(long userId, String idValue);

    /*
     * 将快照内某分类的通知全部标为已读
     * */
    ReadAllNotificationRspVO markAllRead(long userId, String categoryValue, String snapshotValue);

    /*
     * 将非法互动事件写入隔离表
     * */
    void isolate(String source, String identity, String payload, String reason);

}
