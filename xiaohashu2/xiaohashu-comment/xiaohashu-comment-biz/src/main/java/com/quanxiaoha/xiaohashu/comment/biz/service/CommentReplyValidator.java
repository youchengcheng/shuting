package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
public class CommentReplyValidator {
    @Resource private CommentDOMapper commentDOMapper;

    public CommentDO requireTarget(Long noteId, Long replyId) {
        CommentDO target = commentDOMapper.selectByPrimaryKey(replyId);
        if (target == null || !Objects.equals(noteId, target.getNoteId()))
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        requireRoot(noteId, target);
        return target;
    }

    public CommentDO requireRoot(Long noteId, CommentDO target) {
        if (target == null || !Objects.equals(noteId, target.getNoteId()))
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        if (Objects.equals(target.getLevel(), 1)) {
            if (!Objects.equals(target.getParentId(), noteId)) throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
            return target;
        }
        if (!Objects.equals(target.getLevel(), 2) || target.getParentId() == null)
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        CommentDO root = commentDOMapper.selectByPrimaryKey(target.getParentId());
        if (root == null || !Objects.equals(root.getLevel(), 1) || !Objects.equals(root.getNoteId(), noteId)
                || !Objects.equals(root.getParentId(), noteId))
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        return root;
    }
}
