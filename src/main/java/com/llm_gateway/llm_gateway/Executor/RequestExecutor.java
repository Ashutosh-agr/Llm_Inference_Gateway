package com.llm_gateway.llm_gateway.Executor;

import com.llm_gateway.llm_gateway.Cache.CacheKeyGenerator;
import com.llm_gateway.llm_gateway.Cache.CachedResponse;
import com.llm_gateway.llm_gateway.Cache.ResponseCache;
import com.llm_gateway.llm_gateway.Config.PricingConfig.ModelPricing;
import com.llm_gateway.llm_gateway.Exception.IdempotencyConflictException;
import com.llm_gateway.llm_gateway.Idempotency.IdempotencyService;
import com.llm_gateway.llm_gateway.Idempotency.ReservationResult;
import com.llm_gateway.llm_gateway.Ledger.LedgerEntry;
import com.llm_gateway.llm_gateway.Ledger.LedgerWriter;
import com.llm_gateway.llm_gateway.Pricing.PricingService;
import com.llm_gateway.llm_gateway.Router.ProviderRouter;
import com.llm_gateway.llm_gateway.UsageAggregator.ResolvedUsage;
import com.llm_gateway.llm_gateway.UsageAggregator.UsageService;
import com.llm_gateway.llm_gateway.Utils.CachePolicy;
import com.llm_gateway.llm_gateway.Utils.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RequestExecutor {

    private static final Logger log = LoggerFactory.getLogger(RequestExecutor.class);

    private static final int PER_MILLION_SHIFT = 6;
    private static final int COST_SCALE = 8;   // matches ledger.cost_usd DECIMAL(14,8)

    private static final String SERVED_FROM_UPSTREAM = "upstream";
    private static final String SERVED_FROM_CACHE = "cache";
    private static final String SERVED_FROM_REPLAY = "replay";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";     // client's idempotency key
    private static final String IDEMPOTENCY_ENFORCED_HEADER = "X-Idempotency-Enforced";

    private final ProviderRouter providerRouter;
    private final UsageService usageService;
    private final PricingService pricingService;
    private final LedgerWriter ledgerWriter;
    private final IdempotencyService idempotencyService;
    private final Cacheable cacheable;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final ResponseCache responseCache;

    public RequestExecutor(ProviderRouter providerRouter, UsageService usageService,
                           PricingService pricingService, LedgerWriter ledgerWriter,
                           IdempotencyService idempotencyService, Cacheable cacheable,
                           CacheKeyGenerator cacheKeyGenerator, ResponseCache responseCache) {
        this.providerRouter = providerRouter;
        this.usageService = usageService;
        this.pricingService = pricingService;
        this.ledgerWriter = ledgerWriter;
        this.idempotencyService = idempotencyService;
        this.cacheable = cacheable;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.responseCache = responseCache;
    }

    public Flux<String> execute(JsonNode request, ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String apiKey = headers.getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length()).trim();
        // Idempotency key is the CLIENT's Idempotency-Key header — read only, never generated.
        // (X-Request-Id is a gateway-generated correlation id set by RequestIdFilter; not used here.)
        String idempotencyKey = blankToNull(headers.getFirst(IDEMPOTENCY_KEY_HEADER));
        UUID ledgerId = UUID.randomUUID();                                       // always-fresh surrogate PK
        String model = request.path("model").asString();

        // No client key → no idempotency gate; go straight to the cache/upstream path.
        if (idempotencyKey == null) {
            return serveWithCache(ledgerId, null, apiKey, model, request, headers, false);
        }

        return idempotencyService.reserve(apiKey, idempotencyKey, ledgerId)
                .flatMapMany(result -> switch (result) {
                    case ReservationResult.Owned o -> {
                        exchange.getResponse().getHeaders().set(IDEMPOTENCY_ENFORCED_HEADER, "true");
                        yield serveWithCache(o.ledgerId(), idempotencyKey, apiKey, model, request, headers, true);
                    }
                    case ReservationResult.Replay r -> {
                        exchange.getResponse().getHeaders().set(IDEMPOTENCY_ENFORCED_HEADER, "true");
                        yield replay(ledgerId, idempotencyKey, apiKey, model, request, r.chunks());
                    }
                    case ReservationResult.InProgress ip ->
                            Flux.error(new IdempotencyConflictException(idempotencyKey));
                    case ReservationResult.NotEnforced n -> {
                        // Store unreachable — proceed without enforcement, tell the client.
                        exchange.getResponse().getHeaders().set(IDEMPOTENCY_ENFORCED_HEADER, "false");
                        yield serveWithCache(ledgerId, idempotencyKey, apiKey, model, request, headers, false);
                    }
                });
    }

    /** Cache lookup → hit serves from cache, miss goes upstream (and tees into the cache on success). */
    private Flux<String> serveWithCache(UUID ledgerId, String idempotencyKey, String apiKey, String model,
                                        JsonNode request, HttpHeaders headers, boolean enforced) {
        CachePolicy policy = cacheable.isCacheable(request, headers);
        if (!policy.cacheable()) {
            return streamUpstream(ledgerId, idempotencyKey, apiKey, model, request, enforced, null, null);
        }
        String cacheKey = cacheKeyGenerator.generate(apiKey, request);
        return responseCache.lookup(cacheKey).flatMapMany(hit -> hit
                .map(cached -> serveFromCache(ledgerId, idempotencyKey, apiKey, model, request, cached.chunks(), enforced))
                .orElseGet(() -> streamUpstream(ledgerId, idempotencyKey, apiKey, model, request, enforced, cacheKey, policy)));
    }

    /** Cache hit: no upstream call (cost 0). When enforced, mark the reservation completed with the cached body. */
    private Flux<String> serveFromCache(UUID ledgerId, String idempotencyKey, String apiKey, String model,
                                        JsonNode request, List<String> chunks, boolean enforced) {
        recordLedger(ledgerId, idempotencyKey, apiKey, model, request, chunks, SERVED_FROM_CACHE, false);
        Flux<String> body = Flux.fromIterable(chunks);
        if (enforced) {
            return body.concatWith(Mono.defer(() ->
                    idempotencyService.markCompleted(apiKey, idempotencyKey, ledgerId, chunks).then(Mono.empty())));
        }
        return body;
    }

    /** Replay a stored idempotent response — its own fresh ledgerId, cost 0, reservation already completed. */
    private Flux<String> replay(UUID ledgerId, String idempotencyKey, String apiKey,
                                String model, JsonNode request, List<String> chunks) {
        recordLedger(ledgerId, idempotencyKey, apiKey, model, request, chunks, SERVED_FROM_REPLAY, false);
        return Flux.fromIterable(chunks);
    }

    /**
     * Live provider call. Streams chunks while collecting them; on a terminal signal records the ledger
     * row and (when enforced) transitions the reservation. The AtomicBoolean ensures only the first
     * terminal event records, guarding the error/cancel teardown race. The success work runs inside
     * {@code concatWith} so the client's stream does not complete until the reservation is durably marked
     * (closing the "instant retry gets a false 409" window).
     */
    private Flux<String> streamUpstream(UUID ledgerId, String idempotencyKey, String apiKey, String model,
                                        JsonNode request, boolean enforced, String cacheKey, CachePolicy policy) {
        List<String> chunks = new ArrayList<>();
        AtomicBoolean recorded = new AtomicBoolean(false);

        Flux<String> body = providerRouter.route(request)
                .doOnNext(chunks::add)
                .doOnError(e -> {
                    if (recorded.compareAndSet(false, true)) {
                        if (enforced) {
                            idempotencyService.markFailed(apiKey, idempotencyKey, ledgerId, statusOf(e))
                                    .subscribe(v -> {}, err -> log.error("markFailed subscribe error", err));
                        }
                        recordLedger(ledgerId, idempotencyKey, apiKey, model, request, chunks, SERVED_FROM_UPSTREAM, true);
                    }
                })
                .doOnCancel(() -> {
                    if (recorded.compareAndSet(false, true)) {
                        if (enforced) {
                            idempotencyService.markFailed(apiKey, idempotencyKey, ledgerId, null)
                                    .subscribe(v -> {}, err -> log.error("markFailed subscribe error", err));
                        }
                        recordLedger(ledgerId, idempotencyKey, apiKey, model, request, chunks, SERVED_FROM_UPSTREAM, true);
                    }
                });

        return body.concatWith(Mono.defer(() -> {
            if (recorded.compareAndSet(false, true)) {
                recordLedger(ledgerId, idempotencyKey, apiKey, model, request, chunks, SERVED_FROM_UPSTREAM, false);
                if (cacheKey != null) {
                    responseCache.store(cacheKey, new CachedResponse(List.copyOf(chunks), Instant.now(), policy.ttl()))
                            .subscribe(v -> {}, err -> log.error("cache store error", err));
                }
                if (enforced) {
                    return idempotencyService.markCompleted(apiKey, idempotencyKey, ledgerId, chunks)
                            .then(Mono.<String>empty());
                }
            }
            return Mono.<String>empty();
        }));
    }

    private void recordLedger(UUID ledgerId, String idempotencyKey, String apiKey, String model,
                              JsonNode request, List<String> chunks, String servedFrom, boolean failed) {
        try {
            ResolvedUsage usage = usageService.resolve(model, request, chunks);
            ModelPricing pricing = pricingService.lookup(model, apiKey);

            BigDecimal inputPer1m = pricing != null ? pricing.inputPer1m() : BigDecimal.ZERO;
            BigDecimal outputPer1m = pricing != null ? pricing.outputPer1m() : BigDecimal.ZERO;
            if (pricing == null) {
                log.warn("No pricing for model={}, recording zero cost ledgerId={}", model, ledgerId);
            }

            // Cache hits and replays carry no upstream spend; live calls are priced on resolved tokens.
            boolean zeroCost = SERVED_FROM_CACHE.equals(servedFrom) || SERVED_FROM_REPLAY.equals(servedFrom);
            BigDecimal costUsd = zeroCost
                    ? BigDecimal.ZERO.setScale(COST_SCALE)
                    : cost(inputPer1m, usage.inputTokens())
                            .add(cost(outputPer1m, usage.outputTokens()))
                            .setScale(COST_SCALE, RoundingMode.HALF_UP);

            String status = failed ? failureStatus(usage) : "completed";

            LedgerEntry entry = new LedgerEntry(
                    ledgerId, idempotencyKey, apiKey, model, usage.provider(),
                    usage.inputTokens(), usage.outputTokens(),
                    inputPer1m, outputPer1m, costUsd,
                    usage.tokenSource(), servedFrom, status, Instant.now());

            ledgerWriter.write(entry);
        } catch (Exception e) {
            // The client's response already terminated; a ledger failure must not surface to them.
            log.error("Failed to record ledger entry ledgerId={} model={}", ledgerId, model, e);
        }
    }

    private String failureStatus(ResolvedUsage usage) {
        return (usage.inputTokens() + usage.outputTokens() > 0) ? "failed_partial" : "failed_no_tokens";
    }

    private Integer statusOf(Throwable e) {
        return (e instanceof WebClientResponseException w) ? w.getStatusCode().value() : null;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private BigDecimal cost(BigDecimal pricePer1m, long tokens) {
        return pricePer1m.multiply(BigDecimal.valueOf(tokens)).movePointLeft(PER_MILLION_SHIFT);
    }
}