package com.quanxiaoha.ai.robot.service;


import com.quanxiaoha.ai.robot.model.vo.customerService.*;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 客服
 **/
public interface CustomerService {

    /**
     * 上传 Markdown 问答文件
     */
//    Response<?> uploadMarkdownFile(MultipartFile file);

    /**
     * 检查文件是否存在
     */
    Response<CheckFileRspVO> checkFile(CheckFileReqVO checkFileReqVO);

    /**
     * 删除 Markdown 问答文件
     */
    Response<?> deleteMarkdownFile(DeleteMarkdownFileReqVO deleteMarkdownFileReqVO);

    /**
     * 分页查询 Markdown 问答文件
     */
    PageResponse<FindMarkdownFilePageListRspVO> findMarkdownFilePageList(FindMarkdownFilePageListReqVO findMarkdownFilePageListReqVO);

    /**
     * 修改  Markdown 问答文件信息
     */
    Response<?> updateMarkdownFile(UpdateMarkdownFileReqVO updateMarkdownFileReqVO);

    /**
     * 文件分片上传
     */
    Response<?> uploadChunk(UploadChunkReqVO uploadChunkReqVO);

    /**
     * 文件分片合并
     */
    Response<?> mergeChunk(MergeChunkReqVO mergeChunkReqVO);
}

