package com.llm_gateway.llm_gateway.Router;

import com.llm_gateway.llm_gateway.UsageAggregator.Usage;
import com.llm_gateway.llm_gateway.UsageAggregator.UsageAggregator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsageRouter {
    private final List<UsageAggregator> aggregatorList;

    public UsageRouter(List<UsageAggregator> aggregatorList) {
        this.aggregatorList = aggregatorList;
    }

    public Usage route(String model, List<String> chunks) {
        return aggregatorList.stream()
                .filter(aggregator -> aggregator.supports(model))
                .findFirst()
                .map(aggregator -> aggregator.aggregate(model, chunks))
                .orElse(null);   // no aggregator → UsageService falls back to local token counts
    }
}
