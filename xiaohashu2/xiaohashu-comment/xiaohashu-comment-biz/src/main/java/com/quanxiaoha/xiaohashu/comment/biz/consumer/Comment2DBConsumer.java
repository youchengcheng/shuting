package com.quanxiaoha.xiaohashu.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.CommentLevelEnum;
import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentBO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.CountPublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.kv.api.KeyValueFeignApi;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

/*
* 评论批量写库
* */
@Component
@Slf4j
public class Comment2DBConsumer {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Value("${rocketmq.name-server}")
    private String namesrvAddr;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    private DefaultMQPushConsumer consumer;

    //每秒创建 1000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(1000);

    @Bean
    public DefaultMQPushConsumer mqPushConsumer() throws MQClientException {
        // Group 组
        String group = "xiaohashu_group_" + MQConstants.TOPIC_PUBLISH_COMMENT;

        // 创建一个新的 DefaultMQPushConsumer 实例，并指定消费者的消费组名
        consumer = new DefaultMQPushConsumer(group);

        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);

        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(MQConstants.TOPIC_PUBLISH_COMMENT, "*");

        // 设置消费者消费消息的起始位置，如果队列中没有消息，则从最新的消息开始消费。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 设置消息消费模式，这里使用集群模式 (CLUSTERING)
        consumer.setMessageModel(MessageModel.CLUSTERING);

        // 设置每批次消费的最大消息数量，这里设置为 30，表示每次拉取时最多消费 30 条消息
        consumer.setConsumeMessageBatchMaxSize(30);

