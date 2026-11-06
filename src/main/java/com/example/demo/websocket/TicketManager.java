package com.example.demo.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage for WebSocket tickets: which user a ticket belongs to, until it expires.
 * Generating tickets and the single-use rule live in WsTicketService; implementations only store.
 */
public interface TicketManager {

    /** Stores the ticket for the user; it stops being returned by get once ttl has passed. */
    void set(String ticket, Long userId, Duration ttl);

    /** The user the ticket belongs to; empty if it's unknown, deleted or expired. */
    Optional<Long> get(String ticket);

    /**
     * Removes the ticket; returns true if it was present. When several callers delete the same
     * ticket at once, only one gets true, which is what makes tickets single-use.
     */
    boolean delete(String ticket);

    /** Every stored ticket, including expired ones not cleaned up yet. For /health/stats. */
    List<TicketEntry> all();

    record TicketEntry(String ticket, Long userId, Instant expiresAt, boolean expired) {
    }
}
