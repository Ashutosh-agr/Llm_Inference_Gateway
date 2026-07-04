package com.llm_gateway.llm_gateway.Utils;

import java.time.Duration;

public record CachePolicy(boolean cacheable, Duration ttl) {}
