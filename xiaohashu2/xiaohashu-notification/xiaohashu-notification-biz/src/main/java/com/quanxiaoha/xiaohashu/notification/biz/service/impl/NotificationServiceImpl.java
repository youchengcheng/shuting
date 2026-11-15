package com.quanxiaoha.xiaohashu.notification.biz.service.impl;

import com.quanxiaoha.xiaohashu.notification.api.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.*;
import com.quanxiaoha.xiaohashu.notification.biz.domain.mapper.NotificationDOMapper;
import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationCursorUtils;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationEventUtils;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationParamUtils;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.*;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/*
 * 通知服务实现
 * */
@Service
public class NotificationServiceImpl implements NotificationService {

    // 通知记录及用户状态持久化
    @Resource
    private NotificationDOMapper notificationDOMapper;
    // 通知启用时间及开关配置
    @Resource
    private NotificationSettings notificationSettings;
    // 事务提交后的状态变化发布
    @Resource
    private NotificationChangePublisher notificationChangePublisher;
    // 预览组装与访问权限处理
    @Resource
    private NotificationPreviewService notificationPreviewService;
    // 来源数据库事务管理器
    @Resource
    private PlatformTransactionManager transactionManager;

    // 通知写事务
    private TransactionTemplate writeTransactionTemplate;
    // 快照读事务
    private TransactionTemplate readTransactionTemplate;

