package com.llm_gateway.llm_gateway.Utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.OptionalLong;

@Component
public class Cacheable {

    private static final String CACHE_HEADER = "X-Gateway-Cache";

    private static final double DEFAULT_MODEL_TEMPERATURE = 1.0;

    private static final Duration MIN_TTL = Duration.ofSeconds(60);
    private static final Duration MAX_TTL = Duration.ofHours(24);

    private static final CachePolicy NOT_CACHEABLE = new CachePolicy(false, Duration.ZERO);

    private final Duration defaultTtl;

    public Cacheable(@Value("${gateway.cache.default-ttl}") Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }


    public CachePolicy isCacheable(JsonNode request, HttpHeaders headers) {
        String cacheHeader = headers.getFirst(CACHE_HEADER);
        OptionalLong maxAge = maxAge(headers);

        // 1. Explicit opt-out always wins.
        if ("disabled".equalsIgnoreCase(cacheHeader)) {
            return NOT_CACHEABLE;
        }

        // 2. Cache-Control: max-age=0 disables caching.
        if (maxAge.isPresent() && maxAge.getAsLong() == 0) {
            return NOT_CACHEABLE;
        }

        // 3. Explicit opt-in.
        if ("enabled".equalsIgnoreCase(cacheHeader)) {
            return cacheable(maxAge);
        }

        // 4. Deterministic request (temperature 0, or absent with a 0 default).
        if (temperature(request) == 0.0) {
            return cacheable(maxAge);
        }

        // 5. Otherwise, not cacheable.
        return NOT_CACHEABLE;
    }

    private CachePolicy cacheable(OptionalLong maxAge) {

        Duration ttl = maxAge.isPresent()
                ? clamp(Duration.ofSeconds(maxAge.getAsLong()))
                : defaultTtl;
        return new CachePolicy(true, ttl);
    }

    private Duration clamp(Duration ttl) {
        if (ttl.compareTo(MIN_TTL) < 0) {
            return MIN_TTL;
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            return MAX_TTL;
        }
        return ttl;
    }

    private double temperature(JsonNode request) {
        if(request.hasNonNull("temperature")) {
            return request.get("temperature").asDouble();
        }

        if(request.path("options").hasNonNull("temperature")) {
            return request.path("options").get("temperature").asDouble();
        }

        return DEFAULT_MODEL_TEMPERATURE;
    }

    private OptionalLong maxAge(HttpHeaders headers) {
        String cacheControl = headers.getFirst(HttpHeaders.CACHE_CONTROL);
        if (cacheControl == null) {
            return OptionalLong.empty();
        }
        for (String directive : cacheControl.split(",")) {
            String trimmed = directive.trim();
            if (trimmed.regionMatches(true, 0, "max-age=", 0, "max-age=".length())) {
                try {
                    long seconds = Long.parseLong(trimmed.substring("max-age=".length()).trim());
                    return seconds < 0 ? OptionalLong.empty() : OptionalLong.of(seconds);
                } catch (NumberFormatException e) {
                    return OptionalLong.empty();
                }
            }
        }
        return OptionalLong.empty();
    }
}