package com.cache.client.util;

import com.cache.client.model.CacheEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * [组员C] 缓存数据导出工具。
 *
 * 负责：
 * - 将缓存条目导出为 JSON 格式
 * - 将缓存条目导出为 CSV 格式
 */
public class ExportUtil {

    public static void exportJson(Collection<CacheEntry> entries, Path file) throws IOException {
        String json = entries.stream()
                .map(e -> {
                    if (e.getType() == CacheEntry.EntryType.LIST) {
                        return String.format(
                                "    {\"key\":\"%s\",\"type\":\"LIST\",\"listLength\":%d}",
                                e.getKey(), e.getListLength());
                    }
                    return String.format(
                            "    {\"key\":\"%s\",\"value\":\"%s\",\"type\":\"STRING\",\"ttl\":%d}",
                            e.getKey(), escapeJson(e.getValue()), e.getTtlSeconds());
                })
                .collect(Collectors.joining(",\n", "[\n", "\n]"));
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(json);
        }
    }

    public static void exportCsv(Collection<CacheEntry> entries, Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("Key,Type,Value,TTL,CreateTime,ListLength\n");
            for (CacheEntry e : entries) {
                if (e.getType() == CacheEntry.EntryType.LIST) {
                    w.write(String.format("\"%s\",LIST,,%d,%s,%d\n",
                            e.getKey(), e.getTtlSeconds(), e.getCreateTime(), e.getListLength()));
                } else {
                    w.write(String.format("\"%s\",STRING,\"%s\",%d,%s,\n",
                            e.getKey(), e.getValue(), e.getTtlSeconds(), e.getCreateTime()));
                }
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
