package com.quanxiaoha.xiaohashu.note.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteBatchQueryReqDTO {
    private List<Long> ids;
    private Long viewerId;
}
