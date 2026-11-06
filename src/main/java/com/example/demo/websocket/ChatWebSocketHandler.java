package com.example.demo.websocket;

import com.example.demo.repository.UserRepository;
import com.example.demo.service.WsTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.UUID;

/**
 * /ws/chat?ticket=...: one-to-one messages between website users.
 *
 * The client first gets a one-time ticket from POST /ws/ticket (with its JWT),
 * then connects
 * with it. The ticket is redeemed when the connection opens; an invalid, used
 * or expired one
 * closes it right away. The connection belongs to the ticket's user from then
 * on, so messages
 * don't carry the sender. Nothing is re-checked later, so an open connection
 * outlives the JWT's expiry.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final String USER_ID = "userId";
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_URL_LENGTH = 2048;

    private final JsonMapper jsonMapper;
    private final WsTicketService ticketService;
    private final UserRepository userRepository;
    private final ChatSessionRegistry registry;
    private final ChatMessageRouter router;

    public ChatWebSocketHandler(JsonMapper jsonMapper, WsTicketService ticketService, UserRepository userRepository,
            ChatSessionRegistry registry, ChatMessageRouter router) {
        this.jsonMapper = jsonMapper;
        this.ticketService = ticketService;
        this.userRepository = userRepository;
        this.registry = registry;
        this.router = router;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws IOException {
        registry.register(rawSession);

        Long userId = ticketService.redeem(ticketFrom(rawSession.getUri())).orElse(null);
        System.out.println("WebSocket connected: userId=" + userId + " session=" + rawSession.getId() + ", remote="
                + rawSession.getRemoteAddress());
        if (userId == null) {
            rawSession.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired ticket"));
            return;
        }
        WebSocketSession session = registry.get(rawSession);
        session.getAttributes().put(USER_ID, userId);
        registry.setTicket(session, ticketService.issueForConnection(userId));
        registry.bindUser(session, userId);
        router.send(session, new ChatEvent.Connected(userId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage frame) {
        // Any frame, ping or message, shows the client is alive
        registry.touch(rawSession);
        WebSocketSession session = registry.get(rawSession);
        Long senderId = (Long) session.getAttributes().get(USER_ID);
        if (senderId == null) {
            fail(session, "Connection has no user");
            return;
        }

        ChatMessageRequest request;
        try {
            request = jsonMapper.readValue(frame.getPayload(), ChatMessageRequest.class);
        } catch (JacksonException e) {
            fail(session, "Malformed JSON message");
            return;
        }
        if (request == null) {
            fail(session, "Malformed JSON message");
            return;
        }

        if (request.type() == null) {
            fail(session, "type is required");
            return;
        }
        switch (request.type()) {
            case ChatMessageRequest.PING -> router.send(session, new ChatEvent.Pong(Instant.now()));
            case ChatMessageRequest.MESSAGE -> handleChatMessage(session, senderId, request);
            default -> fail(session, "Unknown type: " + request.type());
        }
    }

    private void handleChatMessage(WebSocketSession session, Long senderId, ChatMessageRequest request) {
        String text = blankToNull(request.text());
        String url = blankToNull(request.url());
        String error = validate(senderId, request.receiverId(), text, url);
        if (error != null) {
            fail(session, error);
            return;
        }

        var message = new ChatEvent.Message(UUID.randomUUID().toString(), senderId, text, url, Instant.now());
        boolean delivered = router.deliver(request.receiverId(), message);
        // Receiver offline: tell the sender whether the id exists at all (messages
        // aren't stored yet)
        if (!delivered && !userRepository.existsById(request.receiverId())) {
            fail(session, "Receiver not found");
            return;
        }
        router.send(session, new ChatEvent.Sent(message.id(), request.receiverId(), delivered, message.sentAt()));
    }

    // Browsers answer ChatHeartbeat's pings automatically; a pong means the client
    // is still there
    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        registry.touch(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error on session {}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(USER_ID);
        ticketService.release(registry.ticketOf(session), userId);
        registry.unregister(session, userId);
    }

    /** The ticket query parameter, or null if it's missing. */
    private static String ticketFrom(URI uri) {
        return uri == null ? null : UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
    }

    /** Returns an error message, or null if the message is valid. */
    private static String validate(Long senderId, Long receiverId, String text, String url) {
        if (receiverId == null) {
            return "receiverId is required";
        }
        if (receiverId.equals(senderId)) {
            return "Cannot send a message to yourself";
        }
        if (text == null && url == null) {
            return "text or url is required";
        }
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            return "text must be at most " + MAX_TEXT_LENGTH + " characters";
        }
        if (url != null && !isHttpUrl(url)) {
            return "url must be a valid http or https URL";
        }
        return null;
    }

    // Only http(s): other schemes like javascript: would be dangerous if the client
    // renders the url as a link
    private static boolean isHttpUrl(String url) {
        if (url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void fail(WebSocketSession session, String message) {
        router.send(session, new ChatEvent.Failure(message));
    }
}
