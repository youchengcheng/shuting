package com.quanxiaoha.xiaohashu.kv.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.constant.ApiConstants;
import com.quanxiaoha.xiaohashu.kv.dto.rep.*;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindCommentContentRspDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindNoteContentRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.spec.RSAOtherPrimeInfo;
import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface KeyValueFeignApi {

    /*
    * 前缀
    * */
    String PREFIX = "kv";

    /*
    * 添加笔记
    * */
    @PostMapping(value = PREFIX + "/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO addNoteContentReqDTO);


    /*
     * 根据笔记id查询笔记
     * */
    @PostMapping(value = PREFIX + "/note/content/find")
    Response<FindNoteContentRspDTO> findNoteContent(@RequestBody FindNoteContentReqDTO findNoteContentReqDTO);

    /*
     * 删除笔记
     * */
    @DeleteMapping(value = PREFIX + "/note/content/delete")
    Response<?> deleteNoteContent(@RequestBody DeleteNoteContentReqDTO deleteNoteContentReqDTO);

    /*
    * 批量存储评论内容
    * */
    @PostMapping(value = PREFIX + "/comment/content/batchAdd")
    Response<?> batchAddCommentContent(@RequestBody BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

    /*
     * 批量查询评论内容
     * */
    @PostMapping(value = PREFIX + "/comment/content/batchFind")
    Response<List<FindCommentContentRspDTO>> batchFindCommentContent(@Validated @RequestBody BatchFindCommentContentReqDTO batchFindCommentContentReqDTO);

    /*
     * 删除评论内容
     * */
    @PostMapping(value = PREFIX + "/comment/content/delete")
    Response<?> deleteCommentContent(@Validated @RequestBody DeleteCommentContentReqDTO deleteCommentContentReqDTO);

}
