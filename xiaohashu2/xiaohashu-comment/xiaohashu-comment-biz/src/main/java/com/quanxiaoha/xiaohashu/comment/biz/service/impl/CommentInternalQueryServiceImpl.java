package com.quanxiaoha.xiaohashu.comment.biz.service.impl;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.constant.DateConstants;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.comment.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.comment.biz.domain.dataobject.CommentDO;
import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.*;
import com.quanxiaoha.xiaohashu.comment.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.comment.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.comment.biz.rpc.*;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentInternalQueryService;
import com.quanxiaoha.xiaohashu.comment.biz.service.CommentReplyValidator;
import com.quanxiaoha.xiaohashu.comment.dto.*;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindCommentContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindCommentContentRspDTO;
import com.quanxiaoha.xiaohashu.note.dto.NoteMetadataDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/*
 * 评论内部查询服务实现
 * */
@Service
public class CommentInternalQueryServiceImpl implements CommentInternalQueryService {

    /*
     * 一级评论每页条数
     * */
    private static final int ROOT_PAGE_SIZE = 10;

    /*
     * 回复评论每页条数
     * */
    private static final int CHILD_PAGE_SIZE = 6;

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private CommentLikeStateService commentLikeStateService;
    @Resource
    private CommentReplyValidator commentReplyValidator;
    @Resource
    private NoteAccessService noteAccessService;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /*
     * 批量查询元数据
     * */
    @Override
    public List<CommentMetadataDTO> metadata(CommentBatchQueryReqDTO commentBatchQueryReqDTO) {
        // 查询评论记录并提取作者、笔记和回复归属，供通知消费判断接收者
        return find(commentBatchQueryReqDTO).stream().map(c -> CommentMetadataDTO.builder()
                .id(c.getId())
                .noteId(c.getNoteId())
                .creatorId(c.getUserId())
                .parentId(c.getParentId())
                .replyCommentId(c.getReplyCommentId())
                .replyUserId(c.getReplyUserId())
                .level(c.getLevel())
                .build()).toList();
    }

    /*
     * 批量查询预览信息
     * */
    @Override
    public List<CommentPreviewDTO> previews(CommentBatchQueryReqDTO commentBatchQueryReqDTO) {
        // 校验查询参数并批量查询评论，没有命中记录时直接返回
        List<CommentDO> comments = find(commentBatchQueryReqDTO);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查询所属笔记，过滤当前用户无权查看的评论内容
        List<Long> noteIds = comments.stream()
                .map(CommentDO::getNoteId)
                .distinct()
                .toList();
        Map<Long, NoteMetadataDTO> notes = noteAccessService.metadata(noteIds, commentBatchQueryReqDTO.getViewerId());
        // 评论内容仅查询允许访问的笔记，避免从 KV 读取私密内容
        List<CommentDO> readable = comments.stream().filter(c -> notes.containsKey(c.getNoteId())
                && Boolean.TRUE.equals(notes.get(c.getNoteId()).getAccessible())).toList();
        Map<Long, String> contents = contents(readable);
        // 不可访问的评论只返回定位元数据，正文和图片保持为空
        return comments.stream().map(c -> {
            boolean accessible = notes.containsKey(c.getNoteId()) && Boolean.TRUE.equals(notes.get(c.getNoteId()).getAccessible());
            return CommentPreviewDTO.builder()
                    .id(c.getId())
                    .noteId(c.getNoteId())
                    .creatorId(c.getUserId())
                    .parentId(c.getParentId())
                    .replyCommentId(c.getReplyCommentId())
                    .accessible(accessible)
                    .content(accessible ? contents.get(c.getId()) : null)
                    .imageUrl(accessible ? c.getImageUrl() : null)
                    .build();
        }).toList();
    }

