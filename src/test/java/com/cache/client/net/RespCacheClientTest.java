package com.cache.client.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespCacheClientTest {

    @Test
    void shouldEncodeLpushWithNoValues() throws Exception {
        assertCommand(
                client -> client.lpush("list"),
                "*2\r\n$5\r\nLPUSH\r\n$4\r\nlist\r\n");
    }

    @Test
    void shouldEncodeLpushWithOneValue() throws Exception {
        assertCommand(
                client -> client.lpush("list", "a"),
                "*3\r\n$5\r\nLPUSH\r\n$4\r\nlist\r\n$1\r\na\r\n");
    }

    @Test
    void shouldEncodeLpushWithMultipleValuesInOrder() throws Exception {
        assertCommand(
                client -> client.lpush("list", "a", "b"),
                "*4\r\n$5\r\nLPUSH\r\n$4\r\nlist\r\n$1\r\na\r\n$1\r\nb\r\n");
    }

    @Test
    void shouldEncodeRpushWithNoValues() throws Exception {
        assertCommand(
                client -> client.rpush("list"),
                "*2\r\n$5\r\nRPUSH\r\n$4\r\nlist\r\n");
    }

    @Test
    void shouldEncodeRpushWithOneValue() throws Exception {
        assertCommand(
                client -> client.rpush("list", "a"),
                "*3\r\n$5\r\nRPUSH\r\n$4\r\nlist\r\n$1\r\na\r\n");
    }

    @Test
    void shouldEncodeRpushWithMultipleValuesInOrder() throws Exception {
        assertCommand(
                client -> client.rpush("list", "a", "b"),
                "*4\r\n$5\r\nRPUSH\r\n$4\r\nlist\r\n$1\r\na\r\n$1\r\nb\r\n");
    }

    private void assertCommand(ClientCommand command, String expectedRequest) throws Exception {
        RespCacheClient client = new RespCacheClient();
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        setField(client, "in", new ByteArrayInputStream(":1\r\n".getBytes(StandardCharsets.UTF_8)));
        setField(client, "out", request);

        assertEquals(1, command.execute(client));
        assertEquals(expectedRequest, request.toString(StandardCharsets.UTF_8));
    }

    private void setField(RespCacheClient client, String name, Object value) throws Exception {
        Field field = RespCacheClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(client, value);
    }

    @FunctionalInterface
    private interface ClientCommand {
        int execute(RespCacheClient client);
    }
}
