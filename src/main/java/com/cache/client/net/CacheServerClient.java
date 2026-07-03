package com.cache.client.net;

import com.cache.client.model.CacheEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface defining operations exposed by the cache server (Topic 1).
 * The FXML controller layer depends on this interface, not on concrete implementations.
 */
public interface CacheServerClient {

    /** Connect to the cache server. */
    void connect(String host, int port);

    /** Disconnect from the cache server. */
    void disconnect();

    /** Whether the connection is currently alive. */
    boolean isConnected();

    /** Retrieve a value by key. */
    Optional<String> get(String key);

    /** Set a key-value pair with optional TTL (seconds, <=0 means no expiry). */
    boolean set(String key, String value, long ttlSeconds);

    /** Delete a key. */
    boolean delete(String key);

    /** List all keys matching a pattern (supports wildcard: *, ?). */
    List<String> keys(String pattern);

    /** Retrieve all entries as a map. */
    Map<String, CacheEntry> getAll();

    /** Clear all cache entries. */
    boolean clear();

    /** Return server-side statistics. */
    Map<String, String> stats();

    // ================================================================
    // [组长] 新增接口方法 — 各组员按分工实现各自部分
    // ================================================================

    /** Check whether a key exists (and has not expired). */
    boolean exists(String key);

    /**
     * Set a time-to-live (in seconds) on an existing key.
     * @param key     the existing key
     * @param seconds TTL in seconds (<= 0 means remove expiry)
     * @return true if the key existed and TTL was set
     */
    boolean expire(String key, long seconds);

    /**
     * Get the remaining time-to-live of a key, in seconds.
     * @param key the key to query
     * @return remaining TTL in seconds; -1 if key has no expiry; -2 if key does not exist
     */
    long ttl(String key);

    /**
     * Return the data type of the stored value for the given key.
     * Typical return values: "string", "list", "hash", "none" (if key does not exist).
     */
    String type(String key);
}
