package com.llm_gateway.llm_gateway.Pricing;

import com.llm_gateway.llm_gateway.Config.PricingConfig;
import com.llm_gateway.llm_gateway.Config.PricingConfig.ModelPricing;
import org.springframework.stereotype.Service;

@Service
public class ConfigPricingService implements PricingService {

    private final PricingConfig pricingConfig;

    public ConfigPricingService(PricingConfig pricingConfig) {
        this.pricingConfig = pricingConfig;
    }

    @Override
    public ModelPricing lookup(String model, String apiKey) {
        // Config-driven default pricing is tenant-agnostic, so apiKey is ignored.
        // A future PerTenantPricingService can consult a DB override table keyed
        // by apiKey before falling back to this config lookup.
        return pricingConfig.models().getOrDefault(model, pricingConfig.defaultModel());
    }
}