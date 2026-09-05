package com.quanxiaoha.xiaohashu.user.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.enums.DeletedEnum;
import com.quanxiaoha.framework.common.enums.StatusEnum;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.framework.common.util.NumberUtils;
import com.quanxiaoha.framework.common.util.ParamUtils;
import com.quanxiaoha.xiaohashu.count.dto.FindUserCountsByIdRspDTO;
import com.quanxiaoha.xiaohashu.user.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.user.biz.constant.RoleConstants;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.RoleDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.UserCountDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.UserDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.dataobject.UserRoleDO;
import com.quanxiaoha.xiaohashu.user.biz.domain.mapper.RoleDOMapper;
import com.quanxiaoha.xiaohashu.user.biz.domain.mapper.UserDOMapper;
import com.quanxiaoha.xiaohashu.user.biz.domain.mapper.UserRoleDOMapper;
import com.quanxiaoha.xiaohashu.user.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.user.biz.enums.SexEnum;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.UpdateUserInfoReqVO;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.FindUserProfileReqVO;
import com.quanxiaoha.xiaohashu.user.biz.model.vo.FindUserProfileRspVO;
import com.quanxiaoha.xiaohashu.user.biz.rpc.CountRpcService;
import com.quanxiaoha.xiaohashu.user.biz.rpc.DistributedIdGeneratorRpcService;
import com.quanxiaoha.xiaohashu.user.biz.rpc.OssRpcService;
import com.quanxiaoha.xiaohashu.user.biz.service.UserService;
import com.quanxiaoha.xiaohashu.user.dto.req.*;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/*
 * 用户业务
 * */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private UserDOMapper userDOMapper;
    @Resource
    private OssRpcService ossRpcService;
    @Resource
    private UserRoleDOMapper userRoleDOMapper;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private UserDOMapper userMapper;
    @Resource
    private CountRpcService countRpcService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /*
    * 用户信息本地缓存
    * */
    private static final Cache<Long, FindUserByIdRspDTO> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000)//初始容量
            .maximumSize(10000)//最大容量
            .expireAfterWrite(1,TimeUnit.HOURS)//过期时间
            .build();

    /**
     * 用户主页信息本地缓存
     */
    private static final Cache<Long, FindUserProfileRspVO> PROFILE_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(5, TimeUnit.MINUTES) // 设置缓存条目在写入后 5 分钟过期
            .build();



    /*
     * 更新用户信息
     * */
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        // 被更新的用户 ID
        Long userId = updateUserInfoReqVO.getUserId();
        // 当前登录的用户 ID
        Long loginUserId = LoginUserContextHolder.getUserId();
        //只有自己才能更新个人信息
        if(!Objects.equals(userId,loginUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_UPDATE_OTHER_USER_PROFILE);
        }

        UserDO userDO = new UserDO();
        //设置当前需要更新的用户id
        userDO.setId(LoginUserContextHolder.getUserId());
        //标识位：是否需要更新
        boolean needUpdate = false;

        //头像
        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();
        //Objects.nonNull(avatarFile)判断一个对象是不是 null，不是 null 就返回 true，是 null 就返回 false
        if(Objects.nonNull(avatarFile)){
            // 调用对象存储服务上传文件
            String avatar = ossRpcService.uploadFile(avatarFile);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);

            //如果上传失败，抛出业务异常
            if(StringUtils.isBlank(avatar)){
                throw new BizException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }
            userDO.setAvatar(avatar);
            needUpdate = true;
        }

        //昵称
        String nickname = updateUserInfoReqVO.getNickname();
        if(StringUtils.isNotBlank(nickname)){
            //检查参数是否符合你的要求。Preconditions.checkArgument(条件, 异常提示信息);
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL);
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        // 小哈书号
        String xiaohashuId = updateUserInfoReqVO.getXiaohashuId();
        if (StringUtils.isNotBlank(xiaohashuId)) {
            Preconditions.checkArgument(ParamUtils.checkXiaohashuId(xiaohashuId), ResponseCodeEnum.XIAOHASHU_ID_VALID_FAIL.getErrorMessage());
            userDO.setXiaohashuId(xiaohashuId);
            needUpdate = true;
        }

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.isNotBlank(introduction)) {
            Preconditions.checkArgument(ParamUtils.checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            // 调用对象存储服务上传文件
            String backgroundImg = ossRpcService.uploadFile(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", backgroundImg);

            //如果上传失败，抛出业务异常
            if(StringUtils.isBlank(backgroundImg)){
                throw new BizException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }

            userDO.setBackgroundImg(backgroundImg);
            needUpdate = true;
        }

        if (needUpdate) {
            //跟新用户元数据时先删除redis缓存中的数据----这里采用延迟双删策略
            deleteUserRedisCache(userId);

            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);

            // 资料更新后立即失效缓存，避免 findById/关注列表继续返回旧资料。
//            userId = userDO.getId();
//            LOCAL_CACHE.invalidate(userId);
//            redisTemplate.delete(RedisKeyConstants.buildUserInfoKey(userId));

            //更新完数据库之后再次删除redis中的数据
            sendDelayDeleteUserRedisCacheMQ(userId);
        }
        return Response.success();
    }



    /*
    * 注册
    * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {

        //获取手机号
        String phone = registerUserReqDTO.getPhone();

        //判断手机号是否已经被注册
        UserDO userDO1 = userDOMapper.selectByPhone(phone);
        log.info("==> 用户是否注册, phone: {}, userDO: {}", phone, JsonUtils.toJsonString(userDO1));

        //如果已经注册，则直接返回用用户id
        if(Objects.nonNull(userDO1)){
            return Response.success(userDO1.getId());
        }
        //如果没有注册，则就进行注册
        // 获取全局自增的小哈书 ID
//        Long xiaohashuId = redisTemplate.opsForValue().increment(RedisKeyConstants.XIAOHASHU_ID_GENERATOR_KEY);

        // RPC: 调用分布式 ID 生成服务生成小哈书 ID
        String xiaohashuId = distributedIdGeneratorRpcService.getXiaohashuId();
        // RPC: 调用分布式 ID 生成服务生成用户 ID
        String userIdStr = distributedIdGeneratorRpcService.getUserId();
        Long userId = Long.valueOf(userIdStr);

        UserDO userDO = UserDO.builder()
                .id(userId)
                .phone(phone)
                .xiaohashuId(xiaohashuId) // 自动生成小红书号 ID
                .nickname("小红薯" + xiaohashuId) // 自动生成昵称, 如：小红薯10000
                .status(StatusEnum.ENABLE.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
                .build();

        // 添加入库
        userDOMapper.insert(userDO);

        // 获取刚刚添加入库的用户 ID
//        Long userId = userDO.getId();

        // 给该用户分配一个默认角色
        UserRoleDO userRoleDO = UserRoleDO.builder()
                .userId(userId)
                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();
        userRoleDOMapper.insert(userRoleDO);

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);

        // 将该用户的角色 ID 存入 Redis 中
        List<String> roles = new ArrayList<>(1);
        roles.add(roleDO.getRoleKey());

        String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
        redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

        return Response.success(userId);
    }

    /*
     * 根据手机号查询用户信息
     * */
    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        //获取传递过来的手机号
        String phone = findUserByPhoneReqDTO.getPhone();
        //查询该用户信息
        UserDO userDO = userDOMapper.selectByPhone(phone);
        //判断是否为空
        if(Objects.isNull(userDO)){
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }
        //封装到FindUserByPhoneRspDTO进行返回
        FindUserByPhoneRspDTO findUserByPhoneRspDTO = FindUserByPhoneRspDTO.builder()
                .id(userDO.getId())
                .password(userDO.getPassword())
                .build();

        return Response.success(findUserByPhoneRspDTO);
    }

    /*
    * 更新密码
    * */
    @Override
    public Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        //获取当前登录用户
        Long userId = LoginUserContextHolder.getUserId();
        //构建用户信息
        UserDO userDO = UserDO.builder()
                .id(userId)
                .password(updateUserPasswordReqDTO.getEncodePassword())
                .updateTime(LocalDateTime.now())
                .build();
        //更新用户信息
        userDOMapper.updateByPrimaryKeySelective(userDO);

        return Response.success();
    }

    /*
     * 根据用户 ID 查询用户信息
     * */
    @Override
    public Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        //1.获取用户id
        Long userid = findUserByIdReqDTO.getId();

        //1.1先从本地缓存中查询用户信息
        //LOCAL_CACHE.getIfPresent(userid) 根据用户 ID 查本地缓存，存在则返回用户信息，不存在返回 null
        FindUserByIdRspDTO findUserByIdRspDTOLocalCache = LOCAL_CACHE.getIfPresent(userid);
        //1.2判断查询到的信息是否为空
        if(Objects.nonNull(findUserByIdRspDTOLocalCache)){
            log.info("=======命中了本地缓存：{}",findUserByIdRspDTOLocalCache);
            return Response.success(findUserByIdRspDTOLocalCache);
        }

        //2.构建redis key
        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userid);

        //3.从redis中查询用户信息
        String userInfoRedisValue = (String) redisTemplate.opsForValue().get(userInfoRedisKey);
        //3.1判断从redis中查询到的信息是否为空
        if(StringUtils.isNotBlank(userInfoRedisValue) && !"null".equalsIgnoreCase(userInfoRedisValue.trim())){
            //3.1不为空,将用户信息保存到本地缓存再返回
            FindUserByIdRspDTO findUserByIdRspDTO = JsonUtils.parseObject(userInfoRedisValue, FindUserByIdRspDTO.class);
            //3.1.1异步将用户信息保存到本地缓存
            threadPoolTaskExecutor.submit(()->{
                if(Objects.nonNull(findUserByIdReqDTO)){
                    LOCAL_CACHE.put(userid,findUserByIdRspDTO);
                }
            });

            return Response.success(findUserByIdRspDTO);
        }
        //3.2为空从数据库中查询，然后判断信息是否为空
        UserDO userDO = userDOMapper.selectByPrimaryKey(userid);
        //3.2.1判断数据库查询到的数据是否为空
        if(Objects.isNull(userDO)){
            threadPoolTaskExecutor.execute(()->{
                //为空也存储到redis，但是过期时间设置短一点
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(userInfoRedisKey,"null",expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }
        //3.2.2不为空，构建用户信息
        FindUserByIdRspDTO findUserByIdRspDTO = FindUserByIdRspDTO.builder()
                .id(userDO.getId())
                .nickName(userDO.getNickname())
                .avatar(userDO.getAvatar())
                .introduction(userDO.getIntroduction())
                .build();

        //4.异步将用户信息存储到redis
        threadPoolTaskExecutor.submit(()->{
            //防止同一时刻所有用户信息过期
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            redisTemplate.opsForValue().set(userInfoRedisKey,JsonUtils.toJsonString(findUserByIdRspDTO),expireSeconds,TimeUnit.SECONDS);
        });

        return Response.success(findUserByIdRspDTO);
    }

    /*
    * 批量查询用户信息
    * */
    @Override
    public Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        //获取需要查询的用户id集合
        List<Long> userIds = findUsersByIdsReqDTO.getIds();

        //构建redis key 集合
        List<String> redisKeys = userIds.stream()
                .map(RedisKeyConstants::buildUserInfoKey)
                .toList();

        //从redis 缓存中查询 ，使用multiGet(mget)批量查询
        List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeys);

        //判断redis缓存数据，不为空
        if(CollUtil.isNotEmpty(redisValues)){
            //过滤为空数据————收集不为空的数据
            redisValues = redisValues.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> !"null".equalsIgnoreCase(String.valueOf(value).trim()))
                    .toList();
        }

        //反参（缓存）————Lists，Google的工具，创建集合
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = Lists.newArrayList();

        //将过滤后的缓存集合，转为 DTO 反参实体类
        if(CollUtil.isNotEmpty(redisValues)) {
            findUserByIdRspDTOS = redisValues.stream()
                    //将json字符串转为Java对象
                    .map(value -> JsonUtils.parseObject(String.valueOf(value), FindUserByIdRspDTO.class))
                    .collect(Collectors.toList());
        }

        //判断：如果被查询的用户信息都在redis中，就直接返回
        if(CollUtil.size(userIds) == CollUtil.size(findUserByIdRspDTOS)){
            return Response.success(findUserByIdRspDTOS);
        }
        // 还有另外两种情况：1.一种是缓存里没有用户信息数据，2.还有一种是缓存里数据不全，需要从数据库中补充
        // 筛选出缓存里没有的用户数据，去查数据库
        List<Long> userIdsNeedQuery = null;
        
        if(CollUtil.isNotEmpty(findUserByIdRspDTOS)){
            //将findUserByIdRspDTOS集合转为map
            Map<Long, FindUserByIdRspDTO> map = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

            //筛选出需要查询DB的用户id————将redis缓存中不存在的id收集起来
            userIdsNeedQuery = userIds.stream()
                    .filter(id -> Objects.isNull(map.get(id)))
                    .toList();
        }else {
            // 缓存中一条用户信息都没查到，则提交的用户 ID 集合都需要查数据库
            userIdsNeedQuery = userIds;
        }

        //从数据批量查询
        List<UserDO> userDOS = userDOMapper.selectByIds(userIdsNeedQuery);
        //反参（数据库）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS2 = null;

        //若数据库查询的记录不为空
        if(CollUtil.isNotEmpty(userDOS)){
            //1.手写 DO 转 DTO
            findUserByIdRspDTOS2 = userDOS.stream()
                    .map(userDO -> FindUserByIdRspDTO.builder()
                            .id(userDO.getId())
                            .nickName(userDO.getNickname())
                            .avatar(userDO.getAvatar())
                            .introduction(userDO.getIntroduction())
                            .build())
                    .collect(Collectors.toList());
            //2.使用糊涂包 DO 转 DTO
            //findUserByIdRspDTOS2 = BeanUtil.copyToList(userDOS, FindUserByIdRspDTO.class);

            //异步线程将用户信息同步到 Redis 中
            List<FindUserByIdRspDTO> finalFindUserByIdRspDTOS = findUserByIdRspDTOS2;
            threadPoolTaskExecutor.submit(()->{
                //将 DTO 转为 map
                Map<Long, FindUserByIdRspDTO> map = finalFindUserByIdRspDTOS.stream()
                        .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

                //执行pipeline操作
                redisTemplate.executePipelined(new SessionCallback<>() {
                    public Object execute(RedisOperations operations) {
                        for (UserDO userDO : userDOS) {
                            Long userId = userDO.getId();

                            //用户信息缓存 redis key
                            String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);

                            //将DTO转json字符串
                            FindUserByIdRspDTO findUserByIdRspDTO = map.get(userId);
                            String value = JsonUtils.toJsonString(findUserByIdRspDTO);

                            //设置过期时间
                            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                            operations.opsForValue().set(userInfoRedisKey,value,expireSeconds,TimeUnit.SECONDS);
                        }
                        return null;
                    }
                });
            });
        }

        //合并数据
        if(CollUtil.isNotEmpty(findUserByIdRspDTOS2)){
            findUserByIdRspDTOS.addAll(findUserByIdRspDTOS2);
        }

        return Response.success(findUserByIdRspDTOS);
    }

    /*
     * 查询用户主页资料
     * */
    @Override
    public Response<FindUserProfileRspVO> findUserProfile(FindUserProfileReqVO findUserProfileReqVO) {
        // 要查询的用户 ID
        Long userId = findUserProfileReqVO.getUserId();

        // 若入参中用户 ID 为空，则查询当前登录用户
        if (Objects.isNull(userId)) {
            userId = LoginUserContextHolder.getUserId();
        }

        //0.查询本地缓存
        //用户本人不为保证实时性不走本地缓存
        if(!Objects.equals(userId,LoginUserContextHolder.getUserId())){
            FindUserProfileRspVO userProfileLocalCache = PROFILE_LOCAL_CACHE.getIfPresent(userId);

            if(Objects.nonNull(userProfileLocalCache)) {
                log.info("## 用户主页信息命中本地缓存: {}", JsonUtils.toJsonString(userProfileLocalCache));
                return Response.success(userProfileLocalCache);
            }
        }

        //1. 优先查询缓存
        String userProfileRedisKey = RedisKeyConstants.buildUserProfileKey(userId);

        String userProfileJson = (String) redisTemplate.opsForValue().get(userProfileRedisKey);

        if(StringUtils.isNotBlank(userProfileJson)) {
            FindUserProfileRspVO findUserProfileRspVO = JsonUtils.parseObject(userProfileJson, FindUserProfileRspVO.class);

            //异步同步到本地缓存
            syncUserProfile2LocalCache(userId,findUserProfileRspVO);
            // 如果是博主本人查看，保证计数的实时性
            authorGetActualCountData(userId, findUserProfileRspVO);

            return Response.success(findUserProfileRspVO);
        }

        //2. 若redis中查询不到，再查询数据库
        UserDO userDO = userMapper.selectByPrimaryKey(userId);

        if(Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        //构建反参VO
        FindUserProfileRspVO findUserProfileRspVO = FindUserProfileRspVO.builder()
                .userId(userDO.getId())
                .avatar(userDO.getAvatar())
                .nickname(userDO.getNickname())
                .xiaohashuId(userDO.getXiaohashuId())
                .sex(userDO.getSex())
                .introduction(userDO.getIntroduction())
                .build();
        LocalDate birthday = userDO.getBirthday();
        findUserProfileRspVO.setAge(Objects.isNull(birthday) ? 0 : DateUtils.calculateAge(birthday));

        // RPC: Feign 调用计数服务
        // 关注数、粉丝数、收藏与点赞总数；获得的点赞数、收藏数
        rpcCountServiceAndSetData(userId, findUserProfileRspVO);

        //异步将数据同步到redis中
        syncUserProfile2Redis(userProfileRedisKey,findUserProfileRspVO);

        //异步同步到本地缓存
        syncUserProfile2LocalCache(userId,findUserProfileRspVO);


        return Response.success(findUserProfileRspVO);

    }




    /*
    * 异步同步到本地缓存
    * */
    private void syncUserProfile2LocalCache(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        threadPoolTaskExecutor.submit(() -> {
            PROFILE_LOCAL_CACHE.put(userId,findUserProfileRspVO);
        });
    }

    /*
    * 异步将数据同步到redis中
    * */
    private void syncUserProfile2Redis(String userProfileRedisKey, FindUserProfileRspVO findUserProfileRspVO) {
        threadPoolTaskExecutor.submit(() -> {
            // 设置随机过期时间 (2小时以内)
            long expireTime = 60*60 + RandomUtil.randomInt(60 * 60);

            // 将 VO 转为 Json 字符串写入到 Redis 中
            redisTemplate.opsForValue().set(userProfileRedisKey,JsonUtils.toJsonString(findUserProfileRspVO),expireTime,TimeUnit.SECONDS);
        });
    }

    /*
    * 删除redis中的用户缓存信息
    * */
    private void deleteUserRedisCache(Long userId) {
        //构建rediskey
        String userInfoKey = RedisKeyConstants.buildUserInfoKey(userId);
        String userProfileKey = RedisKeyConstants.buildUserProfileKey(userId);

        //批量删除
        redisTemplate.delete(Arrays.asList(userInfoKey,userProfileKey));
    }

    /*
    * 异步延迟删除redis中的缓存数据
    * */
    private void sendDelayDeleteUserRedisCacheMQ(Long userId) {
        Message<String> message = MessageBuilder.withPayload(String.valueOf(userId)).build();

        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("## 延时删除 Redis 用户缓存消息发送成功...");
            }

            @Override
            public void onException(Throwable e) {
                log.error("## 延时删除 Redis 用户缓存消息发送失败...", e);
            }
        },
                3000,//超时时间
                1 //延迟一秒
        );
    }

    /*
    * 作者本人获取真实的计数数据（保证实时性）
     * */
    private void authorGetActualCountData(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        if(Objects.equals(userId,findUserProfileRspVO.getUserId())) {
            //// 如果是博主本人：调用计数服务, 并设置计数数据
            rpcCountServiceAndSetData(userId,findUserProfileRspVO);
        }
    }

    /*
     * Feign 调用计数服务, 并设置计数数据
     * */
    private void rpcCountServiceAndSetData(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        FindUserCountsByIdRspDTO findUserCountsByIdRspDTO = countRpcService.findUserCountById(userId);

        if (Objects.nonNull(findUserCountsByIdRspDTO)) {
            Long fansTotal = findUserCountsByIdRspDTO.getFansTotal();
            Long followingTotal = findUserCountsByIdRspDTO.getFollowingTotal();
            Long likeTotal = findUserCountsByIdRspDTO.getLikeTotal();
            Long collectTotal = findUserCountsByIdRspDTO.getCollectTotal();
            Long noteTotal = findUserCountsByIdRspDTO.getNoteTotal();

            findUserProfileRspVO.setFansTotal(NumberUtils.formatNumberString(fansTotal));
            findUserProfileRspVO.setFollowingTotal(NumberUtils.formatNumberString(followingTotal));
            findUserProfileRspVO.setLikeAndCollectTotal(NumberUtils.formatNumberString(likeTotal + collectTotal));
            findUserProfileRspVO.setNoteTotal(NumberUtils.formatNumberString(noteTotal));
            findUserProfileRspVO.setLikeTotal(NumberUtils.formatNumberString(likeTotal));
            findUserProfileRspVO.setCollectTotal(NumberUtils.formatNumberString(collectTotal));
        }
    }

}