    /*
     * 查询目标评论及相关评论
     * */
    @Override
    public CommentContextRspVO context(CommentContextReqVO commentContextReqVO) {
        // 获取当前访问者并校验笔记访问权限
        Long viewer = LoginUserContextHolder.getUserId();
        noteAccessService.requireAccessible(commentContextReqVO.getNoteId(), viewer);
        CommentContextRspVO result = CommentContextRspVO.builder()
                .mode("RELATED")
                .noteId(commentContextReqVO.getNoteId())
                .targetCommentId(commentContextReqVO.getCommentId())
                .rootPageSize(ROOT_PAGE_SIZE)
                .childPageSize(CHILD_PAGE_SIZE)
                .build();
        // 查询目标评论，评论删除时返回不可用状态
        CommentDO target = commentDOMapper.selectByPrimaryKey(commentContextReqVO.getCommentId());
        if (target == null) {
            result.setUnavailableReason("COMMENT_UNAVAILABLE");
            return result;
        }
        // 校验目标评论归属并获取一级评论
        CommentDO root;
        try {
            root = commentReplyValidator.requireRoot(commentContextReqVO.getNoteId(), target);
        } catch (BizException error) {
            if (Objects.equals(target.getNoteId(), commentContextReqVO.getNoteId()) && Objects.equals(target.getLevel(), 2)
                    && target.getParentId() != null && commentDOMapper.selectByPrimaryKey(target.getParentId()) == null) {
                result.setUnavailableReason("COMMENT_UNAVAILABLE");
                return result;
            }
            throw error;
        }
        // 按普通评论列表的排序规则计算目标所在页码
        result.setRootCommentId(root.getId());
        result.setRootPageNo(pageNo(rootPosition(commentContextReqVO.getNoteId(), root.getId()), ROOT_PAGE_SIZE));
        if (!Objects.equals(root.getId(), target.getId())) {
            result.setChildPageNo(pageNo(childPosition(root.getId(), target.getId()), CHILD_PAGE_SIZE));
        }
        // 目标为回复时补充被回复评论，同一级评论已包含时不重复查询
        CommentDO repliedTo = null;
        if (target.getReplyCommentId() != null && target.getReplyCommentId() > 0
                && !Objects.equals(target.getReplyCommentId(), root.getId())) {
            CommentDO reply = commentDOMapper.selectByPrimaryKey(target.getReplyCommentId());
            if (reply != null) {
                // 被回复评论必须属于当前笔记及同一一级评论，禁止跨评论链展示
                commentReplyValidator.requireRoot(commentContextReqVO.getNoteId(), reply);
                if (!Objects.equals(reply.getNoteId(), commentContextReqVO.getNoteId())
                        || (!Objects.equals(reply.getId(), root.getId()) && !Objects.equals(reply.getParentId(), root.getId()))) {
                    throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
                }
                repliedTo = reply;
            }
        }
        // 合并相关评论，批量查询内容和用户信息
        Map<Long, CommentDO> unique = new LinkedHashMap<>();
        unique.put(root.getId(), root);
        unique.put(target.getId(), target);
        if (repliedTo != null) {
            unique.put(repliedTo.getId(), repliedTo);
        }
        List<CommentDO> selected = new ArrayList<>(unique.values());
        Map<Long, Boolean> likeStates = commentLikeStateService.findStates(viewer,
                new ArrayList<>(unique.keySet()));
        Map<Long, String> contents = contents(selected);
        // 通过本模块 RPC 包装批量读取作者资料，缺失资料时使用空昵称和头像
        List<FindUserByIdRspDTO> users = userRpcService.findByIds(selected.stream().map(CommentDO::getUserId).distinct().toList());
        Map<Long, FindUserByIdRspDTO> profiles = users == null ? Collections.emptyMap()
                : users.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, u -> u));
        // 分别填充一级评论、目标评论和被回复评论，便于前端定位与高亮
        result.setRoot(contextItem(root, contents, profiles, likeStates));
        result.setTarget(contextItem(target, contents, profiles, likeStates));
        if (repliedTo != null) {
            result.setRepliedTo(contextItem(repliedTo, contents, profiles, likeStates));
        }
        return result;
    }

    /*
     * 查询一级评论的位置
     * */
    private long rootPosition(Long noteId, Long rootId) {
        try {
            // 普通一级评论列表按热度倒序，只采用现有缓存前 500 条的位置
            Long rank = redisTemplate.opsForZSet().reverseRank(RedisKeyConstants.buildCommentListKey(noteId), rootId);
            if (rank != null && rank < 500) {
                return rank;
            }
        } catch (RuntimeException ignored) {
            // 缓存查询失败时，回源数据库查询评论位置
        }
        return commentDOMapper.selectRootCommentPosition(noteId, rootId);
    }

    /*
     * 查询回复评论的位置
     * */
    private long childPosition(Long rootId, Long targetId) {
        try {
            // 普通回复列表按创建顺序排列，只采用现有缓存前 60 条的位置
            Long rank = redisTemplate.opsForZSet().rank(RedisKeyConstants.buildChildCommentListKey(rootId), targetId);
            if (rank != null && rank < 60) {
                return rank;
            }
        } catch (RuntimeException ignored) {
            // 与普通评论分页保持一致，缓存查询失败时回源数据库
        }
        return commentDOMapper.selectChildCommentPosition(rootId, targetId);
    }

    /*
     * 根据评论位置计算所在页码
     * */
    private int pageNo(long position, int pageSize) {
        return Math.toIntExact(position / pageSize + 1);
    }

    /*
     * 组装相关评论信息
     * */
    private CommentContextItemVO contextItem(CommentDO comment, Map<Long, String> contents,
                                            Map<Long, FindUserByIdRspDTO> profiles, Map<Long, Boolean> likeStates) {
        // 关联评论作者资料，计数为空时展示为零，并查询当前访问者是否点赞
        FindUserByIdRspDTO user = profiles.get(comment.getUserId());
        return CommentContextItemVO.builder()
                .commentId(comment.getId())
                .noteId(comment.getNoteId())
                .userId(comment.getUserId())
                .parentId(comment.getParentId())
                .replyCommentId(comment.getReplyCommentId())
                .replyUserId(comment.getReplyUserId())
                .level(comment.getLevel())
                .avatar(user == null ? null : user.getAvatar())
                .nickname(user == null ? null : user.getNickName())
                .content(contents.get(comment.getId()))
                .imageUrl(comment.getImageUrl())
                .createTime(comment.getCreateTime())
                .likeTotal(comment.getLikeTotal() == null ? 0L : comment.getLikeTotal())
                .childCommentTotal(comment.getChildCommentTotal() == null ? 0L : comment.getChildCommentTotal())
                .liked(likeStates.getOrDefault(comment.getId(), false))
                .build();
    }

    /*
     * 过滤查询 ID 并批量查询数据
     * */
    private List<CommentDO> find(CommentBatchQueryReqDTO commentBatchQueryReqDTO) {
        // 限制单次查询数量，避免通知批量查询传入过大的 ID 集合
        if (commentBatchQueryReqDTO == null || commentBatchQueryReqDTO.getIds() == null || commentBatchQueryReqDTO.getIds().size() > 100) {
            throw new IllegalArgumentException("评论查询参数缺失或 ID 数量超过 100 条");
        }
        // 删除空值、非正数和重复 ID，空集合不访问数据库
        List<Long> ids = commentBatchQueryReqDTO.getIds().stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        return ids.isEmpty() ? Collections.emptyList() : commentDOMapper.selectByCommentIds(ids);
    }

    /*
     * 批量查询评论内容
     * */
    private Map<Long, String> contents(List<CommentDO> comments) {
        // 按笔记分组，仅处理包含正文且具有内容标识和创建时间的评论
        Map<Long, String> result = new HashMap<>();
        Map<Long, List<CommentDO>> byNote = comments.stream().filter(c -> Boolean.FALSE.equals(c.getIsContentEmpty())
                && c.getContentUuid() != null && c.getCreateTime() != null).collect(Collectors.groupingBy(CommentDO::getNoteId));
        byNote.forEach((noteId, rows) -> {
            // 根据评论内容标识和创建月份构建 KV 批量查询参数
            List<FindCommentContentReqDTO> keys = rows.stream().map(c -> FindCommentContentReqDTO.builder()
                    .contentId(c.getContentUuid())
                    .yearMonth(c.getCreateTime().format(DateConstants.DATE_FORMAT_Y_M))
                    .build()).toList();
            // 通过本模块 RPC 包装读取正文，再按评论 ID 关联返回内容
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
