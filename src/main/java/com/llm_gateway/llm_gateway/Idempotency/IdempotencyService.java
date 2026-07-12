package com.llm_gateway.llm_gateway.Idempotency;

import com.llm_gateway.llm_gateway.Config.IdempotencyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final TypeReference<List<String>> CHUNK_LIST = new TypeReference<>() {};

    private final IdempotencyRepository repository;
    private final IdempotencyConfig config;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public IdempotencyService(IdempotencyRepository repository, IdempotencyConfig config) {
        this.repository = repository;
        this.config = config;
    }

    /**
     * Atomically claim the key and resolve the outcome. On conflict this reads the existing row
     * internally, so the caller never has to. Store unreachable → {@link ReservationResult.NotEnforced}.
     */
    public Mono<ReservationResult> reserve(String apiKey, String idempotencyKey, UUID ledgerId) {
        return reserveAttempt(apiKey, idempotencyKey, ledgerId, 1);
    }

    private Mono<ReservationResult> reserveAttempt(String apiKey, String idempotencyKey, UUID ledgerId,
                                                   int retriesLeft) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(config.ttl());
        Instant staleBefore = now.minus(config.inProgressStaleness());
        return Mono.fromCallable(() ->
                        repository.reserve(apiKey, idempotencyKey, ledgerId, expiresAt, staleBefore) == 1)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(owned -> owned
                        ? Mono.just(new ReservationResult.Owned(ledgerId))
                        : resolveConflict(apiKey, idempotencyKey, ledgerId, retriesLeft))
                .onErrorResume(DataAccessException.class, e -> {
                    log.error("Idempotency store unreachable, proceeding without enforcement apiKey={} key={}",
                            apiKey, idempotencyKey, e);
                    return Mono.just(new ReservationResult.NotEnforced(ledgerId));
                });
    }

    private Mono<ReservationResult> resolveConflict(String apiKey, String idempotencyKey, UUID ledgerId,
                                                    int retriesLeft) {
        return Mono.fromCallable(() -> repository.findById(new IdempotencyKey(apiKey, idempotencyKey)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        // reserve() saw a conflict but the row is now gone: an expired row blocked the
                        // insert, then the sweep deleted it. A fresh reserve will now succeed cleanly —
                        // retrying avoids a spurious 409 on a legitimate request.
                        if (retriesLeft > 0) {
                            log.warn("Reservation row vanished (expired+swept) between reserve and read, "
                                    + "retrying reserve once key={}", idempotencyKey);
                            return reserveAttempt(apiKey, idempotencyKey, ledgerId, retriesLeft - 1);
                        }
                        log.warn("Reservation still vanishing after retry, returning conflict key={}", idempotencyKey);
                        return Mono.just(new ReservationResult.InProgress());
                    }
                    IdempotencyRecord record = opt.get();
                    if (IdempotencyRecord.STATUS_COMPLETED.equals(record.getStatus())) {
                        return Mono.just(new ReservationResult.Replay(
                                deserialize(record.getResponseChunks()), record.getLedgerId()));
                    }
                    return Mono.just(new ReservationResult.InProgress());
                });
    }
    
    public Mono<Void> markCompleted(String apiKey, String idempotencyKey, UUID ledgerId, List<String> chunks) {
        String json = serialize(chunks);
        return Mono.fromCallable(() ->
                        repository.markCompleted(apiKey, idempotencyKey, ledgerId, json))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("markCompleted failed apiKey={} key={}", apiKey, idempotencyKey, e))
                .onErrorReturn(0)
                .then();
    }

    /** Mark the reservation failed so a later duplicate can reclaim it. Guarded + best-effort (see above). */
    public Mono<Void> markFailed(String apiKey, String idempotencyKey, UUID ledgerId, Integer providerStatusCode) {
        return Mono.fromCallable(() ->
                        repository.markFailed(apiKey, idempotencyKey, ledgerId, providerStatusCode))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("markFailed failed apiKey={} key={}", apiKey, idempotencyKey, e))
                .onErrorReturn(0)
                .then();
    }

    @Scheduled(fixedDelayString = "${gateway.idempotency.sweep-interval-ms}")
    public void scheduledSweep() {
        sweep().subscribe(
                count -> { if (count > 0) log.info("Swept {} expired idempotency records", count); },
                err -> log.error("Idempotency sweep failed", err));
    }

    /** Delete all expired reservations in bounded batches. Emits the total rows removed. */
    public Mono<Integer> sweep() {
        return Mono.fromCallable(this::sweepBlocking)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private int sweepBlocking() {
        Instant cutoff = Instant.now();
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteExpired(cutoff, config.sweepBatch());
            total += deleted;
        } while (deleted == config.sweepBatch());
        return total;
    }

    private String serialize(List<String> chunks) {
        try {
            return jsonMapper.writeValueAsString(chunks);
        } catch (Exception e) {
            log.error("Failed to serialize response chunks for idempotency store", e);
            return "[]";
        }
    }

    private List<String> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, CHUNK_LIST);
        } catch (Exception e) {
            log.error("Failed to deserialize stored response chunks for replay", e);
            return List.of();
        }
    }
}