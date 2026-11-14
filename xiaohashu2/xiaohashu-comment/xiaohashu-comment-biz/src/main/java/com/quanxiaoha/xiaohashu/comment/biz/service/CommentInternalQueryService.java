package com.quanxiaoha.xiaohashu.comment.biz.service;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.constant.DateConstants;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.*;
import com.quanxiaoha.xiaohashu.comment.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.*;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindCommentContentRspDTO;
import com.quanxiaoha.xiaohashu.note.dto.NoteMetadataDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentInternalQueryService {
    private static final int ROOT_PAGE_SIZE = 10;
    private static final int CHILD_PAGE_SIZE = 6;
    @Resource private CommentDOMapper commentDOMapper;
    @Resource private CommentLikeDOMapper commentLikeDOMapper;
    @Resource private CommentReplyValidator replyValidator;
    @Resource private NoteAccessService noteAccessService;
    @Resource private KeyValueRpcService keyValueRpcService;
    @Resource private UserRpcService userRpcService;
    @Resource private RedisTemplate<String, Object> redisTemplate;

    public List<CommentMetadataDTO> metadata(CommentBatchQueryReqDTO request) {
        return find(request).stream().map(c -> CommentMetadataDTO.builder().id(c.getId()).noteId(c.getNoteId())
                .creatorId(c.getUserId()).parentId(c.getParentId()).replyCommentId(c.getReplyCommentId())
                .replyUserId(c.getReplyUserId()).level(c.getLevel()).build()).toList();
    }

    public List<CommentPreviewDTO> previews(CommentBatchQueryReqDTO request) {
        List<CommentDO> comments = find(request);
        if (comments.isEmpty()) return Collections.emptyList();
        Map<Long, NoteMetadataDTO> notes = noteAccessService.metadata(comments.stream().map(CommentDO::getNoteId).distinct().toList(), request.getViewerId());
        List<CommentDO> readable = comments.stream().filter(c -> notes.containsKey(c.getNoteId())
                && Boolean.TRUE.equals(notes.get(c.getNoteId()).getAccessible())).toList();
        Map<Long, String> contents = contents(readable);
        return comments.stream().map(c -> {
            boolean accessible = notes.containsKey(c.getNoteId()) && Boolean.TRUE.equals(notes.get(c.getNoteId()).getAccessible());
            return CommentPreviewDTO.builder().id(c.getId()).noteId(c.getNoteId()).creatorId(c.getUserId())
                    .parentId(c.getParentId()).replyCommentId(c.getReplyCommentId()).accessible(accessible)
                    .content(accessible ? contents.get(c.getId()) : null).imageUrl(accessible ? c.getImageUrl() : null).build();
        }).toList();
    }

    public CommentContextRspVO context(CommentContextReqVO request) {
        Long viewer = LoginUserContextHolder.getUserId();
        noteAccessService.requireAccessible(request.getNoteId(), viewer);
        CommentContextRspVO result = CommentContextRspVO.builder().mode("RELATED").noteId(request.getNoteId())
                .targetCommentId(request.getCommentId()).rootPageSize(ROOT_PAGE_SIZE).childPageSize(CHILD_PAGE_SIZE).build();
        CommentDO target = commentDOMapper.selectByPrimaryKey(request.getCommentId());
        if (target == null) { result.setUnavailableReason("COMMENT_UNAVAILABLE"); return result; }
        CommentDO root;
        try {
            root = replyValidator.requireRoot(request.getNoteId(), target);
        } catch (BizException error) {
            if (Objects.equals(target.getNoteId(), request.getNoteId()) && Objects.equals(target.getLevel(), 2)
                    && target.getParentId() != null && commentDOMapper.selectByPrimaryKey(target.getParentId()) == null) {
                result.setUnavailableReason("COMMENT_UNAVAILABLE");
                return result;
            }
            throw error;
        }
        result.setRootCommentId(root.getId());
        result.setRootPageNo(pageNo(rootPosition(request.getNoteId(), root.getId()), ROOT_PAGE_SIZE));
        if (!Objects.equals(root.getId(), target.getId()))
            result.setChildPageNo(pageNo(childPosition(root.getId(), target.getId()), CHILD_PAGE_SIZE));
        CommentDO repliedTo = null;
        if (target.getReplyCommentId() != null && target.getReplyCommentId() > 0
                && !Objects.equals(target.getReplyCommentId(), root.getId())) {
            CommentDO reply = commentDOMapper.selectByPrimaryKey(target.getReplyCommentId());
            if (reply != null) {
                replyValidator.requireRoot(request.getNoteId(), reply);
                if (!Objects.equals(reply.getNoteId(), request.getNoteId())
                        || (!Objects.equals(reply.getId(), root.getId()) && !Objects.equals(reply.getParentId(), root.getId())))
                    throw new com.quanxiaoha.framework.common.exception.BizException(com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum.COMMENT_CANT_OPERATE);
                repliedTo = reply;
            }
        }
        Map<Long, CommentDO> unique = new LinkedHashMap<>();
        unique.put(root.getId(), root); unique.put(target.getId(), target);
        if (repliedTo != null) unique.put(repliedTo.getId(), repliedTo);
        List<CommentDO> selected = new ArrayList<>(unique.values());
        Map<Long, String> contents = contents(selected);
        List<FindUserByIdRspDTO> users = userRpcService.findByIds(selected.stream().map(CommentDO::getUserId).distinct().toList());
        Map<Long, FindUserByIdRspDTO> profiles = users == null ? Collections.emptyMap()
                : users.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, u -> u));
        result.setRoot(contextItem(root, contents, profiles, viewer));
        result.setTarget(contextItem(target, contents, profiles, viewer));
        if (repliedTo != null) result.setRepliedTo(contextItem(repliedTo, contents, profiles, viewer));
        return result;
    }

    private long rootPosition(Long noteId, Long rootId) {
        try {
            Long rank = redisTemplate.opsForZSet().reverseRank(RedisKeyConstants.buildCommentListKey(noteId), rootId);
            if (rank != null && rank < 500) return rank;
        } catch (RuntimeException ignored) { /* Cache is optional; the database remains authoritative. */ }
        return commentDOMapper.selectRootCommentPosition(noteId, rootId);
    }

    private long childPosition(Long rootId, Long targetId) {
        try {
            Long rank = redisTemplate.opsForZSet().rank(RedisKeyConstants.buildChildCommentListKey(rootId), targetId);
            if (rank != null && rank < 60) return rank;
        } catch (RuntimeException ignored) { /* Match the normal pagination database fallback. */ }
        return commentDOMapper.selectChildCommentPosition(rootId, targetId);
    }

    private int pageNo(long position, int pageSize) {
        return Math.toIntExact(position / pageSize + 1);
    }

    private CommentContextItemVO contextItem(CommentDO comment, Map<Long, String> contents,
                                            Map<Long, FindUserByIdRspDTO> profiles, Long viewer) {
        FindUserByIdRspDTO user = profiles.get(comment.getUserId());
        return CommentContextItemVO.builder().commentId(comment.getId()).noteId(comment.getNoteId()).userId(comment.getUserId())
                .parentId(comment.getParentId()).replyCommentId(comment.getReplyCommentId()).replyUserId(comment.getReplyUserId()).level(comment.getLevel())
                .avatar(user == null ? null : user.getAvatar()).nickname(user == null ? null : user.getNickName())
                .content(contents.get(comment.getId())).imageUrl(comment.getImageUrl()).createTime(comment.getCreateTime())
                .likeTotal(comment.getLikeTotal() == null ? 0L : comment.getLikeTotal())
                .childCommentTotal(comment.getChildCommentTotal() == null ? 0L : comment.getChildCommentTotal())
                .liked(viewer != null && commentLikeDOMapper.selectCountByUserIdAndCommentId(viewer, comment.getId()) > 0).build();
    }

    private List<CommentDO> find(CommentBatchQueryReqDTO request) {
        if (request == null || request.getIds() == null || request.getIds().size() > 100)
            throw new IllegalArgumentException("Provide at most 100 comment IDs");
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        return ids.isEmpty() ? Collections.emptyList() : commentDOMapper.selectByCommentIds(ids);
    }

    private Map<Long, String> contents(List<CommentDO> comments) {
        Map<Long, String> result = new HashMap<>();
        Map<Long, List<CommentDO>> byNote = comments.stream().filter(c -> Boolean.FALSE.equals(c.getIsContentEmpty())
                && c.getContentUuid() != null && c.getCreateTime() != null).collect(Collectors.groupingBy(CommentDO::getNoteId));
        byNote.forEach((noteId, rows) -> {
            List<FindCommentContentReqDTO> keys = rows.stream().map(c -> FindCommentContentReqDTO.builder()
                    .contentId(c.getContentUuid()).yearMonth(c.getCreateTime().format(DateConstants.DATE_FORMAT_Y_M)).build()).toList();
            List<FindCommentContentRspDTO> bodies = keyValueRpcService.batchFindCommentContent(noteId, keys);
            if (bodies != null) {
                Map<String, String> bodyById = bodies.stream().collect(Collectors.toMap(FindCommentContentRspDTO::getContentId,
                        body -> body.getContent() == null ? "" : body.getContent(), (a, b) -> a));
                rows.forEach(c -> result.put(c.getId(), bodyById.get(c.getContentUuid())));
            }
        });
        return result;
    }
}
