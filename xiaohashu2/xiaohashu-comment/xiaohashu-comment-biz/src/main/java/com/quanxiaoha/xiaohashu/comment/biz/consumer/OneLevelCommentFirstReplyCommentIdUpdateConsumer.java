package com.quanxiaoha.xiaohashu.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.github.phantomthief.collection.BufferTrigger;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.CommentLevelEnum;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.CountPublishCommentMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 更新一级评论的 first_reply_comment_id 字段值
 **/
@Component
@RocketMQMessageListener(consumerGroup = "xiaohashu_group_first_reply_comment_id" + MQConstants.TOPIC_COUNT_NOTE_COMMENT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT // 主题 Topic
)
@Slf4j
public class OneLevelCommentFirstReplyCommentIdUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【一级评论 first_reply_comment_id 更新】聚合消息, size: {}", bodys.size());
        log.info("==> 【一级评论 first_reply_comment_id 更新】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 将聚合后的消息体 Json 转 List<CountPublishCommentMqDTO>
        List<CountPublishCommentMqDTO> publishCommentMqDTOS = Lists.newArrayList();

        bodys.forEach(body -> {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                publishCommentMqDTOS.addAll(list);
            }catch (Exception e) {
                log.error("",e);
            }
        });

        // 过滤出二级评论的 parent_id（即一级评论 ID），并去重，需要更新对应一级评论的 first_reply_comment_id
        List<Long> parentIds = publishCommentMqDTOS.stream()
                .filter(PublishCommentMqDTO -> Objects.equals(PublishCommentMqDTO.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CountPublishCommentMqDTO::getParentId)
                .distinct() //去重
                .toList();

        if(CollUtil.isEmpty(parentIds)) return;

        //构建redis key
        List<String> keys = parentIds.stream()
                .map(RedisKeyConstants::buildHaveFirstReplyCommentKey)
                .toList();
        //批量查询redis
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        //提取 redis 中不存在的评论id
        List<Long> missingCommentIds = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if(Objects.isNull(values.get(i))){
                missingCommentIds.add(parentIds.get(i));//存储redis中不存在的评论id
            }
        }

        // 存在的一级评论 ID，说明表中对应记录的 first_reply_comment_id 已经有值
        if (CollUtil.isNotEmpty(missingCommentIds)) {
            // 不存在的，则需要进一步查询数据库来确定，是否要更新记录对应的 first_reply_comment_id 值
            //批量去数据库中查询
            List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(missingCommentIds);

            //异步将 first_reply_comment_id 不为0的一级评论 id，同步到redis中
            threadPoolTaskExecutor.submit(() -> {
                List<Long> needSyncCommentIds = commentDOS.stream()
                        .filter(commentDO -> Objects.nonNull(commentDO.getFirstReplyCommentId())
                                && commentDO.getFirstReplyCommentId() != 0)//保留不为零的评论
                        .map(CommentDO::getId)
                        .toList();

                sync2Redis(needSyncCommentIds);
            });

            //过滤出值为 0 的，都需要更新其 first_reply_comment_id
            List<CommentDO> needUpdateCommentDOS = commentDOS.stream()
                    .filter(commentDO -> Objects.isNull(commentDO.getFirstReplyCommentId())
                            || commentDO.getFirstReplyCommentId() == 0)
                    .toList();

            needUpdateCommentDOS.forEach(needUpdateCommentDO -> {
                //一级评论 id
                Long needUpdateCommentId = needUpdateCommentDO.getId();

                //查询数据库，拿到一级评论最早回复的那条评论
                //根据父id查询的原因是要重新这个评论下有多少条回复，这里的sql是用于查询最早的那条
                CommentDO earliestCommentDO = commentDOMapper.selectEarliestByParentId(needUpdateCommentId);


                if(Objects.nonNull(earliestCommentDO)){
                    //最早回复的那条评论 id
                    Long earliestCommentId = earliestCommentDO.getId();

                    //更新其一级评论的 first_reply_comment_id
                    //earliestCommentId更新的值，needUpdateCommentId需要更新的评论
                    commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(earliestCommentId,needUpdateCommentId);

                    //异步同步到 redis 中
                    threadPoolTaskExecutor.submit(() -> sync2Redis(Lists.newArrayList(needUpdateCommentId)));
                }
            });
        }

        // 子评论发布会改变父评论的首条回复和数量，更新完成后清理详情缓存及各实例本地缓存
        parentIds.forEach(parentId -> {
            // 子评论新增后，已有子评论分页 ZSET 可能不包含新评论，删除后由查询流程按数据库重建
            redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(parentId));
            redisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(parentId));
            rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, parentId,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.debug("==> 父评论本地缓存清理消息发送成功，commentId: {}", parentId);
                        }

                        @Override
                        public void onException(Throwable throwable) {
                            log.warn("==> 父评论本地缓存清理消息发送失败，commentId: {}", parentId, throwable);
                        }
                    });
        });
    }

    /*
    * 同步到redis中
    * */
    private void sync2Redis(List<Long> needSyncCommentIds) {
        //获取ValueOperations
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();

        //使用redistemplate 的管道模式，允许在一个操作中批量发送多个命令，防止频繁操作redis
        redisTemplate.executePipelined((RedisCallback<?>) (connection) -> {
            needSyncCommentIds.forEach(needSyncCommentId -> {
                //构建redis key
                String key = RedisKeyConstants.buildHaveFirstReplyCommentKey(needSyncCommentId);

                //批量设置值并指定过期时间（五个小时内）
                valueOperations.set(key,1, RandomUtil.randomInt(5 * 60 * 60), TimeUnit.SECONDS);
            });
            return null;//因为参数中有RedisCallback所以语法上必须要有一个返回值
        });
    }
}
