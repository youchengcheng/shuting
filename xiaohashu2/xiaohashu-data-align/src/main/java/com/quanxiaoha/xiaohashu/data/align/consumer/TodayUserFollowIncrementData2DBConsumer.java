package com.quanxiaoha.xiaohashu.data.align.consumer;


import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.data.align.constant.MQConstants;
import com.quanxiaoha.xiaohashu.data.align.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.data.align.constant.TableConstants;
import com.quanxiaoha.xiaohashu.data.align.domain.mapper.InsertMapper;
import com.quanxiaoha.xiaohashu.data.align.model.dto.FollowUnfollowMqDTO;
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

@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "xiaohashu_group_data_align_" + MQConstants.TOPIC_COUNT_FOLLOWING,
        topic= MQConstants.TOPIC_COUNT_FOLLOWING
)
public class TodayUserFollowIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private InsertMapper insertMapper;

    @Value("${table.shards}")
    private int tableShards;


    @Override
    public void onMessage(String body) {
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", body);

        //将json数据转为dto对象
        FollowUnfollowMqDTO followUnfollowMqDTO = JsonUtils.parseObject(body, FollowUnfollowMqDTO.class);

        if(Objects.isNull(followUnfollowMqDTO)) return;

        //获取关注/取关的源用户id和目标用户id
        Long userId = followUnfollowMqDTO.getUserId();
        Long targetUserId = followUnfollowMqDTO.getTargetUserId();

        //今日日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // ------------------------- 源用户的关注数变更记录 -------------------------
        //源用户id对应的bloom key
        String userBloomKey = RedisKeyConstants.buildBloomUserFollowListKey(date);

        //1.布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        //lua脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_today_user_follow_check.lua")));
        //返回值类型
        script.setResultType(Long.class);

        //执行lua脚本获取结果
        Long result = redisTemplate.execute(script, Collections.singletonList(userBloomKey), userId);

        // Lua 脚本：添加到布隆过滤器
        RedisScript<Long> bloomAddScript = RedisScript
                .of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);

        if(Objects.equals(result,0L)){
            // 2. 若无，才会落库，减轻数据库压力
            //获取分片信息
            long userIdHashKey = userId % tableShards;

            // 将日增量变更数据,写入t_data_align_following_count_temp_日期_分片序号
            try {
                insertMapper.insert2DataAlignUserFollowingCountTempTable(
                        TableConstants.buildTableNameSuffix(date, userIdHashKey), userId);
            }catch (Exception e){
                log.error("",e);
            }

            // 3. 数据库写入成功后，再添加布隆过滤器中
            redisTemplate.execute(bloomAddScript, Collections.singletonList(userBloomKey), userId);
        }



        // ------------------------- 目标用户的粉丝数变更记录 -------------------------
        // 目标用户 ID 对应的 Bloom Key
        String targetUserBloomKey = RedisKeyConstants.buildBloomUserFansListKey(date);
        // 布隆过滤器判断该日增量数据是否已经记录
        result = redisTemplate.execute(script, Collections.singletonList(targetUserBloomKey), targetUserId);

        if(Objects.equals(result,0L)){
            // 2.若无，才会落库，减轻数据库压力
            //获取分片信息
            long targetUserIdHashKey = targetUserId % tableShards;

            // 将日增量变更数据，写入t_data_align_fans_count_temp_日期_分片序号
            try {
                insertMapper.insert2DataAlignUserFansCountTempTable(
                        TableConstants.buildTableNameSuffix(date, targetUserIdHashKey), targetUserId);
            }catch (Exception e){
                log.error("",e);
            }

            // 3. 数据库写入成功后，再添加布隆过滤器中
            redisTemplate.execute(bloomAddScript, Collections.singletonList(targetUserBloomKey), targetUserId);
        }


    }
}
