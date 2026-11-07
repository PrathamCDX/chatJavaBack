package com.example.demo.websocket;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Messages the server still owes each receiver. A message stays queued until the receiver acks
 * it, so one lost to a dropped connection is sent again when the receiver reconnects.
 */
public interface MessageQueue {

    /**
     * Adds the message to the receiver's queue; false if there's no room for it.
     * A message that's already queued (same sender and id) is kept as it is, and counts as added.
     */
    boolean enqueue(Long receiverId, ChatEvent.Message message);

    /** The receiver's unacked messages, oldest first. */
    List<ChatEvent.Message> pending(Long receiverId);

    /** Removes the message from the receiver's queue; returns true if it was there. */
    boolean ack(Long receiverId, Long senderId, UUID messageId);

    /** receiverId -> that receiver's unacked messages, oldest first. For /health/stats. */
    Map<Long, List<ChatEvent.Message>> all();
}
