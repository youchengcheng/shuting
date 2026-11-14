package com.quanxiaoha.xiaohashu.note.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteMetadataDTO {
    private Long id;
    private Long creatorId;
    private Integer status;
    private Integer visible;
    private Boolean accessible;
}
