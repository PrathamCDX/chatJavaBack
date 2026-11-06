package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.WsTicketResponse;
import com.example.demo.security.AppUserPrincipal;
import com.example.demo.service.WsTicketService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ws")
public class WsTicketController {

    private final WsTicketService ticketService;

    public WsTicketController(WsTicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Requires the normal Bearer JWT; the client then opens /ws/chat?ticket=...
    @PostMapping("/ticket")
    public ResponseEntity<ApiResponse<WsTicketResponse>> ticket(@AuthenticationPrincipal AppUserPrincipal principal) {
        String ticket = ticketService.issue(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("Ticket issued",
                new WsTicketResponse(ticket, WsTicketService.TTL.toSeconds())));
    }
}
