package com.quanxiaoha.xiaohashu.data.align.rpc;

import com.quanxiaoha.xiaohashu.search.api.SearchFeignApi;
import com.quanxiaoha.xiaohashu.search.dto.RebuildNoteDocumentReqDTO;
import com.quanxiaoha.xiaohashu.search.dto.RebuildUserDocumentReqDTO;
import jakarta.annotation.Resource;

/*
* 搜索服务
* */
public class SearchRpcService {

    @Resource
    private SearchFeignApi searchFeignApi;

    /*
    * 调用重建笔记文档接口
    * */
    public void rebuildNoteDocument(Long noteId) {
        RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO = RebuildNoteDocumentReqDTO.builder()
                .id(noteId)
                .build();

        searchFeignApi.rebuildNoteDocument(rebuildNoteDocumentReqDTO);
    }

    /*
    * 调用重构用户文档接口
    * */
    public void rebuildUserDocument(Long userId){
        RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO = RebuildUserDocumentReqDTO.builder()
                .id(userId)
                .build();

        searchFeignApi.rebuildUserDocument(rebuildUserDocumentReqDTO);
    }

}
