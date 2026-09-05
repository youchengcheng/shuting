package com.quanxiaoha.xiaohashu.count.biz.consumer;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.count.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.UserCountDOMapper;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.NoteOperateMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/*
* 计数: 笔记发布数
* */
@Component
@RocketMQMessageListener(consumerGroup = "xiaohashu_group" + MQConstants.TOPIC_NOTE_OPERATE, // Group 组
        topic = MQConstants.TOPIC_NOTE_OPERATE // 主题 Topic
)
@Slf4j
public class CountNotePublishConsumer implements RocketMQListener<Message> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserCountDOMapper userCountDOMapper;


    @Override
    public void onMessage(Message message) {
        //消息体
        String bodyJsonStr = new String(message.getBody());
        //标签
        String tags = message.getTags();

        log.info("==> CountNotePublishConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断笔记操作类型
        if (Objects.equals(tags, MQConstants.TAG_NOTE_PUBLISH)) { // 笔记发布
            handleTagMessage(bodyJsonStr, 1L);
        } else if (Objects.equals(tags, MQConstants.TAG_NOTE_DELETE)) { // 笔记删除
            handleTagMessage(bodyJsonStr, -1L);
        }
    }

    /*
    * 笔记：发布/删除
    * */
    private void handleTagMessage(String bodyJsonStr, Long count) {
        //解析消息体json转 DTO
        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(bodyJsonStr, NoteOperateMqDTO.class);
        if(Objects.isNull(noteOperateMqDTO)) return;
        //笔记发布者id
        Long creatorId = noteOperateMqDTO.getCreatorId();
        //更新redis 中用户维度的计数hash
        String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
        Boolean isExisted = redisTemplate.hasKey(countUserRedisKey);

        // 若存在才会更新
        // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
        if(isExisted){
            //对目标用户hash 中的笔记发布总数，进行操作
            redisTemplate.opsForHash().increment(countUserRedisKey,RedisKeyConstants.FIELD_NOTE_TOTAL,count);
        }

        //更新数据库 t_user_count 表
        userCountDOMapper.insertOrUpdateNoteTotalByUserId(count,creatorId);
    }


}
