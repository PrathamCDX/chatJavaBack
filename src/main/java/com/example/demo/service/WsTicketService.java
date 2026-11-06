package com.example.demo.service;

import com.example.demo.websocket.ChatSessionRegistry;
import com.example.demo.websocket.TicketManager;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * One-time tickets for opening /ws/chat. A logged-in client trades its JWT for a ticket over
 * REST, then connects with ?ticket=...; that way the JWT never ends up in a URL (and in logs).
 * A ticket works once and only for TTL; if it leaks into a log it's already useless.
 * Where tickets are stored is up to the TicketManager.
 *
 * Each open connection holds a ticket of its own, so a user's unused tickets and connections
 * share one limit. Going over it removes the user's oldest ticket, and closes the connection
 * holding it if there is one.
 */
@Service
public class WsTicketService {

    public static final Duration TTL = Duration.ofSeconds(30);

    private static final int TICKET_BYTES = 32;
    // Custom code so clients can tell this apart: reconnecting would just push out their next oldest connection
    private static final CloseStatus TOO_MANY_CONNECTIONS = new CloseStatus(4001, "Too many connections");

    private final TicketManager ticketManager;
    private final ChatSessionRegistry registry;
    private final SecureRandom random = new SecureRandom();

    public WsTicketService(TicketManager ticketManager, ChatSessionRegistry registry) {
        this.ticketManager = ticketManager;
        this.registry = registry;
    }

    public String issue(Long userId) {
        String ticket = generate();
        ticketManager.set(ticket, userId, TTL).ifPresent(oldest -> disconnect(userId, oldest));
        return ticket;
    }

    /** Creates the ticket that stands for a connection that just opened. It can't be used to connect. */
    public String issueForConnection(Long userId) {
        String ticket = generate();
        ticketManager.setConnection(ticket, userId).ifPresent(oldest -> disconnect(userId, oldest));
        return ticket;
    }

    /** Removes a closed connection's ticket, freeing its slot. */
    public void release(String ticket, Long userId) {
        ticketManager.delete(ticket, userId);
    }

    /** Uses up the ticket and returns its user id; empty if it's unknown, already used or expired. */
    public Optional<Long> redeem(String ticket) {
        Optional<Long> userId = ticketManager.get(ticket);
        // Only one caller's delete can return true, so two connections racing on the same ticket can't both win
        if (userId.isEmpty() || !ticketManager.delete(ticket, userId.get())) {
            return Optional.empty();
        }
        return userId;
    }

    private void disconnect(Long userId, String ticket) {
        registry.closeByTicket(userId, ticket, TOO_MANY_CONNECTIONS);
    }

    private String generate() {
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
