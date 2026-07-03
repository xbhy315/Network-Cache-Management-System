package com.cache.client.net;

import com.cache.client.model.CacheEntry;

import com.cache.client.CacheClientApp;
import com.cache.client.util.Config;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation for UI-first development.
 * Replace with TcpCacheClient once the communication protocol is confirmed.
 */
public class MockCacheClient implements CacheServerClient {

    private boolean connected;
    private final Map<String, CacheEntry> store;

    public MockCacheClient() {
        Config config = CacheClientApp.getConfig();
        int maxEntries = (config != null) ? config.getMockMaxEntries() : 10000;
        store = new ConcurrentHashMap<>(maxEntries);

        // Seed some mock data
        put("user:1001", "Alice", 300);
        put("user:1002", "Bob", 120);
        put("session:abc123", "{\"token\":\"xyz\"}", 3600);
        put("config:theme", "dark", -1);
        put("rate:limit", "1000", 60);
    }

    private void put(String key, String value, long ttl) {
        store.put(key, new CacheEntry(key, value, ttl));
    }

    @Override
    public void connect(String host, int port) {
        connected = true;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public Optional<String> get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.getValue());
    }

    @Override
    public boolean set(String key, String value, long ttlSeconds) {
        store.put(key, new CacheEntry(key, value, ttlSeconds));
        return true;
    }

    @Override
    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    @Override
    public List<String> keys(String pattern) {
        List<String> result = new ArrayList<>();
        for (String key : store.keySet()) {
            if (match(pattern, key)) {
                result.add(key);
            }
        }
        return result;
    }

    @Override
    public Map<String, CacheEntry> getAll() {
        return Collections.unmodifiableMap(store);
    }

    @Override
    public boolean clear() {
        store.clear();
        return true;
    }

    @Override
    public Map<String, String> stats() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("entries", String.valueOf(store.size()));
        s.put("memory", "~1.2 MB");
        s.put("hit_rate", "87.3%");
        s.put("connections", "1");
        s.put("uptime", "2h 15m");
        return s;
    }

    // ================================================================
    // [各组员] 新增接口方法 — Mock 实现（各组员完成各自部分）
    // ================================================================

    @Override
    public boolean exists(String key) {
        // TODO [组员A / 组员C]: 检查 key 是否存在且未过期
        // 提示: 参考 get() 方法的过期检查逻辑
        CacheEntry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            store.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public boolean expire(String key, long seconds) {
        // TODO [组员A]: 为指定 key 重新设置过期时间
        // 提示: 获取已有 CacheEntry，修改其 ttlSeconds 和 createTime
        CacheEntry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            store.remove(key);
            return false;
        }
        entry.setTtlSeconds(seconds);
        entry.setCreateTime(Instant.now());
        return true;
    }

    @Override
    public long ttl(String key) {
        // TODO [组员A]: 返回 key 的剩余生存时间（秒）
        // 提示: 计算 createTime + ttlSeconds - now
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return -2;  // key 不存在
        }
        if (entry.isExpired()) {
            store.remove(key);
            return -2;
        }
        if (entry.getTtlSeconds() <= 0) {
            return -1;  // 永不过期
        }
        long elapsed = Duration.between(entry.getCreateTime(), Instant.now()).getSeconds();
        long remaining = entry.getTtlSeconds() - elapsed;
        return Math.max(0, remaining);
    }

    @Override
    public String type(String key) {
        // TODO [组员C]: 返回 key 存储的数据类型
        // 当前所有 value 都是 String，返回 "string"
        // 后续可根据第一组的 Hash/List 类型扩展
        if (store.containsKey(key)) {
            return "string";
        }
        return "none";
    }

    private boolean match(String pattern, String key) {
        String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q")
                                     .replace("?", "\\E.\\Q") + "\\E";
        return key.matches(regex);
    }
}
