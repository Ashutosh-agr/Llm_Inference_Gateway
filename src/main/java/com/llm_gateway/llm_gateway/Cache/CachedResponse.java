package com.llm_gateway.llm_gateway.Cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record CachedResponse(List<String> chunks, Instant cachedAt, Duration ttl) {}
