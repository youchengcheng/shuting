package com.quanxiaoha.ai.robot.service;

import com.quanxiaoha.ai.robot.model.dto.SearchResultDTO;

import java.util.List;

/*
* SearXNGService服务类
* */
public interface SearXNGService {

    /*
    * query搜索关键词
    * */
    List<SearchResultDTO> search(String query);

}
