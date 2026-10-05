package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单篇笔记 AI 解读结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteAnalysisRspVO {

    /**
     * 笔记 ID（字符串，避免前端精度丢失）
     */
    private String noteId;

    private String title;

    private String topicName;

    /**
     * 作者 ID（字符串）
     */
    private String creatorId;

    /**
     * 解读结果（Markdown）
     */
    private String result;

    /**
     * 是否命中缓存
     */
    private Boolean fromCache;
}