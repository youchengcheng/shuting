package com.quanxiaoha.xiaohashu.note.biz.consumer;

import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import jdk.management.jfr.RecordingInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/*
* 删除redis中的笔记缓存
* */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group" + MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE,
        topic = MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE)
//RocketMQListener<String>这里的参数是值消息体的类型
public class DelayDeleteNoteRedisCacheConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Override
    public void onMessage(String message) {
        Long noteId = Long.valueOf(message);
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        log.info("## 延迟消息消费成功, noteId: {}", noteId);

        redisTemplate.delete(noteDetailRedisKey);
        log.info("=====更新笔记二次删除redis中的缓存成功============");
    }
}
