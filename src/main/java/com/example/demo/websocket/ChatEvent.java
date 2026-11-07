package com.example.demo.websocket;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Frames the server sends to the website client. Serialized with a "type" field
 * so the client can switch on it, e.g. {"type":"message","id":"...","senderId":1,...}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatEvent.Connected.class, name = "connected"),
        @JsonSubTypes.Type(value = ChatEvent.Message.class, name = "message"),
        @JsonSubTypes.Type(value = ChatEvent.Sent.class, name = "sent"),
        @JsonSubTypes.Type(value = ChatEvent.Delivered.class, name = "delivered"),
        @JsonSubTypes.Type(value = ChatEvent.ReadReceipt.class, name = "readReceipt"),
        @JsonSubTypes.Type(value = ChatEvent.Pong.class, name = "pong"),
        @JsonSubTypes.Type(value = ChatEvent.Failure.class, name = "error")
})
public sealed interface ChatEvent {

    /** The connection is now bound to this user and will receive their messages. */
    record Connected(Long userId) implements ChatEvent {
    }

    /**
     * A message from another user. id and timestamp are what the sender's client sent, so they
     * can't be trusted; sentAt is the server's clock, so use it for ordering.
     */
    record Message(UUID id, Long senderId, String text, String url, Instant timestamp, Instant sentAt)
            implements ChatEvent {
    }

    /** The server has the sender's message queued for the receiver. Delivered follows once they ack it. */
    record Sent(UUID id, Long receiverId, Instant sentAt) implements ChatEvent {
    }

    /** The receiver acked the sender's message. Lost if the sender is offline at that moment. */
    record Delivered(UUID id, Long receiverId, Instant deliveredAt) implements ChatEvent {
    }

    /**
     * Tells the sender that readerId read their message. Messages aren't stored, so the server
     * can't check that messageId is real or was sent to readerId; only readerId and readAt are trustworthy.
     */
    record ReadReceipt(UUID messageId, Long readerId, Instant readAt) implements ChatEvent {
    }

    /** Reply to the client's {"type":"ping"}; serverTime lets the client measure latency or clock skew. */
    record Pong(Instant serverTime) implements ChatEvent {
    }

    record Failure(String message) implements ChatEvent {
    }
}
