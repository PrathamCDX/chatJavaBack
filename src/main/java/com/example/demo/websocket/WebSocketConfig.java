package com.example.demo.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@EnableScheduling // runs ChatHeartbeat
public class WebSocketConfig implements WebSocketConfigurer {

    // Frames are small JSON objects; this caps what a client can make the server buffer
    private static final int MAX_TEXT_MESSAGE_BYTES = 16 * 1024;

    private final ChatWebSocketHandler chatHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(ChatWebSocketHandler chatHandler,
                           @Value("${websocket.allowed-origins}") String[] allowedOrigins) {
        this.chatHandler = chatHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat")
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        var container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BYTES);
        return container;
    }
}
