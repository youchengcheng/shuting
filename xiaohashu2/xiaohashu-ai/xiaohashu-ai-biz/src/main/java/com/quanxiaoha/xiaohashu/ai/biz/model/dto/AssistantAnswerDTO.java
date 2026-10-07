package com.quanxiaoha.xiaohashu.ai.biz.model.dto;

import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteRefVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 助手一次完整回答
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantAnswerDTO {

    /**
     * 润色后的回答
     */
    private String answer;

    /**
     * 引用到的笔记
     */
    private List<NoteRefVO> notes;

    /**
     * 是否基于站内笔记回答：false 表示站内没有检索到相关笔记，走了 AI 直答兜底
     */
    private Boolean fromNotes;
}