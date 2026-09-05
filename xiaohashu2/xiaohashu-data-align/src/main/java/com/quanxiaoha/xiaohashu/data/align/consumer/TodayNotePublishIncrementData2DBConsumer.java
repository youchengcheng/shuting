package com.quanxiaoha.xiaohashu.data.align.consumer;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.data.align.constant.MQConstants;
import com.quanxiaoha.xiaohashu.data.align.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.data.align.constant.TableConstants;
import com.quanxiaoha.xiaohashu.data.align.domain.mapper.InsertMapper;
import com.quanxiaoha.xiaohashu.data.align.model.dto.NoteOperateMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

/**
 * 日增量数据落库：笔记发布、删除
 **/
@Component
@RocketMQMessageListener(consumerGroup = "xiaohashu_group_data_align_" + MQConstants.TOPIC_NOTE_OPERATE, // Group 组
        topic = MQConstants.TOPIC_NOTE_OPERATE // 主题 Topic
)
@Slf4j
public class TodayNotePublishIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private InsertMapper insertMapper;

    @Value("${table.shards}")
    private int tableShards;

    @Override
    public void onMessage(String body) {
        log.info("## TodayNotePublishIncrementData2DBConsumer 消费到了 MQ: {}", body);

        //消息体json转为dto
        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(body, NoteOperateMqDTO.class);

        if(Objects.isNull(noteOperateMqDTO)) return;

        //获取删除或发布笔记作者id
        Long noteCreatorId = noteOperateMqDTO.getCreatorId();

        //获取今日日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));//转字符串

        String bloomKey = RedisKeyConstants.buildBloomUserNoteOperateListKey(date);

        //1.布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        //lua脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_today_user_note_publish_check.lua")));
        //返回值类型
        script.setResultType(Long.class);

        //执行lua脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(bloomKey), noteCreatorId);

        if(Objects.equals(result,0L)){
            //2.若无，才会落库，减轻数据库压力

            //根据总分片数，取模，分别获取对应的分片序号
            long userIdHashKey = noteCreatorId % tableShards;

            //将日增量变更数据，写入日增量表中
            // - t_data_align_note_publish_count_temp_日期_分片序号
            insertMapper.insert2DataAlignUserNotePublishCountTempTable(TableConstants.buildTableNameSuffix(date,userIdHashKey),noteCreatorId);

            //3.数据写入成功后，在添加到布隆过滤中
            RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])", Long.class);
            redisTemplate.execute(bloomAddScript,Collections.singletonList(bloomKey),noteCreatorId);
        }
    }
}
