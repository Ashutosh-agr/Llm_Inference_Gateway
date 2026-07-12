package com.llm_gateway.llm_gateway.Controller;

import com.llm_gateway.llm_gateway.Executor.RequestExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/chat")
public class ClientController {

    private final RequestExecutor requestExecutor;

    public ClientController(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getCompletions(@RequestBody JsonNode request, ServerWebExchange exchange) {
        return requestExecutor.execute(request, exchange);
    }
}