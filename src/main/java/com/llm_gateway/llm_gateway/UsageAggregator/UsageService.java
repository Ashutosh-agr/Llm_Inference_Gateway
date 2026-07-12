package com.llm_gateway.llm_gateway.UsageAggregator;

import com.llm_gateway.llm_gateway.Router.UsageRouter;
import com.llm_gateway.llm_gateway.TokenCounter.TokenCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);
    private static final int DISCREPANCY_THRESHOLD_PCT = 10;

    private final UsageRouter usageRouter;
    private final TokenCounter tokenCounter;

    public UsageService(UsageRouter usageRouter, TokenCounter tokenCounter) {
        this.usageRouter = usageRouter;
        this.tokenCounter = tokenCounter;
    }

    public ResolvedUsage resolve(String model, JsonNode request, List<String> chunks) {
        Usage providerUsage = usageRouter.route(model, chunks);

        Long provIn  = providerUsage != null ? providerUsage.providerInputTokens()  : null;
        Long provOut = providerUsage != null ? providerUsage.providerOutputTokens() : null;
        String provider = providerUsage != null ? providerUsage.provider() : "unknown";

        long localIn  = tokenCounter.countInput(model, request);
        long localOut = tokenCounter.countOutput(model, String.join("", chunks));

        boolean inputFromProvider  = provIn  != null;
        boolean outputFromProvider = provOut != null;

        long inputTokens  = inputFromProvider  ? provIn  : localIn;
        long outputTokens = outputFromProvider ? provOut : localOut;

        if (!inputFromProvider) {
            log.info("usage.fallback.input model={} provider={} local={}", model, provider, localIn);
        }
        if (!outputFromProvider) {
            log.info("usage.fallback.output model={} provider={} local={}", model, provider, localOut);
        }

        if (inputFromProvider) {
            warnIfDiscrepant("input", model, localIn, provIn);
        }
        if (outputFromProvider) {
            warnIfDiscrepant("output", model, localOut, provOut);
        }

        String tokenSource = (inputFromProvider && outputFromProvider) ? "provider" : "local";

        return new ResolvedUsage(inputTokens, outputTokens, provider, tokenSource);
    }

    private void warnIfDiscrepant(String kind, String model, long local, long provider) {
        if (local == 0 && provider == 0) {
            return;
        }
        double base = Math.max(local, provider);
        long diffPct = Math.round(Math.abs(local - provider) / base * 100);
        if (diffPct > DISCREPANCY_THRESHOLD_PCT) {
            log.warn("Token count discrepancy: kind={}, model={}, local={}, provider={}, diff={}%",
                    kind, model, local, provider, diffPct);
        }
    }
}