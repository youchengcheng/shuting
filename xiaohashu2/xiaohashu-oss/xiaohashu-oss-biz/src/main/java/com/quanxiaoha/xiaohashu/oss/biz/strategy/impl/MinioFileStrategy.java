package com.quanxiaoha.xiaohashu.oss.biz.strategy.impl;

import com.quanxiaoha.xiaohashu.oss.biz.config.MinioProperties;
import com.quanxiaoha.xiaohashu.oss.biz.strategy.FileStrategy;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
public class MinioFileStrategy implements FileStrategy {

    @Resource
    private MinioProperties minioProperties;
    @Resource MinioClient minioClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("========minio文件生成策略");

        //判断文件是否为空
        if(file == null || file.getSize() == 0){
            log.info("========上传文件异常，文件为空");
            throw new RuntimeException("文件大小不能为空");
        }
        //获取文件的原始名称
        String originalFilename = file.getOriginalFilename();
        //获取文件的类型(Content-Type)
        String contentType = file.getContentType();
        //使用uuid生成存储对象的名称
        String key = UUID.randomUUID().toString().replace("-", "");
        //获取文件的后缀--------以最后那个“.”开始截取到最后
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        //拼接文件后缀，最后也就是要存储文件的名称
        String objectName = String.format("%s%s", key, suffix);
        //上传文件到minion
            minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build());
        //返回文件的访问链接
        String url = String.format("%s/%s/%s", minioProperties.getEndpoint(), bucketName, objectName);
        log.info("========文件上传到minio成功，访问路径为：{}",url);
        return url;
    }
}
