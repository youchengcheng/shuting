package com.quanxiaoha.xiaohashu.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import com.quanxiaoha.xiaohashu.oss.biz.config.AliyunOSSProperties;
import com.quanxiaoha.xiaohashu.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;
    @Resource
    private OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("==========aliyunOSS文件上传策略");
        //判断文件是否为空
        if(file == null || file.getSize() == 0){
            log.info("========上传文件异常，文件为空");
            throw new RuntimeException("文件大小不能为空");
        }
        //获取文件的原始名称
        String originalFilename = file.getOriginalFilename();
        //使用uuid生成存储对象的名称
        String key = UUID.randomUUID().toString().replace("-", "");
        //获取文件的后缀--------以最后那个“.”开始截取到最后
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        //拼接文件后缀，最后也就是要存储文件的名称
        String objectName = String.format("%s%s", key, suffix);

        // 上传文件至阿里云 OSS
        ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(file.getInputStream().readAllBytes()));

        // 返回文件的访问链接
        String url = String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
        log.info("==> 上传文件至阿里云 OSS 成功，访问路径: {}", url);
        return url;
    }
}
