package com.cache.client.util;

import com.cache.client.model.CacheEntry;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Matches cache keys using the server's simple, case-insensitive '*' wildcard rule. */
public final class KeyPatternMatcher {

    private KeyPatternMatcher() {
    }

    public static boolean matches(String key, String pattern) {
        if (key == null) return false;

        String normalizedPattern = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
        if (normalizedPattern.isEmpty() || normalizedPattern.equals("*")) return true;

        String[] literalParts = normalizedPattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < literalParts.length; i++) {
            if (i > 0) regex.append(".*");
            regex.append(Pattern.quote(literalParts[i]));
        }
        return key.toLowerCase(Locale.ROOT).matches(regex.toString());
    }

    public static List<CacheEntry> filter(List<CacheEntry> entries, String pattern) {
        return entries.stream()
                .filter(entry -> matches(entry.getKey(), pattern))
                .toList();
    }
}
