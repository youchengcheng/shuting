package com.quanxiaoha.xiaohashu.notification.biz.consumer;

import jakarta.annotation.Resource;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 笔记点赞通知消费者
 */
@Component
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "LikeUnlikeTopic", consumerGroup = "xiaohashu_notification_note_like_v1",
        selectorExpression = "Like", consumeThreadNumber = 4, consumeThreadMax = 4, maxReconsumeTimes = 16)
public class NoteLikeNotificationConsumer implements RocketMQListener<MessageExt>, RocketMQPushConsumerLifecycleListener {
    /**
     * 通知消息处理组件
     */
    @Resource
    private NotificationMessageHandler notificationMessageHandler;

    /*
     * 处理笔记点赞消息
     * */
    @Override
    public void onMessage(MessageExt message) {
        notificationMessageHandler.rawLike(message, false);
    }

    /*
     * 设置新消费者组的起始消费位置
     * */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        // 新消费者组读取保留消息，再按启用时间过滤历史事件；已有消费者组沿用消费进度
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    }
}
