package com.quanxiaoha.xiaohashu.ai.biz.service;

import com.quanxiaoha.xiaohashu.ai.biz.model.dto.NoteCandidateDTO;

import java.util.List;

/**
 * 笔记召回服务（向量检索）
 */
public interface NoteRecallService {

    /**
     * 根据用户提问，从全站已发布笔记里召回候选笔记
     *
     * @param query     用户自然语言需求
     * @param noteLimit 聚合后最多返回的笔记数
     */
    List<NoteCandidateDTO> recall(String query, int noteLimit);
}