package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改笔记可见性
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateNoteVisibleReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long id;

    /**
     * 可见性 (0：公开 1：仅自己可见)
     */
    @NotNull(message = "笔记可见性不能为空")
    private Integer visible;

}
