package com.quanxiaoha.xiaohashu.note.biz.consumer;

import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/*
* 用户资料变更：删除「已发布笔记列表」缓存
* 该缓存 key 按 userId 索引（note:published:list:{userId}），不需要反查笔记 ID，直接删即可 O(1)
* 注意：笔记详情缓存 note:detail:{noteId} 不在这里处理，走读时补齐（见 NoteServiceImpl#fillCreatorInfo）
* */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_note_group_" + MQConstants.TOPIC_USER_PROFILE_CHANGED,
        topic = MQConstants.TOPIC_USER_PROFILE_CHANGED
)
public class UserProfileChangedConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String message) {
        Long userId = Long.valueOf(message);

        String publishedNoteListKey = RedisKeyConstants.buildPublishedNoteListKey(userId);
        redisTemplate.delete(publishedNoteListKey);

        log.info("## 用户资料变更，删除已发布笔记列表缓存, userId: {}", userId);
    }
}
