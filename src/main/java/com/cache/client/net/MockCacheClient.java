package com.cache.client.net;

import com.cache.client.model.CacheEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Mock 实现 — 用于 UI 先行开发。
 *
 * 在内存中模拟第一组 RESP 服务端行为，支持所有 8+2 个命令。
 * 替换为 RespCacheClient 即可对接真实服务端。
 */
public class MockCacheClient implements CacheServerClient {

    private boolean connected;

    // 存储：STRING 类型
    private final Map<String, CacheEntry> stringStore;

    // 存储：LIST 类型
    private final Map<String, List<String>> listStore;

    // 所有 key 的集合（用于 SCAN 模拟）
    private final Set<String> allKeys;

    public MockCacheClient() {
        stringStore = new ConcurrentHashMap<>();
        listStore = new ConcurrentHashMap<>();
        allKeys = ConcurrentHashMap.newKeySet();

        // 预置演示数据
        putString("user:1001", "Alice", 300);
        putString("user:1002", "Bob", 120);
        putString("session:abc123", "{\"token\":\"xyz\"}", 3600);
        putString("config:theme", "dark", -1);
        putString("rate:limit", "1000", 60);
        putList("queue:tasks", Arrays.asList("task1", "task2", "task3"), -1);
        putList("queue:logs", Arrays.asList("log-a", "log-b", "log-c", "log-d"), -1);
    }

    // ============ 内部辅助 ============

    private void putString(String key, String value, long ttl) {
        stringStore.put(key, new CacheEntry(key, value, ttl));
        allKeys.add(key);
    }

    private void putList(String key, List<String> values, long ttl) {
        listStore.put(key, new CopyOnWriteArrayList<>(values));
        allKeys.add(key);
    }

    /** 判断 key 是 STRING 还是 LIST 类型（仅 Mock 内部使用）。 */
    private boolean isStringKey(String key) {
        return stringStore.containsKey(key);
    }

    private boolean isListKey(String key) {
        return listStore.containsKey(key);
    }

    /** 检查 STRING 是否过期并清理。 */
    private CacheEntry getStringIfValid(String key) {
        CacheEntry entry = stringStore.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            stringStore.remove(key);
            allKeys.remove(key);
            return null;
        }
        return entry;
    }

    // ============ 连接管理 ============

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

    // ============ PING ============

    @Override
    public String ping() {
        return "PONG";
    }

    // ============ String 操作 ============

    @Override
    public Optional<String> get(String key) {
        // 先查 STRING 类型
        CacheEntry entry = getStringIfValid(key);
        if (entry != null) {
            return Optional.of(entry.getValue());
        }
        // LIST 类型不能用 GET
        if (isListKey(key)) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public boolean set(String key, String value, long ttlSeconds) {
        // SET 覆盖 LIST 类型的 key
        listStore.remove(key);
        putString(key, value, ttlSeconds);
        return true;
    }

    // ============ Key 操作 ============

    @Override
    public int del(String... keys) {
        int count = 0;
        for (String key : keys) {
            boolean removedString = stringStore.remove(key) != null;
            boolean removedList = listStore.remove(key) != null;
            if (removedString || removedList) {
                allKeys.remove(key);
                count++;
            }
        }
        return count;
    }

    /**
     * SCAN — 返回所有匹配 pattern 的 key 列表。
     *
     * 由于格式待第一组确认，当前实现为一次性返回全部匹配 key。
     * TODO: 第一组确认游标格式后调整。
     */
    // @Override — 待格式确认后放开
    public List<String> scan(String cursor, String matchPattern) {
        if (matchPattern == null || matchPattern.isEmpty() || "*".equals(matchPattern)) {
            return new ArrayList<>(allKeys);
        }
        return allKeys.stream()
                .filter(k -> match(k, matchPattern))
                .collect(Collectors.toList());
    }

    // ============ List 操作 ============

    @Override
    public int lpush(String key, String... values) {
        List<String> list = listStore.get(key);
        if (list == null) {
            // 如果 key 已存在但为 STRING 类型，先移除
            stringStore.remove(key);
            list = new CopyOnWriteArrayList<>();
            listStore.put(key, list);
            allKeys.add(key);
        }
        // LPUSH 从头部插入：后插入的在前面
        for (String v : values) {
            list.add(0, v);
        }
        return list.size();
    }

    @Override
    public int rpush(String key, String... values) {
        List<String> list = listStore.get(key);
        if (list == null) {
            stringStore.remove(key);
            list = new CopyOnWriteArrayList<>();
            listStore.put(key, list);
            allKeys.add(key);
        }
        list.addAll(Arrays.asList(values));
        return list.size();
    }

    @Override
    public String lpop(String key) {
        List<String> list = listStore.get(key);
        if (list == null || list.isEmpty()) return null;
        String value = list.remove(0);
        if (list.isEmpty()) {
            listStore.remove(key);
            allKeys.remove(key);
        }
        return value;
    }

    @Override
    public List<String> lrange(String key, int start, int stop) {
        List<String> list = listStore.get(key);
        if (list == null) return List.of();

        int len = list.size();
        // 处理负索引
        int realStart = start < 0 ? Math.max(0, len + start) : Math.min(start, len);
        int realStop  = stop  < 0 ? len + stop : Math.min(stop, len - 1);
        if (realStart > realStop) return List.of();

        return list.subList(realStart, realStop + 1);
    }

    // ============ TTL ============

    @Override
    public long ttl(String key) {
        // 查询 STRING 类型
        CacheEntry entry = stringStore.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                stringStore.remove(key);
                allKeys.remove(key);
                return -2;
            }
            if (entry.getTtlSeconds() <= 0) return -1;
            long elapsed = Duration.between(entry.getCreateTime(), Instant.now()).getSeconds();
            return Math.max(0, entry.getTtlSeconds() - elapsed);
        }
        // LIST 类型暂不支持 TTL
        if (isListKey(key)) return -1; // 永不过期
        return -2; // 键不存在
    }

    // ============ 通配符匹配 ============

    private boolean match(String pattern, String key) {
        String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q")
                .replace("?", "\\E.\\Q") + "\\E";
        return key.matches(regex);
    }

    // ============ 仅用于本地 UI 展示（不在接口上） ============

    /**
     * 获取所有条目的本地快照（仅 Mock 模式可用）。
     * RespCacheClient 不实现此方法，MainController 通过 instanceof 判断。
     */
    public List<CacheEntry> getAllLocalEntries() {
        List<CacheEntry> result = new ArrayList<>();

        // 收集 STRING 条目
        for (Map.Entry<String, CacheEntry> e : stringStore.entrySet()) {
            CacheEntry entry = e.getValue();
            if (!entry.isExpired()) {
                result.add(entry);
            } else {
                stringStore.remove(e.getKey());
                allKeys.remove(e.getKey());
            }
        }

        // 收集 LIST 条目
        for (Map.Entry<String, List<String>> e : listStore.entrySet()) {
            CacheEntry entry = new CacheEntry(e.getKey(), CacheEntry.EntryType.LIST, -1);
            entry.setListLength(e.getValue().size());
            result.add(entry);
        }

        return result;
    }
}
