package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 笔记润色服务
 */
public interface NotePolishService {

    /**
     * 基于命中笔记，生成润色后的回答
     */
    String polish(String query, List<NoteCandidateDTO> notes);

    /**
     * 流式版本，边生成边返回
     */
    Flux<String> polishStream(String query, List<NoteCandidateDTO> notes);

    /**
     * 兜底直答：没有检索到相关笔记时，直接以 AI 助手身份回答
     */
    String answerDirectly(String query);

    /**
     * 兜底直答（流式）
     */
    Flux<String> answerDirectlyStream(String query);
}