package com.example.demo.websocket;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Frames the server sends to the website client. Serialized with a "type" field
 * so the client can switch on it, e.g. {"type":"message","id":"...","senderId":1,...}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatEvent.Connected.class, name = "connected"),
        @JsonSubTypes.Type(value = ChatEvent.Message.class, name = "message"),
        @JsonSubTypes.Type(value = ChatEvent.Sent.class, name = "sent"),
        @JsonSubTypes.Type(value = ChatEvent.Pong.class, name = "pong"),
        @JsonSubTypes.Type(value = ChatEvent.Failure.class, name = "error")
})
public sealed interface ChatEvent {

    /** The connection is now bound to this user and will receive their messages. */
    record Connected(Long userId) implements ChatEvent {
    }

    /** A message from another user. */
    record Message(String id, Long senderId, String text, String url, Instant sentAt) implements ChatEvent {
    }

    /** Acknowledges the sender's message; delivered is false when the receiver has no open connection. */
    record Sent(String id, Long receiverId, boolean delivered, Instant sentAt) implements ChatEvent {
    }

    /** Reply to the client's {"type":"ping"}; serverTime lets the client measure latency or clock skew. */
    record Pong(Instant serverTime) implements ChatEvent {
    }

    record Failure(String message) implements ChatEvent {
    }
}
