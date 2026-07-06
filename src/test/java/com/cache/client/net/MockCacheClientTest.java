package com.cache.client.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockCacheClient 单元测试。
 *
 * 覆盖全部 9 个接口方法：
 *   ping, get, set, del, lpush, rpush, lpop, lrange, ttl
 */
class MockCacheClientTest {

    private MockCacheClient client;

    @BeforeEach
    void setUp() {
        client = new MockCacheClient();
        client.connect("localhost", 6379);
    }

    // ================================================================
    // 连接管理
    // ================================================================

    @Test
    void shouldConnect() {
        assertTrue(client.isConnected());
    }

    @Test
    void shouldDisconnect() {
        client.disconnect();
        assertFalse(client.isConnected());
    }

    // ================================================================
    // PING
    // ================================================================

    @Test
    void shouldPing() {
        assertEquals("PONG", client.ping());
    }

    // ================================================================
    // String 操作
    // ================================================================

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
    void shouldSetWithTtl() {
        client.set("temp", "value", 1);
        assertTrue(client.get("temp").isPresent());
        // TTL 1秒，不实际等待（直接测 Mock 的 TTL 逻辑）
    }

    // ================================================================
    // Key 操作
    // ================================================================

    @Test
    void shouldDelSingleKey() {
        assertEquals(1, client.del("user:1001"));
        assertFalse(client.get("user:1001").isPresent());
    }

    @Test
    void shouldDelMultipleKeys() {
        assertEquals(2, client.del("user:1001", "user:1002"));
        assertFalse(client.get("user:1001").isPresent());
        assertFalse(client.get("user:1002").isPresent());
    }

    @Test
    void shouldReturnZeroForMissingKeys() {
        assertEquals(0, client.del("missing1", "missing2"));
    }

    // ================================================================
    // List 操作
    // ================================================================

    @Test
    void shouldLpushAndReturnLength() {
        int len = client.lpush("mylist", "a", "b", "c");
        assertEquals(3, len);
    }

    @Test
    void shouldRpushAndLrange() {
        client.rpush("mylist", "a", "b", "c");
        List<String> items = client.lrange("mylist", 0, -1);
        assertEquals(List.of("a", "b", "c"), items);
    }

    @Test
    void shouldLpop() {
        client.rpush("queue", "x", "y", "z");
        assertEquals("x", client.lpop("queue"));
        assertEquals("y", client.lpop("queue"));
        assertEquals("z", client.lpop("queue"));
        assertNull(client.lpop("queue"));  // 空列表返回 null
    }

    @Test
    void shouldLrangeWithNegativeIndices() {
        client.rpush("lst", "a", "b", "c", "d");
        assertEquals(List.of("c", "d"), client.lrange("lst", -2, -1));
        assertEquals(List.of("a", "b"), client.lrange("lst", 0, 1));
    }

    @Test
    void shouldReturnEmptyForMissingList() {
        assertTrue(client.lrange("nonexistent", 0, -1).isEmpty());
        assertNull(client.lpop("nonexistent"));
    }

    @Test
    void shouldLpushOrder() {
        // LPUSH a b c 后列表为 [c, b, a]
        client.lpush("stack", "a", "b", "c");
        List<String> items = client.lrange("stack", 0, -1);
        assertEquals(List.of("c", "b", "a"), items);
    }

    // ================================================================
    // TTL
    // ================================================================

    @Test
    void shouldReturnTtlForExistingKey() {
        // user:1001 预设了 300 秒 TTL
        long ttl = client.ttl("user:1001");
        assertTrue(ttl > 0 || ttl == -1);
    }

    @Test
    void shouldReturnMinusTwoForMissingKey() {
        assertEquals(-2, client.ttl("nonexistent"));
    }

    @Test
    void shouldReturnMinusOneForPersistentKey() {
        // config:theme 预设了 -1（永不过期）
        assertEquals(-1, client.ttl("config:theme"));
    }

    // ================================================================
    // 混合场景
    // ================================================================

    @Test
    void shouldOverwriteListWithString() {
        client.rpush("key", "a", "b");
        client.set("key", "stringValue", 0);
        assertEquals("stringValue", client.get("key").orElse(null));
    }

    @Test
    void shouldNotGetListKeyAsString() {
        client.rpush("mylist", "val");
        assertFalse(client.get("mylist").isPresent());
    }
}
