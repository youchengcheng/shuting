package com.quanxiaoha.ai.robot.service;

import com.quanxiaoha.ai.robot.model.dto.SearchResultDTO;
import com.quanxiaoha.ai.robot.model.vo.customerService.CheckFileReqVO;
import com.quanxiaoha.ai.robot.model.vo.customerService.CheckFileRspVO;
import com.quanxiaoha.ai.robot.utils.Response;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 页面内容提取
 **/
public interface SearchResultContentFetcherService {


    /**
     * 并发批量获取搜索结果页面的内容
     */
    CompletableFuture<List<SearchResultDTO>> batchFetch(List<SearchResultDTO> searchResults,
                                                        long timeout,
                                                        TimeUnit unit);


}