    /*
     * 初始化通知读写事务，分页查询使用可重复读隔离级别
     * */
    @PostConstruct
    public void initTransactions() {
        // 字段注入完成后创建事务模板，不在构造器中读取尚未注入的依赖
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        // 同一快照中的状态、未读数和分页记录使用一致的可重复读视图
        this.readTransactionTemplate.setReadOnly(true);
        this.readTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /*
     * 消费互动事件，生成接收者通知
     * */
    @Override
    public void consume(NotificationEvent event) {
        // 校验互动时间，过滤启用前的事件
        if (event == null || event.getOccurredAt() == null) {
            throw new IllegalArgumentException("缺少互动发生时间");
        }
        if (event.getOccurredAt().isBefore(notificationSettings.cutoff())) {
            return;
        }
        NotificationEventUtils.validateEvent(event);

        // 接收者去重，排除用户自己的操作
        // 按接收者 ID 排序；同一人兼具作者与被回复人身份时保留直接回复角色
        Map<Long, String> recipients = new TreeMap<>();
        for (NotificationRecipient recipient : event.getRecipients()) {
            if (!recipient.getUserId().equals(event.getActorId())) {
                recipients.merge(recipient.getUserId(), recipient.getRole(),
                        (oldRole, newRole) -> "REPLY_TARGET".equals(newRole) ? newRole : oldRole);
            }
        }
        // 按接收者分别提交事务，MQ 重试时只补齐尚未生成的通知
        for (Map.Entry<Long, String> recipient : recipients.entrySet()) {
            NotificationUserStateDO changed = writeTransactionTemplate.execute(status -> {
                // 初始化状态行时直接取得排他锁，避免并发重复通知分配相同序号
                notificationDOMapper.ensureState(recipient.getKey());
                NotificationUserStateDO state = notificationDOMapper.lockState(recipient.getKey());
                // 同一业务键已经生成通知时直接返回
                if (notificationDOMapper.byBusinessKey(recipient.getKey(), event.getBusinessKey()) != null) {
                    return null;
                }

                // 构建通知记录，更新用户通知序号和状态版本
                NotificationDO record = new NotificationDO();
                record.setBusinessKey(event.getBusinessKey());
                record.setRecipientId(recipient.getKey());
                record.setActorId(event.getActorId());
                record.setType(event.getType());
                record.setCategory(NotificationCategoryEnum.forType(event.getType()).name());
                record.setRecipientRole(recipient.getValue());
                record.setNoteId(event.getNoteId());
                record.setCommentId(event.getCommentId());
                record.setReplyCommentId(event.getReplyCommentId());
                record.setOccurredAt(event.getOccurredAt());
                // 在锁定的用户状态基础上分配序号，插入通知与推进版本共同提交
                record.setSeq(Math.incrementExact(state.getLastSeq()));
                notificationDOMapper.insert(record);
                notificationDOMapper.advance(recipient.getKey(), record.getSeq());
                state.setLastSeq(record.getSeq());
                state.setRevision(Math.incrementExact(state.getRevision()));
                return state;
            });
            // 数据库提交后才广播变化；回滚或重复事件不发送提示
            if (changed != null) {
                notificationChangePublisher.publish(changed);
            }
        }
    }

    /*
     * 根据分类和快照分页查询通知
     * */
    @Override
    public FindNotificationPageRspVO list(long userId, String categoryValue, String cursor, String snapshotValue, Integer pageSize) {
        // 校验通知分类、分页大小和快照参数
        NotificationCategoryEnum category = NotificationParamUtils.parseCategory(categoryValue);
        int size = pageSize == null ? 20 : pageSize;
        if (size < 1 || size > 50) {
            throw new BizException(ResponseCodeEnum.INVALID_PAGE_SIZE);
        }
        if (cursor != null && snapshotValue == null) {
            throw new BizException(ResponseCodeEnum.CURSOR_SNAPSHOT_REQUIRED);
        }

        // 在同一读事务中查询用户状态和通知列表
        NotificationSnapshotPageDTO result = readTransactionTemplate.execute(status -> {
            NotificationUserStateDO state = state(userId);
            long snapshot = snapshotValue == null ? state.getLastSeq() : NotificationParamUtils.parseDecimal(snapshotValue, false);
            if (snapshot > state.getLastSeq()) {
                throw new BizException(ResponseCodeEnum.SNAPSHOT_OUT_OF_RANGE);
            }
            // 零表示首屏没有上一页的序号限制
            long before = 0;
            if (cursor != null) {
                // 游标必须与当前账号、分类和快照一致，再读取下一页起始位置
                before = NotificationCursorUtils.decode(cursor, userId, category, snapshot).getBeforeSeq();
            }
            // 多查一条判断是否还有下一页，返回结果仍限制为用户要求的页数
            List<NotificationDO> records = notificationDOMapper.page(userId, category.name(), snapshot, before, size + 1);
            boolean more = records.size() > size;
            List<NotificationDO> page = new ArrayList<>(records.subList(0, Math.min(size, records.size())));
            String next = more ? NotificationCursorUtils.encode(new NotificationCursorDTO(userId, category, snapshot, page.get(page.size() - 1).getSeq())) : null;
            return new NotificationSnapshotPageDTO(page, state, snapshot, next, more);
        });
        return new FindNotificationPageRspVO(notificationPreviewService.render(userId, result.getRecords(), result.getState()), result.getCursor(),
                result.isMore(), Long.toString(result.getSnapshot()), Long.toString(result.getState().getRevision()));
    }

    /*
     * 查询通知未读数量和状态版本
     * */
    @Override
    public FindNotificationSummaryRspVO summary(long userId) {
        return readTransactionTemplate.execute(status -> summaryOf(state(userId)));
    }

    /*
     * 批量查询通知已读状态
     * */
    @Override
    public FindNotificationReadStateRspVO readState(long userId, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw new BizException(ResponseCodeEnum.INVALID_READ_STATE_IDS);
        }
        // 将大整数 ID 解析为 Long 并去重，再按当前用户过滤查询
        List<Long> parsed = ids.stream().map(id -> NotificationParamUtils.parseDecimal(id, true)).distinct().toList();
        return readTransactionTemplate.execute(status -> {
            NotificationUserStateDO state = state(userId);
            List<FindNotificationReadItemRspVO> items = notificationDOMapper.byIds(userId, parsed).stream()
                    .map(row -> new FindNotificationReadItemRspVO(Long.toString(row.getId()), state.isRead(row))).toList();
            return new FindNotificationReadStateRspVO(items, Long.toString(state.getRevision()), watermarks(state));
        });
    }

    /*
     * 将单条通知标为已读
     * */
    @Override
    public ReadNotificationRspVO markRead(long userId, String idValue) {
        long id = NotificationParamUtils.parseDecimal(idValue, true);
        ReadNotificationRspVO result = writeTransactionTemplate.execute(status -> {
            notificationDOMapper.ensureState(userId);
            NotificationUserStateDO state = notificationDOMapper.lockState(userId);
            NotificationDO record = notificationDOMapper.byId(userId, id);
            if (record == null) {
                throw new BizException(ResponseCodeEnum.NOTIFICATION_NOT_FOUND);
            }
            // 已读状态同时考虑单条标记和分类水位，重复标记不再递增版本
            boolean changed = !state.isRead(record);
            if (changed) {
                notificationDOMapper.markRead(userId, id);
                notificationDOMapper.bumpRevision(userId);
                state.setRevision(Math.incrementExact(state.getRevision()));
            }
            FindNotificationSummaryRspVO summary = summaryOf(state);
            return new ReadNotificationRspVO(changed, summary.getRevision(), summary, Long.toString(id), true);
        });
        // 只有状态实际改变才推送版本，减少重复已读请求产生的无效广播
        if (result.isChanged()) {
            notificationChangePublisher.publish(versionState(userId, result.getSummary()));
        }
        return result;
    }

