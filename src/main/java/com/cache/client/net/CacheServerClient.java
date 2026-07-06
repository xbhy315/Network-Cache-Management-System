package com.cache.client.net;

import com.cache.client.model.CacheEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 缓存服务端操作接口。
 *
 * UI 层依赖此接口而非具体实现，支持 Mock / RESP 双模式。
 * 对应第一组 Linux 缓存服务端支持的 RESP 协议命令。
 */
public interface CacheServerClient {

    // ============ 连接管理 ============

    /** 连接到缓存服务器。 */
    void connect(String host, int port);

    /** 断开连接。 */
    void disconnect();

    /** 当前是否已连接。 */
    boolean isConnected();

    // ============ Server 命令 ============

    /**
     * PING — 测试连通性。
     * @return "PONG"（无参数时）或返回消息内容（有参数时）
     */
    String ping();

    // ============ String 操作 ============

    /**
     * GET — 获取键的值。
     * @return Optional.of(value) 键存在；Optional.empty() 键不存在或已过期
     */
    Optional<String> get(String key);

    /**
     * SET — 设置键值对，可选过期时间。
     * @param key        键
     * @param value      值
     * @param ttlSeconds 过期秒数（<=0 表示永不过期）
     * @return true 设置成功
     */
    boolean set(String key, String value, long ttlSeconds);

    // ============ Key 操作 ============

    /**
     * DEL — 删除一个或多个键。
     * @param keys 要删除的键
     * @return 实际删除的键数量
     */
    int del(String... keys);

    /**
     * SCAN — 遍历匹配模式的键列表。
     *
     * TODO [第一组格式待确认]:
     *   标准 RESP: *2\r\n$3\r\n23\r\n*N\r\nkey1\r\nkey2\r\n...
     *   返回: (nextCursor, keys)
     *   待第一组确认后调整返回类型和参数。
     */
    // List<String> scan(String cursor, String matchPattern);

    // ============ List 操作 ============

    /**
     * LPUSH — 从列表左侧（头部）插入一个或多个元素。
     * @return 插入后的列表总长度
     */
    int lpush(String key, String... values);

    /**
     * RPUSH — 从列表右侧（尾部）插入一个或多个元素。
     * @return 插入后的列表总长度
     */
    int rpush(String key, String... values);

    /**
     * LPOP — 从列表左侧（头部）弹出一个元素。
     * @return 弹出的值；列表为空或不存在返回 null
     */
    String lpop(String key);

    /**
     * LRANGE — 获取列表指定范围的元素（闭区间）。
     * @param start 起始索引（0-based，支持负索引 -1 表示最后）
     * @param stop  结束索引
     * @return 元素列表；列表不存在返回空列表
     */
    List<String> lrange(String key, int start, int stop);

    // ============ TTL 操作 ============

    /**
     * TTL — 查询键的剩余生存时间。
     *
     * TODO [第一组格式待确认]:
     *   标准 REDIS: "TTL key" → :seconds\r\n
     *   :n      剩余 n 秒
     *   :-1     key 存在但永不过期
     *   :-2     key 不存在
     *
     * @param key 要查询的键
     * @return 剩余秒数；-1 永不过期；-2 键不存在
     */
    long ttl(String key);
}
