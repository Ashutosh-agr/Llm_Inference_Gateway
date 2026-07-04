package com.llm_gateway.llm_gateway.Cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Component
public class CaffeineResponseCache implements ResponseCache {

    private final Cache<String, CachedResponse> cache;

    public CaffeineResponseCache(
        @Value("${gateway.cache.max-size}") int maxSize,
        @Value("${gateway.cache.default-ttl}") Duration defaultTtl){

        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new PerEntryExpiry())
                .recordStats()
                .build();
    }

    public Mono<Optional<CachedResponse>> lookup(String key) {
        return Mono.fromCallable(() -> Optional.ofNullable(cache.getIfPresent(key)));
    }

    public Mono<Void> store(String key, CachedResponse response) {
        cache.put(key, response);
        return Mono.empty();
    }
}
