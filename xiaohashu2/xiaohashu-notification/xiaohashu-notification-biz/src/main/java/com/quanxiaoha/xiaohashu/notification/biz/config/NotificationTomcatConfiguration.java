package com.quanxiaoha.xiaohashu.notification.biz.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 通知 SSE 的 Tomcat 超时配置
 * */
@Configuration
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
public class NotificationTomcatConfiguration {

    /**
     * Socket 写入超时时间
     */
    public static final int SOCKET_WRITE_TIMEOUT_MILLIS = 10000;

    /*
     * 限制 SSE 写入阻塞时间
     * */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> notificationSocketTimeout() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (!(connector.getProtocolHandler() instanceof Http11NioProtocol protocol)) {
                throw new IllegalStateException("通知实时推送要求 Tomcat 使用 HTTP/1.1 NIO 并设置有限写入超时");
            }
            // Tomcat 10.1.5 从 connectionTimeout 初始化 Socket 写入超时
            // socket.soTimeout 只限制读取时间，无法限制 SSE 刷新时的阻塞
            protocol.setConnectionTimeout(SOCKET_WRITE_TIMEOUT_MILLIS);
        });
    }
}
