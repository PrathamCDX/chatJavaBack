package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.HealthStatsResponse;
import com.example.demo.websocket.ChatSessionRegistry;
import com.example.demo.websocket.MessageQueue;
import com.example.demo.websocket.TicketManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final long MB = 1024 * 1024;

    private final ChatSessionRegistry sessionRegistry;
    private final TicketManager ticketManager;
    private final MessageQueue messageQueue;

    public HealthController(ChatSessionRegistry sessionRegistry, TicketManager ticketManager,
            MessageQueue messageQueue) {
        this.sessionRegistry = sessionRegistry;
        this.ticketManager = ticketManager;
        this.messageQueue = messageQueue;
    }

    // TESTING ONLY: public (/health/** is permitAll) and lists live tickets, which anyone could
    // use to connect as their owner, and the text of queued messages. Remove or protect before deploying.
    @PreAuthorize("permitAll()")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<HealthStatsResponse>> stats() {
        List<HealthStatsResponse.UserSessions> users = sessionRegistry.sessionsByUserSnapshot().entrySet().stream()
                .map(e -> new HealthStatsResponse.UserSessions(e.getKey(), e.getValue()))
                .toList();
        List<TicketManager.TicketEntry> tickets = ticketManager.all();
        List<HealthStatsResponse.UserQueue> queues = messageQueue.all().entrySet().stream()
                .map(e -> new HealthStatsResponse.UserQueue(e.getKey(), e.getValue()))
                .toList();
        int queuedMessageCount = queues.stream().mapToInt(q -> q.messages().size()).sum();

        Runtime runtime = Runtime.getRuntime();
        var memory = new HealthStatsResponse.MemoryStats(
                (runtime.totalMemory() - runtime.freeMemory()) / MB,
                runtime.freeMemory() / MB,
                runtime.totalMemory() / MB,
                runtime.maxMemory() / MB);

        var stats = new HealthStatsResponse(users.size(), sessionRegistry.sessionCount(), users,
                tickets.size(), tickets, sessionRegistry.connectionLog(), queuedMessageCount, queues, memory);
        return ResponseEntity.ok(ApiResponse.ok("Stats", stats));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> getFn() {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("All ok", null));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/debug")
    public ResponseEntity<ApiResponse<DebugDto>> debugFn() {

        return ResponseEntity.status(200).body(ApiResponse.ok("Debug data", new DebugDto("New dta", 1)));
    }
}

class DebugDto {
    private String data;
    private Integer id;

    public DebugDto(String data, Integer id) {
        this.data = data;
        this.id = id;
    }

    public String getData() {
        return this.data;
    }

    public Integer getId() {
        return this.id;
    }
}