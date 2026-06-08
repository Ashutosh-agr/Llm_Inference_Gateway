package com.llm_gateway.llm_gateway.Config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record KeyLimits(Map<String, KeyQuota> apiKeys) {

    public record KeyQuota(int rpm, int tpm) {}
}