package com.llm_gateway.llm_gateway.Cache;

import reactor.core.publisher.Mono;

import java.util.Optional;

public interface ResponseCache {
    Mono<Optional<CachedResponse>> lookup(String key);
    Mono<Void> store(String key, CachedResponse response);
}
