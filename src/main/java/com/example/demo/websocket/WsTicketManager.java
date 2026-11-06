package com.example.demo.websocket;

import com.example.demo.cache.Cache;
import com.example.demo.cache.LruCache;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * In-memory TicketManager backed by LruCache: ticket -> (userId, expiresAt).
 * Tickets are lost on restart and only known to this server.
 */
@Component
public class WsTicketManager implements TicketManager {

    // Bounded, so a flood of ticket requests can't use up memory: the oldest tickets get evicted
    private static final int MAX_TICKETS = 10_000;

    private record Ticket(Long userId, Instant expiresAt) {
    }

    private final Cache<String, Ticket> tickets = new LruCache<>(MAX_TICKETS);

    @Override
    public void set(String ticket, Long userId, Duration ttl) {
        tickets.put(ticket, new Ticket(userId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<Long> get(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Optional<Ticket> found = tickets.get(ticket);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        // Expired tickets are dropped when someone asks for them, instead of by a cleanup job
        if (!Instant.now().isBefore(found.get().expiresAt())) {
            tickets.remove(ticket);
            return Optional.empty();
        }
        return Optional.of(found.get().userId());
    }

    @Override
    public boolean delete(String ticket) {
        return ticket != null && tickets.remove(ticket);
    }

    @Override
    public List<TicketEntry> all() {
        Instant now = Instant.now();
        return tickets.snapshot().entrySet().stream()
                .map(e -> new TicketEntry(e.getKey(), e.getValue().userId(), e.getValue().expiresAt(),
                        !now.isBefore(e.getValue().expiresAt())))
                .toList();
    }
}
