package com.quanxiaoha.xiaohashu.note.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotePreviewDTO {
    private Long id;
    private String title;
    private String cover;
    private Boolean accessible;
}
