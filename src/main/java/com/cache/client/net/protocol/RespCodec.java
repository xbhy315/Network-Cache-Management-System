package com.cache.client.net.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        return decodeValue(in);
    }

    private static RespResponse decodeValue(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) {
            throw new IOException("Connection closed by server");
        }

        return switch (firstByte) {
            case '+' -> RespResponse.simpleString(readLine(in));
            case '-' -> RespResponse.error(readLine(in));
            case ':' -> RespResponse.integer(parseLong(readLine(in), "integer"));
            case '$' -> decodeBulkString(in);
            case '*' -> decodeArray(in);
            default -> throw new IOException("Unknown RESP type: " + (char) firstByte);
        };
    }

    private static RespResponse decodeBulkString(InputStream in) throws IOException {
        int length = parseInt(readLine(in), "bulk string length");
        if (length == -1) {
            return RespResponse.nullBulk();
        }
        if (length < -1) {
            throw new IOException("Invalid bulk string length: " + length);
        }

        byte[] data = readExact(in, length, "bulk string");
        expectCrLf(in);
        return RespResponse.bulkString(new String(data, StandardCharsets.UTF_8));
    }

    private static RespResponse decodeArray(InputStream in) throws IOException {
        int count = parseInt(readLine(in), "array length");
        if (count == -1) {
            return RespResponse.nullBulk();
        }
        if (count < -1) {
            throw new IOException("Invalid array length: " + count);
        }

        List<RespResponse> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(decodeValue(in));
        }
        return RespResponse.array(elements);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int current = in.read();
            if (current == -1) {
                throw new IOException("Unexpected end of stream reading RESP line");
            }
            if (current == '\r') {
                int next = in.read();
                if (next == '\n') {
                    return buffer.toString(StandardCharsets.UTF_8);
                }
                if (next == -1) {
                    throw new IOException("Unexpected end of stream reading RESP line ending");
                }
                throw new IOException("Invalid RESP line ending");
            }
            if (current == '\n') {
                throw new IOException("Invalid RESP line ending");
            }
            buffer.write(current);
        }
    }

    private static byte[] readExact(InputStream in, int length, String valueName) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) {
                throw new IOException("Unexpected end of stream reading " + valueName);
            }
            if (read == 0) {
                int next = in.read();
                if (next == -1) {
                    throw new IOException("Unexpected end of stream reading " + valueName);
                }
                data[offset++] = (byte) next;
            } else {
                offset += read;
            }
        }
        return data;
    }

    private static void expectCrLf(InputStream in) throws IOException {
        int carriageReturn = in.read();
        int lineFeed = in.read();
        if (carriageReturn != '\r' || lineFeed != '\n') {
            throw new IOException("Invalid RESP line ending");
        }
    }

    private static int parseInt(String value, String description) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP " + description + ": " + value, e);
        }
    }

    private static long parseLong(String value, String description) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP " + description + ": " + value, e);
        }
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
