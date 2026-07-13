package com.cache.client.util;

import com.cache.client.model.CacheEntry;
import com.cache.client.net.CacheServerClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds table rows from the commands available on a RESP cache server. */
public final class CacheEntryLoader {

    private CacheEntryLoader() {
    }

    public static List<CacheEntry> load(CacheServerClient client) {
        List<CacheEntry> entries = new ArrayList<>();
        for (String key : client.scan("*")) {
            long ttl = client.ttl(key);
            if (ttl == -2) continue;

            Optional<String> value = client.get(key);
            if (value.isPresent()) {
                entries.add(new CacheEntry(key, value.get(), ttl));
                continue;
            }

            List<String> values = client.lrange(key, 0, -1);
            CacheEntry entry = new CacheEntry(key, CacheEntry.EntryType.LIST, ttl);
            entry.setListLength(values.size());
            entries.add(entry);
        }
        return entries;
    }
}
