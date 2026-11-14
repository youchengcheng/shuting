package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import lombok.*;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentContextRspVO {
    private String mode;
    private Long noteId;
    private Long targetCommentId;
    private Long rootCommentId;
    private Integer rootPageNo;
    private Integer childPageNo;
    private Integer rootPageSize;
    private Integer childPageSize;
    private String unavailableReason;
    private CommentContextItemVO root;
    private CommentContextItemVO target;
    private CommentContextItemVO repliedTo;
}
