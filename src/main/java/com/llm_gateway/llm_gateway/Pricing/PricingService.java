package com.llm_gateway.llm_gateway.Pricing;

import com.llm_gateway.llm_gateway.Config.PricingConfig.ModelPricing;

public interface PricingService {

    ModelPricing lookup(String model, String apiKey);
}