package com.quanxiaoha.xiaohashu.search.service;

import org.springframework.http.ResponseEntity;

/*
* 扩展词典
* */
public interface ExtDictService {

    /*
    * 获取热更新词典
    * */
    ResponseEntity<String> getHotUpdateExtDict();

}
