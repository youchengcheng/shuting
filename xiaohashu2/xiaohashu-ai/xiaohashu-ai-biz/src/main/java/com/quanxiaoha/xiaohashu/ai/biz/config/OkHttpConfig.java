package com.quanxiaoha.xiaohashu.ai.biz.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * OkHttp 客户端配置（调用外部 HTTP 服务，例如联网搜索 / 网关回源）
 */
@Configuration
public class OkHttpConfig {

    @Bean
    public OkHttpClient okHttpClient(
            @Value("${okhttp.connect-timeout:5000}") int connectTimeout,
            @Value("${okhttp.read-timeout:30000}") int readTimeout,
            @Value("${okhttp.write-timeout:15000}") int writeTimeout,
            @Value("${okhttp.max-idle-connections:200}") int maxIdleConnections,
            @Value("${okhttp.keep-alive-duration:5}") int keepAliveDuration) {

        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(maxIdleConnections, keepAliveDuration, TimeUnit.MINUTES))
                .build();
    }
}