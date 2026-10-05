package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答中引用到的笔记
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteRefVO {

    /**
     * 笔记 ID（字符串，避免前端 Long 精度丢失）
     */
    private String noteId;

    private String title;

    private String topicName;

    /**
     * 作者 ID（字符串）
     */
    private String creatorId;

    /**
     * 向量相似度（0~1）
     */
    private Double score;

    /**
     * 大模型判优得分（0~100）
     */
    private Integer judgeScore;

    /**
     * 判优理由
     */
    private String reason;
}