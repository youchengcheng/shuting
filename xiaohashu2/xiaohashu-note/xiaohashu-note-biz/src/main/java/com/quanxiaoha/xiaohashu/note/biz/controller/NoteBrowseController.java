package com.quanxiaoha.xiaohashu.note.biz.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindChannelRspVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindDiscoverNotePageListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindNoteCardRspVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindProfileNotePageListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindTopicListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindTopicRspVO;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteBrowseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 笔记浏览接口：频道列表、发现页笔记、个人主页笔记、话题列表
 **/
@RestController
@RequestMapping("/note")
@Slf4j
public class NoteBrowseController {

    @Resource
    private NoteBrowseService noteBrowseService;

    /**
     * 频道列表
     */
    @PostMapping("/channel/list")
    @ApiOperationLog(description = "频道列表")
    public Response<List<FindChannelRspVO>> listChannels() {
        return noteBrowseService.listChannels();
    }

    /**
     * 发现页笔记分页查询
     */
    @PostMapping("/discover/note/list")
    @ApiOperationLog(description = "发现页笔记分页查询")
    public PageResponse<FindNoteCardRspVO> findDiscoverNotePageList(@RequestBody FindDiscoverNotePageListReqVO reqVO) {
        return noteBrowseService.findDiscoverNotePageList(reqVO);
    }

    /**
     * 个人主页笔记分页查询
     */
    @PostMapping("/profile/note/list")
    @ApiOperationLog(description = "个人主页笔记分页查询")
    public PageResponse<FindNoteCardRspVO> findProfileNotePageList(@RequestBody FindProfileNotePageListReqVO reqVO) {
        return noteBrowseService.findProfileNotePageList(reqVO);
    }

    /**
     * 话题列表查询
     */
    @PostMapping("/topic/list")
    @ApiOperationLog(description = "话题列表查询")
    public Response<List<FindTopicRspVO>> findTopicList(@RequestBody FindTopicListReqVO reqVO) {
        return noteBrowseService.findTopicList(reqVO);
    }
}
