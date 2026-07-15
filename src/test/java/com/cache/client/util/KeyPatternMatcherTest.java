package com.cache.client.util;

import com.cache.client.model.CacheEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyPatternMatcherTest {

    @Test
    void shouldTreatEmptyPatternAsMatchAll() {
        assertTrue(KeyPatternMatcher.matches("user:1001", ""));
        assertTrue(KeyPatternMatcher.matches("user:1001", "   "));
    }

    @Test
    void shouldMatchCaseInsensitively() {
        assertTrue(KeyPatternMatcher.matches("User:Alice", "user:alice"));
    }

    @Test
    void shouldSupportAsteriskWildcard() {
        assertTrue(KeyPatternMatcher.matches("user:1001", "user:*"));
        assertTrue(KeyPatternMatcher.matches("session:abc:active", "*abc*"));
        assertFalse(KeyPatternMatcher.matches("config:theme", "user:*"));
    }

    @Test
    void shouldTreatRegexMetacharactersAsLiterals() {
        assertTrue(KeyPatternMatcher.matches("cache.item[1]", "cache.item[1]"));
        assertFalse(KeyPatternMatcher.matches("cacheXitem1", "cache.item[1]"));
    }

    @Test
    void shouldSupportQuestionMarkWildcard() {
        assertTrue(KeyPatternMatcher.matches("user:1001", "user:100?"));
        assertTrue(KeyPatternMatcher.matches("user:1001", "user:????"));
        assertFalse(KeyPatternMatcher.matches("user:1001", "user:10?"));
        assertTrue(KeyPatternMatcher.matches("abc", "a?c"));
        assertTrue(KeyPatternMatcher.matches("abc", "???"));
    }

    @Test
    void shouldFilterEntriesWithoutMutatingSource() {
        List<CacheEntry> entries = List.of(
                new CacheEntry("User:1", "Alice", 0),
                new CacheEntry("user:2", "Bob", 0),
                new CacheEntry("config:theme", "dark", 0));

        List<CacheEntry> filtered = KeyPatternMatcher.filter(entries, "USER:*");

        assertEquals(List.of("User:1", "user:2"),
                filtered.stream().map(CacheEntry::getKey).toList());
        assertEquals(3, entries.size());
    }
}
