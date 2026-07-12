package com.llm_gateway.llm_gateway.UsageAggregator;

public record Usage(
        Long providerInputTokens,
        Long providerOutputTokens,
        String provider
) {}
