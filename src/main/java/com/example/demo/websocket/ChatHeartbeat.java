package com.example.demo.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import java.io.IOException;

/**
 * Finds dead connections. A client that vanishes without closing (lost signal, laptop asleep)
 * never triggers afterConnectionClosed, so its session would stay in the registry forever.
 *
 * Every interval this pings each session; browsers answer pings with a pong automatically.
 * A session that hasn't sent anything (message or pong) for idle-timeout is closed, which
 * runs afterConnectionClosed and removes it from the registry.
 *
 * Tomcat's own idle timeout (setMaxSessionIdleTimeout) can't do this: it only fires when
 * neither side has sent anything, and our pings count as sending.
 */
@Component
public class ChatHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(ChatHeartbeat.class);

    private final ChatSessionRegistry registry;
    private final long idleTimeoutMs;

    public ChatHeartbeat(ChatSessionRegistry registry, @Value("${websocket.idle-timeout-ms}") long idleTimeoutMs) {
        this.registry = registry;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    @Scheduled(fixedRateString = "${websocket.heartbeat-interval-ms}")
    void pingAll() {
        long now = System.currentTimeMillis();
        for (WebSocketSession session : registry.allSessions()) {
            if (now - registry.lastSeenAt(session) > idleTimeoutMs) {
                closeIdle(session);
            } else {
                ping(session);
            }
        }
    }

    private void ping(WebSocketSession session) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new PingMessage());
        } catch (IOException | SessionLimitExceededException e) {
            // SessionLimitExceededException: pings are piling up unsent; the decorator closes the session
            log.debug("Failed to ping session {}", session.getId(), e);
        }
    }

    private void closeIdle(WebSocketSession session) {
        System.out.println("WebSocket idle, closing: session=" + session.getId());
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("Idle timeout"));
        } catch (IOException e) {
            log.debug("Failed to close idle session {}", session.getId(), e);
        }
    }
}
