package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopNoteReqVO {

    @NotNull(message = "笔记id不能为空")
    private Long id;

    @NotNull(message = "置顶状态不能为空")
    private Boolean isTop;
}
