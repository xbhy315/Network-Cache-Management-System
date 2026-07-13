package com.cache.client.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockCacheClient 单元测试 — 18 个用例。
 *
 * 覆盖全部 12 个接口方法：
 *   connect, disconnect, isConnected, ping,
 *   get, set, del, ttl,
 *   lpush, rpush, lpop, lrange
 *
 * 测试分类：
 *   连接管理 2  + PING 1  + String 3  + Key 3  + List 5  + TTL 3  + 混合 1
 */
class MockCacheClientTest {

    private MockCacheClient client;

    @BeforeEach
    void setUp() {
        client = new MockCacheClient();
        client.connect("localhost", 6379);
    }

    // ================================================================
    // 连接管理（2 个用例，覆盖 connect / disconnect / isConnected）
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
    // PING（1 个用例）
    // ================================================================

    @Test
    void shouldPing() {
        assertEquals("PONG", client.ping());
    }

    // ================================================================
    // String 操作（3 个用例，覆盖 get / set）
    // ================================================================

    @Test
    void shouldGetExistingKey() {
        Optional<String> val = client.get("user:1001");
        assertTrue(val.isPresent());
        assertEquals("Alice", val.get());
    }

    @Test
    void shouldGetReturnEmptyForMissingKey() {
        assertFalse(client.get("nonexistent").isPresent());
    }

    @Test
    void shouldSetAndGet() {
        // SET 后能 GET 到
        client.set("new:key", "hello", 0);
        assertEquals("hello", client.get("new:key").orElse(null));

        // 持久 key（ttl=0）TTL 返回 -1
        assertEquals(-1, client.ttl("new:key"));

        // SET 覆盖已有 key
        client.set("new:key", "world", 60);
        assertEquals("world", client.get("new:key").orElse(null));
    }

    // ================================================================
    // Key 操作（3 个用例，覆盖 del / ttl）
    // ================================================================

    @Test
    void shouldDelSingleKey() {
        assertEquals(1, client.del("user:1001"));
        assertFalse(client.get("user:1001").isPresent());
    }

    @Test
    void shouldDelMultipleKeys() {
        // 删除 2 个存在的 key
        assertEquals(2, client.del("user:1001", "user:1002"));
        // 删除不存在的 key 返回 0
        assertEquals(0, client.del("nonexistent"));
        // 混合：1 存在 + 1 不存在 = 1
        assertEquals(1, client.del("config:theme", "missing"));
    }

    @Test
    void shouldDelReturnZeroForNoArgs() {
        // 空参调用不崩溃
        assertEquals(0, client.del());
    }

    // ================================================================
    // List 操作（5 个用例，覆盖 lpush / rpush / lpop / lrange）
    // ================================================================

    @Test
    void shouldLpushReturnLengthAndOrder() {
        int len = client.lpush("mylist", "a", "b", "c");
        assertEquals(3, len);
        // LPUSH 后插入的在前面 → [c, b, a]
        assertEquals(List.of("c", "b", "a"), client.lrange("mylist", 0, -1));
    }

    @Test
    void shouldRpushAndLrange() {
        client.rpush("mylist", "a", "b", "c", "d");
        // 全范围
        assertEquals(List.of("a", "b", "c", "d"), client.lrange("mylist", 0, -1));
        // 子范围
        assertEquals(List.of("a", "b"), client.lrange("mylist", 0, 1));
        // 负索引
        assertEquals(List.of("c", "d"), client.lrange("mylist", -2, -1));
        // 越界 — start 超过 len
        assertTrue(client.lrange("mylist", 10, 20).isEmpty());
        // 不存在的列表
        assertTrue(client.lrange("nonexistent", 0, -1).isEmpty());
    }

    @Test
    void shouldLpop() {
        client.rpush("queue", "x", "y", "z");
        assertEquals("x", client.lpop("queue"));
        assertEquals("y", client.lpop("queue"));
        assertEquals("z", client.lpop("queue"));
        // 空列表返回 null
        assertNull(client.lpop("queue"));
        // 不存在的列表返回 null
        assertNull(client.lpop("nonexistent"));
    }

    @Test
    void shouldLpushCreateNewList() {
        // LPUSH 到不存在的 key：自动创建列表
        int len = client.lpush("newlist", "item");
        assertEquals(1, len);
        assertEquals(List.of("item"), client.lrange("newlist", 0, -1));
    }

    @Test
    void shouldRpushCreateNewList() {
        // RPUSH 到不存在的 key：自动创建列表
        int len = client.rpush("newlist", "x", "y");
        assertEquals(2, len);
        assertEquals(List.of("x", "y"), client.lrange("newlist", 0, -1));
    }

    // ================================================================
    // TTL 操作（3 个用例，覆盖 ttl）
    // ================================================================

    @Test
    void shouldTtlExistingKey() {
        // user:1001 预设 300 秒 TTL
        long ttl = client.ttl("user:1001");
        assertTrue(ttl > 0 && ttl <= 300);
    }

    @Test
    void shouldTtlPersistentKey() {
        // config:theme 预设 -1（永不过期）
        assertEquals(-1, client.ttl("config:theme"));
    }

    @Test
    void shouldTtlMissingKey() {
        assertEquals(-2, client.ttl("nonexistent"));
    }

    // ================================================================
    // 混合场景（1 个用例，覆盖类型互转）
    // ================================================================

    @Test
    void shouldTypeConversionWorkCorrectly() {
        // SET 覆盖 LIST
        client.rpush("key", "a", "b");
        client.set("key", "stringValue", 0);
        assertEquals("stringValue", client.get("key").orElse(null));

        // LPUSH 覆盖 STRING
        client.set("key2", "value", 0);
        client.lpush("key2", "item");
        assertEquals(List.of("item"), client.lrange("key2", 0, -1));

        // GET LIST 类型返回 empty
        client.rpush("listkey", "val");
        assertFalse(client.get("listkey").isPresent());
    }
}
