package com.quanxiaoha.xiaohashu.user.relation.biz.service;

import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.*;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.api.outbox.NotificationOutboxWriter;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class FollowNotificationPersistenceService {
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private FollowingDOMapper followingDOMapper;
    @Resource private FansDOMapper fansDOMapper;
    @Resource private NotificationOutboxWriter notificationOutboxWriter;

    public boolean persist(FollowUserMqDTO dto) {
        Long actor = dto.getUserId();
        Long target = dto.getFollowingId();
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                if (followingDOMapper.insert(FollowingDO.builder().userId(actor).followingUserId(target).createTime(dto.getCreateTime()).build()) != 1)
                    throw new IllegalStateException("Following insert did not persist a relation");
                if (fansDOMapper.insert(FansDO.builder().userId(target).fansUserId(actor).createTime(dto.getCreateTime()).build()) != 1)
                    throw new IllegalStateException("Fans insert did not persist a relation");
                if (!Objects.equals(actor, target)) notificationOutboxWriter.save(NotificationEvent.builder()
                        .producerService("xiaohashu-user-relation").type("FOLLOW")
                        .businessKey(NotificationBusinessKey.of("FOLLOW", actor, target)).actorId(actor).occurredAt(dto.getCreateTime())
                        .recipients(List.of(NotificationRecipient.builder().userId(target).role("FOLLOW_TARGET").build())).build());
                return true;
            }));
        } catch (DuplicateKeyException duplicate) {
            // The failed transaction is already rolled back. Only both existing sides mean normal redelivery.
            boolean complete = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                    followingDOMapper.selectRelationForUpdate(actor, target) != null
                            && fansDOMapper.selectRelationForUpdate(target, actor) != null));
            if (!complete) throw duplicate;
            return false;
        }
    }
}
