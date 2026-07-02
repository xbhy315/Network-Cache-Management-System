package com.cache.client.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MockCacheClientTest {

    private MockCacheClient client;

    @BeforeEach
    void setUp() {
        client = new MockCacheClient();
        client.connect("localhost", 6379);
    }

    @Test
    void shouldConnect() {
        assertTrue(client.isConnected());
    }

    @Test
    void shouldGetExistingKey() {
        Optional<String> val = client.get("user:1001");
        assertTrue(val.isPresent());
        assertEquals("Alice", val.get());
    }

    @Test
    void shouldReturnEmptyForMissingKey() {
        Optional<String> val = client.get("nonexistent");
        assertFalse(val.isPresent());
    }

    @Test
    void shouldSetAndRetrieve() {
        client.set("new:key", "hello", 0);
        assertEquals("hello", client.get("new:key").orElse(null));
    }

    @Test
    void shouldDeleteKey() {
        assertTrue(client.delete("user:1001"));
        assertFalse(client.get("user:1001").isPresent());
    }

    @Test
    void shouldClearAll() {
        client.clear();
        assertTrue(client.getAll().isEmpty());
    }

    @Test
    void shouldReturnStats() {
        assertFalse(client.stats().isEmpty());
    }

    @Test
    void shouldSupportWildcardSearch() {
        var result = client.keys("user:*");
        assertTrue(result.size() >= 2);
        assertTrue(result.contains("user:1001"));
        assertTrue(result.contains("user:1002"));
    }
}
