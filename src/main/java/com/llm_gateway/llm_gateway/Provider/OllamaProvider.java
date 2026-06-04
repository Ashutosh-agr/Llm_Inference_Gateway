package com.llm_gateway.llm_gateway.Provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Component
public class OllamaProvider implements LlmProvider {

    private final WebClient webClient;
    private final List<String> supportedModels;

    @Autowired
    public OllamaProvider(@Qualifier("ollamaWebClient") WebClient webClient,
                          @Value("${gateway.ollama.models}") List<String> supportedModels) {
        this.webClient = webClient;
        this.supportedModels = supportedModels;
    }

    public boolean supports(String modelName) {
        return supportedModels.contains(modelName);
    }

    public Flux<String> complete(JsonNode request) {
        return webClient.post()
                .uri("/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorMap(WebClientResponseException.class,
                        e -> new Exception("Ollama " + e.getStatusCode() + ": "
                                + e.getResponseBodyAsString()))
                .onErrorMap(e -> !(e instanceof WebClientResponseException),
                        e -> new Exception("Error calling LLM API: " + e.getMessage()));
    }
}
