package com.example.demo.dto;

public record WsTicketResponse(
        String ticket,
        long expiresInSeconds) {
}
