package com.quanxiaoha.xiaohashu.count.biz.consumer;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.count.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.count.biz.enums.FollowUnfollowTypeEnum;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Objects;


/*
* 计数: 关注数
* */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu-group" + MQConstants.TOPIC_COUNT_FOLLOWING,
        topic = MQConstants.TOPIC_COUNT_FOLLOWING
)
public class CountFollowingConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 关注数】, {}...", body);

        //判断传递的数据是否为空，为空直接返回
        if(Objects.isNull(body)) return;
        // 关注数和粉丝数计数场景不同，单个用户无法短时间内关注大量用户，所以无需聚合
        // 直接对 Redis 中的 Hash 进行 +1 或 -1 操作即可

        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);

        //判断操作类型：关注或取关
        Integer type = countFollowUnfollowMqDTO.getType();
        //原用户id
        Long userId = countFollowUnfollowMqDTO.getUserId();
        //更新redis
        String redisKey = RedisKeyConstants.buildCountUserKey(userId);
        //判断hash 是否存在
        Boolean isExisted = redisTemplate.hasKey(redisKey);

        //存在才更新redis
        if(isExisted){
            //关注数：关注：+1，取关：-1
            long count = Objects.equals(type, FollowUnfollowTypeEnum.follow.getCode()) ? 1 : -1;
            //对hash 中的 followingTotal 字段进行更新
            redisTemplate.opsForHash().increment(redisKey,RedisKeyConstants.FIELD_FOLLOWING_TOTAL,count);
        }

        //发送MQ ,关注数写库
        Message<String> message = MessageBuilder.withPayload(body).build();

        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【计数服务：关注数入库】MQ 发送异常: ", e);
            }
        });

    }
}
