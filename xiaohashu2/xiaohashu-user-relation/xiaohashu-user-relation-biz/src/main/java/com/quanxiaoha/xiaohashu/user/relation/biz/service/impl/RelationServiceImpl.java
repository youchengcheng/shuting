package com.quanxiaoha.xiaohashu.user.relation.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FansDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FollowingDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.FansDOMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.enums.FollowCheckResultEnum;
import com.quanxiaoha.xiaohashu.user.relation.biz.enums.LuaResultEnum;
import com.quanxiaoha.xiaohashu.user.relation.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.UnfollowUserMqDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.user.relation.biz.service.RelationService;
import com.quanxiaoha.xiaohashu.user.relation.dto.req.IsFollowedReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class RelationServiceImpl implements RelationService {

    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private FansDOMapper fansDOMapper;


    /*
     * 关注用户
     * */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        //关注的用户id
        Long followUserId = followUserReqVO.getFollowUserId();

        //当前登录的用户id
        Long userId = LoginUserContextHolder.getUserId();

        //校验无法关注自己
        if(Objects.equals(followUserId,userId)){
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }

        //RPC 调用用户服务校验关注的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(followUserId);
        if(Objects.isNull(findUserByIdRspDTO)){
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }


        //构建当前用户关注列表的 redis key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();//创建脚本对象

        //lua 脚本路径。ResourceScriptSource将文件包装为spring能识别的脚本文件，ClassPathResource：路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_check_and_add.lua")));
        //返回值类型
        script.setResultType(Long.class);
        //当前时间
        LocalDateTime now = LocalDateTime.now();
        //将当前时间转为时间戳
        Long timestamp = DateUtils.localDateTime2Timestamp(now);
        //执行lua脚本，拿到返回值
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
        //校验lua脚本执行结果
        checkLuaScriptResult(result);

        // 关注列表不存在
        if(Objects.equals(result,LuaResultEnum.ZSET_NOT_EXIST.getCode())){
            // 从数据库中查询当前用户的关注关系记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);
            // 过期时间
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            //判断记录是否为空
            if(CollUtil.isEmpty(followingDOS)){
                //为空直接ZADD 关系数据，并设置过期时间
                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();//创建脚本对象
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_add_and_expire.lua")));
                script2.setResultType(Long.class);
                // TODO: 可以根据用户类型，设置不同的过期时间，若当前用户为大V, 则可以过期时间设置的长些或者不设置过期时间；如不是，则设置的短些
                // 如何判断呢？可以从计数服务获取用户的粉丝数，目前计数服务还没创建，则暂时采用统一的过期策略
                //执行lua脚本
                redisTemplate.execute(script2,Collections.singletonList(followingRedisKey),followUserId,timestamp,expireSeconds);
            }else {
                // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间;
                //构建 lua 参数
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();//创建脚本对象
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3,Collections.singletonList(followingRedisKey),luaArgs);

                // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                redisTemplate.execute(script,Collections.singletonList(followingRedisKey),followUserId,timestamp);
            }
        }

        //发送 MQ ——————由消费者异步将数据落库
        //构建消息体 DTO
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .userId(userId)
                .followingId(followUserId)
                .createTime(LocalDateTime.now())
                .build();

        //构建消息对象，将DTO转成 json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO)).build();
        // topic
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);

        String hashKey = String.valueOf(userId);

        //异步发送MQ消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable e) {
                log.error("==> MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }

    /*
     * 校验当前登录用户是否已关注目标用户
     * */
    @Override
    public Response<Boolean> isFollowed(IsFollowedReqDTO isFollowedReqDTO) {
        Long followUserId = isFollowedReqDTO.getFollowUserId();
        // 当前登录用户（由网关注入的 userId 请求头解析而来）
        Long currUserId = LoginUserContextHolder.getUserId();

        // 未登录、或查询的是自己，直接返回未关注
        if (Objects.isNull(currUserId) || Objects.equals(currUserId, followUserId)) {
            return Response.success(Boolean.FALSE);
        }

        // 1.先查 Redis 中的关注列表缓存
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(currUserId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_check_only.lua")));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId);
        FollowCheckResultEnum followCheckResultEnum = FollowCheckResultEnum.valueOf(result);

        // 缓存命中：直接以缓存结果为准
        if (Objects.equals(FollowCheckResultEnum.FOLLOWED, followCheckResultEnum)) {
            return Response.success(Boolean.TRUE);
        }
        if (Objects.equals(FollowCheckResultEnum.NOT_FOLLOWED, followCheckResultEnum)) {
            return Response.success(Boolean.FALSE);
        }

        // 返回值不在枚举范围内，属于异常情况，抛出业务异常
        if (Objects.isNull(followCheckResultEnum)) {
            log.error("==> 校验是否关注：Lua 脚本返回了未知结果, result: {}, currUserId: {}, followUserId: {}",
                    result, currUserId, followUserId);
            throw new BizException(ResponseCodeEnum.FOLLOW_STATUS_CHECK_FAIL);
        }

        // 2.缓存不存在（CACHE_NOT_EXIST），回源数据库
        boolean isFollowed = followingDOMapper.selectCountByUserIdAndFollowingUserId(currUserId, followUserId) > 0;

        // 3.异步重建关注列表缓存，避免后续请求继续打到数据库
        threadPoolTaskExecutor.submit(() -> syncFollowingList2Redis(currUserId));

        return Response.success(isFollowed);
    }

    /*
    * 取消关注
    * */
    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        //要取关的用户id
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        //当前登录用户id
        Long userId = LoginUserContextHolder.getUserId();

        //无法取关自己
        if(Objects.equals(userId,unfollowUserId)){
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }

        //RPC 调用用户服务判断取关的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(unfollowUserId);
        if(Objects.isNull(findUserByIdRspDTO)){
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        //关注列表 key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/unfollow_check_and_delete.lua")));
        script.setResultType(Long.class);

        //执行lua脚本，获取结果
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);

        //校验lua脚本执行的结果
        //  1.取关的用户不在关注列表中
        if(Objects.equals(result,LuaResultEnum.NOT_FOLLOWED.getCode())){
            throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
        }
        //  2.关注列表不存在
        if(Objects.equals(result,LuaResultEnum.ZSET_NOT_EXIST.getCode())){
            //从数据库中查询当前用户的关注记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);
            //设置一个过期时间
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            //判断数据库中查询的结果（存在关注列表和不存在关注列表）
            if(CollUtil.isEmpty(followingDOS)){
                //  1.不存在，表示当前用户为关注任何人
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            }else {
                //  2.存在，将关注列表同步到redis缓存中
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

                // 构建lua脚本
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);

                //执行lua脚本
                redisTemplate.execute(script3,Collections.singletonList(followingRedisKey),luaArgs);

                //再次删除当前登录用户缓存中的关注列表中的取关用户
                result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);

                //再次校验删除删除是否成功
                if(Objects.equals(result,LuaResultEnum.NOT_FOLLOWED.getCode())){
                    throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
                }
            }
        }

        //发送MQ
        //构建消息体 TDO
        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .userId(userId)
                .unfollowUserId(unfollowUserId)
                .createTime(LocalDateTime.now())
                .build();

        //构建消息对象，将DTO转成 json 字符串设置到消息体中
        Message<String> message = MessageBuilder
                .withPayload(JsonUtils.toJsonString(unfollowUserMqDTO)).build();
        
        //topic 通过冒号连接，可让MQ发送给主题topic时，携带上标签
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;
        log.info("==> 开始发送取关操作 MQ, 消息体: {}", unfollowUserMqDTO);

        String hashKey = String.valueOf(userId);

        //异步发送MQ消息，提升响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable e) {
                log.error("==> MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }


    /*
    * 查询关注列表
    * */
    @Override
    public PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO) {
        //想要查的用户id
        Long userId = findFollowingListReqVO.getUserId();
        //页码
        Integer pageNo = findFollowingListReqVO.getPageNo();
        //先从redis中查询
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);

        //获取查询目标用户关注列表的总大小
        Long total = redisTemplate.opsForZSet().zCard(followingRedisKey);

        //反参
        List<FindFollowingUserRspVO> findFollowingUserRspVOS = null;

        //每页展示10条数据
        long limit = 10;

        //缓存中有数据
        if(total > 0){
            //计算有多少页
            long totalPage = PageResponse.getTotalPage(total, limit);

            //判断请求页码是否超出总页数
            if(pageNo > totalPage){
                return PageResponse.success(Collections.emptyList(),pageNo,total);
            }
            //准备从redis中查询ZSET分页数据
            //计算偏移量————每页10条记录
            long offset = (pageNo - 1) * limit;

            // 使用 ZREVRANGEBYSCORE 命令按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 注意：这里使用了 Double.POSITIVE_INFINITY 和 Double.NEGATIVE_INFINITY 作为分数范围
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(followingRedisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);

            //查询到的数据不为空
            if(CollUtil.isNotEmpty(followingUserIdsSet)){
                //提取所有用户id到集合中
                List<Long> userIds = followingUserIdsSet.stream()
                        .map(Object -> Long.valueOf(Object.toString()))
                        .toList();

                //RPC：调用用户服务批量查询用户信息
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);
            }
        }else {
            //若 Redis 中没有数据，则从数据库查询
            //先查询记录总量————关注了多少人
            long count = followingDOMapper.selectCountByUserId(userId);
            //计算有多少页
            long totalPage = PageResponse.getTotalPage(count, limit);

            //判断请求页码是否大于总页码
            if(pageNo > totalPage){
                return PageResponse.success(Collections.emptyList(),pageNo,total);
            }
            //偏移量
            long offset = (pageNo - 1) * limit;
            //分页查询
            List<FollowingDO> followingDOS = followingDOMapper.selectPageListByUserId(userId, offset, limit);

            //赋值真实的记录总数
            total = count;

            //记录不为空
            //提取所有关注用户id到集合中
            List<Long> userIds = followingDOS.stream().map(FollowingDO::getFollowingUserId).toList();

            //RPC 调用用户服务将DTO转为VO
            findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds,findFollowingUserRspVOS);

            //异步将关注列表全量同步到 Redis
            threadPoolTaskExecutor.submit(() -> syncFollowingList2Redis(userId));
        }

        return PageResponse.success(Objects.requireNonNullElse(findFollowingUserRspVOS, Collections.emptyList()),pageNo,total);
    }

    /*
    * 查询粉丝列表
    * */
    @Override
    public PageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO findFansListReqVO) {
        //想要查询的用户id
        Long userId = findFansListReqVO.getUserId();
        //页码
        Integer pageNo = findFansListReqVO.getPageNo();
        //先从redis中查询
        String fansListRedisKey = RedisKeyConstants.buildUserFansKey(userId);
        //查询目标用户粉丝列表的总大小
        Long total = redisTemplate.opsForZSet().zCard(fansListRedisKey);

        //反参
        List<FindFansUserRspVO> findFansUserRspVOS = null;
        //每页展示数据量
        long limit = 10;

        //判断，缓存中是否有数据
        if(total > 0){//有数据
            //计算一共多少页
            long totalPage = PageResponse.getTotalPage(total,limit);

            //请求页超出总页数
            if(pageNo > totalPage){
                return PageResponse.success(Collections.emptyList(),pageNo,total);
            }

            //准备从redis中进行分页查询
            //偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            //使用 ZREVRANGEBYSCORE 命令按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(fansListRedisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);

            if(CollUtil.isNotEmpty(followingUserIdsSet)){
                //redis中查询的数据不为空，提取用户id到集合中
                List<Long> userIds = followingUserIdsSet.stream()
                        .map(Object -> Long.valueOf(Object.toString())).toList();

                //RPC:批量查询用户信息
                findFansUserRspVOS = rpcUserServiceAndCountServiceAndDTO2VO(userIds,findFansUserRspVOS);
            }

        }else {//无数据————查询数据库
            //先查询总的粉丝关注数
            long count = fansDOMapper.selectCountByUserId(userId);
            //计算一共有多少页
            long totalPage = PageResponse.getTotalPage(count, limit);

            //请求页超出总页码数（只允许查询前500页）
            //请求页超出总页数
            if(pageNo > totalPage || pageNo > 500){
                return PageResponse.success(Collections.emptyList(),pageNo,total);
            }
            //偏移量
            long offset = PageResponse.getOffset(pageNo, limit);

            //分页查询
            List<FansDO> fansDOS = fansDOMapper.selectPageListByUserId(userId, offset, limit);

            //判断查询结果，若不为空
            if(CollUtil.isNotEmpty(fansDOS)){
                //提取所有粉丝用户id到集合中
                List<Long> userIds = fansDOS.stream().map(FansDO::getFansUserId).toList();
                //RPC:调用用户服务，计数服务，并将 DTO 转为 VO
                findFansUserRspVOS = rpcUserServiceAndCountServiceAndDTO2VO(userIds,findFansUserRspVOS);
                //异步将粉丝列表同步到redis中（最多5000条）
                threadPoolTaskExecutor.submit(() -> syncFansList2Redis(userId));
            }
        }

        return PageResponse.success(Objects.requireNonNullElse(findFansUserRspVOS, Collections.emptyList()),pageNo,total);
    }

    /*
    * 将粉丝列表同步到redis中（最多5000条）
    * */
    private void syncFansList2Redis(Long userId) {
        List<FansDO> fansDOS = fansDOMapper.select5000FansByUserId(userId);

        //构建redis key
        String fansRedisKey = RedisKeyConstants.buildUserFansKey(userId);
        //过期时间
        long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
        //构建lua脚本参数
        Object[] luaArgs = buildFansZSetLuaArgs(fansDOS, expireSeconds);

        //执行lua脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_batch_add_and_expire.lua")));
        script.setResultType(Long.class);
        redisTemplate.execute(script,Collections.singletonList(fansRedisKey),luaArgs);
    }

    /*
     * 调用用户服务、计数服务，并将 DTO 转换为 VO 粉丝列表
     * */
    private List<FindFansUserRspVO> rpcUserServiceAndCountServiceAndDTO2VO(List<Long> userIds, List<FindFansUserRspVO> findFansUserRspVOS) {

        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        if(CollUtil.isNotEmpty(findUserByIdRspDTOS)){
            //1.使用糊涂包进行对象拷贝
            //findFansUserRspVOS =  BeanUtil.copyToList(findUserByIdRspDTOS,FindFansUserRspVO.class);
            //2.使用builder
            findFansUserRspVOS = findUserByIdRspDTOS.stream()
                    .map(dto -> FindFansUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .noteTotal(0L) // TODO: 这块的数据暂无，后续补充
                            .fansTotal(0L) // TODO: 这块的数据暂无，后续补充
                            .build())
                    .toList();
        }
        return findFansUserRspVOS;
    }


    /*
    * 将关注列表全量同步到 Redis
    * */
    private void syncFollowingList2Redis(Long userId) {
        //查询用户的关注列表
        List<FollowingDO> followingDOS = followingDOMapper.selectAllByUserId(userId);

        //用户关注列表redis key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        //过期时间
        long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

        //构建lua参数
        Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

        //执行lua脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_batch_add_and_expire.lua")));
        script.setResultType(Long.class);
        redisTemplate.execute(script,Collections.singletonList(followingRedisKey),luaArgs);
    }

    /*
    * RPC: 调用用户服务，并将 DTO 转换为 VO
    * */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds, List<FindFollowingUserRspVO> findFollowingUserRspVOS) {
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        //查询的信息不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            //1.直接转
            findFollowingUserRspVOS = findUserByIdRspDTOS.stream().map(
                    dto -> FindFollowingUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .introduction(dto.getIntroduction())
                            .build()
            ).toList();
            //2.使用糊涂包
            //findFollowingUserRspVOS = BeanUtil.copyToList(findUserByIdRspDTOS,FindFollowingUserRspVO.class);
        }
        return findFollowingUserRspVOS;
    }

    /*
    * 校验lua脚本结果，根据状态吗抛出对应的业务异常
    * */
    private static void checkLuaScriptResult(Long result){
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        //校验结果
        switch (luaResultEnum) {
            //关注已达上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }

    /*
    * 构建 lua 脚本参数
    * */
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOS,long expireSeconds){
        //每个关注关系都有两个参数（score和value），再加一个过期时间
        int argsLength = followingDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];
        
        int i = 0;
        for (FollowingDO followingDO : followingDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(followingDO.getCreateTime());
            luaArgs[i + 1] = followingDO.getFollowingUserId();
            i = i + 2;
        }
        //最后一个参数时过期时间
        luaArgs[argsLength - 1] = expireSeconds;
        return luaArgs;
    }


    /**
     * 构建 Lua 脚本参数：粉丝列表
     */
    private static Object[] buildFansZSetLuaArgs(List<FansDO> fansDOS, long expireSeconds) {
        int argsLength = fansDOS.size() * 2 + 1; // 每个粉丝关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FansDO fansDO : fansDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(fansDO.getCreateTime()); // 粉丝的关注时间作为 score
            luaArgs[i + 1] = fansDO.getFansUserId();          // 粉丝的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

}
