package com.cache.client.util;

import com.cache.client.model.CacheEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldEscapeEveryJsonStringField() throws Exception {
        CacheEntry entry = new CacheEntry("key\"\\\n", "value,\"\\\r\n", 30);
        Path output = tempDir.resolve("entries.json");

        ExportUtil.exportJson(List.of(entry), output);

        String json = Files.readString(output);
        assertTrue(json.contains("\"key\":\"key\\\"\\\\\\n\""));
        assertTrue(json.contains("\"value\":\"value,\\\"\\\\\\r\\n\""));
    }

    @Test
    void shouldEscapeQuotesAndLineBreaksInCsvFields() throws Exception {
        CacheEntry entry = new CacheEntry("key,\"one\"\nnext", "value,\"two\"\nnext", 0);
        entry.setCreateTime(Instant.parse("2026-07-13T03:00:00Z"));
        Path output = tempDir.resolve("entries.csv");

        ExportUtil.exportCsv(List.of(entry), output);

        String csv = Files.readString(output);
        assertTrue(csv.contains("\"key,\"\"one\"\"\nnext\",STRING,"));
        assertTrue(csv.contains("\"value,\"\"two\"\"\nnext\""));
    }

    @Test
    void shouldExportListTypeAndLength() throws Exception {
        CacheEntry entry = new CacheEntry("queue,\"main\"", CacheEntry.EntryType.LIST, -1);
        entry.setListLength(3);
        Path json = tempDir.resolve("list.json");
        Path csv = tempDir.resolve("list.csv");

        ExportUtil.exportJson(List.of(entry), json);
        ExportUtil.exportCsv(List.of(entry), csv);

        assertTrue(Files.readString(json).contains(
                "{\"key\":\"queue,\\\"main\\\"\",\"type\":\"LIST\",\"listLength\":3}"));
        assertTrue(Files.readString(csv).contains("\"queue,\"\"main\"\"\",LIST"));
    }
}
