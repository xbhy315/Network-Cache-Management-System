package com.cache.client.net.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RESP 协议编解码器。
 *
 * 请求编码：将命令参数编码为 RESP 数组格式
 *   输入: "SET", "key", "value"
 *   输出: "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n"
 *
 * 响应解码：从输入流读取并解析 RESP 响应
 *   支持: +OK\r\n, -ERR\r\n, :100\r\n, $5\r\nhello\r\n, $-1\r\n, *3\r\n...
 */
public class RespCodec {

    // ================================================================
    // 编码：客户端请求 → RESP 数组
    // ================================================================

    /**
     * 将命令参数编码为 RESP 数组格式的字节数组。
     * @param args 命令及其参数，如 ("SET", "key", "value")
     * @return RESP 编码后的字节数组
     */
    public static byte[] encode(String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.length).append("\r\n");
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(bytes.length).append("\r\n");
            sb.append(arg).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ================================================================
    // 解码：输入流 → RespResponse
    // ================================================================

    /**
     * 从输入流中读取并解析一个 RESP 响应。
     * @param in 从 Socket 获取的输入流
     * @return 解析后的 RespResponse
     * @throws IOException 网络读取错误
     */
    public static RespResponse decode(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        // 读取首行，第一个字符决定类型
        int firstByte = in.read();
        if (firstByte == -1) {
            throw new IOException("Connection closed by server");
        }

        return switch (firstByte) {
            case '+' -> decodeSimpleString(reader);
            case '-' -> decodeError(reader);
            case ':' -> decodeInteger(reader);
            case '$' -> decodeBulkString(reader);
            case '*' -> decodeArray(in, reader);
            default -> throw new IOException("Unknown RESP type: " + (char) firstByte);
        };
    }

    private static RespResponse decodeSimpleString(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Unexpected end of stream");
        return RespResponse.simpleString(line);
    }

    private static RespResponse decodeError(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Unexpected end of stream");
        return RespResponse.error(line);
    }

    private static RespResponse decodeInteger(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Unexpected end of stream");
        return RespResponse.integer(Long.parseLong(line));
    }

    private static RespResponse decodeBulkString(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Unexpected end of stream");
        int length = Integer.parseInt(line);
        if (length == -1) {
            return RespResponse.nullBulk();
        }
        // 读取 length 字节数据 + 末尾 \r\n
        char[] buf = new char[length];
        int read = reader.read(buf, 0, length);
        if (read != length) throw new IOException("Unexpected end of stream reading bulk string");
        reader.read(); // \r
        reader.read(); // \n
        return RespResponse.bulkString(new String(buf));
    }

    private static RespResponse decodeArray(InputStream in, BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Unexpected end of stream");
        int count = Integer.parseInt(line);
        List<RespResponse> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 对每个元素递归调用 decode（直接读 in 而非 reader，因为 reader 有缓冲可能多读）
            elements.add(decode(in));
        }
        return RespResponse.array(elements);
    }

    // ================================================================
    // 便捷方法
    // ================================================================

    /**
     * 编码并直接返回字符串形式（用于日志/调试）。
     */
    public static String encodeToString(String... args) {
        return new String(encode(args), StandardCharsets.UTF_8);
    }
}
