package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import lombok.*;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentContextItemVO {
    private Long commentId;
    private Long noteId;
    private Long userId;
    private Long parentId;
    private Long replyCommentId;
    private Long replyUserId;
    private Integer level;
    private String avatar;
    private String nickname;
    private String content;
    private String imageUrl;
    private LocalDateTime createTime;
    private Long likeTotal;
    private Long childCommentTotal;
    private Boolean liked;
}
