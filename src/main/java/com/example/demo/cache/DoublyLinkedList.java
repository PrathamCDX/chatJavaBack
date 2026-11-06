package com.example.demo.cache;

import java.util.function.BiConsumer;

/**
 * Doubly linked list of key-value nodes, used by LruCache to keep entries in usage order
 * (front = most recently used, back = least recently used).
 *
 * Callers keep references to the nodes it returns, so removing or moving a node is O(1).
 * Not thread safe: LruCache holds its lock around every call.
 */
class DoublyLinkedList<K, V> {

    static final class Node<K, V> {
        final K key;
        V value;
        private Node<K, V> prev;
        private Node<K, V> next;

        private Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // Sentinels: real nodes always sit between them, so we never have to null-check prev/next
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);
    private int size;

    DoublyLinkedList() {
        head.next = tail;
        tail.prev = head;
    }

    /** Creates a node for the entry, puts it at the front and returns it. */
    Node<K, V> addFirst(K key, V value) {
        Node<K, V> node = new Node<>(key, value);
        linkFirst(node);
        size++;
        return node;
    }

    void remove(Node<K, V> node) {
        unlink(node);
        size--;
    }

    void moveToFront(Node<K, V> node) {
        unlink(node);
        linkFirst(node);
    }

    /** Removes and returns the back node, or null if the list is empty. */
    Node<K, V> removeLast() {
        if (size == 0) {
            return null;
        }
        Node<K, V> last = tail.prev;
        remove(last);
        return last;
    }

    int size() {
        return size;
    }

    void clear() {
        head.next = tail;
        tail.prev = head;
        size = 0;
    }

    /** Visits every entry from front (most recent) to back. */
    void forEach(BiConsumer<K, V> action) {
        for (Node<K, V> node = head.next; node != tail; node = node.next) {
            action.accept(node.key, node.value);
        }
    }

    private void linkFirst(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }
}
