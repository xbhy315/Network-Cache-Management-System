package com.cache.client.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 缓存条目数据模型。
 * 支持 STRING 和 LIST 两种类型。
 */
public class CacheEntry {

    public enum EntryType { STRING, LIST }

    private String key;            // 键名
    private EntryType type;        // 数据类型
    private String value;          // STRING 类型的值
    private long ttlSeconds;       // TTL（秒），<=0 表示永不过期
    private Instant createTime;    // 创建时间
    private int listLength;        // LIST 类型的元素个数（仅 LIST 类型有效）

    public CacheEntry() {
        this.type = EntryType.STRING;
        this.createTime = Instant.now();
    }

    public CacheEntry(String key, String value, long ttlSeconds) {
        this.key = key;
        this.type = EntryType.STRING;
        this.value = value;
        this.ttlSeconds = ttlSeconds;
        this.createTime = Instant.now();
    }

    public CacheEntry(String key, EntryType type, long ttlSeconds) {
        this.key = key;
        this.type = type;
        this.ttlSeconds = ttlSeconds;
        this.createTime = Instant.now();
    }

    // ============ Getter / Setter ============

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public EntryType getType() { return type; }
    public void setType(EntryType type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }

    public int getListLength() { return listLength; }
    public void setListLength(int listLength) { this.listLength = listLength; }

    // ============ 业务方法 ============

    /** 判断当前条目是否已过期。ttl<=0 永不过期。 */
    public boolean isExpired() {
        if (ttlSeconds <= 0) return false;
        return Instant.now().isAfter(createTime.plusSeconds(ttlSeconds));
    }

    /** 返回剩余生存秒数；永不过期返回 -1；已过期返回 0。 */
    public long getRemainingTtl() {
        if (ttlSeconds <= 0) return -1;
        long elapsed = Duration.between(createTime, Instant.now()).getSeconds();
        return Math.max(0, ttlSeconds - elapsed);
    }

    @Override
    public String toString() {
        if (type == EntryType.LIST) {
            return key + " : [LIST " + listLength + " items]";
        }
        return key + " : " + value;
    }
}
