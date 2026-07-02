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
        // TODO 组员C: 将 entries 序列化为 JSON 写入 file
        // 提示: 可以手动拼接简单 JSON 格式，或引入 jackson/gson 依赖
        String json = entries.stream()
                .map(e -> String.format(
                        "    {\"key\":\"%s\",\"value\":\"%s\",\"ttl\":%d}",
                        e.getKey(), e.getValue(), e.getTtlSeconds()))
                .collect(Collectors.joining(",\n", "[\n", "\n]"));
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(json);
        }
    }

    public static void exportCsv(Collection<CacheEntry> entries, Path file) throws IOException {
        // TODO 组员C: 将 entries 写出为 CSV 格式写入 file
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("Key,Value,TTL,CreateTime\n");
            for (CacheEntry e : entries) {
                w.write(String.format("\"%s\",\"%s\",%d,%s\n",
                        e.getKey(), e.getValue(), e.getTtlSeconds(), e.getCreateTime()));
            }
        }
    }
}
