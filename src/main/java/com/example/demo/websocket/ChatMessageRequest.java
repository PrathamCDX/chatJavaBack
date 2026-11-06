package com.example.demo.websocket;

/**
 * A frame the website client sends on /ws/chat. The sender is the user of the ticket
 * the connection was opened with, so it isn't part of the payload.
 *
 * type is required:
 * type "message": {"type":"message","receiverId":14,"text":"hi","url":null}
 * type "ping": {"type":"ping"}; the server answers {"type":"pong"}, so the client can tell it's connected
 */
public record ChatMessageRequest(String type, Long receiverId, String text, String url) {

    public static final String MESSAGE = "message";
    public static final String PING = "ping";
}
