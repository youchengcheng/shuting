package com.quanxiaoha.xiaohashu.count.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.count.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.count.biz.domain.dataobject.NoteCountDO;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.NoteCountDOMapper;
import com.quanxiaoha.xiaohashu.count.dto.FindNoteCountsByIdRspDTO;
import com.quanxiaoha.xiaohashu.count.dto.FindNoteCountsByIdsReqDTO;
import com.quanxiaoha.xiaohashu.count.biz.service.NoteCountService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/*
* 笔记计数
* */
@Service
@Slf4j
public class NoteCountServiceImpl implements NoteCountService {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    /*
     * 批量查询笔记计数
     * */
    @Override
    public Response<List<FindNoteCountsByIdRspDTO>> findNotesCountData(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO) {
        //需要查询的笔记id集合
        List<Long> noteIds = findNoteCountsByIdsReqDTO.getNoteIds();
        // 1.先查询 Redis 缓存
        //构建rediskey集合
        List<String> hashKeys = noteIds.stream()
                .map(RedisKeyConstants::buildCountNoteKey)
                .toList();

        //使用pipeline通道 ，从redis中批量查询笔记hash计数
        List<Object> countHashes = getCountHashesByPipelineFromRedis(hashKeys);

        //反参 DTO
        List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = Lists.newArrayList();

        //用于存储缓存中不存在，需要查数据的笔记id
        List<Long> noteIdsNeedQuery = Lists.newArrayList();

        //循环入参中需要查询的笔记id集合，构建对应的DTO，并设置缓存中已存在的计数，以及过滤出需要查询数据库的笔记id
        for (int i = 0; i < noteIds.size(); i++) {
            Long currNoteId = noteIds.get(i);
            List<Integer> currCountHash = (List<Integer>) countHashes.get(i);

            //点赞数，收藏数，评论数
            Integer likeTotal = currCountHash.get(0);
            Integer collectTotal = currCountHash.get(1);
            Integer commentTotal = currCountHash.get(2);

            //hash中存在任意一个field为空，都需要查询数据库
            if (Objects.isNull(likeTotal) || Objects.isNull(collectTotal) || Objects.isNull(commentTotal)) {
                noteIdsNeedQuery.add(currNoteId);
            }

            //构建DTO
            FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = FindNoteCountsByIdRspDTO.builder()
                    .noteId(currNoteId)
                    .likeTotal(Objects.nonNull(likeTotal) ? Long.valueOf(likeTotal) : null)
                    .collectTotal(Objects.nonNull(collectTotal) ? Long.valueOf(collectTotal) : null)
                    .commentTotal(Objects.nonNull(commentTotal) ? Long.valueOf(commentTotal) : null)
                    .build();

            findNoteCountsByIdRspDTOS.add(findNoteCountsByIdRspDTO);
        }

        //所有hash计数都在redis中，直接反参
        if(CollUtil.isEmpty(noteIdsNeedQuery)) {
            return Response.success(findNoteCountsByIdRspDTOS);
        }

        // 2.若缓存中无，则查询数据库
        //从数据库中批量查询过滤出的noteIdsNeedQuery笔记id
        List<NoteCountDO> noteCountDOS = noteCountDOMapper.selectByNoteIds(noteIdsNeedQuery);

        //若数据库查询不为空
        if(CollUtil.isNotEmpty(noteCountDOS)) {
            //DO转MAP，方便后续查对应的笔记的计数
            Map<Long, NoteCountDO> noteIdAndDOMap = noteCountDOS.stream()
                    .collect(Collectors.toMap(NoteCountDO::getNoteId, noteCountDO -> noteCountDO));

            //将笔记hash计数同步到redis中
            syncNoteHash2Redis(findNoteCountsByIdRspDTOS, noteIdAndDOMap);

            //针对 DTO 中为 null 的数据字段，循环设置从数据库中查询到的计数
            for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

                NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);
                if (Objects.isNull(likeTotal))
                    findNoteCountsByIdRspDTO.setLikeTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : 0);
                if (Objects.isNull(collectTotal))
                    findNoteCountsByIdRspDTO.setCollectTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : 0);
                if (Objects.isNull(commentTotal))
                    findNoteCountsByIdRspDTO.setCommentTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : 0);
            }

        }

        return Response.success(findNoteCountsByIdRspDTOS);
    }

    /*
     * 将笔记hash计数同步到redis中
     * */
    private void syncNoteHash2Redis(List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS, Map<Long, NoteCountDO> noteIdAndDOMap) {
        //将笔记计数同步到redis中
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                //循环已构建号的反参DTO集合
                for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                    Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                    Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                    Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

                    //若当前所有计数都不为空，则无需同步hash
                    if (Objects.nonNull(likeTotal) && Objects.nonNull(collectTotal) && Objects.nonNull(commentTotal)) {
                        continue;
                    }

                    //否则，若有任意一个field计数为空，则就要进行同步
                    Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                    //构建key
                    String noteCountHashKey = RedisKeyConstants.buildCountNoteKey(noteId);

                    //设置field计数
                    HashMap<String,Long> countMap = Maps.newHashMap();
                    NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);

                    if (Objects.isNull(likeTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : 0);
                    }
                    if (Objects.isNull(collectTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : 0);
                    }
                    if (Objects.isNull(commentTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COMMENT_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : 0);
                    }

                    //批量添加hash的计数field
                    operations.opsForHash().putAll(noteCountHashKey,countMap);
                    //设置随机过期时间
                    long expireTime = 60*30 + RandomUtil.randomInt(60 * 30);
                    operations.expire(noteCountHashKey, expireTime, TimeUnit.SECONDS);
                }
                return null;
            }
        });
    }

    /*
     * 使用pipeline通道 ，从redis中批量查询笔记hash计数
     * */
    private List<Object> getCountHashesByPipelineFromRedis(List<String> hashKeys) {
        return redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String hashKey : hashKeys) {
                    //批量获取多个字段
                    operations.opsForHash().multiGet(hashKey,List.of(
                            RedisKeyConstants.FIELD_LIKE_TOTAL,
                            RedisKeyConstants.FIELD_COLLECT_TOTAL,
                            RedisKeyConstants.FIELD_COMMENT_TOTAL
                    ));
                }
                return null;
            }
        });

    }

}
