package com.quanxiaoha.xiaohashu.comment.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentHeatBO;
import com.quanxiaoha.xiaohashu.comment.biz.util.HeatCalculator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.retry.annotation.Recover;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/*
* 评论热度值计算
* */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group_" + MQConstants.TOPIC_COMMENT_HEAT_UPDATE,
        topic = MQConstants.TOPIC_COMMENT_HEAT_UPDATE
)
public class CommentHeatUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    //快手buffertrigger
    private BufferTrigger bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000)
            .batchSize(300)
            .linger(Duration.ofSeconds(2))
            .setConsumerEx(this::consumeMessage)//设置消费者方法
            .build();
    
    @Override
    public void onMessage(String message) {
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【评论热度值计算】聚合消息, size: {}", bodys.size());
        log.info("==> 【评论热度值计算】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 将聚合后的消息体 Json 转 Set<Long>, 去重相同的评论 ID, 防止重复计算
        Set<Long> commentIds = Sets.newHashSet();

        bodys.forEach(body -> {
            try {
                Set<Long> list = JsonUtils.parseSet(body, Long.class);
                commentIds.addAll(list);
            }catch (Exception e){
                log.error("",e);
            }
        });

        log.info("==> 去重后的评论 ID: {}", commentIds);

        //批量查询评论
        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(commentIds.stream().toList());

        //评论id
        List<Long> ids = new ArrayList<>();
        //热度id
        List<CommentHeatBO> commentHeatBOS = new ArrayList<>();

        //重新计算每条评论的热度值
        commentDOS.forEach(commentDO -> {
            Long commentId = commentDO.getId();
            //被点赞数
            Long likeTotal = commentDO.getLikeTotal();
            //被回复数
            Long replyTotal = commentDO.getChildCommentTotal();

            //计算热度值
            BigDecimal heatNum = HeatCalculator.calculateHeat(likeTotal, replyTotal);
            ids.add(commentId);
            commentHeatBOS.add(CommentHeatBO.builder()
                    .id(commentId)
                    .heat(heatNum.doubleValue())
                    .noteId(commentDO.getNoteId())
                    .build());
        });

        //批量更新评论热度值
        int count = commentDOMapper.batchUpdateHeatByCommentIds(ids, commentHeatBOS);

        if(count ==0) return;

        // 更新 Redis 中热度评论 ZSET
        updateRedisHotComments(commentHeatBOS);
    }

    /*
    * 更新 Redis 中热点评论 ZSET
    * */
    private void updateRedisHotComments(List<CommentHeatBO> commentHeatBOS) {
        // 过滤出热度值大于 0 的，并按所属笔记 ID 分组（若热度等于0，则不进行更新）
        Map<Long, List<CommentHeatBO>> noteIdAndBOListMap = commentHeatBOS.stream()
                .filter(commentHeatBO -> commentHeatBO.getHeat() > 0)
                .collect(Collectors.groupingBy(CommentHeatBO::getNoteId));

        //循环
        noteIdAndBOListMap.forEach((noteId,commentHeatBDS) -> {
            //构建热点评论redis key
            String key = RedisKeyConstants.buildCommentListKey(noteId);

            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            //lua脚本路径
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/update_hot_comments.lua")));
            //返回值类型
            script.setResultType(Long.class);

            //构建执行lua脚本所需的ARGS参数
            List<Object> agrs = Lists.newArrayList();
            commentHeatBDS.forEach(commentHeatBO -> {
                agrs.add(commentHeatBO.getId());//评论id
                agrs.add(commentHeatBO.getHeat());//热度值
            });
            //执行lua脚本
            redisTemplate.execute(script,Collections.singletonList(key),agrs.toArray());
        });









    }
}
