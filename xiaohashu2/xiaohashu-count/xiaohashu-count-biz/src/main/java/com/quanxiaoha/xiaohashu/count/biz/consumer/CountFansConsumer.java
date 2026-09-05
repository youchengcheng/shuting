package com.quanxiaoha.xiaohashu.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/*
* 计数: 粉丝数
* */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu-group" + MQConstants.TOPIC_COUNT_FANS,
        topic = MQConstants.TOPIC_COUNT_FANS
)
public class CountFansConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;


    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) //缓存队列的最多容量
            .batchSize(1000) //一批次最多聚合1000条
            .linger(Duration.ofSeconds(1)) //多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();
    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtils.toJsonString(bodys));

        // List<String> 转 List<CountFollowUnfollowMqDTO>
        List<CountFollowUnfollowMqDTO> countFollowUnfollowMqDTOS = bodys.stream()
                .map(body ->JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class))
                .toList();

        //按目标用户进行分组
        Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = countFollowUnfollowMqDTOS.stream()
                .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));

        //安组汇总数据，统计出最终计数
        //key为目标用户id，value为最终操作的计数
        HashMap<Long,Integer> countMap = Maps.newHashMap();
        //遍历对每一个目标用户的操作
        for (Map.Entry<Long, List<CountFollowUnfollowMqDTO>> entry : groupMap.entrySet()) {
            List<CountFollowUnfollowMqDTO> list = entry.getValue();
            //最终的计数值，默认为0
            int finalCount = 0;


            for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : list) {
                //获取操作类型
                Integer type = countFollowUnfollowMqDTO.getType();
                //更具操作类型获取对应枚举
                FollowUnfollowTypeEnum followUnfollowTypeEnum = FollowUnfollowTypeEnum.valueOf(type);
                //枚举为空，进入下一次循环
                if(Objects.isNull(followUnfollowTypeEnum)){
                    continue;
                }

                switch (followUnfollowTypeEnum){
                    case follow -> finalCount += 1; //关注
                    case unfollow -> finalCount -=1; //取关
                }
            }
            //将分组后统计出的最终计数，存入countMap中
            countMap.put(entry.getKey(),finalCount);
        }
        log.info("## 聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));

        /*
           map.forEach( (键变量, 值变量) -> {
                每次循环，都会拿到当前的 键 和 值
            });
        * */
        //跟新redis
        countMap.forEach((k, v) -> {
            //构建redis key
            String redisKey = RedisKeyConstants.buildCountUserKey(k);
            //判断redis中hash是否存在
            Boolean isExisted = redisTemplate.hasKey(redisKey);
            //存在才进行更新
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if(isExisted){
                // 对目标用户 Hash 中的粉丝数字段进行计数操作
                redisTemplate.opsForHash().increment(redisKey,RedisKeyConstants.FIELD_FANS_TOTAL,v);
            }
        });

        //发送 MQ,计数数据落库
        //构建消息体 DTO
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countMap)).build();

        //异步发送MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【计数服务：粉丝数入库】MQ 发送异常: ", e);
            }
        });

    }
}
