package com.llm_gateway.llm_gateway.Cache;

import com.github.benmanes.caffeine.cache.Expiry;

public class PerEntryExpiry implements Expiry<String, CachedResponse> {

    @Override
    public long expireAfterCreate(String key, CachedResponse response, long currentTime) {
        return response.ttl().toNanos();
    }

    @Override
    public long expireAfterUpdate(String key, CachedResponse response, long currentTime,long currentDuration) {
        return response.ttl().toNanos();
    }

    @Override
    public long expireAfterRead(String key, CachedResponse response, long currentTime, long currentDuration) {
        return currentDuration;
    }
}
