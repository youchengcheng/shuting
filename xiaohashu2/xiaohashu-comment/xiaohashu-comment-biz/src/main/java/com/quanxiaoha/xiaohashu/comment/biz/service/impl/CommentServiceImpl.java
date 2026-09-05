package com.quanxiaoha.xiaohashu.comment.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.constant.DateConstants;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.comment.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentLikeDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentLikeDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.NoteCountDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.*;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;
import com.quanxiaoha.xiaohashu.comment.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.comment.biz.retry.SendMqRetryHelper;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.DistributedIdGeneratorRpcService;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentService;
import com.quanxiaoha.xiaohashu.kv.api.KeyValueFeignApi;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindCommentContentRspDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.K;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/*
* 笔记服务
* */
@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Resource
    private SendMqRetryHelper sendMqRetryHelper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 评论详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    /*
     *发布评论
     * */
    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        //评论正文
        String content = publishCommentReqVO.getContent();
        //附件图片
        String imageUrl = publishCommentReqVO.getImageUrl();

        //评论内容和图片不能同时为空
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论内容和图片不能同时为空");

        //获取笔记发布者id
        Long userId = LoginUserContextHolder.getUserId();
        //调用分布式 id 生成服务
        String commentId = distributedIdGeneratorRpcService.generateCommentId();

        //---------发送MQ-----------
        //构建消息体 DTO
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .noteId(publishCommentReqVO.getNoteId())
                .content(content)
                .imageUrl(imageUrl)
                .replyCommentId(publishCommentReqVO.getReplyCommentId())
                .createTime(LocalDateTime.now())
                .creatorId(userId)
                .commentId(Long.valueOf(commentId))
                .build();

        //构建消息对象，并将 DTO 转为 json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(publishCommentMqDTO)).build();

        //发送 MQ，包含重试机制
        sendMqRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT,JsonUtils.toJsonString(publishCommentMqDTO));

        // 返回异步落库前已经生成的评论 ID，前端需要用它继续点赞或回复该评论
        return Response.success(Long.valueOf(commentId));
    }

    /**
     * 评论列表分页查询
     */
    @Override
    public PageResponse<FindCommentItemRspVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO) {
        //笔记 id
        Long noteId = findCommentPageListReqVO.getNoteId();
        //当前页码
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        //每页展示一级评论数
        long pageSize = 10;

        //从缓存中查询数据
        //构建评论总数redis key
        String noteCommentTotalKey = RedisKeyConstants.buildNoteCommentTotalKey(noteId);
        //先从redis中查询该笔记的评论总数,RedisKeyConstants.FIELD_COMMENT_TOTAL这是评论总数字段
        Number commentTotal = (Number) redisTemplate.opsForHash().get(noteCommentTotalKey, RedisKeyConstants.FIELD_COMMENT_TOTAL);
        // 如果 Redis 中没有值，就用 0；有值就转成 long 类型
        long count = Objects.isNull(commentTotal) ? 0L : commentTotal.longValue();

        //若缓存不存在，则查询数据库
        if(Objects.isNull(commentTotal)) {
            //查询评论总数
            Long dbCount = noteCountDOMapper.selectCommentTotalByNoteId(noteId);

            //若数据库中也不存在，则抛出业务异常
            if(Objects.isNull(dbCount)) {
                throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
            }

            count = dbCount;
            //异步将评论总数同步到redis中
            threadPoolTaskExecutor.execute(() -> {
                syncNoteCommentTotal2Redis(noteCommentTotalKey,dbCount);
            });
        }
        if(count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        //分页反参
        List<FindCommentItemRspVO> commentRspVOS = new ArrayList<>();

        //计算分页查询的偏移量offset
        long offset = PageResponse.getOffset(pageNo, pageSize);

        //评论分页缓存使用ZSET + STRING 实现
        //构建评论ZSET key
        String commentZsetKey = RedisKeyConstants.buildCommentListKey(noteId);

        //判断ZSET是否存在
        Boolean hasKey = redisTemplate.hasKey(commentZsetKey);

        //若不存在，异步将热点评论同步到redis中
        if(!hasKey){threadPoolTaskExecutor.execute(() -> syncHeatComments2Redis(commentZsetKey,noteId));}

        //若ZSET存在，并且查询的是前五十页的评论，50页之后依然是查询数据库
        if(hasKey && offset < 500) {
            //使用ZRevrange 获取谋篇笔记下，按热度降序排序的一级评论id
            Set<Object> commnetIds = redisTemplate.opsForZSet()
                    .reverseRangeByScore(commentZsetKey, -Double.MAX_VALUE, Double.MAX_VALUE, offset, pageSize);

            //若结果不为空
            if(CollUtil.isNotEmpty(commnetIds)) {
                //set 转 list
                List<Object> commentIdList = Lists.newArrayList(commnetIds);

                //先查询本地缓存
                //新建一个集合，用于存储本地缓存中不存在的评论id
                List<Long> localCacheExpiredCommentIds = new ArrayList<>();

                //构建查询本地缓存的key集合
                List<Long> localCacheKeys = commentIdList.stream()
                        .map(commentId -> Long.valueOf(commentId.toString())).toList();


                /**
                 * 批量查询本地缓存
                 * localCacheKeys：要查询的笔记id
                 * missingKeys：查询内容为空的笔记id
                 */
                //commentIdAndDetailJsonMap是所有的评论数据，包含有内容的评论和没有内容的评论
                Map<Long, String> commentIdAndDetailJsonMap = LOCAL_CACHE.getAll(localCacheKeys, missingKeys -> {
                    //对于本地缓存中缺失的 key，返回空字符串
                    Map<Long, String> missingData = Maps.newHashMap();
                    missingKeys.forEach(missingKey -> {
                        //记录缓存中不存在的评论id
                        localCacheExpiredCommentIds.add(missingKey);
                        //不存在的评论详情，对其value值设置为空字符串
                        missingData.put(missingKey, Strings.EMPTY);
                    });
                    return missingData;
                });

                // 若 localCacheExpiredCommentIds 的大小不等于 commentIdList 的大小，说明本地缓存中有数据
                if(CollUtil.size(localCacheExpiredCommentIds) != commentIdList.size()){
                    // 将本地缓存中的评论详情 Json, 转换为实体类，添加到 VO 返参集合中
                    for (String value : commentIdAndDetailJsonMap.values()) {
                        //判断从本地缓存中的查询的评论是否有内容，没有就跳过
                        if(StringUtils.isBlank(value)) continue;
                        FindCommentItemRspVO commentRspVO = JsonUtils.parseObject(value, FindCommentItemRspVO.class);
                        commentRspVOS.add(commentRspVO);
                    }
                }

                // 若 localCacheExpiredCommentIds 大小等于 0，说明评论详情数据都在本地缓存中，直接响应返参
                if(CollUtil.size(localCacheExpiredCommentIds) == 0) {
                    setCommentCountData(commentRspVOS,localCacheExpiredCommentIds);
                    log.info("本次查询走的是本地缓存，查询的数据是：{}",commentRspVOS);
                    return PageResponse.success(commentRspVOS,pageNo,count,pageSize);
                }

                //构建mget批量查询评论详情的key集合
                List<String> commentIdKeys = localCacheExpiredCommentIds.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey)
                        .toList();

                //mget批量获取评论数据
                List<Object> commentsJsonList = redisTemplate.opsForValue().multiGet(commentIdKeys);

                // 可能存在部分评论不在缓存中，已经过期被删除，这些评论 ID 需要提取出来，等会查数据库
                List<Long> expiredCommentIds = Lists.newArrayList();

                for (int i = 0; i < commentsJsonList.size(); i++) {
                    String commentJson = (String) commentsJsonList.get(i);
                    //缓存中存在的评论json，直接转换为VO，添加到反参集合中
                    if(Objects.nonNull(commentJson)){
                        FindCommentItemRspVO commentRspVO = parseCachedComment(commentJson, FindCommentItemRspVO.class);
                        if (Objects.nonNull(commentRspVO)) {
                            commentRspVOS.add(commentRspVO);
                        } else {
                            expiredCommentIds.add(Long.valueOf(commentIdList.get(i).toString()));
                        }
                    }else {
                        //评论失效，添加到失效评论列表
                        expiredCommentIds.add(Long.valueOf(commentIdList.get(i).toString()));
                    }
                }

                // 对于缓存中存在的评论详情, 需要再次查询其计数数据
                if (CollUtil.isNotEmpty(commentRspVOS)) {
                    setCommentCountData(commentRspVOS, expiredCommentIds);
                }

                // 对于不存在的一级评论，需要批量从数据库中查询，并添加到 commentRspVOS 中
                if(CollUtil.isNotEmpty(expiredCommentIds)) {
                    List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredCommentIds);
                    getCommentDataAndSync2Redis(commentDOS,noteId,commentRspVOS);
                }
            }
            // 按热度值进行降序排列
            commentRspVOS = commentRspVOS.stream()
                    .sorted(Comparator.comparing(FindCommentItemRspVO::getHeat).reversed())
                    .collect(Collectors.toList());

            // 异步将评论详情，同步到本地缓存
            syncCommentDetail2LocalCache(commentRspVOS);

            return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
        }

        //查询一级评论
        List<CommentDO> oneLevelCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
        getCommentDataAndSync2Redis(oneLevelCommentDOS, noteId, commentRspVOS);

        // 异步将评论详情，同步到本地缓存
        syncCommentDetail2LocalCache(commentRspVOS);

        return PageResponse.success(commentRspVOS,pageNo,count,pageSize);
    }

    /*
    * 二级评论分页查询
    * */
    @Override
    public PageResponse<FindChildCommentItemRspVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO) {
        //父评论id
        Long parentCommentId = findChildCommentPageListReqVO.getParentCommentId();
        //当前页码
        Integer pageNo = findChildCommentPageListReqVO.getPageNo();
        //每页展示的二级评论数（默认为6）
        long pageSize = 6;

        //先从缓存中查询
        String countCommentKey = RedisKeyConstants.buildCountCommentKey(parentCommentId);//构建key
        //子评论总数
        Number redisCount =(Number) redisTemplate.opsForHash().get(countCommentKey, RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL);

        long count = Objects.isNull(redisCount) ? 0L : redisCount.longValue();

        //若缓存不存在，走数据库查询
        if(Objects.isNull(redisCount)) {
            // 查询一级评论下子评论的总数 (直接查询 t_comment 表的 child_comment_total 字段，提升查询性能, 避免 count(*))
            Long dbCount = commentDOMapper.selectChildCommentTotalById(parentCommentId);

            //若数据库中也不存在，则抛出业务异常
            if(Objects.isNull(dbCount)) {
                throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
            }

            count = dbCount;
            //异步将子评论总数同步到redis中
            threadPoolTaskExecutor.execute(() -> {
                syncCommentCount2Redis(countCommentKey,dbCount);
            });
        }

        // 若子评论总数为 0，直接返参
        if(count == 0){
            return PageResponse.success(Lists.newArrayList(), pageNo, 0, pageSize);
        }

        //分页反参VO
        List<FindChildCommentItemRspVO> childCommentRspVOS = Lists.newArrayList();

        // 返回完整分页数据；前端会对已经预置展示的 firstReplyComment 去重
        long offset = PageResponse.getOffset(pageNo, pageSize);

        //子评论分页缓存使用zset + string实现
        //构建子评论zset key
        String childCommentZsetKey = RedisKeyConstants.buildChildCommentListKey(parentCommentId);
        //先判断zset是否存在
        Boolean hasKey = redisTemplate.hasKey(childCommentZsetKey);

        //若不存在
        if(!hasKey) {
            //异步将子评论同步到redis中（最多60条）
            threadPoolTaskExecutor.execute(() -> {
                syncChildComments2Redis(parentCommentId, childCommentZsetKey);
            });
        }

        //若子评论zset缓存存在，并查询的是前10页的子评论
        if(hasKey && offset < 6 * 10) {
            // 使用 ZRevRange 获取某个一级评论下的子评论，按回复时间升序排列
            Set<Object> childCommentIds = redisTemplate.opsForZSet()
                    .rangeByScore(childCommentZsetKey, 0, Double.MAX_VALUE, offset, pageSize);

            // 若结果不为空
            if (CollUtil.isNotEmpty(childCommentIds)) {
                // Set 转 List
                List<Object> childCommentIdList = Lists.newArrayList(childCommentIds);

                // 构建 MGET 批量查询子评论详情的 Key 集合
                List<String> commentIdKeys = childCommentIds.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey)
                        .toList();

                // MGET 批量获取评论数据
                List<Object> commentsJsonList = redisTemplate.opsForValue().multiGet(commentIdKeys);

                // 可能存在部分评论不在缓存中，已经过期被删除，这些评论 ID 需要提取出来，等会查数据库
                List<Long> expiredChildCommentIds = Lists.newArrayList();

                for (int i = 0; i < commentsJsonList.size(); i++) {
                    String commentJson = (String) commentsJsonList.get(i);
                    Long commentId = Long.valueOf(childCommentIdList.get(i).toString());
                    if (Objects.nonNull(commentJson)) {
                        // 缓存中存在的评论 Json，直接转换为 VO 添加到返参集合中
                        FindChildCommentItemRspVO childCommentRspVO = parseCachedComment(commentJson, FindChildCommentItemRspVO.class);
                        if (Objects.nonNull(childCommentRspVO)) {
                            childCommentRspVOS.add(childCommentRspVO);
                        } else {
                            expiredChildCommentIds.add(commentId);
                        }
                    } else {
                        // 评论失效，添加到失效评论列表
                        expiredChildCommentIds.add(commentId);
                    }
                }
                //对于缓存中存在的子评论, 需要再次查询 Hash, 获取其计数数据
                if (CollUtil.isNotEmpty(childCommentRspVOS)) {
                    setChildCommentCountData(childCommentRspVOS, expiredChildCommentIds);
                }

                // 对于不存在的子评论，需要批量从数据库中查询，并添加到 commentRspVOS 中
                if(CollUtil.isNotEmpty(expiredChildCommentIds)) {
                    List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredChildCommentIds);
                    getChildCommentDataAndSync2Redis(commentDOS, childCommentRspVOS);
                }

                //按批量id升序排序（等同于按回复时间升序）
                childCommentRspVOS = childCommentRspVOS.stream()
                        .sorted(Comparator.comparing(FindChildCommentItemRspVO::getCommentId))
                        .collect(Collectors.toList());

                return PageResponse.success(childCommentRspVOS, pageNo, count, pageSize);
            }
        }

        //分页查询子评论
        List<CommentDO> childCommentDOS = commentDOMapper.selectChildPageList(parentCommentId, offset, pageSize);

        getChildCommentDataAndSync2Redis(childCommentDOS, childCommentRspVOS);

        return PageResponse.success(childCommentRspVOS, pageNo, count, pageSize);
    }

    /**
     * 评论点赞
     */
    @Override
    public Response<?> likeComment(LikeCommentReqVO likeCommentReqVO) {
        //笔记id
        Long commentId = likeCommentReqVO.getCommentId();

        //当前登录用户id
        Long userId = LoginUserContextHolder.getUserId();

        // 1. 校验被点赞的评论是否存在
        checkCommentIsExist(commentId);

        // 2. 判断目标评论，是否已经被点赞
        //布隆过滤器key
        String bloomUserCommentLikeListKey = RedisKeyConstants.buildBloomCommentLikesKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        //lua脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_comment_like_check.lua")));
        //返回值类型
        script.setResultType(Long.class);
        //执行lua脚本，并拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(bloomUserCommentLikeListKey), commentId);

        CommentLikeLuaResultEnum commentLikeLuaResultEnum = CommentLikeLuaResultEnum.valueOf(result);

        //返回结果未知，抛出异常
        if(Objects.isNull(commentLikeLuaResultEnum)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        switch (commentLikeLuaResultEnum){
            case NOT_EXIST -> {//redis中布隆过滤器不存在
                // 从数据库中校验评论是否被点赞，并异步初始化布隆过滤器，设置过期时间
                int count = commentLikeDOMapper.selectCountByUserIdAndCommentId(userId, commentId);

                // 保底1小小时+随机秒数
                long expireSeconds = 60*60 + RandomUtil.randomInt(60*60);

                //若count大于0说明已经点赞了，这时异步初始化布隆过滤器，并抛出异常
                if(count > 0) {
                    //异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() ->
                            batchAddCommentLike2BloomAndExpire(userId, expireSeconds, bloomUserCommentLikeListKey));

                    throw new BizException(ResponseCodeEnum.COMMENT_ALREADY_LIKED);
                }
                // 若目标评论未被点赞，查询当前用户是否有点赞其他评论，有则同步初始化布隆过滤器
                batchAddCommentLike2BloomAndExpire(userId, expireSeconds, bloomUserCommentLikeListKey);

                //添加当前未点赞的评论id到布隆过滤器中
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_comment_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                redisTemplate.execute(script, Collections.singletonList(bloomUserCommentLikeListKey), commentId, expireSeconds);
            }
            case COMMENT_LIKED -> {//目标评论已经被点赞（可能存在误判，要进一步确认）
                // 查询数据库校验是否点赞
                int count = commentLikeDOMapper.selectCountByUserIdAndCommentId(userId, commentId);

                if (count > 0) {
                    throw new BizException(ResponseCodeEnum.COMMENT_ALREADY_LIKED);
                }
            }
        }

        // 3. 发送 MQ, 异步将评论点赞记录落库
        //构建消息体 DTO
        LikeUnlikeCommentMqDTO likeUnlikeCommentMqDTO = LikeUnlikeCommentMqDTO.builder()
                .commentId(commentId)
                .type(LikeUnlikeCommentTypeEnum.LIKE.getCode())
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();

        //构建消息对象，并将 DTO 转成 json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeCommentMqDTO)).build();


        //通过冒号连接，可让MQ发送给主题Topic时，携带上标签tag
        String destination = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        //MQ分区键-----hashKey相同的消息会被路由到同一个队列
        String hashKey = String.valueOf(userId);

        //异步发送MQ消息，提升接口响应速度-----asyncSendOrderly顺序消息
        rocketMQTemplate.asyncSendOrderly(destination,message,hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论点赞】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 取消评论点赞
     */
    @Override
    public Response<?> unlikeComment(UnLikeCommentReqVO unLikeCommentReqVO) {
        // 被取消点赞的评论 ID
        Long commentId = unLikeCommentReqVO.getCommentId();

        // 1. 校验评论是否存在
        checkCommentIsExist(commentId);

        //2. 校验评论是否被点赞过
        //当前登录用户id
        Long userId = LoginUserContextHolder.getUserId();

        //布隆过滤器key
        String buildUserCommentLikeListKey = RedisKeyConstants.buildBloomCommentLikesKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        //lua脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_comment_unlike_check.lua")));
        //返回值类型
        script.setResultType(Long.class);

        //执行lua脚本，并拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(buildUserCommentLikeListKey), commentId);

        CommentUnlikeLuaResultEnum commentUnlikeLuaResultEnum = CommentUnlikeLuaResultEnum.valueOf(result);

        if(Objects.isNull(commentUnlikeLuaResultEnum)) {
            throw new BizException(ResponseCodeEnum.COMMENT_ALREADY_LIKED);
        }

        switch (commentUnlikeLuaResultEnum) {
            case NOT_EXIST -> {//布隆过滤器不存在
                //异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    //设置过期时间————一个小时
                    long expireSeconds = 60*60 + RandomUtil.randomInt(60*60);
                    //将点赞评论添加到布隆过滤器中
                    batchAddCommentLike2BloomAndExpire(userId,expireSeconds,buildUserCommentLikeListKey);
                });

                //从数据库中校验评论是否被点赞
                int count = commentLikeDOMapper.selectCountByUserIdAndCommentId(userId, commentId);

                //未点赞，无法进行取消点赞操作，抛出业务异常
                if(count == 0) {
                    throw new BizException(ResponseCodeEnum.COMMENT_NOT_LIKED);
                }
            }

            //布隆过滤器检验目标评论未被点赞（绝对正确）
            case COMMENT_NOT_LIKED -> throw new BizException(ResponseCodeEnum.COMMENT_NOT_LIKED);
        }

        // 3. 发送顺序 MQ，删除评论点赞记录
        //构建消息体
        LikeUnlikeCommentMqDTO likeUnlikeCommentMqDTO = LikeUnlikeCommentMqDTO.builder()
                .type(LikeUnlikeCommentTypeEnum.UNLIKE.getCode())
                .userId(userId)
                .commentId(commentId)
                .createTime(LocalDateTime.now())
                .build();

        //将消息体DTO转为json字符串
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeCommentMqDTO)).build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;

        //MQ 分区键
        String hashKey = String.valueOf(userId);

        //异步发送MQ顺序消息
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论取消点赞】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 删除评论
     */
    @Override
    public Response<?> deleteComment(DeleteCommentReqVO deleteCommentReqVO) {
        //获取被删除的评论id
        Long commentId = deleteCommentReqVO.getCommentId();

        // 1. 校验评论是否存在
        CommentDO commentDO = commentDOMapper.selectByPrimaryKey(commentId);

        if(Objects.isNull(commentDO)) {
            throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
        }

        // 2. 校验是否有权限删除
        //获取当前登录用户id
        Long userId = LoginUserContextHolder.getUserId();
        if(!Objects.equals(userId,commentDO.getUserId())){
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }

        // 3. 物理删除评论、评论内容
        //编程式事物，保证原子性
        transactionTemplate.execute(status -> {
            try {
                //删除元数据
                commentDOMapper.deleteByPrimaryKey(commentId);
                //删除评论内容
                keyValueRpcService.deleteCommentContent(
                        commentDO.getNoteId(),
                        commentDO.getCreateTime(),
                        commentDO.getContentUuid());

                return null;
            }catch (Exception e) {
                status.setRollbackOnly();//标记事物为回滚
                log.error("",e);
                return e;
            }
        });

        // 4. 删除 Redis 缓存（ 和 String）
        Integer level = commentDO.getLevel();
        Long noteId = commentDO.getNoteId();
        Long parentCommentId = commentDO.getParentId();

        //根据评论级别，后见对应的Zset key
        String redisZsetKey = Objects.equals(level, 1) ?
                RedisKeyConstants.buildCommentListKey(noteId) : RedisKeyConstants.buildChildCommentListKey(parentCommentId);

        //使用redistemplate 执行管道操作
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public  Object execute(RedisOperations operations){
                //删除zset中对应评论id（ZSet）
                operations.opsForZSet().remove(redisZsetKey,commentId);

                //删除评论详情（String）
                operations.delete(RedisKeyConstants.buildCommentDetailKey(commentId));
                return null;
            }
        });

        // 5. 发布广播 MQ, 将本地缓存删除
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, commentId, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【删除评论详情本地缓存】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【删除评论详情本地缓存】MQ 发送异常: ", throwable);
            }
        });

        // 6. 发送 MQ, 异步去更新计数、删除关联评论、热度值等
        // 构建消息对象，并将 DO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentDO))
                .build();

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELETE_COMMENT, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论删除】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论删除】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /*
    * 删除本地评论缓存
    * */
    @Override
    public void deleteCommentLocalCache(Long commentId) {
        LOCAL_CACHE.invalidate(commentId);
    }

    /*
    * 异步将用户点赞评论添加到布隆过滤器中
    * */
    private void batchAddCommentLike2BloomAndExpire(Long userId, long expireSeconds, String bloomUserCommentLikeListKey) {
        try {
            //查询该用户点赞的所有评论
            List<CommentLikeDO> commentLikeDOS = commentLikeDOMapper.selectByUserId(userId);

            //若不为空，批量添加到布隆过滤器中
            if (CollUtil.isNotEmpty(commentLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                //lua脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_batch_add_comment_like_and_expire.lua")));
                //返回值结果
                script.setResultType(Long.class);

                //构建lua参数
                List<Object> luaArgs = Lists.newArrayList();
                commentLikeDOS.forEach(commentLikeDO -> luaArgs.add(commentLikeDO.getCommentId()));
                luaArgs.add(expireSeconds);

                //执行lua脚本
                redisTemplate.execute(script, Collections.singletonList(bloomUserCommentLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【评论点赞】布隆过滤器异常: ", e);
        }
    }

    /*
    * 校验被点赞的评论是否存在
    * */
    private void checkCommentIsExist(Long commentId) {
        //先从本地缓存中校验
        String localCacheJson = LOCAL_CACHE.getIfPresent(commentId);

        //若本地缓存中不存在
        if(Objects.isNull(localCacheJson)) {
            //再从redis中校验
            String commentDetailRedisKey = RedisKeyConstants.buildCommentDetailKey(commentId);
            Boolean hasKey = redisTemplate.hasKey(commentDetailRedisKey);

            //若redis中不存在就查询数据库
            if(!hasKey) {
                CommentDO commentDO = commentDOMapper.selectByPrimaryKey(commentId);

                //若数据库中也不存在，那就抛出业务异常
                if(Objects.isNull(commentDO)) {
                    throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
                }

            }
        }
    }

    /*
    * 设置子评论 VO 的计数
    * */
    private void setChildCommentCountData(List<FindChildCommentItemRspVO> commentRspVOS, List<Long> expiredCommentIds) {
        // 准备从评论 Hash 中查询计数 (被点赞数)
        // 缓存中存在的子评论 ID
        List<Long> notExpiredCommentIds = Lists.newArrayList();

        // 遍历从缓存中解析出的 VO 集合，提取二级评论 ID
        commentRspVOS.forEach(commentRspVO -> {
            Long childCommentId = commentRspVO.getCommentId();
            notExpiredCommentIds.add(childCommentId);
        });

        // 从 Redis 中查询评论计数 Hash 数据
        Map<Long, Map<Object, Object>> commentIdAndCountMap = getCommentCountDataAndSync2RedisHash(notExpiredCommentIds);

        // 遍历 VO, 设置对应子评论的点赞数
        for (FindChildCommentItemRspVO commentRspVO : commentRspVOS) {
            // 评论 ID
            Long commentId = commentRspVO.getCommentId();

            // 若当前这条评论是从数据库中查询出来的, 则无需设置点赞数，以数据库查询出来的为主
            if (CollUtil.isNotEmpty(expiredCommentIds)
                    && expiredCommentIds.contains(commentId)) {
                continue;
            }

            // 设置子评论的点赞数
            Map<Object, Object> hash = commentIdAndCountMap.get(commentId);
            if (CollUtil.isNotEmpty(hash)) {
                Object likeTotalObj = hash.get(RedisKeyConstants.FIELD_LIKE_TOTAL);
                Long likeTotal = Objects.isNull(likeTotalObj) ? 0L : Long.valueOf(likeTotalObj.toString());
                commentRspVO.setLikeTotal(likeTotal);
            }
        }
    }

    /*
    * 获取评论计数数据，并同步到 Redis 中
    * */
    private Map<Long, Map<Object, Object>> getCommentCountDataAndSync2RedisHash(List<Long> notExpiredCommentIds) {
        // 已失效的 Hash 评论 ID
        List<Long> expiredCountCommentIds = Lists.newArrayList();
        // 构建需要查询的 Hash Key 集合
        List<String> commentCountKeys = notExpiredCommentIds.stream()
                .map(RedisKeyConstants::buildCountCommentKey).toList();

        // 使用 RedisTemplate 执行管道批量操作
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                // 遍历需要查询的评论计数的 Hash 键集合
                commentCountKeys.forEach(key ->
                        // 在管道中执行 Redis 的 hash.entries 操作
                        // 此操作会获取指定 Hash 键中所有的字段和值
                        operations.opsForHash().entries(key));
                return null;
            }
        });

        // 评论 ID - 计数数据字典
        Map<Long, Map<Object, Object>> commentIdAndCountMap = Maps.newHashMap();
        // 遍历未过期的评论 ID 集合
        for (int i = 0; i < notExpiredCommentIds.size(); i++) {
            // 当前评论 ID
            Long currCommentId = Long.valueOf(notExpiredCommentIds.get(i).toString());
            // 从缓存查询结果中，获取对应 Hash
            Map<Object, Object> hash = (Map<Object, Object>) results.get(i);
            // 若 Hash 结果为空，说明缓存中不存在，添加到 expiredCountCommentIds 中，保存一下
            if (CollUtil.isEmpty(hash)) {
                expiredCountCommentIds.add(currCommentId);
                continue;
            }
            // 若存在，则将数据添加到 commentIdAndCountMap 中，方便后续读取
            commentIdAndCountMap.put(currCommentId, hash);
        }

        // 若已过期的计数评论 ID 集合大于 0，说明部分计数数据不在 Redis 缓存中
        // 需要查询数据库，并将这部分的评论计数 Hash 同步到 Redis 中
        if (CollUtil.size(expiredCountCommentIds) > 0) {
            // 查询数据库
            List<CommentDO> commentDOS = commentDOMapper.selectCommentCountByIds(expiredCountCommentIds);

            commentDOS.forEach(commentDO -> {
                Integer level = commentDO.getLevel();
                Map<Object, Object> map = Maps.newHashMap();
                map.put(RedisKeyConstants.FIELD_LIKE_TOTAL, commentDO.getLikeTotal());
                // 只有一级评论需要统计子评论总数
                if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                    map.put(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, commentDO.getChildCommentTotal());
                }
                // 统一添加到 commentIdAndCountMap 字典中，方便后续查询
                commentIdAndCountMap.put(commentDO.getId(), map);
            });

            // 异步同步到 Redis 中
            threadPoolTaskExecutor.execute(() -> {
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        commentDOS.forEach(commentDO -> {
                            // 构建 Hash Key
                            String key = RedisKeyConstants.buildCountCommentKey(commentDO.getId());
                            // 评论级别
                            Integer level = commentDO.getLevel();
                            // 设置 Field 数据
                            Map<String, Long> fieldsMap = Objects.equals(level, CommentLevelEnum.ONE.getCode()) ?
                                    Map.of(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, commentDO.getChildCommentTotal(),
                                            RedisKeyConstants.FIELD_LIKE_TOTAL, commentDO.getLikeTotal()) : Map.of(RedisKeyConstants.FIELD_LIKE_TOTAL, commentDO.getLikeTotal());
                            // 添加 Hash 数据
                            operations.opsForHash().putAll(key, fieldsMap);

                            // 设置随机过期时间 (5小时以内)
                            long expireTime = 60*60 + RandomUtil.randomInt(4 * 60 * 60);
                            operations.expire(key, expireTime, TimeUnit.SECONDS);
                        });
                        return null;
                    }
                });
            });
        }
        return commentIdAndCountMap;
    }

    /*
    * 获取子评论列表，并同步到 Redis 中
    * */
    private void getChildCommentDataAndSync2Redis(List<CommentDO> childCommentDOS, List<FindChildCommentItemRspVO> childCommentRspVOS) {
        if (CollUtil.isEmpty(childCommentDOS)) {
            return;
        }

        //调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        //调用用户服务的入参
        Set<Long> userIds = Sets.newHashSet();

        //归属的笔记id
        Long noteId = null;

        //循环提取RPC调用需要的入参数据
        for (CommentDO childCommentDO : childCommentDOS) {
            noteId = childCommentDO.getNoteId();
            //构建调用 KV 服务批量查询评论内容的入参
            Boolean isContentEmpty = childCommentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(childCommentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(childCommentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            // 构建调用用户服务批量查询用户信息的入参 (包含评论发布者、回复的目标用户)
            userIds.add(childCommentDO.getUserId());

            Long parentId = childCommentDO.getParentId();
            Long replyCommentId = childCommentDO.getReplyCommentId();
            // 若当前评论的 replyCommentId 不等于 parentId，则前端需要展示回复的哪个用户，如  “回复 犬小哈：”
            if (!Objects.equals(parentId, replyCommentId)) {
                userIds.add(childCommentDO.getReplyUserId());
            }
        }

        //RPC：调用 KV 服务，批量获取评论内容
        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        //DTO 集合转 Map，方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if(CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
        }

        //RPC：调用用户服务，批量获取用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds.stream().toList());

        //DTO转Map，方便后续拼装数据
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = null;
        if(CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, findUserByIdRspDTO -> findUserByIdRspDTO));
        }
        // DO 转 VO
        for (CommentDO childCommentDO : childCommentDOS) {
            // 构建 VO 实体类
            Long userId = childCommentDO.getUserId();
            FindChildCommentItemRspVO childCommentRspVO = FindChildCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(childCommentDO.getId())
                    .imageUrl(childCommentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(childCommentDO.getCreateTime()))
                    .likeTotal(childCommentDO.getLikeTotal())
                    .build();

            // 填充用户信息(包括评论发布者、回复的用户)
            if (CollUtil.isNotEmpty(userIdAndDTOMap)) {
                FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
                // 评论发布者用户信息(头像、昵称)
                if (Objects.nonNull(findUserByIdRspDTO)) {
                    childCommentRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                    childCommentRspVO.setNickname(findUserByIdRspDTO.getNickName());
                }

                // 评论回复的哪个
                Long replyCommentId = childCommentDO.getReplyCommentId();
                Long parentId = childCommentDO.getParentId();

                if (Objects.nonNull(replyCommentId)
                        && !Objects.equals(replyCommentId, parentId)) {
                    Long replyUserId = childCommentDO.getReplyUserId();
                    FindUserByIdRspDTO replyUser = userIdAndDTOMap.get(replyUserId);
                    if (Objects.nonNull(replyUser)) {
                        childCommentRspVO.setReplyUserName(replyUser.getNickName());
                        childCommentRspVO.setReplyUserId(replyUser.getId());
                    }
                }
            }
            // 评论内容
            if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
                String contentUuid = childCommentDO.getContentUuid();
                if (StringUtils.isNotBlank(contentUuid)) {
                    childCommentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
                }
            }

            childCommentRspVOS.add(childCommentRspVO);
        }

        //异步将笔记详情，同步到redis中
        threadPoolTaskExecutor.execute(() -> {
            //准备批量写入的数据
            Map<String,String> data = new HashMap<>();
            childCommentRspVOS.forEach(commentRspVO -> {
                //评论id
                Long commentId = commentRspVO.getCommentId();
                //构建key
                String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                data.put(key,JsonUtils.toJsonString(commentRspVO));
            });
            //批量添加评论详情 Json 到 Redis 中
            batchAddCommentDetailJson2Redis(data);
        });


    }

    /*
    * 批量添加评论详情 Json 到 Redis 中
    * */
    private void batchAddCommentDetailJson2Redis(Map<String, String> data) {
        //使用 Redis Pipeline 提升写入性能
        redisTemplate.executePipelined((RedisCallback<?>) (connection) -> {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                //将Java对象序列化为json字符串
                // entry value is already a JSON document; serializing it again would store
                // a quoted JSON string that cannot be parsed as a comment object after restart.
                String jsonString = entry.getValue();
                //随机时间
                int randomExpire = 60*60 + RandomUtil.randomInt(4 * 60 * 60);
                //批量写入并设置过期时间
                connection.setEx(
                        redisTemplate.getStringSerializer().serialize(entry.getKey()),
                        randomExpire,
                        redisTemplate.getStringSerializer().serialize(jsonString)
                );
            }
            return null;
        });
    }

    /*
    * 异步将子评论同步到redis中（最多60条）
    * */
    private void syncChildComments2Redis(Long parentCommentId, String childCommentZsetKey) {
        List<CommentDO> childCommentDOS = commentDOMapper
                .selectChildCommentsByParentIdAndLimit(parentCommentId, 6 * 10);
        //使用 redispipeline提升性能
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            //遍历子评论数据并批量写入zset
            for (CommentDO childCommentDO : childCommentDOS) {
                Long commentId = childCommentDO.getId();
                //create_time转时间戳
                Long commentTimeTamp = DateUtils.localDateTime2Timestamp(childCommentDO.getCreateTime());
                //zSetOps.add("评论集合key", 评论ID, 时间戳排序分值);
                zSetOps.add(childCommentZsetKey,commentId,commentTimeTamp);
            }
            //设置过期时间
            int randomExpiryTime = 60*60 + RandomUtil.randomInt(4 * 60 * 60); // 5小时以内
            redisTemplate.expire(childCommentZsetKey,randomExpiryTime,TimeUnit.SECONDS);
            return null;
        });
    }

    /*
    * 同步评论计数到 Redis 中
    * */
    private void syncCommentCount2Redis(String countCommentKey, Long dbCount) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations){
                //同步hash数据
                operations.opsForHash().put(countCommentKey,RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL,dbCount);

                //设置过期时间
                long expireTime = 60*60 + RandomUtil.randomInt(4*60*60);
                operations.expire(countCommentKey,expireTime,TimeUnit.SECONDS);
                return null;
            }
        });
    }
    /*
    * 同步评论详情到本地缓存中
    * */
    private void syncCommentDetail2LocalCache(List<FindCommentItemRspVO> commentRspVOS) {
        //开启一个异步线程
        threadPoolTaskExecutor.execute(() -> {
            //构建缓存所需的键值
            Map<Long,String> localCacheData = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                Long commentId = commentRspVO.getCommentId();
                localCacheData.put(commentId,JsonUtils.toJsonString(commentRspVO));
            });

            //批量写入本地缓存
            LOCAL_CACHE.putAll(localCacheData);
        });
    }

    /*
    * 获取全部评论数据，并将评论详情同步到 Redis 中
    * */
    private void getCommentDataAndSync2Redis(List<CommentDO> oneLevelCommentDOS, Long noteId, List<FindCommentItemRspVO> commentRspVOS) {
        // 过滤出所有最早回复的二级评论 ID
        List<Long> twoLevelCommentIds = oneLevelCommentDOS.stream()
                .map(CommentDO::getFirstReplyCommentId)
                .filter(firstReplyCommentId -> Objects.nonNull(firstReplyCommentId) && firstReplyCommentId != 0)
                .toList();

        // 查询二级评论
        Map<Long, CommentDO> commentIdAndDOMap = null;
        List<CommentDO> twoLevelCommonDOS = null;
        if (CollUtil.isNotEmpty(twoLevelCommentIds)) {
            twoLevelCommonDOS = commentDOMapper.selectTwoLevelCommentByIds(twoLevelCommentIds);

            // 转 Map 集合，方便后续拼装数据
            commentIdAndDOMap = twoLevelCommonDOS.stream()
                    .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
        }

        // 调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        // 调用用户服务的入参
        List<Long> userIds = Lists.newArrayList();

        // 将一级评论和二级评论合并到一起
        List<CommentDO> allCommentDOS = Lists.newArrayList();
        CollUtil.addAll(allCommentDOS, oneLevelCommentDOS);
        CollUtil.addAll(allCommentDOS, twoLevelCommonDOS);

        // 循环提取 RPC 调用需要的入参数据
        allCommentDOS.forEach(commentDO -> {
            // 构建调用 KV 服务批量查询评论内容的入参
            boolean isContentEmpty = commentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(commentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            // 构建调用用户服务批量查询用户信息的入参
            userIds.add(commentDO.getUserId());
        });

        // RPC: 调用 KV 服务，批量获取评论内容
        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
        }

        // RPC: 调用用户服务，批量获取用户信息（头像、昵称等）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = null;
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto));
        }

        // DO 转 VO, 组合拼装一二级评论数据
        for (CommentDO commentDO : oneLevelCommentDOS) {
            // 一级评论
            Long userId = commentDO.getUserId();
            FindCommentItemRspVO oneLevelCommentRspVO = FindCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(commentDO.getId())
                    .imageUrl(commentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(commentDO.getCreateTime()))
                    .likeTotal(commentDO.getLikeTotal())
                    .childCommentTotal(commentDO.getChildCommentTotal())
                    .heat(commentDO.getHeat())
                    .build();

            // 用户信息
            setUserInfo(commentIdAndDOMap, userIdAndDTOMap, userId, oneLevelCommentRspVO);
            // 笔记内容
            setCommentContent(commentUuidAndContentMap, commentDO, oneLevelCommentRspVO);


            // 二级评论
            Long firstReplyCommentId = commentDO.getFirstReplyCommentId();
            if (CollUtil.isNotEmpty(commentIdAndDOMap)) {
                CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                if (Objects.nonNull(firstReplyCommentDO)) {
                    Long firstReplyCommentUserId = firstReplyCommentDO.getUserId();
                    FindCommentItemRspVO firstReplyCommentRspVO = FindCommentItemRspVO.builder()
                            .userId(firstReplyCommentDO.getUserId())
                            .commentId(firstReplyCommentDO.getId())
                            .imageUrl(firstReplyCommentDO.getImageUrl())
                            .createTime(DateUtils.formatRelativeTime(firstReplyCommentDO.getCreateTime()))
                            .likeTotal(firstReplyCommentDO.getLikeTotal())
                            .heat(firstReplyCommentDO.getHeat())
                            .build();

                    setUserInfo(commentIdAndDOMap, userIdAndDTOMap, firstReplyCommentUserId, firstReplyCommentRspVO);

                    // 用户信息
                    oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRspVO);
                    // 笔记内容
                    setCommentContent(commentUuidAndContentMap, firstReplyCommentDO, firstReplyCommentRspVO);
                }
            }
            commentRspVOS.add(oneLevelCommentRspVO);
        }

        // 异步将笔记详情，同步到 Redis 中
        threadPoolTaskExecutor.execute(() -> {
            // 准备批量写入的数据
            Map<String, String> data = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                // 评论 ID
                Long commentId = commentRspVO.getCommentId();
                // 构建 Key
                String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                data.put(key, JsonUtils.toJsonString(commentRspVO));
            });

            // 使用 Redis Pipeline 提升写入性能
            batchAddCommentDetailJson2Redis(data);
        });
    }


    /*
    * 将热点评论同步到redis中
    * */
    private void syncHeatComments2Redis(String commentZsetKey, Long noteId) {
        List<CommentDO> commentDOS = commentDOMapper.selectHeatComments(noteId);
        if(CollUtil.isNotEmpty(commentDOS)){
            //使用redis pipeline提升性能------不理解为什么和添加评论总数哪里的管道不一样
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

                //遍历评论数据并批量写入ZSET
                for (CommentDO commentDO : commentDOS) {
                    Long commentId = commentDO.getId();
                    Double commentHeat = commentDO.getHeat();
                    zSetOps.add(commentZsetKey,commentId,commentHeat);
                }

                //设置过期时间
                int randomInt = RandomUtil.randomInt(5 * 60 * 60);
                redisTemplate.expire(commentZsetKey,randomInt,TimeUnit.SECONDS);
                return null;
            });
        }
    }

    /**
     * 异步将评论总数同步到redis中
     */
    private void syncNoteCommentTotal2Redis(String noteCommentTotalKey, Long dbCount) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                //同步hash数据
                operations.opsForHash().put(noteCommentTotalKey,RedisKeyConstants.FIELD_COMMENT_TOTAL,dbCount);

                //设置过期时间
                long expireTime = 60 * 60 + RandomUtil.randomInt(4*60*60);
                operations.expire(noteCommentTotalKey,expireTime, TimeUnit.SECONDS);
                return null;
            }
        });
    }


    /**
     *
     * @param commentIdAndDOMap 二级评论
     * @param userIdAndDTOMap 用户信息
     * @param userId 用户id
     * @param oneLevelCommentRspVO 一级评论
     * 用户信息
     */
    private void setUserInfo(Map<Long, CommentDO> commentIdAndDOMap, Map<Long, FindUserByIdRspDTO> userIdAndDTOMap, Long userId, FindCommentItemRspVO oneLevelCommentRspVO) {
        //通过用户id获取对应用户的信息
        if (CollUtil.isEmpty(userIdAndDTOMap)) {
            return;
        }
        FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
        if (Objects.nonNull(findUserByIdRspDTO)) {
            oneLevelCommentRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
            oneLevelCommentRspVO.setNickname(findUserByIdRspDTO.getNickName());
        }
    }

    /**
     *
     * @param commentUuidAndContentMap 评论内容
     * @param commentDO 评论数据
     * @param firstReplyCommentRspVO 一级评论
     */
    private void setCommentContent(Map<String, String> commentUuidAndContentMap, CommentDO commentDO, FindCommentItemRspVO firstReplyCommentRspVO) {
        //判断Cassandra中的评论内容是否为空
        if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
            String contentUuid = commentDO.getContentUuid();
            if (StringUtils.isNotBlank(contentUuid)) {
                firstReplyCommentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
            }
        }
    }

    /**
     * 设置评论 VO 的计数
     *
     * @param commentRspVOS 返参 VO 集合
     * @param expiredCommentIds 缓存中已失效的评论 ID 集合
     */
    private void setCommentCountData(List<FindCommentItemRspVO> commentRspVOS, List<Long> expiredCommentIds) {
        // 准备从评论 Hash 中查询计数 (子评论总数、被点赞数)
        // 缓存中存在的评论 ID
        List<Long> notExpiredCommentIds = Lists.newArrayList();

        // 遍历从缓存中解析出的 VO 集合，提取一级、二级评论 ID
        commentRspVOS.forEach(commentRspVO -> {
            Long oneLevelCommentId = commentRspVO.getCommentId();
            notExpiredCommentIds.add(oneLevelCommentId);
            FindCommentItemRspVO firstCommentVO = commentRspVO.getFirstReplyComment();
            if (Objects.nonNull(firstCommentVO)) {
                notExpiredCommentIds.add(firstCommentVO.getCommentId());
            }
        });

        // 已失效的 Hash 评论 ID
        Map<Long, Map<Object, Object>> commentIdAndCountMap = getCommentCountDataAndSync2RedisHash(notExpiredCommentIds);

        // 遍历 VO, 设置对应评论的二级评论数、点赞数
        for (FindCommentItemRspVO commentRspVO : commentRspVOS) {
            // 评论 ID
            Long commentId = commentRspVO.getCommentId();

            // 若当前这条评论是从数据库中查询出来的, 则无需设置二级评论数、点赞数，以数据库查询出来的为主
            if (CollUtil.isNotEmpty(expiredCommentIds)
                    && expiredCommentIds.contains(commentId)) {
                continue;
            }

            // 设置一级评论的子评论总数、点赞数
            Map<Object, Object> hash = commentIdAndCountMap.get(commentId);
            if (CollUtil.isNotEmpty(hash)) {
                Object childCommentTotalObj = hash.get(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
                Long childCommentTotal = Objects.isNull(childCommentTotalObj) ? 0 : Long.parseLong(childCommentTotalObj.toString());
                Object likeTotalObj = hash.get(RedisKeyConstants.FIELD_LIKE_TOTAL);
                Long likeTotal = Objects.isNull(likeTotalObj) ? 0L : Long.valueOf(likeTotalObj.toString());
                commentRspVO.setChildCommentTotal(childCommentTotal);
                commentRspVO.setLikeTotal(likeTotal);
                // 最初回复的二级评论
                FindCommentItemRspVO firstCommentVO = commentRspVO.getFirstReplyComment();
                if (Objects.nonNull(firstCommentVO)) {
                    Long firstCommentId = firstCommentVO.getCommentId();
                    Map<Object, Object> firstCommentHash = commentIdAndCountMap.get(firstCommentId);
                    if (CollUtil.isNotEmpty(firstCommentHash)) {
                        Object firstCommentLikeTotalObj = firstCommentHash.get(RedisKeyConstants.FIELD_LIKE_TOTAL);
                        Long firstCommentLikeTotal = Objects.isNull(firstCommentLikeTotalObj)
                                ? 0L : Long.valueOf(firstCommentLikeTotalObj.toString());
                        firstCommentVO.setLikeTotal(firstCommentLikeTotal);
                    }
                }
            }
        }
    }

    /**
     * 读取评论详情缓存。兼容旧版本已经写入 Redis 的二次编码 JSON，避免缓存脏数据
     * 直接导致整个评论列表请求失败。
     */
    private <T> T parseCachedComment(String json, Class<T> type) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, type);
        } catch (Exception firstException) {
            try {
                String decodedJson = JsonUtils.parseObject(json, String.class);
                return JsonUtils.parseObject(decodedJson, type);
            } catch (Exception secondException) {
                log.warn("评论详情缓存格式无效，将回源数据库。type: {}", type.getSimpleName(), secondException);
                return null;
            }
        }
    }
}
