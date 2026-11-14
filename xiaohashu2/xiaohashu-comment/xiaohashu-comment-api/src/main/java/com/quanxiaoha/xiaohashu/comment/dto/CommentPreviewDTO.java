package com.quanxiaoha.xiaohashu.comment.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentPreviewDTO {
    private Long id;
    private Long noteId;
    private Long parentId;
    private Long replyCommentId;
    private Long creatorId;
    private String content;
    private String imageUrl;
    private Boolean accessible;
}
