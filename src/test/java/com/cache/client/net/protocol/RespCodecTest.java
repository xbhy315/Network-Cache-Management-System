package com.cache.client.net.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RespCodec 编解码单元测试。
 *
 * 覆盖全部 5 种 RESP 响应类型 + 编码 + 边界情况。
 */
class RespCodecTest {

    // ================================================================
    // 编码测试
    // ================================================================

    @Test
    void shouldEncodeSimpleCommand() {
        byte[] encoded = RespCodec.encode("PING");
        String expected = "*1\r\n$4\r\nPING\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeGetCommand() {
        byte[] encoded = RespCodec.encode("GET", "key");
        String expected = "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeSetCommand() {
        byte[] encoded = RespCodec.encode("SET", "name", "Alice");
        String expected = "*3\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAlice\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeSetWithEx() {
        byte[] encoded = RespCodec.encode("SET", "key", "value", "EX", "60");
        String expected = "*5\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n$2\r\nEX\r\n$2\r\n60\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeLpushCommand() {
        byte[] encoded = RespCodec.encode("LPUSH", "q", "a", "b", "c");
        String expected = "*4\r\n$5\r\nLPUSH\r\n$1\r\nq\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    // ================================================================
    // 解码测试 — 简单字符串
    // ================================================================

    @Test
    void shouldDecodeSimpleString() throws Exception {
        String resp = "+OK\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.SIMPLE_STRING, result.getType());
        assertEquals("OK", result.asString());
    }

    @Test
    void shouldDecodePong() throws Exception {
        String resp = "+PONG\r\n";
        RespResponse result = decode(resp);
        assertEquals("PONG", result.asString());
    }

    // ================================================================
    // 解码测试 — 错误
    // ================================================================

    @Test
    void shouldDecodeError() throws Exception {
        String resp = "-ERR unknown command\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.ERROR, result.getType());
        assertEquals("unknown command", result.getError());
    }

    // ================================================================
    // 解码测试 — 整数
    // ================================================================

    @Test
    void shouldDecodeInteger() throws Exception {
        String resp = ":3\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.INTEGER, result.getType());
        assertEquals(3, result.asInteger());
    }

    @Test
    void shouldDecodeZeroInteger() throws Exception {
        String resp = ":0\r\n";
        RespResponse result = decode(resp);
        assertEquals(0, result.asInteger());
    }

    @Test
    void shouldDecodeNegativeInteger() throws Exception {
        String resp = ":-2\r\n";
        RespResponse result = decode(resp);
        assertEquals(-2, result.asInteger());
    }

    // ================================================================
    // 解码测试 — 批量字符串
    // ================================================================

    @Test
    void shouldDecodeBulkString() throws Exception {
        String resp = "$5\r\nhello\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.BULK_STRING, result.getType());
        assertEquals("hello", result.asString());
    }

    @Test
    void shouldDecodeNullBulkString() throws Exception {
        String resp = "$-1\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.NULL, result.getType());
        assertNull(result.asString());
        assertTrue(result.isNull());
    }

    @Test
    void shouldDecodeEmptyBulkString() throws Exception {
        String resp = "$0\r\n\r\n";
        RespResponse result = decode(resp);
        assertEquals("", result.asString());
    }

    // ================================================================
    // 解码测试 — 数组
    // ================================================================

    @Test
    void shouldDecodeArray() throws Exception {
        String resp = "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.ARRAY, result.getType());
        List<RespResponse> elements = result.asArray();
        assertEquals(2, elements.size());
        assertEquals("GET", elements.get(0).asString());
        assertEquals("key", elements.get(1).asString());
    }

    @Test
    void shouldDecodeEmptyArray() throws Exception {
        String resp = "*0\r\n";
        RespResponse result = decode(resp);
        assertEquals(RespResponse.Type.ARRAY, result.getType());
        assertTrue(result.asArray().isEmpty());
    }

    @Test
    void shouldDecodeArrayOfBulkStrings() throws Exception {
        // LRANGE 响应: *3\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n
        String resp = "*3\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n";
        RespResponse result = decode(resp);
        List<RespResponse> elements = result.asArray();
        assertEquals(List.of("a", "b", "c"),
                elements.stream().map(RespResponse::asString).toList());
    }

    // ================================================================
    // 编解码集成
    // ================================================================

    @Test
    void codecRoundTrip() throws Exception {
        // 编码 GET 命令
        byte[] encoded = RespCodec.encode("GET", "name");

        // 模拟服务端响应（正常情况服务端返回数据，这里仅验证编码格式）
        String encodedStr = new String(encoded, StandardCharsets.UTF_8);
        assertTrue(encodedStr.startsWith("*2\r\n$3\r\nGET\r\n$4\r\nname\r\n"));

        // 验证响应解析
        String response = "$5\r\nAlice\r\n";
        RespResponse result = decode(response);
        assertEquals("Alice", result.asString());
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private RespResponse decode(String respData) throws Exception {
        InputStream in = new ByteArrayInputStream(respData.getBytes(StandardCharsets.UTF_8));
        return RespCodec.decode(in);
    }
}
