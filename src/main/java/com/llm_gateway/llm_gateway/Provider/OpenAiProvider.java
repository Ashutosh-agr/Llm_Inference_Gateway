package com.llm_gateway.llm_gateway.Provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

@Component
public class OpenAiProvider implements LlmProvider {

    private final WebClient webClient;

    @Autowired
    public OpenAiProvider(@Qualifier("openaiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public boolean supports(String modelName) {
        return modelName != null
                && (modelName.startsWith("gpt-") || modelName.startsWith("o1") || modelName.startsWith("o3"));
    }

    public Flux<String> complete(JsonNode request) {
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorMap(WebClientResponseException.class,
                        e -> new Exception("OpenAI " + e.getStatusCode() + ": "
                                + e.getResponseBodyAsString()))
                .onErrorMap(e -> !(e instanceof WebClientResponseException),
                        e -> new Exception("Error calling LLM API: " + e.getMessage()));
    }
}