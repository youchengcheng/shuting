package com.quanxiaoha.xiaohashu.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.quanxiaoha.xiaohashu.data.align.domain.mapper")
@EnableFeignClients("com.quanxiaoha.xiaohashu")
public class XiaohashuDataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaohashuDataAlignApplication.class,args);
    }
}
