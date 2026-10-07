package com.quanxiaoha.xiaohashu.ai.biz.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.AssistantAnswerDTO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.AssistantSearchReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.AssistantSearchRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.IndexRebuildRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.service.AssistantService;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteIndexService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiExceptionUtils;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 笔记检索助手
 *
 * <p>核心链路：用户提问 → 向量召回全站公开笔记 → 大模型判优 → 回 Cassandra 取全文 → 润色后返回。</p>
 */
@RestController
@RequestMapping("/ai/assistant")
@Slf4j
public class NoteAssistantController {

    @Resource
    private AssistantService assistantService;

    @Resource
    private NoteIndexService noteIndexService;

    /**
     * 检索并润色（同步返回）
     */
    @PostMapping("/search")
    @ApiOperationLog(description = "AI 检索笔记")
    public Response<AssistantSearchRspVO> search(@Validated @RequestBody AssistantSearchReqVO reqVO) {
        AssistantAnswerDTO answer = assistantService.answer(reqVO.getQuery(), null);
        return Response.success(AssistantSearchRspVO.builder()
                .answer(answer.getAnswer())
                .notes(answer.getNotes())
                .fromNotes(answer.getFromNotes())
                .build());
    }

    /**
     * 检索并润色（SSE 流式返回润色过程）
     */
    @PostMapping(value = "/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> searchStream(@RequestBody AssistantSearchReqVO reqVO) {
        try {
            if (reqVO == null || AiStringUtils.isBlank(reqVO.getQuery())) {
                return Flux.just("[出错了] 提问内容不能为空");
            }
            return assistantService.answerStream(reqVO.getQuery(), null)
                    .onErrorResume(e -> Flux.just("[出错了] " + AiExceptionUtils.resolveMessage(e)));
        } catch (Exception e) {
            log.warn("## AI 检索流式接口异常", e);
            return Flux.just("[出错了] " + AiExceptionUtils.resolveMessage(e));
        }
    }

    /**
     * 只返回相关笔记（不润色），用于「相关推荐」
     */
    @PostMapping("/related")
    @ApiOperationLog(description = "AI 检索相关笔记")
    public Response<AssistantSearchRspVO> related(@Validated @RequestBody AssistantSearchReqVO reqVO) {
        AssistantAnswerDTO answer = assistantService.retrieve(reqVO.getQuery());
        return Response.success(AssistantSearchRspVO.builder()
                .notes(answer.getNotes())
                .fromNotes(answer.getFromNotes())
                .build());
    }

    /**
     * 索引统计
     */
    @GetMapping("/index/stats")
    public Response<Map<String, Object>> indexStats() {
        Map<String, Object> stats = new LinkedHashMap<>(4);
        stats.put("indexedNotes", noteIndexService.countIndexed());
        stats.put("vectors", noteIndexService.countVectors());
        return Response.success(stats);
    }

    /**
     * 全量重建索引（兜底手段，异步接口返回耗时可能较长）
     */
    @PostMapping("/index/rebuild")
    @ApiOperationLog(description = "重建 AI 笔记索引")
    public Response<IndexRebuildRspVO> rebuildIndex() {
        return Response.success(noteIndexService.rebuildAll());
    }
}
