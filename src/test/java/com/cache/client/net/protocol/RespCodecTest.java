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
    // 编码测试（6 个）
    // ================================================================

    @Test
    void shouldEncodePing() {
        byte[] encoded = RespCodec.encode("PING");
        String expected = "*1\r\n$4\r\nPING\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeGet() {
        byte[] encoded = RespCodec.encode("GET", "key");
        String expected = "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeSet() {
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
    void shouldEncodeLpush() {
        byte[] encoded = RespCodec.encode("LPUSH", "q", "a", "b", "c");
        String expected = "*5\r\n$5\r\nLPUSH\r\n$1\r\nq\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n";
        assertEquals(expected, new String(encoded, StandardCharsets.UTF_8));
    }

    // ================================================================
    // 解码测试 — 简单字符串（2 个，RESP 类型 +）
    // ================================================================

    @Test
    void shouldDecodeSimpleString() throws Exception {
        RespResponse result = decode("+OK\r\n");
        assertEquals(RespResponse.Type.SIMPLE_STRING, result.getType());
        assertEquals("OK", result.asString());
    }

    @Test
    void shouldDecodePong() throws Exception {
        RespResponse result = decode("+PONG\r\n");
        assertEquals("PONG", result.asString());
    }

    // ================================================================
    // 解码测试 — 错误（1 个，RESP 类型 -）
    // ================================================================

    @Test
    void shouldDecodeError() throws Exception {
        RespResponse result = decode("-ERR unknown command\r\n");
        assertEquals(RespResponse.Type.ERROR, result.getType());
        assertEquals("ERR unknown command", result.getError());
    }

    // ================================================================
    // 解码测试 — 整数（3 个，RESP 类型 :）
    // ================================================================

    @Test
    void shouldDecodeInteger() throws Exception {
        RespResponse result = decode(":100\r\n");
        assertEquals(RespResponse.Type.INTEGER, result.getType());
        assertEquals(100, result.asInteger());
    }

    @Test
    void shouldDecodeZeroInteger() throws Exception {
        RespResponse result = decode(":0\r\n");
        assertEquals(0, result.asInteger());
    }

    @Test
    void shouldDecodeNegativeInteger() throws Exception {
        RespResponse result = decode(":-2\r\n");
        assertEquals(-2, result.asInteger());
    }

    // ================================================================
    // 解码测试 — 批量字符串（3 个，RESP 类型 $）
    // ================================================================

    @Test
    void shouldDecodeBulkString() throws Exception {
        RespResponse result = decode("$5\r\nhello\r\n");
        assertEquals(RespResponse.Type.BULK_STRING, result.getType());
        assertEquals("hello", result.asString());
    }

    @Test
    void shouldDecodeNullBulkString() throws Exception {
        RespResponse result = decode("$-1\r\n");
        assertEquals(RespResponse.Type.NULL, result.getType());
        assertTrue(result.isNull());
        assertNull(result.asString());
    }

    @Test
    void shouldDecodeEmptyBulkString() throws Exception {
        RespResponse result = decode("$0\r\n\r\n");
        assertEquals(RespResponse.Type.BULK_STRING, result.getType());
        assertEquals("", result.asString());
    }

    // ================================================================
    // 解码测试 — 数组（1 个，RESP 类型 *）
    // ================================================================

    @Test
    void shouldDecodeEmptyArray() throws Exception {
        RespResponse result = decode("*0\r\n");
        assertEquals(RespResponse.Type.ARRAY, result.getType());
        assertTrue(result.asArray().isEmpty());
    }

    @Test
    void shouldDecodeArray() throws Exception {
        RespResponse result = decode("*2\r\n+OK\r\n:1\r\n");

        assertEquals(RespResponse.Type.ARRAY, result.getType());
        List<RespResponse> elements = result.asArray();
        assertEquals(2, elements.size());
        assertEquals(RespResponse.Type.SIMPLE_STRING, elements.get(0).getType());
        assertEquals("OK", elements.get(0).asString());
        assertEquals(RespResponse.Type.INTEGER, elements.get(1).getType());
        assertEquals(1, elements.get(1).asInteger());
    }

    @Test
    void shouldDecodeArrayOfBulkStrings() throws Exception {
        String response = "*3\r\n"
                + "$3\r\nfoo\r\n"
                + "$3\r\nbar\r\n"
                + "$3\r\nbaz\r\n";

        RespResponse result = decode(response);
        List<RespResponse> elements = result.asArray();

        assertEquals(3, elements.size());
        assertTrue(elements.stream()
                .allMatch(element -> element.getType() == RespResponse.Type.BULK_STRING));
        assertEquals(List.of("foo", "bar", "baz"),
                elements.stream().map(RespResponse::asString).toList());
    }

    @Test
    void shouldDecodeNullArray() throws Exception {
        RespResponse result = decode("*-1\r\n");

        assertEquals(RespResponse.Type.NULL, result.getType());
        assertTrue(result.isNull());
        assertNull(result.asArray());
    }

    @Test
    void shouldDecodeNestedMixedArray() throws Exception {
        String response = "*3\r\n"
                + "+OK\r\n"
                + ":2\r\n"
                + "*2\r\n$3\r\nfoo\r\n$-1\r\n";

        RespResponse result = decode(response);
        List<RespResponse> elements = result.asArray();

        assertEquals(3, elements.size());
        assertEquals("OK", elements.get(0).asString());
        assertEquals(2, elements.get(1).asInteger());
        assertEquals(RespResponse.Type.ARRAY, elements.get(2).getType());
        assertEquals("foo", elements.get(2).asArray().get(0).asString());
        assertTrue(elements.get(2).asArray().get(1).isNull());
    }

    @Test
    void shouldDecodeUtf8BulkStringByByteLength() throws Exception {
        RespResponse result = decode("$6\r\n缓存\r\n");

        assertEquals(RespResponse.Type.BULK_STRING, result.getType());
        assertEquals("缓存", result.asString());
    }

    @Test
    void shouldRejectInvalidLengths() {
        assertThrows(java.io.IOException.class, () -> decode("$-2\r\n"));
        assertThrows(java.io.IOException.class, () -> decode("*-2\r\n"));
        assertThrows(java.io.IOException.class, () -> decode("$abc\r\n"));
    }

    @Test
    void shouldRejectTruncatedBulkString() {
        assertThrows(java.io.IOException.class, () -> decode("$5\r\nabc\r\n"));
    }

    @Test
    void shouldRejectInvalidCrLf() {
        assertThrows(java.io.IOException.class, () -> decode("+OK\n"));
        assertThrows(java.io.IOException.class, () -> decode("$3\r\nfoo\n"));
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private RespResponse decode(String respData) throws Exception {
        InputStream in = new ByteArrayInputStream(respData.getBytes(StandardCharsets.UTF_8));
        return RespCodec.decode(in);
    }
}
