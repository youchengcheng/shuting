package com.quanxiaoha.xiaohashu.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FansDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FollowingDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.FansDOMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.enums.FollowUnfollowTypeEnum;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.CountFollowUnfollowMqDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.UnfollowUserMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

/*
* 关注/取关 MQ 消费者
* */
@Component
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group" + MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW,
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW,
        consumeMode = ConsumeMode.ORDERLY //顺序消费模式
)
@Slf4j
public class FollowUnfollowConsumer implements RocketMQListener<Message> {

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private RateLimiter rateLimiter;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();

        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        if(Objects.equals(tags,MQConstants.TAG_FOLLOW)){
            //关注
            handleFollowTagMessage(bodyJsonStr);
        }else if(Objects.equals(tags,MQConstants.TAG_UNFOLLOW)){
            //取关
            handleUnfollowTagMessage(bodyJsonStr);
        }
    }

    /*
    * 关注
    * */
    private void handleFollowTagMessage(String bodyJsonStr) {

        //将消息体json字符串转为DTO 对象
        FollowUserMqDTO followUserMqDTO = JsonUtils.parseObject(bodyJsonStr, FollowUserMqDTO.class);

        //判空
        if(Objects.isNull(followUserMqDTO)){
            return;
        }
        //幂等性：通过联合唯一索引保证

        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowingId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();

        //编程式提交事物
        //Boolean.TRUE.equals()判断返回的值是否为true
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                //关注成功后向数据库添加两条记录
                //关注表
                int count = followingDOMapper.insert(FollowingDO.builder()
                        .userId(userId)
                        .followingUserId(followUserId)
                        .createTime(createTime)
                        .build());

                //粉丝表
                if (count > 0) {
                    fansDOMapper.insert(FansDO.builder()
                            .userId(followUserId)
                            .fansUserId(userId)
                            .createTime(createTime)
                            .build());
                }
                return true;
            } catch (Exception e) {
                status.setRollbackOnly();//标记事物为回滚
                log.error("", e);
            }
            return false;
        }));
        log.info("## 数据库添加记录结果：{}", isSuccess);

        // 若数据库操作成功，更新 Redis 中被关注用户的 ZSet 粉丝列表
        if(isSuccess){
            //创建脚本对象
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_check_and_update_fans_zset.lua")));
            script.setResultType(Long.class);

            //时间戳
            Long timestamp = DateUtils.localDateTime2Timestamp(createTime);
            //构建被关注用户的粉丝列表 key
            String fansRedisKey = RedisKeyConstants.buildUserFansKey(followUserId);

            //执行lua脚本，Collections.singletonList(fansRedisKey)因为lua脚本接收的key和value都是集合，所以先将key封装到集合中
            //解释一下为什么是userId：因为我关注别人，我就是粉丝，所以粉丝id就是当前用户id
            redisTemplate.execute(script, Collections.singletonList(fansRedisKey),userId,timestamp);

            // 发送 MQ 通知计数服务：统计关注数
            // 构建消息体 DTO
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(userId)
                    .targetUserId(followUserId)
                    .type(FollowUnfollowTypeEnum.FOLLOW.getCode())
                    .build();
            // 发送 MQ
            send(countFollowUnfollowMqDTO);
        }
    }

    /*
    * 取关
    * */
    private void handleUnfollowTagMessage(String bodyJsonStr) {
        //将消息体 json 字符串转为 DTO 对象
        UnfollowUserMqDTO unfollowUserMqDTO = JsonUtils.parseObject(bodyJsonStr, UnfollowUserMqDTO.class);
        //判空————直接返回
        if(Objects.isNull(unfollowUserMqDTO)){
            return;
        }

        Long userId = unfollowUserMqDTO.getUserId();
        Long unfollowUserId = unfollowUserMqDTO.getUnfollowUserId();
        LocalDateTime createTime = unfollowUserMqDTO.getCreateTime();

        //编程式提交事物
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                //取关成功删除两张表中的记录
                //1.关注表
                int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);

                //2，粉丝表
                if (count > 0) {
                    fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
                }
                return true;
            } catch (Exception e) {
                //标记事物为回滚
                status.setRollbackOnly();
                log.error("", e);
            }
            return false;
        }));

        //若数据库删除成功，更新 Redis，将自己从被取关用户的 ZSet 粉丝列表删除
        if(isSuccess){
            // 被取关用户的粉丝列表 Redis Key
            String fansRedisKey  = RedisKeyConstants.buildUserFansKey(unfollowUserId);
            // 删除指定粉丝
            redisTemplate.opsForZSet().remove(fansRedisKey,userId);

            // 发送 MQ 通知计数服务：统计关注数
            // 构建消息体 DTO
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(userId)
                    .targetUserId(unfollowUserId)
                    .type(FollowUnfollowTypeEnum.UNFOLLOW.getCode())
                    .build();
            // 发送 MQ
            send(countFollowUnfollowMqDTO);
        }
    }


    /*
    * 发送 MQ 通知计数服务
    * */
    private void send(CountFollowUnfollowMqDTO countFollowUnfollowMqDTO) {
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countFollowUnfollowMqDTO)).build();

        // 异步发送 MQ 消息,统计关注数
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【计数服务：关注数】MQ 发送异常: ", e);
            }
        });

        // 发送 MQ 通知计数服务：统计粉丝数
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【计数服务：粉丝数】MQ 发送异常: ", e);
            }
        });
    }
}
