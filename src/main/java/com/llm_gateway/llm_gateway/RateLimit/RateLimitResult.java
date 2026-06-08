package com.llm_gateway.llm_gateway.RateLimit;

public record RateLimitResult(
        boolean allowed,
        long retryAfterSeconds,
        long remainingRequests,
        long remainingTokens) {}