package com.quanxiaoha.xiaohashu.user.biz.comsumer;

import com.quanxiaoha.xiaohashu.user.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.biz.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/*
* 用户资料变更：广播删除本实例的本地缓存
* 本地缓存在各自 JVM 内，单机 invalidate 清不到别的实例，所以必须用广播模式
* */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group_" + MQConstants.TOPIC_USER_PROFILE_CHANGED,
        topic = MQConstants.TOPIC_USER_PROFILE_CHANGED,
        messageModel = MessageModel.BROADCASTING // 广播模式
)
public class UserProfileChangedConsumer implements RocketMQListener<String> {

    @Resource
    private UserService userService;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public void onMessage(String message) {
        Long userId = Long.valueOf(message);
        log.info("## 用户资料变更，广播删除本地缓存, userId: {}", userId);

        // 1. 立即失效一次
        userService.deleteUserLocalCache(userId);

        // 2. 延迟补删一次：findById 命中 Redis 后是「异步」回填本地缓存的，
        //    若回填发生在第 1 步之后，本地缓存会残留旧值（TTL 1 小时），所以补一次。
        //    注意：RocketMQ 广播模式不支持延迟消息，这里的延迟只能在消费端本地实现。
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, threadPoolTaskExecutor)
                .execute(() -> userService.deleteUserLocalCache(userId));
    }
}
