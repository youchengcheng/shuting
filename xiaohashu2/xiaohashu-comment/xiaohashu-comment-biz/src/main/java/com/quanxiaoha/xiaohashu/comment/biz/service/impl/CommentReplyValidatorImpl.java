package com.quanxiaoha.xiaohashu.comment.biz.service.impl;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentReplyValidator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

/*
 * 评论回复归属校验实现
 * */
@Service
public class CommentReplyValidatorImpl implements CommentReplyValidator {

    @Resource
    private CommentDOMapper commentDOMapper;

    /*
     * 校验被回复评论归属
     * */
    @Override
    public CommentDO requireTarget(Long noteId, Long replyId) {
        // 查询被回复评论，禁止回复不存在或其他笔记下的评论
        CommentDO target = commentDOMapper.selectByPrimaryKey(replyId);
        if (target == null || !Objects.equals(noteId, target.getNoteId())) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }

        // 校验一级评论归属，防止错误层级进入回复链路
        requireRoot(noteId, target);
        return target;
    }

    /*
     * 校验一级评论与笔记的归属
     * */
    @Override
    public CommentDO requireRoot(Long noteId, CommentDO target) {
        // 目标评论必须存在，并且属于当前笔记
        if (target == null || !Objects.equals(noteId, target.getNoteId())) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }

        // 一级评论的父级 ID 为笔记 ID，校验通过后直接返回
        if (Objects.equals(target.getLevel(), 1)) {
            if (!Objects.equals(target.getParentId(), noteId)) {
                throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
            }
            return target;
        }

        // 回复评论必须为二级评论，并且保存了一级评论 ID
        if (!Objects.equals(target.getLevel(), 2) || target.getParentId() == null) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }

        // 查询一级评论，再次校验层级及所属笔记
        CommentDO root = commentDOMapper.selectByPrimaryKey(target.getParentId());
        if (root == null || !Objects.equals(root.getLevel(), 1) || !Objects.equals(root.getNoteId(), noteId)
                || !Objects.equals(root.getParentId(), noteId)) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }
        return root;
    }
}
