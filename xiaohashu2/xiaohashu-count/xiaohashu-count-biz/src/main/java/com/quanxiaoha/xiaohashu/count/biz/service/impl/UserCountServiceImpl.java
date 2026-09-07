package com.quanxiaoha.xiaohashu.count.biz.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Maps;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.count.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.count.biz.domain.dataobject.UserCountDO;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.UserCountDOMapper;
import com.quanxiaoha.xiaohashu.count.biz.service.UserCountService;
import com.quanxiaoha.xiaohashu.count.dto.FindUserCountsByIdReqDTO;
import com.quanxiaoha.xiaohashu.count.dto.FindUserCountsByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*
* 用户计数业务
* */
@Slf4j
@Service
public class UserCountServiceImpl implements UserCountService {

    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /*
    * 查询用户相关计数
    * */
    @Override
    public Response<FindUserCountsByIdRspDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        //获取用户id
        Long userId = findUserCountsByIdReqDTO.getUserId();

        //1.先从redis中获取用户计数
        FindUserCountsByIdRspDTO findUserCountByIdRspDTO = FindUserCountsByIdRspDTO.builder()
                .userId(userId)
                .build();

        //构建key
        String userCountHashKey = RedisKeyConstants.buildCountUserKey(userId);

        //批量查询
        List<Object> counts = redisTemplate.opsForHash().multiGet(userCountHashKey, List.of(
                RedisKeyConstants.FIELD_COLLECT_TOTAL,
                RedisKeyConstants.FIELD_COLLECT_TOTAL,
                RedisKeyConstants.FIELD_FANS_TOTAL,
                RedisKeyConstants.FIELD_NOTE_TOTAL,
                RedisKeyConstants.FIELD_FOLLOWING_TOTAL,
                RedisKeyConstants.FIELD_LIKE_TOTAL
        ));

        //若redis（hash）中计数不为空，优先以其为主
        Object collectTotal = counts.get(0);
        Object fansTotal = counts.get(1);
        Object noteTotal = counts.get(2);
        Object followingTotal = counts.get(3);
        Object likeTotal = counts.get(4);

        findUserCountByIdRspDTO.setCollectTotal(Objects.isNull(collectTotal) ? 0 : Long.parseLong(String.valueOf(collectTotal)));
        findUserCountByIdRspDTO.setFansTotal(Objects.isNull(fansTotal) ? 0 : Long.parseLong(String.valueOf(fansTotal)));
        findUserCountByIdRspDTO.setNoteTotal(Objects.isNull(noteTotal) ? 0 : Long.parseLong(String.valueOf(noteTotal)));
        findUserCountByIdRspDTO.setFollowingTotal(Objects.isNull(followingTotal) ? 0 : Long.parseLong(String.valueOf(followingTotal)));
        findUserCountByIdRspDTO.setLikeTotal(Objects.isNull(likeTotal) ? 0 : Long.parseLong(String.valueOf(likeTotal)));

        // 若 Hash 中有任何一个计数为空
        boolean isAnyNull = counts.stream().anyMatch(Objects::isNull);

        if (isAnyNull) {
            // 从数据库查询该用户的计数
            UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);

            // 判断 Redis 中对应计数，若为空，则使用 DO 中的计数
            if (Objects.nonNull(userCountDO) && Objects.isNull(collectTotal)) {
                findUserCountByIdRspDTO.setCollectTotal(userCountDO.getCollectTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(fansTotal)) {
                findUserCountByIdRspDTO.setFansTotal(userCountDO.getFansTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(noteTotal)) {
                findUserCountByIdRspDTO.setNoteTotal(userCountDO.getNoteTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(followingTotal)) {
                findUserCountByIdRspDTO.setFollowingTotal(userCountDO.getFollowingTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(likeTotal)) {
                findUserCountByIdRspDTO.setLikeTotal(userCountDO.getLikeTotal());
            }

            // 异步同步到 Redis 缓存中, 以便下次查询能够命中缓存
            syncHashCount2Redis(userCountHashKey, userCountDO, collectTotal, fansTotal, noteTotal, followingTotal, likeTotal);
        }

        //2.从数据库获取用户计数
        //构建反参对象
        //设置相关计数的默认值为 0，防止数据库查询不到记录时，返回计数为 null
        FindUserCountsByIdRspDTO findUserCountsByIdRspDTO = FindUserCountsByIdRspDTO.builder()
                .userId(userId)
                .fansTotal(0L)
                .followingTotal(0L)
                .noteTotal(0L)
                .likeTotal(0L)
                .collectTotal(0L)
                .build();

        //从数据查询该用户的计数
        UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);

        //若计数不为空，设置相关计数数据
        if(Objects.nonNull(userCountDO)){
            findUserCountsByIdRspDTO.setFansTotal(userCountDO.getFansTotal());
            findUserCountsByIdRspDTO.setFollowingTotal(userCountDO.getFollowingTotal());
            findUserCountsByIdRspDTO.setNoteTotal(userCountDO.getNoteTotal());
            findUserCountsByIdRspDTO.setLikeTotal(userCountDO.getLikeTotal());
            findUserCountsByIdRspDTO.setCollectTotal(userCountDO.getCollectTotal());
        }

        return Response.success(findUserCountsByIdRspDTO);
    }


    /*
    * 异步同步到 Redis 缓存中, 以便下次查询能够命中缓存
    * */
    private void syncHashCount2Redis(String userCountHashKey, UserCountDO userCountDO, Object collectTotal, Object fansTotal, Object noteTotal, Object followingTotal, Object likeTotal) {
        if(Objects.nonNull(userCountDO)){
            threadPoolTaskExecutor.submit(() -> {
                // 存放计数
                Map<String, Long> userCountMap = Maps.newHashMap();
                if (Objects.isNull(collectTotal))
                    userCountMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL, Objects.isNull(userCountDO.getCollectTotal()) ? 0 : userCountDO.getCollectTotal());

                if (Objects.isNull(fansTotal))
                    userCountMap.put(RedisKeyConstants.FIELD_FANS_TOTAL, Objects.isNull(userCountDO.getFansTotal()) ? 0 : userCountDO.getFansTotal());

                if (Objects.isNull(noteTotal))
                    userCountMap.put(RedisKeyConstants.FIELD_NOTE_TOTAL, Objects.isNull(userCountDO.getNoteTotal()) ? 0 : userCountDO.getNoteTotal());

                if (Objects.isNull(followingTotal))
                    userCountMap.put(RedisKeyConstants.FIELD_FOLLOWING_TOTAL, Objects.isNull(userCountDO.getFollowingTotal()) ? 0 : userCountDO.getFollowingTotal());

                if (Objects.isNull(likeTotal))
                    userCountMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL, Objects.isNull(userCountDO.getLikeTotal()) ? 0 : userCountDO.getLikeTotal());

                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        //批量添加hash的计数field
                        operations.opsForHash().putAll(userCountHashKey,userCountMap);

                        //设置过期时间
                        long expireTime = 60*60 + RandomUtil.randomInt(60*60);
                        operations.expire(userCountHashKey,expireTime, TimeUnit.SECONDS);

                        return null;
                    }
                });
            });
        }
    }
}
