package com.quanxiaoha.xiaohashu.notification.biz.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/*
 * 通知 SSE 连接管理服务
 * */
public interface NotificationStreamHub {

    /*
     * 建立用户通知连接并发送当前状态版本
     * */
    SseEmitter connect(long userId, String token);

    /*
     * 将状态变更提示推送给用户连接
     * */
    void changed(long userId, long revision, long lastSeq);

    /*
     * 校验登录状态，通过心跳补齐遗漏的状态变更
     * */
    void heartbeat();

    /*
     * 关闭发送积压或存活时间已到上限的连接
     * */
    void closeSlowClients();

    /*
     * 服务关闭时清理连接并停止线程池
     * */
    void shutdown();
}
