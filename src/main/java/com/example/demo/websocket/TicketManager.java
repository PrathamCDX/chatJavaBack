package com.example.demo.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage for WebSocket tickets: which user a ticket belongs to, until it expires.
 * Generating tickets and the single-use rule live in WsTicketService; implementations only store.
 *
 * There are two kinds of ticket, and both count towards the user's limit:
 * - A login ticket is what a client connects with. It expires, and get returns it.
 * - A connection ticket stands for one open connection. It lasts until the connection closes,
 *   and get never returns it, so it can't be used to connect.
 */
public interface TicketManager {

    /**
     * Stores a login ticket; it stops being returned by get once ttl has passed.
     * If that puts the user over their limit, their oldest ticket is removed and returned.
     */
    Optional<String> set(String ticket, Long userId, Duration ttl);

    /**
     * Stores a connection ticket.
     * If that puts the user over their limit, their oldest ticket is removed and returned.
     */
    Optional<String> setConnection(String ticket, Long userId);

    /** The user a login ticket belongs to; empty if it's unknown, deleted or expired. */
    Optional<Long> get(String ticket);

    /**
     * Removes the ticket; returns true if it was present. When several callers delete the same
     * ticket at once, only one gets true, which is what makes tickets single-use.
     */
    boolean delete(String ticket, Long userId);

    /** Every stored ticket, including expired ones not cleaned up yet. For /health/stats. */
    List<TicketEntry> all();

    /** expiresAt is null for a connection ticket. */
    record TicketEntry(String ticket, Long userId, Instant expiresAt, boolean expired, boolean connection) {
    }
}
