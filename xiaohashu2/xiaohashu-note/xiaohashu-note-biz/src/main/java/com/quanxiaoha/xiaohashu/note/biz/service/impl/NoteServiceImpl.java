package com.quanxiaoha.xiaohashu.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cn.hutool.core.util.RandomUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteCollectionDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteLikeDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteCountDO;
import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.ChannelDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.ChannelTopicRelDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.TopicDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteLikeDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.TopicDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.ChannelDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.ChannelTopicRelDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.enums.*;
import com.quanxiaoha.xiaohashu.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.quanxiaoha.xiaohashu.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.quanxiaoha.xiaohashu.note.biz.model.dto.NoteOperateMqDTO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.quanxiaoha.xiaohashu.note.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.note.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteService;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/*
* 笔记业务
* */
@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private ChannelDOMapper channelDOMapper;
    @Resource
    private ChannelTopicRelDOMapper channelTopicRelDOMapper;
    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RedisTemplate<String,String> redisTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;



    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();


    /*
     * 笔记发布
     * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        //1笔记类型
        Integer type = publishNoteReqVO.getType();

        //获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        //若非图文，视频，抛出业务异常
        if(Objects.isNull(noteTypeEnum)){
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        //笔记内容是否为空，默认值为true，及空
        boolean isContentEmpty = true;
        String videoUri = null;

        switch (noteTypeEnum){
            case IMAGE_TEXT:
                //switch 1.图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                //校验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList),"笔记图片不能为空");
                //校验图片数量，小于等于八张
                Preconditions.checkArgument(imgUriList.size() <= 8,"笔记的图片数量要小于8");
                //将图片链接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");

                break;
            case VIDEO:
                //switch 2.视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                //校验视频链接是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri),"笔记视频不能为空");

                break;

            default:
                //switch 3.默认直接break结束
                break;
        }

        //RPC：调用分布式id生成服务，生成笔记id
        String snowflakeIdId = distributedIdGeneratorRpcService.getSnowflakeId();
        //笔记内容 UUID
        String contentUuid = null;

        //2笔记内容
        String content = publishNoteReqVO.getContent();
        //判断笔记内容是否为空
        if(StringUtils.isNotBlank(content)){
            //不为为空，设为false
            isContentEmpty = false;
            //生成笔记内容 UUID
            contentUuid = UUID.randomUUID().toString();
            //RPC 调用KV键值服务，存储短文本
            boolean isSaveSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
            //判断，若存储失败，抛出业务异常，提示用户
            if(!isSaveSuccess){
                throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }

        //3 话题：已有话题直接复用；新话题由服务端落库
        Long topicId = publishNoteReqVO.getTopicId();
        if (Objects.nonNull(topicId) && topicId <= 0) {
            topicId = null;
        }
        String topicName = null;
        if (Objects.isNull(topicId) && StringUtils.isNotBlank(publishNoteReqVO.getTopicName())) {
            String requestedTopicName = publishNoteReqVO.getTopicName().trim();
            TopicDO existingTopic = topicDOMapper.selectByKeyword(requestedTopicName).stream()
                    .filter(topic -> requestedTopicName.equalsIgnoreCase(topic.getName()))
                    .findFirst()
                    .orElse(null);
            if (Objects.nonNull(existingTopic)) {
                topicId = existingTopic.getId();
                topicName = existingTopic.getName();
            } else {
                topicId = Long.valueOf(distributedIdGeneratorRpcService.getSnowflakeId());
                topicName = requestedTopicName;
                Date now = new Date();
                topicDOMapper.insert(TopicDO.builder()
                        .id(topicId)
                        .name(topicName)
                        .createTime(now)
                        .updateTime(now)
                        .isDeleted(false)
                        .build());
            }
        }

        if(Objects.nonNull(topicId)){
            TopicDO topic = topicDOMapper.selectByPrimaryKey(topicId);
            if (Objects.isNull(topic) || Boolean.TRUE.equals(topic.getIsDeleted())) {
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
            topicName = topic.getName();
        }

        // 频道通过频道-话题关系表关联；频道不存在时拒绝请求
        Long channelId = publishNoteReqVO.getChannelId();
        if (Objects.nonNull(channelId) && channelId <= 0) {
            channelId = null;
        }
        if (Objects.nonNull(channelId)) {
            ChannelDO channel = channelDOMapper.selectByPrimaryKey(channelId);
            if (Objects.isNull(channel) || Boolean.TRUE.equals(channel.getIsDeleted())) {
                throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
            }
            if (Objects.isNull(topicId)) {
                throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
            }
        }

        //4 发布者用户id
        Long creatorId = LoginUserContextHolder.getUserId();

        //5 构建笔记DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeIdId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(topicId)
                .topicName(topicName)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();
        try {
            //6 笔记入库存储
            noteDOMapper.insert(noteDO);

            // 只有同时选择频道和话题时才建立频道-话题关系
            if (Objects.nonNull(channelId) && Objects.nonNull(topicId)
                    && channelTopicRelDOMapper.selectByChannelIdAndTopicId(channelId, topicId) == null) {
                Date now = new Date();
                channelTopicRelDOMapper.insert(ChannelTopicRelDO.builder()
                        .id(Long.valueOf(distributedIdGeneratorRpcService.getSnowflakeId()))
                        .channelId(channelId)
                        .topicId(topicId)
                        .createTime(now)
                        .updateTime(now)
                        .build());
            }
        } catch (Exception e){
            log.error("======笔记存储失败",e);
            //RPC 笔记保存失败，则删除笔记内容
            if(StringUtils.isNotBlank(contentUuid)){
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
            throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
        }

        //发送 MQ
        //构建消息体
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(creatorId)
                .noteId(Long.valueOf(snowflakeIdId))
                .type(NoteOperateEnum.PUBLISH.getCode())
                .build();
        //构建消息对象
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteOperateMqDTO)).build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;

        //异步发送
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【笔记发布】MQ 发送异常: ", e);
            }
        });

        //返回
        return Response.success();
    }


    /*
     * 根据笔记id查看笔记
     * */
    @Override
    @SneakyThrows
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        //查询的笔记id
        Long noteId = findNoteDetailReqVO.getId();
        //获取当前登录的用户
        Long userId = LoginUserContextHolder.getUserId();

        //先到本地缓存中查询笔记详情
        String findNoteDetailRspVOStrLocalCache  = LOCAL_CACHE.getIfPresent(noteId);
        //判断是否在本地缓存中查询到
        if(StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)){
            //查询到了，校验可见性再返回
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRspVOStrLocalCache);

            checkNoteVisibleFromVO(userId,findNoteDetailRspVO);
            // 填充点赞/收藏/评论计数
            fillNoteCount(findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
        }
        //再到redis中查询笔记是否存在
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

        //判断是否从redis中查询成功
        if(StringUtils.isNotBlank(noteDetailJson)){
            //存在校验可见性,后直接返回
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);

            //异步缓存到本地
            threadPoolTaskExecutor.submit(()->{
                log.info("==> 异步缓存到本地；{}", findNoteDetailRspVOStrLocalCache);
                //先判断从redis中查询到的数据进行判断
                LOCAL_CACHE.put(noteId,Objects.isNull(findNoteDetailRspVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRspVO));
            });

            //可见性校验
            if(Objects.nonNull(findNoteDetailRspVO)){
                //笔记可见性校验
                Integer visible = findNoteDetailRspVO.getVisible();
                checkNoteVisible(visible,userId,findNoteDetailRspVO.getCreatorId());
            }
            // 填充点赞/收藏/评论计数
            fillNoteCount(findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
        }

        //redis中没有查询到，就去数据库查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        //判断笔记是否存在，不存在抛出异常
        if(Objects.isNull(noteDO)){
            //异步将空数据存入redis，防止缓存穿透
            threadPoolTaskExecutor.execute(()->{
                //设置过期时间
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey,"null",expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        //再进行异步查询之前要判断笔记的可见性，可见才进行查询
        Integer visible = noteDO.getVisible();
        checkNoteVisible(visible,userId,noteDO.getCreatorId());

        //优化：并异步进行RPC服务调用查询信息
        //获取笔记发布者id
        Long creatorId = noteDO.getCreatorId();
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                //RPC 调用用户服务
                .supplyAsync(() ->
                    rpcWithAppClassLoader(() -> userRpcService.findById(creatorId)), threadPoolTaskExecutor
                );

        //先赋值为空，判断笔记不为空在进行异步调用K-V服务
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);

        //笔记不为空，再查询笔记内容
        if(Objects.equals(noteDO.getIsContentEmpty(),Boolean.FALSE)){
            //RPC 调用 k-v 存储服务获取内容
            contentResultFuture = CompletableFuture
                .supplyAsync(() ->
                    rpcWithAppClassLoader(() -> keyValueRpcService.findNoteContent(noteDO.getContentUuid())), threadPoolTaskExecutor);
        }

        //因为contentResultFuture被二次赋值，已经不是final修饰了，再lambda中无法使用
        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRspVO> resultFuture = CompletableFuture
                //allOf所有的异步任务执行完再往下执行
                .allOf(userResultFuture,contentResultFuture)
                //结果处理
                .thenApply(s -> {
                    //join获取出参
                    FindUserByIdRspDTO findUserByIdRspDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();

                    //笔记类型
                    Integer noteType = noteDO.getType();
                    //图文笔记图片连接(字符串)
                    String imgUriStr = noteDO.getImgUris();
                    //图文笔记图片连接(集合)
                    List<String> imgUris = null;
                    //判断是否为图文笔记
                    if(Objects.equals(noteType,NoteTypeEnum.IMAGE_TEXT.getCode()) && StringUtils.isNotBlank(imgUriStr)){
                        //如果是图文笔记，用逗号进行分割存储再list集合
                        imgUris = List.of(imgUriStr.split(","));
                    }

                    //构造反参实体
                    FindNoteDetailRspVO findNoteDetailRspVO = FindNoteDetailRspVO.builder()
                            .id(noteDO.getId())
                            .type(noteDO.getType())
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(findUserByIdRspDTO.getNickName())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(noteDO.getVisible())
                            .build();

                    return findNoteDetailRspVO;
                });

        // 获取拼装后的 FindNoteDetailRspVO
        FindNoteDetailRspVO findNoteDetailRspVO = resultFuture.get();

        // 填充点赞/收藏/评论计数
        fillNoteCount(findNoteDetailRspVO);

        //异步线程将笔记详情存入redis中
        threadPoolTaskExecutor.submit(()->{
            String noteDetailJson1 = JsonUtils.toJsonString(findNoteDetailRspVO);
            //设置过期时间
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            redisTemplate.opsForValue().set(noteDetailRedisKey,noteDetailJson1,expireSeconds,TimeUnit.SECONDS);
        });

        return Response.success(findNoteDetailRspVO);
    }

    /*
    * 笔记更新
    * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        //1.获取笔记id和笔记类型
        Integer type = updateNoteReqVO.getType();
        Long noteId = updateNoteReqVO.getId();

        //笔记枚举类型
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        //判断笔记类型
        if(Objects.isNull(noteTypeEnum)){
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        String videoUri = null;
        //2.判断笔记类型再处理相关业务
        switch (noteTypeEnum){
            case IMAGE_TEXT://图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                //校验图片是否符合要求
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList),"笔记图片不能为空");
                Preconditions.checkArgument(imgUriList.size() <= 8,"图片数量不能多余 8 张");

                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO://视频笔记
                videoUri = updateNoteReqVO.getVideoUri();
                //校验视频是否符合要求
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri),"笔记视频不能为空");
                break;
            default:
                break;
        }

        //bug修复，校验笔记是否存在和权限校验，只有发布者才能修改笔记
        //获取登录用户
        Long userId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        //笔记不存在
        if(Objects.isNull(selectNoteDO)){
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        //判断权限
        if(!Objects.equals(userId,selectNoteDO.getCreatorId())){
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        //3.话题
        Long topicId = updateNoteReqVO.getTopicId();
        if (Objects.nonNull(topicId) && topicId <= 0) {
            topicId = null;
        }
        String topicName = null;
        if(Objects.nonNull(topicId)){
            //查询笔记名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            if(StringUtils.isBlank(topicName)){
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
        }

        //双删策略解决数据不一致性问题,先删除Reids中的缓存数据，再更新数据库
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        //4.更新笔记元数据
        String content = updateNoteReqVO.getContent();

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StringUtils.isBlank(content))
                .imgUris(imgUris)
                .title(updateNoteReqVO.getTitle())
                .topicId(topicId)
                .topicName(topicName)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .build();
        //更新
        noteDOMapper.updateByPrimaryKey(noteDO);

        //5.删除redis中的缓存
        //在第二次删除redis中缓存时，使用延迟删除，避免更新笔记删除redis缓存过快，导致
        //数据不一致性问题（就是查询笔记还没有执行完，更新笔记就执行完了，导致redis中的缓存还是旧数据）
        //构建消息体
        Message<String> message = MessageBuilder.withPayload(String.valueOf(noteId)).build();

        //使用mq异步发送
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE,message,
                new SendCallback(){
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 笔记缓存消息发送成功...");
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.info("## 延时删除 Redis 笔记缓存消息发送失败...");
                    }
                },
                3000,// 超时时间为3秒
                1 // 延迟级别1，表示延时1秒
        );
        String redisNoteDetailKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(redisNoteDetailKey);


        //删除本地缓存
        //LOCAL_CACHE.invalidate(noteId);
        //同步发送广播MQ，将所有实例中的本地缓存删除
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE,noteId);
//        log.info("====> MQ：删除笔记本地缓存发送成功...");

        //6.更新K-V存储
        //根据笔记id查询笔记
        NoteDO noteDO1 = noteDOMapper.selectByPrimaryKey(noteId);
        String contentUuid = noteDO1.getContentUuid();

        //笔记内容更新是的成功
        boolean isUpdateContentSuccess = false;
        if(StringUtils.isBlank(content)){
            // 笔记本来就没有正文时无需调用 KV 删除接口，避免把 null UUID 当成非法请求。
            isUpdateContentSuccess = StringUtils.isBlank(contentUuid)
                    || keyValueRpcService.deleteNoteContent(contentUuid);
        } else {
            // 如果将无内容笔记更新为有内容笔记，需要生成 UUID
            //StringUtils.isBlank(contentUuid)判断未更新前笔记是否存储了内容，contentUuid不存在就是原笔记无内容
            contentUuid = StringUtils.isBlank(contentUuid) ? UUID.randomUUID().toString() : contentUuid;
            //调用 K-V 服务更新短文本，更新成功将isUpdateContentSuccess该为true
            isUpdateContentSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);

            // 首次为无正文笔记补充正文时，必须把新 UUID 回写到元数据，否则详情查询无法读到正文。
            if (isUpdateContentSuccess && StringUtils.isBlank(noteDO1.getContentUuid())) {
                noteDOMapper.updateByPrimaryKeySelective(NoteDO.builder()
                        .id(noteId)
                        .contentUuid(contentUuid)
                        .build());
            }
        }

        //更新失败，抛出异常，事物回滚
        if(!isUpdateContentSuccess){
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        return Response.success();
    }

    /*
    * 删除笔记
    * */
    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        //1.获取笔记id
        Long noteId = deleteNoteReqVO.getId();

        //bug修复，校验笔记是否存在和权限校验，只有发布者才能修改笔记
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        //笔记不存在
        if(Objects.isNull(selectNoteDO)){
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        //判断权限
        Long userId = LoginUserContextHolder.getUserId();//获取登录用户
        if(!Objects.equals(userId,selectNoteDO.getCreatorId())){
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        //2.逻辑删除笔记
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        int count = noteDOMapper.updateByPrimaryKeySelective(noteDO);

        if(count == 0){
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        //3.删除redis中的笔记缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        //4.同步删除本地缓存中的笔记内容
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE,noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        //发送 MQ
        //构建消息体
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .type(NoteOperateEnum.DELETE.getCode())
                .build();
        //构建消息对象
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteOperateMqDTO)).build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_DELETE;

        //异步发送
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记删除】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【笔记删除】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }


    /*
     * 笔记仅自己可见
     * */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        //1.获取笔记id
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();

        //bug修复，校验笔记是否存在和权限校验，只有发布者才能修改笔记
        //获取登录用户
        Long userId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        //笔记不存在
        if(Objects.isNull(selectNoteDO)){
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        //判断权限
        if(!Objects.equals(userId,selectNoteDO.getCreatorId())){
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        //2.设置笔记仅自己可见
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        int count = noteDOMapper.updateVisibleOnlyMe(noteDO);
        if(count == 0){
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        //3.删除redis中的笔记缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        //4.同步删除本地缓存中的笔记内容
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE,noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");


        return Response.success();
    }

    /*
    * 笔记置顶
    * */
    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        //1.获取笔记id
        Long noteId = topNoteReqVO.getId();

        //获取当前登录id
        Long creatorId = LoginUserContextHolder.getUserId();

        //2.构建实体VO，设置笔记置顶
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(topNoteReqVO.getIsTop())
                .creatorId(creatorId)
                .updateTime(LocalDateTime.now())
                .build();

        int count = noteDOMapper.updateIsTop(noteDO);
        if(count == 0){
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        //3.删除redis中的笔记缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        //4.同步删除本地缓存中的笔记内容
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE,noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }


    /*
     * 笔记点赞
     * */
    @Override
    public Response<?> lickNote(LikeNoteReqVO likeNoteReqVO) {
        //笔记id
        Long noteId = likeNoteReqVO.getId();

        // 1. 校验被点赞的笔记是否存在
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 判断目标笔记，是否已经点赞过
        Long userId = LoginUserContextHolder.getUserId();//当前登录用户id

        //布隆过滤器 key
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);

        //构建lua脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_note_like_check.lua")));
        script.setResultType(Long.class);

        //执行lua脚本，并拿到返回值
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), String.valueOf(noteId));
        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);

        //用户点赞列表 zset key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);

        //判断返回值类型，使用switch进行比对
        switch (noteLikeLuaResultEnum){
            //情况一：redis中布隆过滤器不存在
            case NOT_EXIST ->{
                // 从数据库中校验笔记是否被点赞，并异步初始化布隆过滤器，设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                //过期时间
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                //目标笔记是否被点赞
                if(count > 0){
                    //异步初始化布隆过滤器
                    threadPoolTaskExecutor.execute(() -> batchAddNoteLike2BloomAndExpire(userId,expireSeconds,bloomUserNoteLikeListKey));
                    //已经被点赞
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }

                // 若目标笔记未被点赞，查询当前用户是否有点赞其他笔记，有则同步初始化布隆过滤器
                batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey);

                //若数据库中也没有点赞记录，说明该用户还未点赞过任何笔记
                //构建lua脚本
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_add_note_like_and_expire.lua")));
                script.setResultType(Long.class);
                //执行lua脚本
                stringRedisTemplate.execute(script,Collections.singletonList(bloomUserNoteLikeListKey),String.valueOf(noteId),String.valueOf(expireSeconds));
            }
            //情况二：目标笔记已经被点赞
            case NOTE_LIKED -> {
                //校验ZSET列表中是否包含被点赞的笔记id
                Double score = stringRedisTemplate.opsForZSet().score(userNoteLikeZSetKey, String.valueOf(noteId));

                if(Objects.nonNull(score)){
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }

                //若score为空，则表示ZSET点赞列表中不存在，查询数据库校验
                int count = noteLikeDOMapper.selectNoteIsLiked(userId, noteId);
                if(count > 0){
                    // 数据库里面有点赞记录，而 Redis 中 ZSet 不存在，需要重新异步初始化 ZSet
                    asynInitUserNoteLikesZSet(userId, userNoteLikeZSetKey);

                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
            }
        }

        // 3. 更新用户 ZSET 点赞列表
        LocalDateTime now = LocalDateTime.now();

        //构建lua脚本
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/note_like_check_and_update_zset.lua")));
        script.setResultType(Long.class);

        //执行lua脚本，并拿到返回结果
        result = stringRedisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), String.valueOf(noteId), String.valueOf(DateUtils.localDateTime2Timestamp(now)));
        //判断，若ZSET不存在，需要重新初始化
        if(Objects.equals(result,NoteLikeLuaResultEnum.NOT_EXIST.getCode())){
            //查询当前用户最新点赞的100篇日记
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);
            //过期时间
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            //构建lua脚本
            DefaultRedisScript<Long> script1 = new DefaultRedisScript<>();
            script1.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/batch_add_note_like_zset_and_expire.lua")));
            script1.setResultType(Long.class);

            //判断数据库中是否有点赞列表
            if(CollUtil.isNotEmpty(noteLikeDOS)){//1.有
                //构建lua参数
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                //执行lua脚本,初始化zset
                stringRedisTemplate.execute(script1,Collections.singletonList(userNoteLikeZSetKey),luaArgs);

                //再次调用 note_like_check_and_update_zset.lua 脚本，将点赞的笔记添加到 zset 中
                stringRedisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), String.valueOf(noteId), String.valueOf(DateUtils.localDateTime2Timestamp(now)));
            }else {//2.没有
                //若数据库中，无点赞过的笔记记录，则直接将当前点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                //点赞时间戳
                luaArgs.add(String.valueOf(DateUtils.localDateTime2Timestamp(LocalDateTime.now())));
                //点赞笔记id
                luaArgs.add(String.valueOf(noteId));
                //过期时间
                luaArgs.add(String.valueOf(expireSeconds));
                //执行lua脚本
                stringRedisTemplate.execute(script1,Collections.singletonList(userNoteLikeZSetKey),luaArgs.toArray());
            }
        }


        // 4. 发送 MQ, 将点赞数据落库
        //构建消息体DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode())
                .createTime(now)
                .noteCreatorId(creatorId)
                .build();

        //构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO)).build();

        String hashKey = String.valueOf(userId);

        //通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        //异步发送MQ消息，提升响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【笔记点赞】MQ 发送异常: ", e);
            }
        });
        return Response.success();
    }


    /*
     * 取消点赞笔记
     * */
    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        // 笔记ID
        Long noteId = unlikeNoteReqVO.getId();

        // 1. 校验笔记是否真实存在
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 校验笔记是否被点赞过
        //当前登录用户id
        Long userId = LoginUserContextHolder.getUserId();
        //布隆过滤器 key
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);

        //构建lua脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_note_unlike_check.lua")));
        script.setResultType(Long.class);

        //执行lua脚本，并获取返回值
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), String.valueOf(noteId));
        //对返回值进行判断
        NoteUnlikeLuaResultEnum noteUnlikeLuaResultEnum = NoteUnlikeLuaResultEnum.valueOf(result);

        switch (noteUnlikeLuaResultEnum){
            case NOT_EXIST -> {//1：为-1，表示布隆过滤器不存在
                //异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    //过期时间
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    batchAddNoteLike2BloomAndExpire(userId,expireSeconds,bloomUserNoteLikeListKey);
                });
                //从数据库中校验笔记是否被点赞
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                //未点赞，无法进行取消点赞操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
            case NOTE_NOT_LIKED -> {//2：为0，表示布隆过滤器校验目标笔记未被点赞（判断绝对正确）
                throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
        }

        //3. 删除 ZSET 中已点赞的笔记 ID————能走到这里，说明布隆过滤器判断已点赞，直接删除 ZSET 中已点赞的笔记 ID
        //用户点赞列表ZSET key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        stringRedisTemplate.opsForZSet().remove(userNoteLikeZSetKey,String.valueOf(noteId));

        // 4. 发送 MQ, 数据更新落库
        //构建消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode())
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId)
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO)).build();
        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;
        //hashKey
        String hasjKey = String.valueOf(userId);

        //异步发送MQ消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hasjKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }


    /**
     * 收藏笔记
     */
    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        // 笔记ID
        Long noteId = collectNoteReqVO.getId();

        // 1. 校验被收藏的笔记是否存在
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 判断目标笔记，是否已经收藏过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // 布隆过滤器 Key
        String bloomUserNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_collect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), String.valueOf(noteId));

        // 用户收藏列表 ZSet Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);

        switch (noteCollectLuaResultEnum) {
            // Redis 中布隆过滤器不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被收藏，并异步初始化布隆过滤器，设置过期时间
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 目标笔记已经被收藏
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, bloomUserNoteCollectListKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }

                // 若目标笔记未被收藏，查询当前用户是否有收藏其他笔记，有则同步初始化布隆过滤器
                batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, bloomUserNoteCollectListKey);

                // 添加当前收藏笔记 ID 到布隆过滤器中
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                stringRedisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), String.valueOf(noteId), String.valueOf(expireSeconds));
            }
            // 目标笔记已经被收藏 (可能存在误判，需要进一步确认)
            case NOTE_COLLECTED -> {
                // 校验 ZSet 列表中是否包含被收藏的笔记ID
                Double score = stringRedisTemplate.opsForZSet().score(userNoteCollectZSetKey, String.valueOf(noteId));

                if (Objects.nonNull(score)) {
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }

                // 若 Score 为空，则表示 ZSet 收藏列表中不存在，查询数据库校验
                int count = noteCollectionDOMapper.selectNoteIsCollected(userId, noteId);

                if (count > 0) {
                    // 数据库里面有收藏记录，而 Redis 中 ZSet 未初始化，需要重新异步初始化 ZSet
                    asynInitUserNoteCollectsZSet(userId, userNoteCollectZSetKey);

                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }
            }
        }

        // 3. 更新用户 ZSET 收藏列表
        LocalDateTime now = LocalDateTime.now();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_collect_check_and_update_zset.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        result = stringRedisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), String.valueOf(noteId), String.valueOf(DateUtils.localDateTime2Timestamp(now)));

        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, NoteCollectLuaResultEnum.NOT_EXIST.getCode())) {
            // 查询当前用户最新收藏的 300 篇笔记
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                // 构建 Lua 参数
                Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);

                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                // Lua 脚本路径
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
                // 返回值类型
                script2.setResultType(Long.class);

                stringRedisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);

                // 再次调用 note_collect_check_and_update_zset.lua 脚本，将当前收藏的笔记添加到 zset 中
                stringRedisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), String.valueOf(noteId), String.valueOf(DateUtils.localDateTime2Timestamp(now)));
            }
        }

        // 4. 发送 MQ, 将收藏数据落库
        // 构建消息体 DTO
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.COLLECT.getCode()) // 收藏笔记
                .createTime(now)
                .noteCreatorId(creatorId)
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(collectUnCollectNoteMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_COLLECT;

        String hashKey = String.valueOf(userId);

        // 异步发送顺序 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记收藏】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }


    /**
     * 取消收藏笔记
     */
    @Override
    public Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO) {
        // 笔记ID
        Long noteId = unCollectNoteReqVO.getId();

        // 1. 校验笔记是否真实存在
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 校验笔记是否被收藏过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // 布隆过滤器 Key
        String bloomUserNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_uncollect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), String.valueOf(noteId));

        NoteUnCollectLuaResultEnum noteUnCollectLuaResultEnum = NoteUnCollectLuaResultEnum.valueOf(result);

        switch (noteUnCollectLuaResultEnum) {
            // 布隆过滤器不存在
            case NOT_EXIST -> {
                // 异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, bloomUserNoteCollectListKey);
                });

                // 从数据库中校验笔记是否被收藏
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 未收藏，无法取消收藏操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
            }
            // 布隆过滤器校验目标笔记未被收藏（判断绝对正确）
            case NOTE_NOT_COLLECTED -> throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
        }

        // 3. 删除 ZSET 中已收藏的笔记 ID
        // 能走到这里，说明布隆过滤器判断已收藏，直接删除 ZSET 中已收藏的笔记 ID
        // 用户收藏列表 ZSet Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        stringRedisTemplate.opsForZSet().remove(userNoteCollectZSetKey, String.valueOf(noteId));

        // 4. 发送 MQ, 数据更新落库
        // 构建消息体 DTO
        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode()) // 取消收藏笔记
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId)
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unCollectNoteMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_UN_COLLECT;

        String hashKey = String.valueOf(userId);

        // 异步发送顺序 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消收藏】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /*
    * 获取是否点赞、收藏数据
    * */
    @Override
    public Response<FindNoteIsLikedAndCollectedRspVO> isLikedAndCollectedData(FindNoteIsLikedAndCollectedReqVO findNoteIsLikedAndCollectedReqVO) {
        Long noteId = findNoteIsLikedAndCollectedReqVO.getNoteId();

        //已登录的用户id
        Long currUserId = LoginUserContextHolder.getUserId();

        //默认未点赞，未收藏
        boolean isLiked = false;
        boolean isCollected = false;

        //若当前用户登录
        if(Objects.nonNull(currUserId)){
            //校验是否点赞
            isLiked = checkNoteIsLiked(noteId, currUserId);
            //校验是否收藏
            isCollected = checkNoteIsCollected(noteId, currUserId);
        }

        return Response.success(FindNoteIsLikedAndCollectedRspVO.builder()
                        .noteId(noteId)
                        .isCollected(isCollected)
                        .isLiked(isLiked)
                        .build());
    }

    /*
    * 校验当前登录用户是否收藏笔记
    * */
    private boolean checkNoteIsCollected(Long noteId, Long currUserId) {
        // 是否收藏
        boolean isCollected = false;

        // Roaring Bitmap Key
        String rbitmapUserNoteCollectListKey = RedisKeyConstants.buildRBitmapUserNoteCollectListKey(currUserId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_collect_only_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), String.valueOf(noteId));

        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);

        switch (noteCollectLuaResultEnum) {
            // Redis 中 Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被收藏，并异步初始化布隆过滤器，设置过期时间
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(currUserId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 目标笔记已经被收藏
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteCollect2RBitmapAndExpire(currUserId, expireSeconds, rbitmapUserNoteCollectListKey));
                    isCollected = true;
                }
            }
            // 目标笔记已经被收藏
            case NOTE_COLLECTED -> isCollected = true;
        }

        return isCollected;
    }

    /*
    * 校验当前登录用户是否点赞笔记
    * */
    private boolean checkNoteIsLiked(Long noteId, Long currUserId) {
        //是否点赞
        boolean isLiked = false;

        //Roaring Bittmap Key
        String rbitmapUserNoteLikeListKey = RedisKeyConstants.buildRBitmapUserNoteLikeListKey(currUserId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        //lua脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rbitmap_note_like_only_check.lua")));
        //返回值类型
        script.setResultType(Long.class);

        //执行lua脚本，并获取返回值
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), String.valueOf(noteId));

        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);

        switch (noteLikeLuaResultEnum) {
            //redis中 roaring bitmap 不存在
            case NOT_EXIST -> {
                //从数据库中校验笔记是否被点赞，并异步初始化roaring bitmap设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(currUserId, noteId);

                //过期时间————保底一天
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

                //目标笔记已经被点赞
                if (count > 0) {
                    // 异步初始化 Roaring Bitmap
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteLike2RBitmapAndExpire(currUserId, expireSeconds, rbitmapUserNoteLikeListKey));
                    isLiked = true;
                }
            }
            case NOTE_LIKED -> isLiked = true; // Roaring Bitmap 判断已点赞

        }
        return isLiked;
    }

    /**
     * 初始化笔记点赞 Roaring Bitmap
     * @param userId
     * @param expireSeconds
     * @param rbitmapUserNoteLikeListKey
     */
    private void batchAddNoteLike2RBitmapAndExpire(Long userId, long expireSeconds, String rbitmapUserNoteLikeListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_batch_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(String.valueOf(noteLikeDO.getNoteId()))); // 将每个点赞的笔记 ID 传入
                luaArgs.add(String.valueOf(expireSeconds));  // 最后一个参数是过期时间（秒）
                stringRedisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记点赞】Roaring Bitmap 异常: ", e);
        }
    }

    /**
     * 初始化笔记收藏 Roaring Bitmap
     */
    private void batchAddNoteCollect2RBitmapAndExpire(Long userId, long expireSeconds, String rbitmapUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_batch_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(String.valueOf(noteCollectionDO.getNoteId()))); // 将每个收藏的笔记 ID 传入
                luaArgs.add(String.valueOf(expireSeconds));  // 最后一个参数是过期时间（秒）
                stringRedisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】Roaring Bitmap 异常: ", e);
        }
    }



    /**
     * 初始化笔记收藏布隆过滤器
     */
    private void batchAddNoteCollect2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(String.valueOf(noteCollectionDO.getNoteId()))); // 将每个收藏的笔记 ID 传入
                luaArgs.add(String.valueOf(expireSeconds));  // 最后一个参数是过期时间（秒）
                stringRedisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】布隆过滤器异常: ", e);
        }
    }


    /**
     * 异步初始化用户收藏笔记 ZSet
     */
    private void asynInitUserNoteCollectsZSet(Long userId, String userNoteCollectZSetKey) {
        threadPoolTaskExecutor.execute(() -> {
            // 判断用户笔记收藏 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteCollectZSetKey);

            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询当前用户最新收藏的 300 篇笔记
                List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);
                if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);

                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    // Lua 脚本路径
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
                    // 返回值类型
                    script2.setResultType(Long.class);

                    stringRedisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);
                }
            }
        });
    }


    /**
     * 构建笔记收藏 ZSET Lua 脚本参数
     */
    private static Object[] buildNoteCollectZSetLuaArgs(List<NoteCollectionDO> noteCollectionDOS, long expireSeconds) {
        int argsLength = noteCollectionDOS.size() * 2 + 1; // 每个笔记收藏关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteCollectionDO noteCollectionDO : noteCollectionDOS) {
            luaArgs[i] = String.valueOf(DateUtils.localDateTime2Timestamp(noteCollectionDO.getCreateTime())); // 收藏时间作为 score
            luaArgs[i + 1] = String.valueOf(noteCollectionDO.getNoteId());          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = String.valueOf(expireSeconds); // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }




    /*
    * 异步初始化布隆过滤器————在lua脚本中创建
    * */
    @SneakyThrows
    private void batchAddNoteLike2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteLikeListKey) {
        //使用try-catch是因为异步线程的异常不会被主线程捕获
        try{
            //异步全量同步一下，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if(CollUtil.isNotEmpty(noteLikeDOS)){
                //构建lua脚本
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_batch_add_note_like_and_expire.lua")));
                script.setResultType(Long.class);

                //lua参数
                ArrayList<Object> luaArgs = Lists.newArrayList();
                //每个点赞笔记id
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(String.valueOf(noteLikeDO.getNoteId())));
                //最后一个参数为过期时间
                luaArgs.add(String.valueOf(expireSeconds));
                //执行lua脚本
                stringRedisTemplate.execute(script,Collections.singletonList(bloomUserNoteLikeListKey),luaArgs.toArray());
            }
        }catch (Exception e){
            log.error("## 异步初始化布隆过滤器异常: ", e);
        }
    }

    /*
    * 校验笔记是否存在,若存在，则获取笔记的发布者 ID
    * */
    /*
    * 填充笔记互动数据（点赞/收藏/评论总数）
    * */
    private void fillNoteCount(FindNoteDetailRspVO findNoteDetailRspVO) {
        if (Objects.isNull(findNoteDetailRspVO) || Objects.isNull(findNoteDetailRspVO.getId())) {
            return;
        }
        List<NoteCountDO> noteCountDOS = noteDOMapper.selectCountByNoteIds(Collections.singletonList(findNoteDetailRspVO.getId()));
        NoteCountDO noteCountDO = CollUtil.isEmpty(noteCountDOS) ? null : noteCountDOS.get(0);
        findNoteDetailRspVO.setLikeTotal(resolveNoteCount(noteCountDO, NoteCountDO::getLikeTotal));
        findNoteDetailRspVO.setCollectTotal(resolveNoteCount(noteCountDO, NoteCountDO::getCollectTotal));
        findNoteDetailRspVO.setCommentTotal(resolveNoteCount(noteCountDO, NoteCountDO::getCommentTotal));
    }

    /*
    * 空计数默认 0
    * */
    private long resolveNoteCount(NoteCountDO noteCountDO, java.util.function.Function<NoteCountDO, Long> countGetter) {
        Long count = Objects.isNull(noteCountDO) ? null : countGetter.apply(noteCountDO);
        return Objects.isNull(count) ? 0L : count;
    }

    /*
    * 在线程池线程中调用 Feign RPC 时，临时切换上下文类加载器为应用类加载器。
    * 避免 fat jar（java -jar）启动时，异步线程的 TCCL 为系统类加载器，
    * 无法加载 BOOT-INF/lib 下的依赖类，导致负载均衡客户端初始化失败。
    * */
    private <T> T rpcWithAppClassLoader(Supplier<T> rpcSupplier) {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(NoteServiceImpl.class.getClassLoader());
            return rpcSupplier.get();
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    /*
    * 校验笔记是否存在，若存在，则获取笔记的发布者 ID
    * */
    private Long checkNoteIsExistAndGetCreatorId(Long noteId) {
        //1.先从本地缓存中校验
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        //解析json 字符串转为 VO 对象
        FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);

        //判断本地缓存中是否有数据
        if(Objects.isNull(findNoteDetailRspVO)){
            //2.没有再从redis中校验
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
            String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

            //解析json 字符串转为 VO 对象
            findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);

            //3.都不存在，最后查询数据库校验笔记是否存在
            if(Objects.isNull(findNoteDetailRspVO)){
                //笔记发布者用户id
                Long creatorId = noteDOMapper.selectCreatorIdByNoteId(noteId);

                if(Objects.isNull(creatorId)) {
                    //数据库中也不存在，提醒用户
                    throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }

                //数据库中存在，异步更新本地缓存
                threadPoolTaskExecutor.submit(() ->{
                    FindNoteDetailReqVO findNoteDetailReqVO = FindNoteDetailReqVO.builder().id(noteId).build();
                    //这里进行查询笔记主要是为了将笔记信息添加到本地缓存
                    findNoteDetail(findNoteDetailReqVO);
                });
                return creatorId;
            }
        }
        return findNoteDetailRspVO.getCreatorId();
    }

    /*
    * 异步初始化 ZSet
    * */
    private void asynInitUserNoteLikesZSet(Long userId, String userNoteLikeZSetKey) {
        threadPoolTaskExecutor.execute(() ->{
            //判断用户笔记点赞ZSET是否存在
            Boolean isExsited = redisTemplate.hasKey(userNoteLikeZSetKey);
            //不存在，则重新初始化
            if(!isExsited){
                //查询当前用户最新点赞的100篇日记
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);
                //过期时间
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                //构建lua脚本
                DefaultRedisScript<Long> script1 = new DefaultRedisScript<>();
                script1.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/batch_add_note_like_zset_and_expire.lua")));
                script1.setResultType(Long.class);
                //构建lua参数
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);

                //执行lua脚本,初始化zset
                stringRedisTemplate.execute(script1,Collections.singletonList(userNoteLikeZSetKey),luaArgs);
            }
        });
    }


    /*
    * 删除本地缓存的笔记
    * */
    @Override
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }


    /*
    * 校验笔记可见性
    * */
    public void checkNoteVisible(Integer visible, Long currUserId, Long creatorId){
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) {
            // 仅自己可见, 并且访问用户为笔记创建者才能访问，非本人则抛出异常
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    /*
    * 校验笔记可见性（针对VO实体）
    * */
    public void checkNoteVisibleFromVO(Long userID,FindNoteDetailRspVO findNoteDetailRspVO){
        if(Objects.nonNull(findNoteDetailRspVO)){
            Integer visible = findNoteDetailRspVO.getVisible();
            //调用checkNoteVisible校验笔记的可见性
            checkNoteVisible(visible,userID,findNoteDetailRspVO.getCreatorId());
        }
    }

    /*
    * 构建lua参数
    * */
    private static Object[] buildNoteLikeZSetLuaArgs(List<NoteLikeDO> noteLikeDOS, long expireSeconds) {
        //每个关注关系都有两个参数（score和value），再加一个过期时间
        int argsLength = noteLikeDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            luaArgs[i] = String.valueOf(DateUtils.localDateTime2Timestamp(noteLikeDO.getCreateTime()));
            luaArgs[i+1] = String.valueOf(noteLikeDO.getNoteId());
            i += 2;
        }
        //最后一个参数时过期时间
        luaArgs[argsLength - 1] = String.valueOf(expireSeconds);
        return luaArgs;
    }

}
