package com.llm_gateway.llm_gateway.Controller;

import tools.jackson.databind.JsonNode;
import com.llm_gateway.llm_gateway.Router.ProviderRouter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/v1/chat")
public class ClientController {

//    @Autowired
//    private String apiKey; currently using ollama
    private final ProviderRouter providerRouter;

    public ClientController(ProviderRouter providerRouter) {
        this.providerRouter = providerRouter;
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getCompletions(@RequestBody JsonNode request) {

//        if(apiKey == null) {
//            return Flux.error(new Exception("apiKey is null"));
//        }

        return providerRouter.route(request);
    }
}