        //注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs,context) -> {
            log.info("==> 本批次消息大小: {}", msgs.size());
            try {
                //令牌统控流
                rateLimiter.acquire();

                //消息体 json 字符串转为 DTO
                //将所有一级评论存入集合
                List<PublishCommentMqDTO> publishCommentMqDTOS = Lists.newArrayList();
                msgs.forEach(msg -> {
                    String msgJson = new String(msg.getBody());//msg.getBody()拿到原始二进制数据\
                    log.info("==> Consumer - Received message: {}", msgJson);
                    publishCommentMqDTOS.add(JsonUtils.parseObject(msgJson, PublishCommentMqDTO.class));
                });

                //提取所有不为空的回复评论id
                List<Long> replyCommentIds = publishCommentMqDTOS.stream()
                        .filter(publishCommentMqDTO -> Objects.nonNull(publishCommentMqDTO.getReplyCommentId()))
                        .map(PublishCommentMqDTO::getReplyCommentId).toList();

                //批量查询相关回复评论记录
                List<CommentDO> replyCommentDOS = null;
                if (CollUtil.isNotEmpty(replyCommentIds)){
                    //查询数据库
                    replyCommentDOS = commentDOMapper.selectByCommentIds(replyCommentIds);
                }

                //DO 集合转 < 评论 id - 评论 DO > 字典，以方便后续查找
                Map<Long,CommentDO> commentIdAndCommentDOMap = Maps.newHashMap();
                if(Objects.nonNull(replyCommentDOS)){
                    commentIdAndCommentDOMap = replyCommentDOS.stream().collect(Collectors
                            .toMap(CommentDO::getId,commentDO -> commentDO));
                }

                // 回复目标尚未落库时等待下一次消费，避免把二级评论静默降级成一级评论
                for (PublishCommentMqDTO publishCommentMqDTO : publishCommentMqDTOS) {
                    Long replyCommentId = publishCommentMqDTO.getReplyCommentId();
                    if (Objects.nonNull(replyCommentId)
                            && replyCommentId > 0
                            && !commentIdAndCommentDOMap.containsKey(replyCommentId)) {
                        log.warn("==> 回复目标评论尚未落库，稍后重试。replyCommentId: {}, commentId: {}",
                                replyCommentId, publishCommentMqDTO.getCommentId());
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                //DTO 转 BO
                List<CommentBO> commentBOS = Lists.newArrayList();
                for (PublishCommentMqDTO publishCommentMqDTO : publishCommentMqDTOS) {
                    String imageUrl = publishCommentMqDTO.getImageUrl();
                    CommentBO commentBO = CommentBO.builder()
                            .id(publishCommentMqDTO.getCommentId())
                            .noteId(publishCommentMqDTO.getNoteId())
                            .userId(publishCommentMqDTO.getCreatorId())
                            .isContentEmpty(true) // 默认评论内容为空
                            .imageUrl(StringUtils.isBlank(imageUrl) ? "" : imageUrl)
                            .level(CommentLevelEnum.ONE.getCode()) // 默认为一级评论
                            .parentId(publishCommentMqDTO.getNoteId()) // 默认设置为所属笔记 ID
                            .createTime(publishCommentMqDTO.getCreateTime())
                            .updateTime(publishCommentMqDTO.getCreateTime())
                            .isTop(false)
                            .replyTotal(0L)
                            .likeTotal(0L)
                            .replyCommentId(0L)
                            .replyUserId(0L)
                            .build();

                    //评论内容若不为空
                    String content = publishCommentMqDTO.getContent();
                    if (StringUtils.isNotBlank(content)) {
                        commentBO.setContentUuid(UUID.randomUUID().toString());//笔记标识
                        commentBO.setIsContentEmpty(false);
                        commentBO.setContent(content);
                    }

                    //设置笔记级别，回复用户 id （replay——user_id），父评论id（parent——id）
                    Long replyCommentId = publishCommentMqDTO.getReplyCommentId();
                    if(Objects.nonNull(replyCommentId) && replyCommentId > 0){
                        CommentDO replyCommentDO = commentIdAndCommentDOMap.get(replyCommentId);

                        if(Objects.nonNull(replyCommentDO)){
                            //若回复的评论id不为空，说明是二级评论
                            commentBO.setLevel(CommentLevelEnum.TWO.getCode());

                            commentBO.setReplyCommentId(publishCommentMqDTO.getReplyCommentId());
                            //父评论id
                            commentBO.setParentId(replyCommentDO.getId());
                            if(Objects.equals(replyCommentDO.getLevel(),CommentLevelEnum.TWO.getCode())){
                                //如果回复的评论属于二级评论------给二级评论回复了的那条评论也被称为二级评论
                                commentBO.setParentId(replyCommentDO.getParentId());
                            }
                            //回复的那个用户-----被回复的评论是谁发的
                            commentBO.setReplyUserId(replyCommentDO.getUserId());
                        }
                    }
                    commentBOS.add(commentBO);
                }

                log.info("## 清洗后的 CommentBOS: {}", JsonUtils.toJsonString(commentBOS));

                //编程式事物，保证整体操作的原子性
                Integer insertedRows = transactionTemplate.execute(status -> {
                    try {
                        //先批量存入评论元数据
                        int count = commentDOMapper.batchInsert(commentBOS);
                        //过滤出评论内容不为空的 BO
                        List<CommentBO> commentContentNotEmptyBOS = commentBOS.stream()
                                .filter(commentBO -> Boolean.FALSE.equals(commentBO.getIsContentEmpty()))
                                .toList();

                        if (CollUtil.isNotEmpty(commentContentNotEmptyBOS)) {
                            //批量写入评论内容
                            keyValueRpcService.batchSaveCommentContent(commentContentNotEmptyBOS);
                        }

                        return count;
                    } catch (Exception e) {
                        status.setRollbackOnly();//标记事物回滚
                        log.error("", e);
                        throw e;
                    }
                });

                //如果插入的行数大于0
                if(Objects.nonNull(insertedRows) && insertedRows > 0){
                    //构建发送给计数服务的 DTO 集合
                    List<CountPublishCommentMqDTO> countPublishCommentMqDTOS = commentBOS.stream()
                            .map(commentBO ->
                                CountPublishCommentMqDTO.builder()
                                        .commentId(commentBO.getId())
                                        .noteId(commentBO.getNoteId())
                                        .level(commentBO.getLevel())
                                        .parentId(commentBO.getParentId())
                                        .build())
                            .toList();

                    //异步发送计数 MQ
                    Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countPublishCommentMqDTOS)).build();

                    // 同步一级评论到 Redis 热点评论 ZSET 中
                    syncOneLevelComment2RedisZSet(commentBOS);

                    //异步发送 MQ 消息
                    rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_COMMENT, message, new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("==> 【计数: 评论发布】MQ 发送成功，SendResult: {}", sendResult);
                        }

                        @Override
                        public void onException(Throwable throwable) {
                            log.error("==> 【计数: 评论发布】MQ 发送异常: ", throwable);
                        }
                    });
                }

                // 手动 ACK，告诉 RocketMQ 这批次消息消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }catch (Exception e){
                log.error("",e);
                // 手动 ACK，告诉 RocketMQ 这批次消息消费失败
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });

        //启动消费者
        consumer.start();
        return consumer;
    }

    /*
    * 同步一级评论到 Redis 热点评论 ZSET 中
    * commentBOS包含一二级评论
    * */
    private void syncOneLevelComment2RedisZSet(List<CommentBO> commentBOS) {
        // 过滤出一级评论，并按所属笔记进行分组，转换为一个 Map 字典
        Map<Long, List<CommentBO>> commentIdAndBOListMap = commentBOS.stream()
                //只保留一级评论
                .filter(commentBO -> Objects.equals(commentBO.getLevel(), CommentLevelEnum.ONE.getCode()))
                .collect(Collectors.groupingBy(CommentBO::getNoteId));

        //循环字典
        commentIdAndBOListMap.forEach((noteId,commemtBOList) -> {
            //构建redis热点评论zset key
            String key = RedisKeyConstants.buildCommentListKey(noteId);

            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            //lua脚本路劲
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/add_hot_comments.lua")));
            //返回值类型
            script.setResultType(Long.class);

            //构建执行lua脚本所需的ARGS参数
           List<Object> agrs = Lists.newArrayList();
           commemtBOList.forEach(commentBO -> {
               agrs.add(commentBO.getId());//评论id
               agrs.add(0);//热度值
           });
            //执行lua脚本-----agrs.toArray()转为数组
            redisTemplate.execute(script,Collections.singletonList(key),agrs.toArray());
        });
    }

    @PreDestroy
    public void destroy(){
        if (Objects.nonNull(consumer)){
            try {
                consumer.shutdown();
            }catch (Exception e){
                log.error("",e);
            }
        }
    }
}
