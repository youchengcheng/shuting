package com.quanxiaoha.xiaohashu.notification.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationChangePublisher;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/*
 * 通知状态变更发布组件
 * */
@Slf4j
@Service
public class NotificationChangePublisherImpl implements NotificationChangePublisher {

    /**
     * Redis 消息发布模板
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 状态消息序列化工具
     */
    @Resource
    private ObjectMapper objectMapper;

    /*
     * 事务提交后发布用户通知状态版本
     * */
    @Override
    public void publish(NotificationUserStateDO state) {
        try {
            // 将用户 ID、状态版本和最新通知序号序列化后发布
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(Map.of("userId", Long.toString(state.getRecipientId()),
                    "revision", Long.toString(state.getRevision()), "lastSeq", Long.toString(state.getLastSeq()))));
        } catch (RuntimeException | JsonProcessingException ex) {
            // 数据已提交，变更提示发送失败时由 SSE 心跳和 REST 查询补齐状态
            log.warn("Notification change hint unavailable, userId={}, revision={}", state.getRecipientId(), state.getRevision());
        }
    }
}
