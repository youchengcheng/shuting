package com.quanxiaoha.xiaohashu.ai.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 小哈书 AI 服务启动类
 *
 * <p>本模块的 Spring Boot 版本（3.4.5）与 xiaohashu 其它微服务（3.0.2）不同，
 * 依赖版本由本模块自行管理，见 xiaohashu-ai/pom.xml 注释。</p>
 */
@SpringBootApplication
@EnableScheduling
public class XiaohashuAiBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohashuAiBizApplication.class, args);
    }

}