package com.example.demo.websocket;

import java.time.Instant;
import java.util.UUID;

/**
 * A frame the website client sends on /ws/chat. The sender is the user of the ticket
 * the connection was opened with, so it isn't part of the payload.
 *
 * type is required:
 * type "message": {"type":"message","id":"<uuid>","timestamp":"2026-11-06T09:25:19Z","receiverId":14,"text":"hi","url":null}
 * id and timestamp are required for "message"; the client generates them, and the "sent" ack carries the same id
 * type "readReceipt": {"type":"readReceipt","messageId":"<uuid>","receiverId":13}
 * receiverId is the user who sent the message being marked read; there's no reply unless it's invalid
 * type "ping": {"type":"ping"}; the server answers {"type":"pong"}, so the client can tell it's connected
 */
public record ChatMessageRequest(String type, UUID id, Instant timestamp, Long receiverId, String text, String url,
        UUID messageId) {

    public static final String MESSAGE = "message";
    public static final String READ_RECEIPT = "readReceipt";
    public static final String PING = "ping";
}
