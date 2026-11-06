package com.example.demo.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Sends events to connections. Delivery is local only for now;
 * forwarding to peer servers will plug in to deliver() later.
 */
@Component
public class ChatMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageRouter.class);

    private final ChatSessionRegistry registry;
    private final JsonMapper jsonMapper;

    public ChatMessageRouter(ChatSessionRegistry registry, JsonMapper jsonMapper) {
        this.registry = registry;
        this.jsonMapper = jsonMapper;
    }

    /** Sends the message to every open connection of the receiver; returns true if at least one got it. */
    boolean deliver(Long receiverId, ChatEvent.Message message) {
        boolean delivered = false;
        for (WebSocketSession session : registry.sessionsOf(receiverId)) {
            delivered |= send(session, message);
        }
        return delivered;
    }

    /** Sends one event; returns false if the connection is closed or the send failed. */
    boolean send(WebSocketSession session, ChatEvent event) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(event)));
            return true;
        } catch (IOException | SessionLimitExceededException e) {
            // SessionLimitExceededException: the client reads too slowly; the decorator closes the session
            log.debug("Failed to send to session {}", session.getId(), e);
            return false;
        }
    }
}
