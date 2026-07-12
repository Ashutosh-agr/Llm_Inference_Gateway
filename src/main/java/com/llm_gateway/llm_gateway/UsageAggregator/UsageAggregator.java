package com.llm_gateway.llm_gateway.UsageAggregator;

import java.util.List;

public interface UsageAggregator {
    boolean supports(String modelName);
    String provider();
    Usage aggregate(String model, List<String> chunks);

}
