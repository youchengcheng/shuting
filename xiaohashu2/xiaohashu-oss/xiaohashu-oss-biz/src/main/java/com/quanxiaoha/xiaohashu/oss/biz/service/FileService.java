package com.quanxiaoha.xiaohashu.oss.biz.service;


import com.quanxiaoha.framework.common.response.Response;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /*
    * 文件上传
    * */
    Response<?> uploadFile(MultipartFile file);
}
