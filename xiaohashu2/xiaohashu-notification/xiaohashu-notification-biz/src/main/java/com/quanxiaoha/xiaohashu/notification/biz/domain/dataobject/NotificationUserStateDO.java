package com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject;

import com.quanxiaoha.xiaohashu.notification.biz.enums.NotificationCategoryEnum;

import lombok.Data;

/**
 * 用户通知状态
 */
@Data
public class NotificationUserStateDO {

    /**
     * 接收者用户 ID
     */
    private Long recipientId;

    /**
     * 最新通知序号
     */
    private long lastSeq;

    /**
     * 通知状态版本
     */
    private long revision;

    /**
     * 点赞收藏已读水位
     */
    private long likeCollectionReadSeq;

    /**
     * 评论回复已读水位
     */
    private long commentReplyReadSeq;

    /**
     * 关注已读水位
     */
    private long followReadSeq;


    /*
     * 获取指定分类的全部已读水位
     * */
    public long watermark(NotificationCategoryEnum category) {
        return switch (category) {
            case LIKE_COLLECTION -> likeCollectionReadSeq;
            case COMMENT_REPLY -> commentReplyReadSeq;
            case FOLLOW -> followReadSeq;
        };
    }

    /*
     * 根据单条已读时间和分类水位判断通知是否已读
     * */
    public boolean isRead(NotificationDO record) {
        return record.getReadAt() != null || record.getSeq() <= watermark(NotificationCategoryEnum.valueOf(record.getCategory()));
    }

    /*
     * 初始化用户通知状态
     * */
    public static NotificationUserStateDO empty(Long recipientId) {
        NotificationUserStateDO state = new NotificationUserStateDO();
        state.setRecipientId(recipientId);
        return state;
    }
}
