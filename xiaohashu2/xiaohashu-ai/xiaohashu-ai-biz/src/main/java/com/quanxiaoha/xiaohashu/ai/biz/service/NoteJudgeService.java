package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;

import java.util.List;

/**
 * 笔记判优服务
 *
 * <p>向量召回只能保证「语义相近」，是否真的满足用户需求，交给大模型打分判断。</p>
 */
public interface NoteJudgeService {

    /**
     * 对候选笔记逐篇打分，返回达标且排序后的结果
     *
     * @param query      用户需求
     * @param candidates 候选笔记
     */
    List<NoteCandidateDTO> judge(String query, List<NoteCandidateDTO> candidates);
}
