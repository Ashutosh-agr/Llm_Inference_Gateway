package com.llm_gateway.llm_gateway.RateLimit;

public interface RateLimiter {
    RateLimitResult tryAcquire(String apiKey, int estimatedTokens);
}