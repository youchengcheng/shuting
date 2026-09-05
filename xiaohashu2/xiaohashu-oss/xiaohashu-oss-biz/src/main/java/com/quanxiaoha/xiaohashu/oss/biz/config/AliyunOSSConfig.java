package com.quanxiaoha.xiaohashu.oss.biz.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* 阿里云client配置
* */
@Configuration
public class AliyunOSSConfig {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    /*
    * 构建阿里云OSS客户端
    * */
    @Bean
    public OSS aliyunOSSClient(){

        //设置访问凭证
        DefaultCredentialProvider credentialProvider = CredentialsProviderFactory
                .newDefaultCredentialProvider(
                        aliyunOSSProperties.getAccessKey(), aliyunOSSProperties.getSecretKey());

        //创建OSSClient实例
        return new OSSClientBuilder().build(aliyunOSSProperties.getEndpoint(),credentialProvider);
    }

}
