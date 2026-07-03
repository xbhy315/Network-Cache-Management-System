package com.cache.client.model;

import java.time.Instant;

public class CacheEntry {

    private String key;
    private String value;
    private long ttlSeconds;
    private Instant createTime;

    public CacheEntry() {
    }

    public CacheEntry(String key, String value, long ttlSeconds) {
        this.key = key;
        this.value = value;
        this.ttlSeconds = ttlSeconds;
        this.createTime = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public boolean isExpired() {
        if (ttlSeconds <= 0) {
            return false;
        }
        return Instant.now().isAfter(createTime.plusSeconds(ttlSeconds));
    }

    /**
     * [组员A] 返回剩余生存时间（秒）。
     * @return 剩余秒数；如果永不过期返回 -1；如果已过期返回 0
     */
    public long getRemainingTtl() {
        if (ttlSeconds <= 0) {
            return -1;
        }
        long elapsed = java.time.Duration.between(createTime, Instant.now()).getSeconds();
        long remaining = ttlSeconds - elapsed;
        return Math.max(0, remaining);
    }
}
