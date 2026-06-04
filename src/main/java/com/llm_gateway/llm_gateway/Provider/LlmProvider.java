package com.llm_gateway.llm_gateway.Provider;

import tools.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;

public interface LlmProvider {

    boolean supports(String modelName);
    Flux<String> complete(JsonNode request);
}
