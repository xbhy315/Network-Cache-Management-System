package com.cache.client.net.protocol;

import java.util.List;

/**
 * RESP 协议响应封装。
 *
 * RESP 五种数据类型：
 *   +  简单字符串（Simple String）
 *   -  错误（Error）
 *   :  整数（Integer）
 *   $  批量字符串 / 空值（Bulk String / Null）
 *   *  数组（Array）
 */
public class RespResponse {

    public enum Type {
        SIMPLE_STRING,  // +OK\r\n
        ERROR,          // -ERR msg\r\n
        INTEGER,        // :100\r\n
        BULK_STRING,    // $5\r\nhello\r\n
        NULL,           // $-1\r\n
        ARRAY           // *2\r\n...\r\n
    }

    private final Type type;
    private final String stringValue;     // SIMPLE_STRING / ERROR / BULK_STRING
    private final long integerValue;      // INTEGER
    private final List<RespResponse> arrayValue; // ARRAY

    private RespResponse(Type type, String stringValue, long integerValue, List<RespResponse> arrayValue) {
        this.type = type;
        this.stringValue = stringValue;
        this.integerValue = integerValue;
        this.arrayValue = arrayValue;
    }

    // ============ 工厂方法 ============

    public static RespResponse simpleString(String value) {
        return new RespResponse(Type.SIMPLE_STRING, value, 0, null);
    }

    public static RespResponse error(String message) {
        return new RespResponse(Type.ERROR, message, 0, null);
    }

    public static RespResponse integer(long value) {
        return new RespResponse(Type.INTEGER, null, value, null);
    }

    public static RespResponse bulkString(String value) {
        return new RespResponse(Type.BULK_STRING, value, 0, null);
    }

    public static RespResponse nullBulk() {
        return new RespResponse(Type.NULL, null, 0, null);
    }

    public static RespResponse array(List<RespResponse> elements) {
        return new RespResponse(Type.ARRAY, null, 0, elements);
    }

    // ============ 便捷访问 ============

    public Type getType() { return type; }

    /** 获取字符串值（SIMPLE_STRING / ERROR / BULK_STRING）。 */
    public String asString() {
        if (type == Type.NULL) return null;
        return stringValue;
    }

    /** 获取整数值（INTEGER）。 */
    public long asInteger() {
        return integerValue;
    }

    /** 获取数组元素（ARRAY）。 */
    public List<RespResponse> asArray() {
        return arrayValue;
    }

    /** 是否为空值。 */
    public boolean isNull() {
        return type == Type.NULL;
    }

    /** 是否为错误响应。 */
    public boolean isError() {
        return type == Type.ERROR;
    }

    /** 获取错误消息（仅 ERROR 类型）。 */
    public String getError() {
        return type == Type.ERROR ? stringValue : null;
    }

    /**
     * 将响应统一转换为字符串（用于 UI 展示）。
     * INTEGER → "100"
     * BULK_STRING → "hello"
     * SIMPLE_STRING → "OK"
     * NULL → "null"
     * ERROR → "ERR: message"
     * ARRAY → "[elem1, elem2, ...]"
     */
    public String displayString() {
        return switch (type) {
            case SIMPLE_STRING, BULK_STRING -> stringValue;
            case INTEGER -> String.valueOf(integerValue);
            case NULL -> "null";
            case ERROR -> "ERR: " + stringValue;
            case ARRAY -> arrayValue == null ? "[]"
                    : arrayValue.stream().map(RespResponse::displayString).toList().toString();
        };
    }

    @Override
    public String toString() {
        return "RespResponse{" +
                "type=" + type +
                ", value=" + displayString() +
                '}';
    }
}
