package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.model.dto.AssistantAnswerDTO;
import reactor.core.publisher.Flux;

/**
 * AI 笔记助手编排服务
 *
 * <p>把「召回 → 判优 → 润色」三级流水线串起来，对上层只暴露一个回答。</p>
 */
public interface AssistantService {

    /**
     * 完整回答（同步）
     *
     * @param query        用户需求
     * @param topN         最多引用几篇笔记
     * @param extraContext 额外上下文（例如多轮对话历史），可为 null
     */
    AssistantAnswerDTO answer(String query, Integer topN, String extraContext);

    /**
     * 完整回答（流式）：召回 + 判优同步完成，润色过程流式返回
     */
    Flux<String> answerStream(String query, Integer topN, String extraContext);

    /**
     * 只做召回 + 判优，返回命中笔记（供上层做二次加工）
     */
    AssistantAnswerDTO retrieve(String query, Integer topN);
}