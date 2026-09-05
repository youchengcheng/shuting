package com.quanxiaoha.xiaohashu.search.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.search.constant.ApiConstants;
import com.quanxiaoha.xiaohashu.search.dto.RebuildNoteDocumentReqDTO;
import com.quanxiaoha.xiaohashu.search.dto.RebuildUserDocumentReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/*
* 对外提供搜索用户和笔记接口
* */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface SearchFeignApi {

    String PREFIX = "/search";

    /*
    * 重构笔记文档
    * */
    @PostMapping(value = PREFIX + "/note/document/rebuild")
    Response<?> rebuildNoteDocument(@Validated @RequestBody RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);

    /*
    * 重构用户文档
    * */
    @PostMapping(value = PREFIX + "/user/document/rebuild")
    Response<?> rebuildUserDocument(@Validated @RequestBody RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
