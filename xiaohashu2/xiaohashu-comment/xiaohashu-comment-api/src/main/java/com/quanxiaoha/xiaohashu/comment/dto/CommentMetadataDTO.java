package com.quanxiaoha.xiaohashu.comment.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentMetadataDTO {
    private Long id;
    private Long noteId;
    private Long creatorId;
    private Long parentId;
    private Long replyCommentId;
    private Long replyUserId;
    private Integer level;
}
