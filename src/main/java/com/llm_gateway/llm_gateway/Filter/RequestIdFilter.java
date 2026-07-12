package com.llm_gateway.llm_gateway.Filter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(1)
public class RequestIdFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTR = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        UUID requestId = getRequestId(header);

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId.toString());

        exchange.getAttributes().put(REQUEST_ID_ATTR, requestId);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(REQUEST_ID_ATTR, requestId));
    }

    private UUID getRequestId(String header) {
        if (header == null || header.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException malformed) {
            return UUID.randomUUID();
        }
    }
}
