package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单篇笔记 AI 解读请求
 */
@Data
public class NoteAnalysisReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;

    /**
     * 是否强制重新解读（忽略缓存）
     */
    private Boolean forceRefresh;
}