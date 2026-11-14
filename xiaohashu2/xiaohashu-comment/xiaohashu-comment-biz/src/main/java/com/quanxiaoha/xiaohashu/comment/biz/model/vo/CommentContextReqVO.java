package com.quanxiaoha.xiaohashu.comment.biz.model.vo;

import lombok.*;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentContextReqVO {
    @NotNull @Positive
    private Long noteId;
    @NotNull @Positive
    private Long commentId;
}
