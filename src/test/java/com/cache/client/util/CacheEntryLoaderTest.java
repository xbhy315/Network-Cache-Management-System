package com.cache.client.util;

import com.cache.client.model.CacheEntry;
import com.cache.client.net.CacheServerClient;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheEntryLoaderTest {

    @Test
    void shouldLoadStringAndListEntriesFromRespClient() {
        FakeClient client = new FakeClient();
        client.keys = List.of("user:1", "queue:tasks");
        client.values.put("user:1", "Alice");
        client.lists.put("queue:tasks", List.of("task1", "task2"));
        client.ttls.put("user:1", 60L);
        client.ttls.put("queue:tasks", -1L);

        List<CacheEntry> entries = CacheEntryLoader.load(client);

        assertEquals("*", client.scannedPattern);
        assertEquals(2, entries.size());
        assertEquals(CacheEntry.EntryType.STRING, entries.get(0).getType());
        assertEquals("Alice", entries.get(0).getValue());
        assertEquals(60, entries.get(0).getTtlSeconds());
        assertEquals(CacheEntry.EntryType.LIST, entries.get(1).getType());
        assertEquals(2, entries.get(1).getListLength());
        assertEquals(-1, entries.get(1).getTtlSeconds());
    }

    @Test
    void shouldSkipKeyThatExpiredAfterScan() {
        FakeClient client = new FakeClient();
        client.keys = List.of("expired");
        client.ttls.put("expired", -2L);

        assertEquals(List.of(), CacheEntryLoader.load(client));
        assertEquals(0, client.getCalls);
    }

    private static final class FakeClient implements CacheServerClient {
        private List<String> keys = List.of();
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, List<String>> lists = new HashMap<>();
        private final Map<String, Long> ttls = new HashMap<>();
        private String scannedPattern;
        private int getCalls;

        @Override
        public List<String> scan(String pattern) {
            scannedPattern = pattern;
            return keys;
        }

        @Override
        public long ttl(String key) {
            return ttls.getOrDefault(key, -2L);
        }

        @Override
        public Optional<String> get(String key) {
            getCalls++;
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public List<String> lrange(String key, int start, int stop) {
            return lists.getOrDefault(key, List.of());
        }

        @Override public void connect(String host, int port) { }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public String ping() { return "PONG"; }
        @Override public boolean set(String key, String value, long ttlSeconds) { return false; }
        @Override public int del(String... keys) { return 0; }
        @Override public int lpush(String key, String... values) { return 0; }
        @Override public int rpush(String key, String... values) { return 0; }
        @Override public String lpop(String key) { return null; }
        @Override public boolean reconnect() { return true; }
        @Override public String getLastHost() { return "fake"; }
        @Override public int getLastPort() { return 6379; }
    }
}
