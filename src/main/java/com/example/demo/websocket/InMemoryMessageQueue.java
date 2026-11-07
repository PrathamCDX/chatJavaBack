package com.example.demo.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory MessageQueue: receiverId -> that receiver's messages, oldest first.
 * Queued messages are lost on restart and only known to this server.
 *
 * Messages are keyed by sender as well as id, because ids come from clients: otherwise one
 * sender could overwrite or block another's queued message by reusing its id.
 */
@Component
public class InMemoryMessageQueue implements MessageQueue {

    // Bounded, so messaging offline users can't use up memory. Unlike tickets, a full queue
    // refuses new messages instead of evicting: the sender is told, and nothing vanishes silently
    private static final int MAX_MESSAGES_PER_USER = 100;
    private static final int MAX_MESSAGES = 10_000;
    private static final Duration MAX_AGE = Duration.ofHours(24);

    private record Key(Long senderId, UUID id) {
    }

    private final Map<Long, Map<Key, ChatEvent.Message>> queues = new HashMap<>();
    private final Lock lock = new ReentrantLock();
    private int size = 0;

    @Override
    public boolean enqueue(Long receiverId, ChatEvent.Message message) {
        Key key = new Key(message.senderId(), message.id());
        lock.lock();
        try {
            Map<Key, ChatEvent.Message> queue = queues.getOrDefault(receiverId, Map.of());
            if (queue.containsKey(key)) {
                return true;
            }
            if (queue.size() >= MAX_MESSAGES_PER_USER || size >= MAX_MESSAGES) {
                return false;
            }
            queues.computeIfAbsent(receiverId, id -> new LinkedHashMap<>()).put(key, message);
            size++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ChatEvent.Message> pending(Long receiverId) {
        lock.lock();
        try {
            return List.copyOf(queues.getOrDefault(receiverId, Map.of()).values());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean ack(Long receiverId, Long senderId, UUID messageId) {
        lock.lock();
        try {
            Map<Key, ChatEvent.Message> queue = queues.get(receiverId);
            if (queue == null || queue.remove(new Key(senderId, messageId)) == null) {
                return false;
            }
            size--;
            if (queue.isEmpty()) {
                queues.remove(receiverId);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<Long, List<ChatEvent.Message>> all() {
        lock.lock();
        try {
            Map<Long, List<ChatEvent.Message>> copy = new TreeMap<>();
            queues.forEach((receiverId, queue) -> copy.put(receiverId, List.copyOf(queue.values())));
            return copy;
        } finally {
            lock.unlock();
        }
    }

    // Otherwise the messages of a user who never comes back would hold their slots forever
    @Scheduled(fixedRate = 60_000)
    void removeExpired() {
        Instant cutoff = Instant.now().minus(MAX_AGE);
        lock.lock();
        try {
            queues.values().removeIf(queue -> {
                int before = queue.size();
                queue.values().removeIf(message -> message.sentAt().isBefore(cutoff));
                size -= before - queue.size();
                return queue.isEmpty();
            });
        } finally {
            lock.unlock();
        }
    }
}
