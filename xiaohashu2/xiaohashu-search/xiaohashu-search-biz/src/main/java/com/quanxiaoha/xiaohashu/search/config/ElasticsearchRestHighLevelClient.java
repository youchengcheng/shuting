package com.quanxiaoha.xiaohashu.search.config;

import jakarta.annotation.Resource;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* ElasticsearchRestClient 客户端
* */
@Configuration
public class ElasticsearchRestHighLevelClient {

    @Resource
    private ElasticsearchProperties elasticsearchProperties;

    private static final String COLON = ":";
    private static final String HTTP = "http";

    @Bean
    public RestHighLevelClient restHighLevelClient(){
        String address = elasticsearchProperties.getAddress();

        String[] addressArr = address.split(COLON);
        //地址
        String host = addressArr[0];
        //端口
        int port = Integer.parseInt(addressArr[1]);

        HttpHost httpHost = new HttpHost(host, port, HTTP);

        return new RestHighLevelClient(RestClient.builder(httpHost)
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(3000)
                        .setConnectionRequestTimeout(3000)
                        .setSocketTimeout(5000)));

    }

}
