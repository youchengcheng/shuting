package com.quanxiaoha.xiaohashu.note.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteMetadataDTO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 发布者用户 ID
     */
    private Long creatorId;

    /**
     * 笔记状态
     */
    private Integer status;

    /**
     * 笔记可见性
     */
    private Integer visible;

    /**
     * 当前用户是否可访问
     */
    private Boolean accessible;

}
