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

    // ================================================================
    // [组员C] 新增接口测试 — 实现以下测试方法
    // ================================================================

    @Test
    void shouldCheckKeyExists() {
        // TODO [组员C]: 测试 exists() 方法
        // 1. 已存在的 key → true
        // 2. 不存在的 key → false
    }

    @Test
    void shouldSetExpiryOnExistingKey() {
        // TODO [组员C]: 测试 expire() 方法
        // 1. 对已存在的 key 设置 TTL → true
        // 2. 对不存在的 key 设置 TTL → false
        // 3. 设置后查询 ttl() 应返回正确剩余秒数
    }

    @Test
    void shouldReturnTtlForExistingKey() {
        // TODO [组员C]: 测试 ttl() 方法
        // 1. 有过期时间的 key → 返回剩余秒数 (>0)
        // 2. 永不过期的 key → 返回 -1
        // 3. 不存在的 key → 返回 -2
    }

    @Test
    void shouldReturnTypeOfKey() {
        // TODO [组员C]: 测试 type() 方法
        // 1. 存在的 key → "string"
        // 2. 不存在的 key → "none"
    }

    @Test
    void shouldHandleExpiredKeyGracefully() {
        // TODO [组员C]: 测试过期 key 的边界场景
        // 1. 设置一个极短 TTL 的 key
        // 2. 等待过期后 exists/get/ttl 均应返回"不存在"
    }
}
