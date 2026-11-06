package com.example.demo.websocket;

import com.example.demo.cache.Cache;
import com.example.demo.cache.LruCache;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory TicketManager backed by two LruCaches: ticket -> (userId,
 * expiresAt), and
 * userId -> that user's tickets, oldest first. Tickets are lost on restart and
 * only known to
 * this server.
 *
 * Every operation holds one lock, so the two caches can't disagree halfway
 * through a change.
 *
 * The caches can still drift when full, because LruCache evicts without telling
 * us. A ticket
 * evicted from tickets stays in its user's list, which is what keeps counting
 * its connection.
 * A list evicted from ticketsByUser resets that user's count until their
 * connections close.
 */
@Component
public class WsTicketManager implements TicketManager {

    // Bounded, so a flood of ticket requests can't use up memory: the oldest
    // tickets get evicted
    private static final int MAX_TICKETS = 10_000;
    private static final int MAX_TICKETS_PER_USER = 2;

    // expiresAt is null for a connection ticket, which lasts until the connection
    // closes
    private record Ticket(Long userId, Instant expiresAt) {

        boolean isConnection() {
            return expiresAt == null;
        }

        boolean isExpired(Instant now) {
            return !isConnection() && !now.isBefore(expiresAt);
        }
    }

    private final Cache<String, Ticket> tickets = new LruCache<>(MAX_TICKETS);
    private final Cache<Long, List<String>> ticketsByUser = new LruCache<>(MAX_TICKETS);
    private final Lock lock = new ReentrantLock();

    @Override
    public Optional<String> set(String ticket, Long userId, Duration ttl) {
        return add(ticket, new Ticket(userId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> setConnection(String ticket, Long userId) {
        return add(ticket, new Ticket(userId, null));
    }

    private Optional<String> add(String ticket, Ticket entry) {
        lock.lock();
        try {
            List<String> userTickets = ticketsByUser.get(entry.userId()).orElseGet(ArrayList::new);
            // Otherwise a login ticket nobody used would take up a slot, and push out a
            // live connection
            userTickets.removeIf(this::dropIfExpired);

            Optional<String> oldest = Optional.empty();
            if (userTickets.size() >= MAX_TICKETS_PER_USER) {
                oldest = Optional.of(userTickets.removeFirst());
                tickets.remove(oldest.get());
            }
            userTickets.add(ticket);
            ticketsByUser.put(entry.userId(), userTickets);
            tickets.put(ticket, entry);
            return oldest;
        } finally {
            lock.unlock();
        }
    }

    private boolean dropIfExpired(String ticket) {
        boolean expired = tickets.get(ticket).filter(t -> t.isExpired(Instant.now())).isPresent();
        if (expired) {
            tickets.remove(ticket);
        }
        return expired;
    }

    @Override
    public Optional<Long> get(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        lock.lock();
        try {
            Optional<Ticket> found = tickets.get(ticket);
            if (found.isEmpty() || found.get().isConnection()) {
                return Optional.empty();
            }
            // Expired tickets are dropped when someone asks for them, instead of by a
            // cleanup job
            if (found.get().isExpired(Instant.now())) {
                delete(ticket, found.get().userId());
                return Optional.empty();
            }
            return Optional.of(found.get().userId());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean delete(String ticket, Long userId) {
        if (ticket == null || userId == null) {
            return false;
        }
        lock.lock();
        try {
            // The list is cleaned up even if tickets already evicted this one
            ticketsByUser.get(userId).ifPresent(userTickets -> {
                userTickets.remove(ticket);
                if (userTickets.isEmpty()) {
                    ticketsByUser.remove(userId);
                }
            });
            return tickets.remove(ticket);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TicketEntry> all() {
        Instant now = Instant.now();
        return tickets.snapshot().entrySet().stream()
                .map(e -> new TicketEntry(e.getKey(), e.getValue().userId(), e.getValue().expiresAt(),
                        e.getValue().isExpired(now), e.getValue().isConnection()))
                .toList();
    }
}
