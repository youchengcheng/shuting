package com.quanxiaoha.xiaohashu.kv.biz.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.dto.rep.AddNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.DeleteNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindNoteContentRspDTO;

/*
* 笔记内容存储业务
* */
public interface NoteContentService {

    /*
    * 添加笔记
    * */
    Response<?> AddNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

    /*
    * 查询笔记
    * */
    Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO);

    /*
    * 删除笔记
    * */
    Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
