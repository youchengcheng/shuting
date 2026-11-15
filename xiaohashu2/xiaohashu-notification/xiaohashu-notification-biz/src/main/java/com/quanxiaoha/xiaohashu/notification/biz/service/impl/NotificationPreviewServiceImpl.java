package com.quanxiaoha.xiaohashu.notification.biz.service.impl;

import com.quanxiaoha.xiaohashu.comment.dto.CommentPreviewDTO;
import com.quanxiaoha.xiaohashu.note.dto.NotePreviewDTO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationDO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.FindNotificationItemRspVO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.NotificationActorRspVO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.NotificationPreviewRspVO;
import com.quanxiaoha.xiaohashu.notification.biz.rpc.CommentRpcService;
import com.quanxiaoha.xiaohashu.notification.biz.rpc.NoteRpcService;
import com.quanxiaoha.xiaohashu.notification.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationPreviewService;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
 * 通知预览查询服务实现
 * */
@Service
public class NotificationPreviewServiceImpl implements NotificationPreviewService {

    @Resource
    private UserRpcService userRpcService;

    @Resource
    private NoteRpcService noteRpcService;

    @Resource
    private CommentRpcService commentRpcService;

    /*
     * 查询用户资料、笔记和评论预览，组装通知列表
     * */
    @Override
    public List<FindNotificationItemRspVO> render(long viewerId, List<NotificationDO> records, NotificationUserStateDO state) {
        if (records.isEmpty()) {
            return List.of();
        }

        // 按用户接口的批次限制查询通知发起者资料
        Map<Long, FindUserByIdRspDTO> profiles = new HashMap<>();
        List<Long> actorIds = records.stream().map(NotificationDO::getActorId).distinct().toList();
        for (int start = 0; start < actorIds.size(); start += 10) {
            try {
                profiles.putAll(userRpcService.findByIds(actorIds.subList(start, Math.min(start + 10, actorIds.size()))));
            } catch (RuntimeException ignored) {
                // 用户资料查询失败时使用默认昵称和头像
            }
        }

        // 批量查询笔记预览，由笔记服务判断当前用户是否可访问
        Map<Long, NotePreviewDTO> notePreviews = new HashMap<>();
        Map<Long, CommentPreviewDTO> commentPreviews = new HashMap<>();
        boolean noteLookupFailed = false;
        boolean commentLookupFailed = false;
        List<Long> noteIds = records.stream().map(NotificationDO::getNoteId).filter(Objects::nonNull).distinct().toList();
        if (!noteIds.isEmpty()) {
            try {
                notePreviews.putAll(noteRpcService.findPreviews(viewerId, noteIds));
            } catch (RuntimeException ex) {
                noteLookupFailed = true;
            }
        }

        // 仅查询当前用户可访问笔记下的评论预览
        List<Long> commentIds = records.stream().filter(record -> {
            NotePreviewDTO note = notePreviews.get(record.getNoteId());
            return note != null && Boolean.TRUE.equals(note.getAccessible());
        }).map(NotificationDO::getCommentId).filter(Objects::nonNull).distinct().toList();
        if (!commentIds.isEmpty()) {
            try {
                commentPreviews.putAll(commentRpcService.findPreviews(viewerId, commentIds));
            } catch (RuntimeException ex) {
                commentLookupFailed = true;
            }
        }

        // 组装通知发起者、内容状态和预览信息
        List<FindNotificationItemRspVO> items = new ArrayList<>();
        for (NotificationDO record : records) {
            FindUserByIdRspDTO profile = profiles.get(record.getActorId());
            NotificationActorRspVO actor = new NotificationActorRspVO(Long.toString(record.getActorId()),
                    profile == null ? "用户" : profile.getNickName(), profile == null ? "" : profile.getAvatar());
            String contentStatus = "AVAILABLE";
            NotificationPreviewRspVO preview = new NotificationPreviewRspVO("", "", "", "");
            if (!"FOLLOW".equals(record.getType())) {
                NotePreviewDTO note = notePreviews.get(record.getNoteId());
                if (noteLookupFailed) {
                    contentStatus = "TEMPORARY_UNAVAILABLE";
                } else if (note == null || !Boolean.TRUE.equals(note.getAccessible())) {
                    contentStatus = "UNAVAILABLE";
                } else if (record.getCommentId() != null) {
                    CommentPreviewDTO comment = commentPreviews.get(record.getCommentId());
                    if (commentLookupFailed) {
                        contentStatus = "TEMPORARY_UNAVAILABLE";
                    } else if (comment == null || !Boolean.TRUE.equals(comment.getAccessible())
                            || !Objects.equals(comment.getNoteId(), record.getNoteId())) {
                        contentStatus = "UNAVAILABLE";
                    } else {
                        preview = new NotificationPreviewRspVO(text(note.getTitle()), text(note.getCover()),
                                abbreviate(comment.getContent()), text(comment.getImageUrl()));
                    }
                } else {
                    preview = new NotificationPreviewRspVO(text(note.getTitle()), text(note.getCover()), "", "");
                }
            }

            // 无权访问或查询失败时清空内容预览，避免展示已经失效的标题和封面
            if (!"AVAILABLE".equals(contentStatus)) {
                preview = new NotificationPreviewRspVO("", "", "", "");
            }
            items.add(new FindNotificationItemRspVO(Long.toString(record.getId()), Long.toString(record.getSeq()),
                    record.getType(), record.getCategory(), actor, record.getRecipientRole(), id(record.getNoteId()),
                    id(record.getCommentId()), id(record.getReplyCommentId()), record.getOccurredAt().toString(),
                    state.isRead(record), contentStatus, preview));
        }
        return items;
    }

    /*
     * 将 ID 转为字符串，空 ID 保持为空
     * */
    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    /*
     * 将空文本转为空字符串
     * */
    private static String text(String value) {
        return value == null ? "" : value;
    }

    /*
     * 截取评论预览，按字符码点限制长度
     * */
    private static String abbreviate(String value) {
        String text = text(value);
        return text.codePointCount(0, text.length()) <= 160 ? text : text.substring(0, text.offsetByCodePoints(0, 160));
    }
}
