package com.quanxiaoha.xiaohashu.kv.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.biz.service.NoteContentService;
import com.quanxiaoha.xiaohashu.kv.dto.rep.AddNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.DeleteNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindNoteContentRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
* 笔记内容存储业务
* */
@RestController
@RequestMapping("/kv")
@Slf4j
public class NoteContentController {
    @Resource
    private NoteContentService noteContentService;

    /*
    * 添加笔记
    * */
    @PostMapping("/note/content/add")
    public Response<?> addNoteContent(@Validated @RequestBody AddNoteContentReqDTO addNoteContentReqDTO){
        return noteContentService.AddNoteContent(addNoteContentReqDTO);
    }

    /*
    * 根据笔记id查询笔记
    * */
    @PostMapping("/note/content/find")
    public Response<FindNoteContentRspDTO> findNoteContent(@Validated @RequestBody FindNoteContentReqDTO findNoteContentReqDTO){
        return noteContentService.findNoteContent(findNoteContentReqDTO);
    }

    /*
    * 删除笔记
    * */
    @DeleteMapping("/note/content/delete")
    public Response<?> deleteNoteContent(@Validated @RequestBody DeleteNoteContentReqDTO deleteNoteContentReqDTO){
        return noteContentService.deleteNoteContent(deleteNoteContentReqDTO);
    }
}
