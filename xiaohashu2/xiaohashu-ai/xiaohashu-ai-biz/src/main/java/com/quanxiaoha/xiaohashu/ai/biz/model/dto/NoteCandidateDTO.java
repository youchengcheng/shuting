package com.quanxiaoha.xiaohashu.ai.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索候选笔记
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteCandidateDTO {

    private Long noteId;

    private String title;

    private String topicName;

    private Long creatorId;

    private String contentUuid;

    /**
     * 向量相似度得分（0~1，越大越相关）
     */
    private Double score;

    /**
     * 命中的最佳片段
     */
    private String bestChunk;

    /**
     * 该笔记所有命中的片段
     */
    private List<String> chunks;

    /**
     * 大模型判优得分（0~100）
     */
    private Integer judgeScore;

    /**
     * 大模型判优理由
     */
    private String judgeReason;

    /**
     * 笔记全文（润色阶段回填）
     */
    private String content;
}