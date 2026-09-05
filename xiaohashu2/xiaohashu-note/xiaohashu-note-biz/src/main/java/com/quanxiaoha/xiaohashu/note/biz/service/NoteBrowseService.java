package com.quanxiaoha.xiaohashu.note.biz.service;

import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindChannelRspVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindDiscoverNotePageListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindNoteCardRspVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindProfileNotePageListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindTopicListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindTopicRspVO;

import java.util.List;

/**
 * 笔记浏览（发现页/个人主页/频道/话题）
 **/
public interface NoteBrowseService {

    /**
     * 查询频道列表
     */
    Response<List<FindChannelRspVO>> listChannels();

    /**
     * 发现页笔记分页查询
     */
    PageResponse<FindNoteCardRspVO> findDiscoverNotePageList(FindDiscoverNotePageListReqVO reqVO);

    /**
     * 个人主页笔记分页查询
     */
    PageResponse<FindNoteCardRspVO> findProfileNotePageList(FindProfileNotePageListReqVO reqVO);

    /**
     * 话题列表查询
     */
    Response<List<FindTopicRspVO>> findTopicList(FindTopicListReqVO reqVO);
}
