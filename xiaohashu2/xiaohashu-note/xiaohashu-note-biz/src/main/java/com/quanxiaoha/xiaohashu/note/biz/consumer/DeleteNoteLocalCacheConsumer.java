package com.quanxiaoha.xiaohashu.note.biz.consumer;

import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/*
* 删除本地笔记缓存
* */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group" + MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, // 组
        topic = MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, // 消费的主题
        messageModel = MessageModel.BROADCASTING // 广播模式
)
public class DeleteNoteLocalCacheConsumer implements RocketMQListener<String> {

    @Resource
    private NoteService noteService;

    //message就是接收的消息
    @Override
    public void onMessage(String message) {
        Long noteId = Long.valueOf(message);
        log.info("## 消费者消费成功, noteId: {}", noteId);

        noteService.deleteNoteLocalCache(noteId);
        log.info("## MQ：删除笔记本地缓存发送成功, noteId: {}", noteId);
    }
}
