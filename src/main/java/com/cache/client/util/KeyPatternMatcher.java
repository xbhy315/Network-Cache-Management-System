package com.cache.client.util;

import com.cache.client.model.CacheEntry;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Matches cache keys using the server's simple, case-insensitive '*' / '?' wildcard rule. */
public final class KeyPatternMatcher {

    private KeyPatternMatcher() {
    }

    public static boolean matches(String key, String pattern) {
        if (key == null) return false;

        String normalizedPattern = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
        if (normalizedPattern.isEmpty() || normalizedPattern.equals("*")) return true;

        String[] literalParts = normalizedPattern.split("\\*", -1);
        String regex = Arrays.stream(literalParts)
                .map(KeyPatternMatcher::quoteLiteral)
                .collect(Collectors.joining(".*"));
        return key.toLowerCase(Locale.ROOT).matches(regex);
    }

    /**
     * Quote a literal part (between '*' wildcards), converting '?' to '.'.
     * E.g. "a?c" → "\Qa\E.\Qc\E"
     */
    private static String quoteLiteral(String s) {
        return Arrays.stream(s.split("\\?", -1))
                .map(part -> part.isEmpty() ? "" : Pattern.quote(part))
                .collect(Collectors.joining("."));
    }

    public static List<CacheEntry> filter(List<CacheEntry> entries, String pattern) {
        return entries.stream()
                .filter(entry -> matches(entry.getKey(), pattern))
                .toList();
    }
}
