package com.quanxiaoha.xiaohashu.user.biz.comsumer;

import com.quanxiaoha.xiaohashu.user.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.biz.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group_" + MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE,
        topic = MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE
)
public class DelayDeleteUserRedisCacheConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String message) {
        Long userId = Long.valueOf(message);
        log.info("## 延迟消息消费成功, userId: {}", userId);

        //构建rediskey
        String userInfoKey = RedisKeyConstants.buildUserInfoKey(userId);
        String userProfileKey = RedisKeyConstants.buildUserProfileKey(userId);
        //批量删除
        redisTemplate.delete(Arrays.asList(userInfoKey,userProfileKey));

    }
}
