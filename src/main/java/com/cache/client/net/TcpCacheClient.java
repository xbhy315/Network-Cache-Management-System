package com.cache.client.net;

import com.cache.client.model.CacheEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * [组员B] TcpCacheClient — TCP 通信实现（TLV 协议）。
 *
 * 负责：
 * - 通过 TLV 协议与第一组的 Linux 缓存服务端通信
 * - 实现 CacheServerClient 接口的所有方法
 *
 * 开发顺序：
 * 1. 先实现 TLV 编解码方法（encode/decode）
 * 2. 再实现 connect/disconnect/isConnected
 * 3. 最后逐个实现 get/set/delete/exists/expire/ttl/type 等命令
 *
 * 参考协议格式（第一组答辩文档）：
 *   Length(4B) | Type(1B) | ArgCnt(1B) | Args(变长)
 *   Type: 0x01=Request, 0x02=Response, 0x03=Error
 *   Args: 每个参数 = [2字节长度][数据]
 */
public class TcpCacheClient implements CacheServerClient {

    // ================================================================
    // [组员B] TLV 编解码 — 核心
    // ================================================================

    /**
     * 将命令参数编码为 TLV 二进制请求。
     * @param args 命令及其参数，如 ["GET", "key1"]
     * @return TLV 编码后的字节数组
     */
    public byte[] encode(String... args) {
        // TODO [组员B]: 按 TLV 协议格式编码请求
        // 提示:
        //   1. body 部分 = [ArgCnt(1B)] + 每个参数[2字节长度][数据]
        //   2. header = [Length(4B, 大端)] + [Type(1B=0x01)]
        //   3. 拼接 header + body
        return new byte[0];
    }

    /**
     * 解码服务端返回的 TLV 二进制响应。
     * @param data 收到的完整 TLV 数据
     * @return 解码后的响应参数列表（第一个元素通常是状态或值）
     */
    public List<String> decode(byte[] data) {
        // TODO [组员B]: 按 TLV 协议格式解码响应
        // 提示:
        //   1. 读取前 4 字节 Length（大端）
        //   2. 第 5 字节 Type（0x02=成功, 0x03=错误）
        //   3. 第 6 字节 ArgCnt
        //   4. 循环读取每个参数 [2字节长度][数据]
        //   5. Type=0x03 时应抛出异常
        return List.of();
    }

    // ================================================================
    // [组员B] 连接管理
    // ================================================================

    private java.net.Socket socket;
    private java.io.InputStream in;
    private java.io.OutputStream out;

    @Override
    public void connect(String host, int port) {
        // TODO [组员B]: 创建 Socket 连接，获取输入/输出流
    }

    @Override
    public void disconnect() {
        // TODO [组员B]: 关闭 Socket 和流
    }

    @Override
    public boolean isConnected() {
        // TODO [组员B]: 检查 Socket 连接状态
        return false;
    }

    // ================================================================
    // [组员B] 命令实现 — 通过 TLV 协议收发
    // ================================================================

    private String executeCommand(String... args) {
        // TODO [组员B]: 通用命令执行方法
        //   1. 调用 encode(args) 编码请求
        //   2. 通过 out 发送
        //   3. 通过 in 读取响应
        //   4. 调用 decode() 解码并返回
        return null;
    }

    @Override
    public Optional<String> get(String key) {
        // TODO [组员B]: 发送 GET 命令
        return Optional.empty();
    }

    @Override
    public boolean set(String key, String value, long ttlSeconds) {
        // TODO [组员B]: 发送 SET 命令（含可选 EX 参数）
        return false;
    }

    @Override
    public boolean delete(String key) {
        // TODO [组员B]: 发送 DEL 命令
        return false;
    }

    @Override
    public List<String> keys(String pattern) {
        // TODO [组员B]: 发送 KEYS 命令
        return List.of();
    }

    @Override
    public Map<String, CacheEntry> getAll() {
        // TODO [组员B]: 可通过 KEYS * 遍历或第一组提供的批量接口
        return Map.of();
    }

    @Override
    public boolean clear() {
        // TODO [组员B]: 发送 CLEAR 或 FLUSHDB 命令
        return false;
    }

    @Override
    public Map<String, String> stats() {
        // TODO [组员B]: 发送 STATS 或 INFO 命令
        return Map.of();
    }

    // ================================================================
    // [组员B] 新增接口实现
    // ================================================================

    @Override
    public boolean exists(String key) {
        // TODO [组员B]: 发送 EXISTS 命令
        return false;
    }

    @Override
    public boolean expire(String key, long seconds) {
        // TODO [组员B]: 发送 EXPIRE 命令
        return false;
    }

    @Override
    public long ttl(String key) {
        // TODO [组员B]: 发送 TTL 命令
        return -2;
    }

    @Override
    public String type(String key) {
        // TODO [组员B]: 发送 TYPE 命令
        return "none";
    }
}
