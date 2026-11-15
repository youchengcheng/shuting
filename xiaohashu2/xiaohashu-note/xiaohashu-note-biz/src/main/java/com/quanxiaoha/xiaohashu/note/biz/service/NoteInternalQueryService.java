package com.quanxiaoha.xiaohashu.note.biz.service;

import com.quanxiaoha.xiaohashu.note.dto.*;

import java.util.List;

/*
 * 笔记内部查询服务
 * */
public interface NoteInternalQueryService {

    /*
     * 批量查询元数据
     * */
    List<NoteMetadataDTO> metadata(NoteBatchQueryReqDTO noteBatchQueryReqDTO);

    /*
     * 批量查询预览信息
     * */
    List<NotePreviewDTO> previews(NoteBatchQueryReqDTO noteBatchQueryReqDTO);

}
