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
import java.util.Locale;
import java.util.Optional;

/**
 * [组员B] RespCacheClient — RESP 协议 TCP 实现。
 *
 * 负责：
 * - 通过 RESP 协议与第二组的 Linux 缓存服务端通信
 * - 实现 CacheServerClient 接口的所有方法
 * - 使用 RespCodec 进行 RESP 编解码
 *
 * 替换 MockCacheClient 即可对接真实服务端。
 */
public class RespCacheClient implements CacheServerClient {

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected;

    /** 上次成功连接的地址 — 用于自动重连。 */
    private String lastHost;
    private int lastPort;

    /** 串行化 socket 操作，防止多线程并发读写。 */
    private final Object lock = new Object();

    // ================================================================
    // 连接管理
    // ================================================================

    @Override
    public void connect(String host, int port) {
        synchronized (lock) {
            try {
                int timeout = CacheClientApp.getConfig().getConnectTimeout();
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), timeout);
                socket.setSoTimeout(timeout);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                connected = true;
                lastHost = host;
                lastPort = port;
            } catch (IOException e) {
                throw new RuntimeException("Failed to connect to " + host + ":" + port, e);
            }
        }
    }

    @Override
    public void disconnect() {
        synchronized (lock) {
            try {
                connected = false;
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                // 忽略关闭时的异常
            } finally {
                in = null;
                out = null;
                socket = null;
            }
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
     * 线程安全：通过 lock 串行化 socket 操作。
     * 断线检测：捕获 IOException 时自动标记 connected=false。
     */
    private RespResponse execute(String... args) {
        synchronized (lock) {
            try {
                if (out == null) {
                    throw new IOException("Client not connected — call connect() first");
                }
                byte[] request = RespCodec.encode(args);
                out.write(request);
                out.flush();
                return RespCodec.decode(in);
            } catch (IOException e) {
                connected = false;
                throw new RuntimeException("RESP command failed: " + String.join(" ", args), e);
            }
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
        if (resp.isError()) {
            String error = resp.getError();
            if (error != null) {
                String upper = error.toUpperCase(Locale.ROOT);
                if (upper.contains("WRONGTYPE") || upper.contains("WRONG TYPE")) {
                    return Optional.empty();
                }
            }
            throw new RuntimeException("Command failed: " + error);
        }
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
     * 第二组已确认格式（非游标式，一次性返回全部匹配 key）。
     * pattern 为 null / "" / "*" 时发送无参数 SCAN。
     */
    @Override
    public List<String> scan(String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) {
            return expectArray("SCAN");
        }
        return expectArray("SCAN", pattern);
    }

    // ================================================================
    // List 操作
    // ================================================================

    @Override
    public int lpush(String key, String... values) {
        String[] args = new String[values.length + 2];
        args[0] = "LPUSH";
        args[1] = key;
        System.arraycopy(values, 0, args, 2, values.length);
        return (int) expectInteger(args);
    }

    @Override
    public int rpush(String key, String... values) {
        String[] args = new String[values.length + 2];
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
        return expectInteger("TTL", key);
    }

    // ================================================================
    // Reconnect
    // ================================================================

    @Override
    public boolean reconnect() {
        if (lastHost == null) return false;
        try {
            disconnect(); // 先关闭旧 socket，防止连接泄漏
            connect(lastHost, lastPort);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLastHost() {
        return lastHost;
    }

    @Override
    public int getLastPort() {
        return lastPort;
    }
}
