package com.llm_gateway.llm_gateway.UsageAggregator;

public record ResolvedUsage(
        long inputTokens,
        long outputTokens,
        String provider,
        String tokenSource   // "provider" | "local"
) {}