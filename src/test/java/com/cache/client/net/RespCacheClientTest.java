package com.cache.client.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespCacheClientTest {

    @Test
    void shouldEncodeScanAllAndDecodeKeys() throws Exception {
        RespCacheClient client = new RespCacheClient();
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        setField(client, "in", new ByteArrayInputStream(
                "*2\r\n$4\r\nname\r\n$4\r\ncity\r\n".getBytes(StandardCharsets.UTF_8)));
        setField(client, "out", request);

        assertEquals(List.of("name", "city"), client.scan("*"));
        assertEquals("*1\r\n$4\r\nSCAN\r\n", request.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldEncodeScanPattern() throws Exception {
        RespCacheClient client = new RespCacheClient();
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        setField(client, "in", new ByteArrayInputStream("*0\r\n".getBytes(StandardCharsets.UTF_8)));
        setField(client, "out", request);

        assertEquals(List.of(), client.scan("user*"));
        assertEquals("*2\r\n$4\r\nSCAN\r\n$5\r\nuser*\r\n",
                request.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnConfirmedTtlCodes() throws Exception {
        assertEquals(-1, clientWithResponse(":-1\r\n").ttl("persistent"));
        assertEquals(-2, clientWithResponse(":-2\r\n").ttl("missing"));
    }

    @Test
    void shouldRejectUnexpectedTtlError() throws Exception {
        RespCacheClient client = clientWithResponse("-ERR wrong number of arguments\r\n");

        assertThrows(RuntimeException.class, () -> client.ttl("key"));
    }

    @Test
    void shouldReturnEmptyWhenGetReportsWrongType() throws Exception {
        RespCacheClient client = clientWithResponse(
                "-WRONGTYPE Operation against a key holding the wrong kind of value\r\n");

        assertTrue(client.get("queue").isEmpty());
    }

    @Test
    void shouldRejectUnexpectedGetError() throws Exception {
        RespCacheClient client = clientWithResponse("-ERR internal failure\r\n");

        assertThrows(RuntimeException.class, () -> client.get("key"));
    }

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

    private RespCacheClient clientWithResponse(String response) throws Exception {
        RespCacheClient client = new RespCacheClient();
        setField(client, "in", new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
        setField(client, "out", new ByteArrayOutputStream());
        return client;
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
