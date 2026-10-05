package com.quanxiaoha.xiaohashu.ai.biz.controller;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteAnalysisReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteAnalysisRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.service.NoteAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单篇笔记 AI 解读
 */
@RestController
@RequestMapping("/ai/note")
@Slf4j
public class NoteAnalysisController {

    @Resource
    private NoteAnalysisService noteAnalysisService;

    /**
     * 解读一篇笔记
     */
    @PostMapping("/analyze")
    @ApiOperationLog(description = "AI 解读笔记")
    public Response<NoteAnalysisRspVO> analyze(@Validated @RequestBody NoteAnalysisReqVO reqVO) {
        return Response.success(noteAnalysisService.analyze(LoginUserContextHolder.getUserId(), reqVO));
    }
}