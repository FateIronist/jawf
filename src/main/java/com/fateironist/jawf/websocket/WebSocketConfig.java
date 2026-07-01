package com.fateironist.jawf.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置。
 * <p>
 * 使用 STOMP 协议进行 WebSocket 通信。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理。
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户端发送消息的前缀
        config.setApplicationDestinationPrefixes("/app");

        // 服务端推送消息的前缀（客户端订阅）
        config.enableSimpleBroker("/topic", "/queue");

        // 用户目标前缀（点对点消息）
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 注册 STOMP 端点。
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // 支持 SockJS 降级
    }
}
