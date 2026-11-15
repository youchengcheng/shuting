package com.quanxiaoha.xiaohashu.note.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记预览信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotePreviewDTO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 笔记封面
     */
    private String cover;

    /**
     * 当前用户是否可访问
     */
    private Boolean accessible;

}
