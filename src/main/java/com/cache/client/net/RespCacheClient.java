package com.cache.client.net;

import com.cache.client.CacheClientApp;
import com.cache.client.model.CacheEntry;
import com.cache.client.net.protocol.RespCodec;
import com.cache.client.net.protocol.RespResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * [组员B] RespCacheClient — RESP 协议 TCP 实现。
 *
 * 负责：
 * - 通过 RESP 协议与第一组的 Linux 缓存服务端通信
 * - 实现 CacheServerClient 接口的所有方法
 * - 使用 RespCodec 进行 RESP 编解码
 *
 * 替换 MockCacheClient 即可对接真实服务端。
 */
public class RespCacheClient implements CacheServerClient {

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean connected;

    // ================================================================
    // 连接管理
    // ================================================================

    @Override
    public void connect(String host, int port) {
        try {
            int timeout = CacheClientApp.getConfig().getConnectTimeout();
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            connected = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        try {
            connected = false;
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // 忽略关闭时的异常
        }
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ================================================================
    // 通用收发
    // ================================================================

    /**
     * 发送 RESP 请求并返回解码后的响应。
     */
    private RespResponse execute(String... args) {
        try {
            byte[] request = RespCodec.encode(args);
            out.write(request);
            out.flush();
            return RespCodec.decode(in);
        } catch (IOException e) {
            throw new RuntimeException("RESP command failed: " + String.join(" ", args), e);
        }
    }

    /**
     * 发送命令，期望返回 +OK 或 -ERR。
     */
    private void expectOk(String... args) {
        RespResponse resp = execute(args);
        if (resp.isError()) {
            throw new RuntimeException("Command failed: " + resp.getError());
        }
    }

    /**
     * 发送命令，返回整数值。
     */
    private long expectInteger(String... args) {
        RespResponse resp = execute(args);
        if (resp.isError()) {
            throw new RuntimeException("Command failed: " + resp.getError());
        }
        return resp.asInteger();
    }

    /**
     * 发送命令，返回批量字符串值（可能为 null）。
     */
    private String expectBulkString(String... args) {
        RespResponse resp = execute(args);
        if (resp.isError()) {
            throw new RuntimeException("Command failed: " + resp.getError());
        }
        return resp.asString();
    }

    /**
     * 发送命令，返回字符串数组。
     */
    private List<String> expectArray(String... args) {
        RespResponse resp = execute(args);
        if (resp.isError()) {
            throw new RuntimeException("Command failed: " + resp.getError());
        }
        if (resp.isNull()) return List.of();
        List<RespResponse> elements = resp.asArray();
        List<String> result = new ArrayList<>(elements.size());
        for (RespResponse elem : elements) {
            result.add(elem.asString());
        }
        return result;
    }

    // ================================================================
    // PING
    // ================================================================

    @Override
    public String ping() {
        RespResponse resp = execute("PING");
        // PING 无参数返回 +PONG（SIMPLE_STRING），有参数返回 $msg（BULK_STRING）
        return resp.asString();
    }

    // ================================================================
    // String 操作
    // ================================================================

    @Override
    public Optional<String> get(String key) {
        RespResponse resp = execute("GET", key);
        if (resp.isNull()) return Optional.empty();
        return Optional.ofNullable(resp.asString());
    }

    @Override
    public boolean set(String key, String value, long ttlSeconds) {
        if (ttlSeconds > 0) {
            expectOk("SET", key, value, "EX", String.valueOf(ttlSeconds));
        } else {
            expectOk("SET", key, value);
        }
        return true;
    }

    // ================================================================
    // Key 操作
    // ================================================================

    @Override
    public int del(String... keys) {
        String[] args = new String[keys.length + 1];
        args[0] = "DEL";
        System.arraycopy(keys, 0, args, 1, keys.length);
        return (int) expectInteger(args);
    }

    /**
     * SCAN — 遍历匹配模式的键列表。
     *
     * TODO [第一组格式待确认]:
     *   当前实现为占位，待第一组确认 SCAN 的 RESP 返回格式后实现。
     *   标准 Redis SCAN 返回:
     *     *2\r\n
     *     $3\r\n23\r\n       ← 下一个游标
     *     *N\r\nk1\r\nk2\r\n...  ← 本批 key 列表
     */
    // @Override — 待格式确认后放开
    public List<String> scan(String cursor, String matchPattern) {
        // TODO [组员B]: 待第一组确认 SCAN 格式后实现
        // 预期实现:
        //   RespResponse resp = execute("SCAN", cursor, "MATCH", matchPattern);
        //   return expectArray(resp);  // 解析数组元素
        throw new UnsupportedOperationException("SCAN not yet implemented - waiting for protocol confirmation");
    }

    // ================================================================
    // List 操作
    // ================================================================

    @Override
    public int lpush(String key, String... values) {
        String[] args = new String[values.length + 1];
        args[0] = "LPUSH";
        args[1] = key;
        System.arraycopy(values, 0, args, 2, values.length);
        return (int) expectInteger(args);
    }

    @Override
    public int rpush(String key, String... values) {
        String[] args = new String[values.length + 1];
        args[0] = "RPUSH";
        args[1] = key;
        System.arraycopy(values, 0, args, 2, values.length);
        return (int) expectInteger(args);
    }

    @Override
    public String lpop(String key) {
        return expectBulkString("LPOP", key);
    }

    @Override
    public List<String> lrange(String key, int start, int stop) {
        return expectArray("LRANGE", key, String.valueOf(start), String.valueOf(stop));
    }

    // ================================================================
    // TTL
    // ================================================================

    @Override
    public long ttl(String key) {
        // TODO [第一组格式待确认]:
        //   标准 Redis TTL 返回:
        //     :n    剩余 n 秒
        //     :-1   key 存在但无过期
        //     :-2   key 不存在
        // 待第一组确认后实现
        RespResponse resp = execute("TTL", key);
        if (resp.isError()) return -2;
        return resp.asInteger();
    }
}
