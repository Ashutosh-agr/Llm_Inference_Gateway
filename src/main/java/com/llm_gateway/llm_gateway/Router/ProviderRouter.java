package com.llm_gateway.llm_gateway.Router;

import com.llm_gateway.llm_gateway.Exception.NoProviderFoundException;
import com.llm_gateway.llm_gateway.Provider.LlmProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class ProviderRouter {

    private final List<LlmProvider> providers;

    public ProviderRouter(List<LlmProvider> providers) {
        this.providers = providers;
    }

    public Flux<String> route(JsonNode request) {
        String model = request.path("model").asString();

        return providers.stream()
                .filter(provider -> provider.supports(model))
                .findFirst()
                .map(provider -> provider.complete(request))
                .orElseGet(() -> Flux.error(new NoProviderFoundException(model)));
    }
}