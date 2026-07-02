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
}
