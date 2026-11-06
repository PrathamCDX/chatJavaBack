package com.example.demo.service;

import com.example.demo.websocket.TicketManager;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * One-time tickets for opening /ws/chat. A logged-in client trades its JWT for a ticket over
 * REST, then connects with ?ticket=...; that way the JWT never ends up in a URL (and in logs).
 * A ticket works once and only for TTL; if it leaks into a log it's already useless.
 * Where tickets are stored is up to the TicketManager.
 */
@Service
public class WsTicketService {

    public static final Duration TTL = Duration.ofSeconds(30);

    private static final int TICKET_BYTES = 32;

    private final TicketManager ticketManager;
    private final SecureRandom random = new SecureRandom();

    public WsTicketService(TicketManager ticketManager) {
        this.ticketManager = ticketManager;
    }

    public String issue(Long userId) {
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ticketManager.set(ticket, userId, TTL);
        return ticket;
    }

    /** Uses up the ticket and returns its user id; empty if it's unknown, already used or expired. */
    public Optional<Long> redeem(String ticket) {
        Optional<Long> userId = ticketManager.get(ticket);
        // Only one caller's delete can return true, so two connections racing on the same ticket can't both win
        if (userId.isEmpty() || !ticketManager.delete(ticket)) {
            return Optional.empty();
        }
        return userId;
    }
}
