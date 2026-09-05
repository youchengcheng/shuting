package com.quanxiaoha.xiaohashu.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.NoteCountDOMapper;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.UserCountDOMapper;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 计数：笔记点赞数 批量入库消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group" + MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB,
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB
)
public class CountNoteLike2DBConsumer implements RocketMQListener<String> {

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private UserCountDOMapper userCountDOMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    // 每秒 5000 令牌限流
    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String message) {
        // 限流
        rateLimiter.acquire();

        log.info("## 消费到 MQ 【计数: 笔记点赞数入库】: {}", message);

        List<AggregationCountLikeUnlikeNoteMqDTO> countList = null;

        // 1. 解析消息
        try {
            countList = JsonUtils.parseList(message, AggregationCountLikeUnlikeNoteMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 异常: {}", message, e);
            return; // 解析失败直接返回，避免空指针
        }

        // 2. 空集合直接跳过
        if (CollUtil.isEmpty(countList)) {
            return;
        }

        // 3. 批量更新计数
        for (AggregationCountLikeUnlikeNoteMqDTO item : countList) {
            try {
                transactionTemplate.execute(status -> {
                    Long noteId = item.getNoteId();
                    Long creatorId = item.getCreatorId();
                    Integer count = item.getCount();

                    // 更新笔记点赞数
                    noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(count, noteId);
                    // 更新用户获赞总数
                    userCountDOMapper.insertOrUpdateLikeTotalByUserId(count, creatorId);

                    return true;
                });
            } catch (Exception e) {
                log.error("## 笔记点赞计数更新异常, noteId:{}", item.getNoteId(), e);
            }
        }
    }
}