package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.xiaohashu.comment.biz.model.bo.CommentBO;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.PublishCommentMqDTO;

import java.util.List;
import java.util.Map;

/*
 * 评论及通知事件持久化服务
 * */
public interface CommentNotificationPersistenceService {

    /*
     * 在同一事务中保存业务数据和通知事件
     * */
    int persist(List<CommentBO> comments, Map<Long, PublishCommentMqDTO> sources);

}
