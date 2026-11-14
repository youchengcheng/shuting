package com.quanxiaoha.xiaohashu.comment.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentBatchQueryReqDTO {
    private List<Long> ids;
    private Long viewerId;
}
