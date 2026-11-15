package com.quanxiaoha.xiaohashu.notification.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知服务启动类
 */
@SpringBootApplication
@MapperScan("com.quanxiaoha.xiaohashu.notification.biz.domain.mapper")
@EnableFeignClients(basePackages = {"com.quanxiaoha.xiaohashu.user.api", "com.quanxiaoha.xiaohashu.note.api", "com.quanxiaoha.xiaohashu.comment.api"})
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
