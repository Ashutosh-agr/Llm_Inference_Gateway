package com.llm_gateway.llm_gateway.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.pricing")
public record PricingConfig(ModelPricing defaultModel, Map<String, ModelPricing> models) {

    public record ModelPricing(BigDecimal inputPer1m, BigDecimal outputPer1m) {}
}
