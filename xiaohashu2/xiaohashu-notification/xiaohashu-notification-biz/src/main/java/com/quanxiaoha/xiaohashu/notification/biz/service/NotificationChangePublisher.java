package com.quanxiaoha.xiaohashu.notification.biz.service;

import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;

/*
 * 通知状态变更发布服务
 * */
public interface NotificationChangePublisher {

    // Redis 通知状态变更频道
    String CHANNEL = "notification:changed:v1";

    /*
     * 事务提交后发布用户通知状态版本
     * */
    void publish(NotificationUserStateDO notificationUserStateDO);
}
