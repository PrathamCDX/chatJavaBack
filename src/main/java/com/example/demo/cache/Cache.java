package com.example.demo.cache;

import java.util.Map;
import java.util.Optional;

/**
 * A key-value cache with a fixed capacity. Implementations decide which entry to evict when it's full.
 */
public interface Cache<K, V> {

    /** Returns the value for the key, or empty if it isn't cached. */
    Optional<V> get(K key);

    /** Adds or replaces the value for the key, evicting an entry if the cache is full. */
    void put(K key, V value);

    /** Removes the key; returns true if it was present. */
    boolean remove(K key);

    boolean containsKey(K key);

    int size();

    int capacity();

    void clear();

    /** A copy of all entries, most recently used first. Doesn't count as a use. */
    Map<K, V> snapshot();
}
