package com.llm_gateway.llm_gateway.Controller;

import com.llm_gateway.llm_gateway.Cache.CacheKeyGenerator;
import com.llm_gateway.llm_gateway.Cache.CachedResponse;
import com.llm_gateway.llm_gateway.Cache.ResponseCache;
import com.llm_gateway.llm_gateway.Utils.CachePolicy;
import com.llm_gateway.llm_gateway.Utils.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import com.llm_gateway.llm_gateway.Router.ProviderRouter;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/chat")
public class ClientController {

//    @Autowired
//    private String apiKey; currently using ollama
    private final ProviderRouter providerRouter;
    private final Cacheable cacheable;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final ResponseCache responseCache;

    public ClientController(ProviderRouter providerRouter, Cacheable cacheable, CacheKeyGenerator cacheKeyGenerator, ResponseCache responseCache) {
        this.providerRouter = providerRouter;
        this.cacheable = cacheable;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.responseCache = responseCache;
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getCompletions(@RequestBody JsonNode request, @RequestHeader HttpHeaders headers) {

        String apiKey = headers.getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length()).trim();
        CachePolicy policy = cacheable.isCacheable(request, headers);

        if(!policy.cacheable()){
            return providerRouter.route(request);
        }

        String cacheKey = cacheKeyGenerator.generate(apiKey,request);

        return responseCache.lookup(cacheKey).flatMapMany(hit -> {
            if (hit.isPresent()) {
                return Flux.fromIterable(hit.get().chunks());
            }

            List<String> collected = new ArrayList<>();
            return providerRouter.route(request).
                    doOnNext(collected::add).
                    doOnComplete(() -> {
                        CachedResponse cachedResponse = new CachedResponse(List.copyOf(collected), Instant.now(), policy.ttl());
                        responseCache.store(cacheKey, cachedResponse).subscribe();
                    })
                    .doOnError(e -> {
                        collected.clear();
                    })
                    .doOnCancel(collected::clear);
        });
    }
}
