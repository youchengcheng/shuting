package com.quanxiaoha.xiaohashu.user.relation.biz.service.impl;

import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.service.FollowNotificationPersistenceService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/*
 * 关注关系及通知事件持久化服务实现
 * */
@Service
public class FollowNotificationPersistenceServiceImpl implements FollowNotificationPersistenceService {

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private NotificationOutboxWriter notificationOutboxWriter;

    /*
     * 在同一事务中保存业务数据和通知事件
     * */
    @Override
    public boolean persist(FollowUserMqDTO followUserMqDTO) {
        // 从已校验的关注消息中读取操作者及被关注用户
        Long userId = followUserMqDTO.getUserId();
        Long followingUserId = followUserMqDTO.getFollowingId();
        try {
            // 关注关系、粉丝关系和通知事件在同一事务中保存
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // 先保存操作者的关注关系
                if (followingDOMapper.insert(FollowingDO.builder()
                        .userId(userId)
                        .followingUserId(followingUserId)
                        .createTime(followUserMqDTO.getCreateTime())
                        .build()) != 1) {
                    throw new IllegalStateException("关注关系未成功保存");
                }
                // 再保存被关注用户的粉丝关系，两张表须一同提交
                if (fansDOMapper.insert(FansDO.builder()
                        .userId(followingUserId)
                        .fansUserId(userId)
                        .createTime(followUserMqDTO.getCreateTime())
                        .build()) != 1) {
                    throw new IllegalStateException("粉丝关系未成功保存");
                }
                // 排除关注自己的情况，将关注通知写入同一事务的 outbox
                if (!Objects.equals(userId, followingUserId)) {
                    notificationOutboxWriter.save(NotificationEvent.builder()
                            .producerService("xiaohashu-user-relation")
                            .type("FOLLOW")
                            .businessKey(NotificationBusinessKey.of("FOLLOW", userId, followingUserId))
                            .actorId(userId)
                            .occurredAt(followUserMqDTO.getCreateTime())
                            .recipients(List.of(NotificationRecipient.builder()
                                    .userId(followingUserId)
                                    .role("FOLLOW_TARGET")
                                    .build()))
                            .build());
                }
                return true;
            }));
        } catch (DuplicateKeyException duplicate) {
            // 失败事务已回滚，关注表和粉丝表记录均存在时才视为重复投递
            boolean complete = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                    followingDOMapper.selectRelationForUpdate(userId, followingUserId) != null
                            && fansDOMapper.selectRelationForUpdate(followingUserId, userId) != null));
            if (!complete) {
                throw duplicate;
            }
            return false;
        }
    }
}
