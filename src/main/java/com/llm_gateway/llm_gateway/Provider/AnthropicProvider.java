package com.llm_gateway.llm_gateway.Provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

@Component
public class AnthropicProvider implements LlmProvider {

    private final WebClient webClient;

    @Autowired
    public AnthropicProvider(@Qualifier("anthropicWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public boolean supports(String modelName) {
        return modelName != null && modelName.startsWith("claude-");
    }

    public Flux<String> complete(JsonNode request) {
        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorMap(WebClientResponseException.class,
                        e -> new Exception("Anthropic " + e.getStatusCode() + ": "
                                + e.getResponseBodyAsString()))
                .onErrorMap(e -> !(e instanceof WebClientResponseException),
                        e -> new Exception("Error calling LLM API: " + e.getMessage()));
    }
}