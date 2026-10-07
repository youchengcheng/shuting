package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 笔记检索助手 - 回答
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantSearchRspVO {

    /**
     * 润色后的回答（Markdown）
     */
    private String answer;

    /**
     * 引用到的笔记
     */
    private List<NoteRefVO> notes;

    /**
     * 会话 UUID（仅对话接口返回）
     */
    private String chatUuid;

    /**
     * 是否基于站内笔记回答：false 表示站内没有检索到相关笔记，走了 AI 直答兜底
     */
    private Boolean fromNotes;
}