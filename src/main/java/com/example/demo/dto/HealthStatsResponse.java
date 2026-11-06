package com.example.demo.dto;

import com.example.demo.websocket.ChatSessionRegistry.SessionInfo;
import com.example.demo.websocket.TicketManager.TicketEntry;

import java.util.List;

/**
 * Runtime state for /health/stats (testing only).
 * sessionCount counts every open socket; users only those bound to a user, so the
 * two can differ briefly while a connection's ticket is being checked.
 */
public record HealthStatsResponse(
        int userCount,
        int sessionCount,
        List<UserSessions> users,
        int ticketCount,
        List<TicketEntry> tickets,
        MemoryStats memory) {

    public record UserSessions(Long userId, List<SessionInfo> sessions) {
    }

    /** JVM heap, in MB: used = total - free; max is the most the heap can grow to (-Xmx). */
    public record MemoryStats(long usedMb, long freeMb, long totalMb, long maxMb) {
    }
}