    /*
     * 将分类快照内的通知全部标为已读
     * */
    @Override
    public ReadAllNotificationRspVO markAllRead(long userId, String categoryValue, String snapshotValue) {
        NotificationCategoryEnum category = NotificationParamUtils.parseCategory(categoryValue);
        long snapshot = NotificationParamUtils.parseDecimal(snapshotValue, false);
        ReadAllNotificationRspVO result = writeTransactionTemplate.execute(status -> {
            notificationDOMapper.ensureState(userId);
            NotificationUserStateDO state = notificationDOMapper.lockState(userId);
            if (snapshot > state.getLastSeq()) {
                throw new BizException(ResponseCodeEnum.SNAPSHOT_OUT_OF_RANGE);
            }
            // 水位只推进到客户端已加载的快照，快照之后的新通知仍保持未读
            boolean changed = snapshot > state.watermark(category);
            if (changed) {
                notificationDOMapper.setWatermark(userId, category.name(), snapshot);
                switch (category) {
                    case LIKE_COLLECTION -> state.setLikeCollectionReadSeq(snapshot);
                    case COMMENT_REPLY -> state.setCommentReplyReadSeq(snapshot);
                    case FOLLOW -> state.setFollowReadSeq(snapshot);
                }
                state.setRevision(Math.incrementExact(state.getRevision()));
            }
            FindNotificationSummaryRspVO summary = summaryOf(state);
            return new ReadAllNotificationRspVO(changed, summary.getRevision(), summary, category.name(), Long.toString(snapshot));
        });
        // 只有状态实际改变才推送版本，减少重复已读请求产生的无效广播
        if (result.isChanged()) {
            notificationChangePublisher.publish(versionState(userId, result.getSummary()));
        }
        return result;
    }

    /*
     * 保存无法处理的通知事件
     * */
    @Override
    public void isolate(String source, String identity, String payload, String reason) {
        // 隔离失败时抛出异常交由 MQ 重试，隔离成功后才确认消费
        notificationDOMapper.isolate(source, identity, payload, reason == null ? "非法通知事件" : reason.substring(0, Math.min(1000, reason.length())));
    }

    /*
     * 查询用户通知状态，不存在时初始化空状态
     * */
    private NotificationUserStateDO state(long userId) {
        NotificationUserStateDO state = notificationDOMapper.state(userId);
        return state == null ? NotificationUserStateDO.empty(userId) : state;
    }

    /*
     * 汇总分类未读数量和已读水位
     * */
    private FindNotificationSummaryRspVO summaryOf(NotificationUserStateDO state) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (NotificationCategoryEnum category : NotificationCategoryEnum.values()) {
            // 分类水位之前的通知视为已读，只统计水位之后且未单独标记的记录
            long count = notificationDOMapper.unread(state.getRecipientId(), category.name(), state.watermark(category));
            counts.put(category.name(), count);
            total = Math.addExact(total, count);
        }
        return new FindNotificationSummaryRspVO(counts, total, Long.toString(state.getLastSeq()), Long.toString(state.getRevision()), watermarks(state));
    }

    /*
     * 将各分类已读水位转为字符串
     * */
    private static Map<String, String> watermarks(NotificationUserStateDO state) {
        Map<String, String> values = new LinkedHashMap<>();
        for (NotificationCategoryEnum category : NotificationCategoryEnum.values()) {
            values.put(category.name(), Long.toString(state.watermark(category)));
        }
        return values;
    }

    /*
     * 构建通知状态变更提示所需的版本信息
     * */
    private static NotificationUserStateDO versionState(long userId, FindNotificationSummaryRspVO summary) {
        NotificationUserStateDO state = NotificationUserStateDO.empty(userId);
        state.setLastSeq(Long.parseLong(summary.getLastSeq()));
        state.setRevision(Long.parseLong(summary.getRevision()));
        return state;
    }

}
