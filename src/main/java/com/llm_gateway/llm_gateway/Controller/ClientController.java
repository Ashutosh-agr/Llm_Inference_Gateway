package com.llm_gateway.llm_gateway.Controller;

import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/v1/chat")
public class ClientController {

//    @Autowired
//    private String apiKey; currently using ollama

    private final WebClient webClient;

    @Autowired
    public ClientController(WebClient webClient) {
        this.webClient = webClient;
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getCompletions(@RequestBody JsonNode request) {

//        if(apiKey == null) {
//            return Flux.error(new Exception("apiKey is null"));
//        }

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorMap(e -> new Exception("Error calling LLM API: " + e.getMessage()));
    }
}
