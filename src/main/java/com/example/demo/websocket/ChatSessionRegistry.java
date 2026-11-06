package com.example.demo.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open connections on this server, and which user each one belongs to.
 * A user can have several connections (one per browser tab).
 *
 * Sessions are stored wrapped in ConcurrentWebSocketSessionDecorator, because
 * WebSocketSession.sendMessage isn't thread safe and several senders can write to
 * the same receiver at once.
 */
@Component
public class ChatSessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final String LAST_SEEN_AT = "lastSeenAt";

    // Raw session id -> decorated session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    void register(WebSocketSession session) {
        touch(session);
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES));
    }

    /** The thread-safe wrapper for a session passed in by the WebSocket container. */
    WebSocketSession get(WebSocketSession session) {
        return sessions.get(session.getId());
    }

    /** Every open session (thread-safe wrappers), for the heartbeat. */
    Collection<WebSocketSession> allSessions() {
        return sessions.values();
    }

    /** Records that the client just sent something (a message or a pong), so it's alive. */
    void touch(WebSocketSession session) {
        session.getAttributes().put(LAST_SEEN_AT, System.currentTimeMillis());
    }

    /** When the client last sent anything, in epoch millis. */
    long lastSeenAt(WebSocketSession session) {
        return (Long) session.getAttributes().get(LAST_SEEN_AT);
    }

    // Add inside compute so it can't race with unregister dropping the user's empty set
    void bindUser(WebSocketSession session, Long userId) {
        sessionsByUser.compute(userId, (id, userSessions) -> {
            if (userSessions == null) {
                userSessions = ConcurrentHashMap.newKeySet();
            }
            userSessions.add(session);
            return userSessions;
        });
    }

    Set<WebSocketSession> sessionsOf(Long userId) {
        return sessionsByUser.getOrDefault(userId, Set.of());
    }

    public record SessionInfo(String sessionId, String remoteAddress, Instant lastSeenAt, boolean open) {
    }

    /** userId -> that user's sessions, as the router sees them. For /health/stats. */
    public Map<Long, List<SessionInfo>> sessionsByUserSnapshot() {
        Map<Long, List<SessionInfo>> snapshot = new TreeMap<>();
        sessionsByUser.forEach((userId, userSessions) ->
                snapshot.put(userId, userSessions.stream().map(this::info).toList()));
        return snapshot;
    }

    /** Every open session, bound to a user or not (a session is unbound until its ticket is redeemed). */
    public int sessionCount() {
        return sessions.size();
    }

    private SessionInfo info(WebSocketSession session) {
        return new SessionInfo(session.getId(), String.valueOf(session.getRemoteAddress()),
                Instant.ofEpochMilli(lastSeenAt(session)), session.isOpen());
    }

    void unregister(WebSocketSession session, Long userId) {
        WebSocketSession decorated = sessions.remove(session.getId());
        if (decorated == null || userId == null) {
            return;
        }
        sessionsByUser.computeIfPresent(userId, (id, userSessions) -> {
            userSessions.remove(decorated);
            return userSessions.isEmpty() ? null : userSessions;
        });
    }
}
