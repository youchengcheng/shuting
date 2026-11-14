package com.quanxiaoha.xiaohashu.notification.biz.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true")
public class NotificationTomcatConfiguration {
    public static final int SOCKET_WRITE_TIMEOUT_MILLIS = 10000;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> notificationSocketTimeout() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (!(connector.getProtocolHandler() instanceof Http11NioProtocol protocol)) {
                throw new IllegalStateException("Notification streaming requires Tomcat HTTP/1.1 NIO with a bounded write timeout");
            }
            // Tomcat 10.1.5 NioEndpoint initializes each socket's writeTimeout from connectionTimeout.
            // socket.soTimeout alone only changes the socket read timeout; it does not bound SSE flush().
            protocol.setConnectionTimeout(SOCKET_WRITE_TIMEOUT_MILLIS);
        });
    }
}
