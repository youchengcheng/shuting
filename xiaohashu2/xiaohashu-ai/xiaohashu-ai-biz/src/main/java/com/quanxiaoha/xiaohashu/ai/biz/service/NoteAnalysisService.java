package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteAnalysisReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteAnalysisRspVO;

/**
 * 单篇笔记 AI 解读服务
 */
public interface NoteAnalysisService {

    /**
     * 解读一篇笔记（同样内容会命中缓存）
     *
     * @param userId 当前登录用户，用于判断私密笔记的可见性 + 缓存隔离
     */
    NoteAnalysisRspVO analyze(Long userId, NoteAnalysisReqVO reqVO);
}