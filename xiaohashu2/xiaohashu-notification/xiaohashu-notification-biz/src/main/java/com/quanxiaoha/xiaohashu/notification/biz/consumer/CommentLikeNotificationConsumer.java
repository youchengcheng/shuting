package com.quanxiaoha.xiaohashu.notification.biz.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "CommentLikeUnlikeTopic", consumerGroup = "xiaohashu_notification_comment_like_v1",
        selectorExpression = "Like", consumeThreadNumber = 4, consumeThreadMax = 4, maxReconsumeTimes = 16)
public class CommentLikeNotificationConsumer implements RocketMQListener<MessageExt>, RocketMQPushConsumerLifecycleListener {
    private final NotificationMessageHandler handler;
    public CommentLikeNotificationConsumer(NotificationMessageHandler handler) { this.handler = handler; }

    @Override
    public void onMessage(MessageExt message) { handler.rawLike(message, true); }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        // Fresh groups replay retained messages; cutoff filters history. Existing groups retain their offsets.
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    }
}
