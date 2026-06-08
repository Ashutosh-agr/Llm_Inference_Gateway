package com.llm_gateway.llm_gateway.Filter;

import com.llm_gateway.llm_gateway.Config.KeyLimits;
import com.llm_gateway.llm_gateway.RateLimit.RateLimitResult;
import com.llm_gateway.llm_gateway.RateLimit.RateLimiter;
import com.llm_gateway.llm_gateway.RateLimit.TokenEstimator;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
@Order(2)
public class RateLimitFilter implements WebFilter {

    private final TokenEstimator tokenEstimator;
    private final RateLimiter rateLimiter;
    private final KeyLimits keyLimits;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public RateLimitFilter(TokenEstimator tokenEstimator, RateLimiter rateLimiter, KeyLimits keyLimits) {
        this.tokenEstimator = tokenEstimator;
        this.rateLimiter = rateLimiter;
        this.keyLimits = keyLimits;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String apiKey = authHeader.substring("Bearer ".length()).trim();

        if (!keyLimits.apiKeys().containsKey(apiKey)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    JsonNode json;
                    try {
                        json = jsonMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
                    } catch (JacksonException e) {
                        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                        return exchange.getResponse().setComplete();
                    }

                    int estimatedTokens = tokenEstimator.estimate(json);

                    RateLimitResult result = rateLimiter.tryAcquire(apiKey, estimatedTokens);

                    ServerHttpResponse response = exchange.getResponse();
                    DataBufferFactory bufferFactory = response.bufferFactory();

                    if (result.allowed()) {
                        response.getHeaders().set("X-RateLimit-Remaining-Requests",
                                String.valueOf(result.remainingRequests()));
                        response.getHeaders().set("X-RateLimit-Remaining-Tokens",
                                String.valueOf(result.remainingTokens()));

                        ServerHttpRequest mutated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(bufferFactory.wrap(bytes));
                            }
                        };
                        return chain.filter(exchange.mutate().request(mutated).build());
                    }

                    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    response.getHeaders().set(HttpHeaders.RETRY_AFTER,
                            String.valueOf(result.retryAfterSeconds()));
                    return response.setComplete();
                });
    }
}