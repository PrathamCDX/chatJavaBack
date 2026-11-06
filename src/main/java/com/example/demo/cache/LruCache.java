package com.example.demo.cache;

import com.example.demo.cache.DoublyLinkedList.Node;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cache that evicts the least recently used entry when full.
 * Both get and put count as a "use" and move the entry to the most recently used position.
 *
 * A HashMap finds nodes in O(1); a DoublyLinkedList keeps them in usage order
 * (front = most recent, back = least recent), so moving and evicting are O(1) too.
 *
 * Thread safe: every operation that touches the map or the list holds one lock.
 * A read/write lock wouldn't help, because get also writes (it moves the node to the front).
 */
public class LruCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> map = new HashMap<>();
    private final DoublyLinkedList<K, V> list = new DoublyLinkedList<>();
    private final Lock lock = new ReentrantLock();

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public Optional<V> get(K key) {
        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) {
                return Optional.empty();
            }
            list.moveToFront(node);
            return Optional.ofNullable(node.value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node != null) {
                node.value = value;
                list.moveToFront(node);
                return;
            }
            if (map.size() == capacity) {
                Node<K, V> lru = list.removeLast();
                map.remove(lru.key);
            }
            map.put(key, list.addFirst(key, value));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        lock.lock();
        try {
            Node<K, V> node = map.remove(key);
            if (node == null) {
                return false;
            }
            list.remove(node);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // Doesn't count as a use: checking presence shouldn't change eviction order
    @Override
    public boolean containsKey(K key) {
        lock.lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    // final field, never changes: no lock needed
    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
            list.clear();
        } finally {
            lock.unlock();
        }
    }

    // LinkedHashMap keeps the list's order (most recent first)
    @Override
    public Map<K, V> snapshot() {
        lock.lock();
        try {
            Map<K, V> copy = new LinkedHashMap<>();
            list.forEach(copy::put);
            return copy;
        } finally {
            lock.unlock();
        }
    }
}
