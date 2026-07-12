package com.llm_gateway.llm_gateway;

import com.llm_gateway.llm_gateway.Idempotency.IdempotencyRepository;
import com.llm_gateway.llm_gateway.Idempotency.IdempotencyService;
import com.llm_gateway.llm_gateway.Idempotency.ReservationResult;
import com.llm_gateway.llm_gateway.Ledger.LedgerEntity;
import com.llm_gateway.llm_gateway.Ledger.LedgerRepository;
import com.llm_gateway.llm_gateway.support.StubProviderConfiguration;
import com.llm_gateway.llm_gateway.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests for the ledger + idempotency + cache pipeline, mirroring the manual Step-6
 * verification. Uses a real Postgres (Testcontainers) and a stubbed provider — only Docker is
 * required, no local Postgres or Ollama. Run with {@code ./mvnw test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, StubProviderConfiguration.class})
class GatewayLedgerIdempotencyIntegrationTest {

    private static final String API_KEY = "test-key-bob";
    private static final Duration LEDGER_TIMEOUT = Duration.ofSeconds(15);

    // Default temperature (1.0) → not cacheable, so idempotency is the only dedup.
    private static final String NON_CACHEABLE =
            "{\"model\":\"test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hello there\"}],\"stream\":true}";
    // temperature 0 → cacheable.
    private static final String CACHEABLE =
            "{\"model\":\"test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"cache me\"}],\"stream\":true,\"temperature\":0}";

    @Value("${local.server.port}") int port;
    @Autowired LedgerRepository ledgerRepository;
    @Autowired IdempotencyRepository idempotencyRepository;
    @Autowired IdempotencyService idempotencyService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    void liveRequest_writesUpstreamLedgerRow() {
        String key = newKey();

        bodyOf(fire(API_KEY, key, NON_CACHEABLE));

        await().atMost(LEDGER_TIMEOUT).untilAsserted(() -> {
            List<LedgerEntity> rows = ledgerRowsFor(key);
            assertThat(rows).hasSize(1);
            LedgerEntity row = rows.get(0);
            assertThat(row.getServedFrom()).isEqualTo("upstream");
            assertThat(row.getStatus()).isEqualTo("completed");
            assertThat(row.getInputTokens()).isPositive();
            assertThat(row.getOutputTokens()).isPositive();
            assertThat(row.getCostUsd()).isGreaterThan(BigDecimal.ZERO);
        });
    }

    @Test
    void cacheHit_producesZeroCostRow() {
        String missKey = newKey();
        String hitKey = newKey();

        // Same body (same cache key), different idempotency keys so the 2nd isn't a replay.
        bodyOf(fire(API_KEY, missKey, CACHEABLE));   // miss → upstream, stores cache
        bodyOf(fire(API_KEY, hitKey, CACHEABLE));    // same body → cache hit

        await().atMost(LEDGER_TIMEOUT).untilAsserted(() -> {
            assertThat(ledgerRowsFor(missKey)).singleElement()
                    .satisfies(r -> assertThat(r.getServedFrom()).isEqualTo("upstream"));
            List<LedgerEntity> hit = ledgerRowsFor(hitKey);
            assertThat(hit).hasSize(1);
            assertThat(hit.get(0).getServedFrom()).isEqualTo("cache");
            assertThat(hit.get(0).getCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(hit.get(0).getStatus()).isEqualTo("completed");
        });
    }

    @Test
    void duplicateIdempotencyKey_replaysAndRecordsReplayRow() {
        String key = newKey();

        String first = bodyOf(fire(API_KEY, key, NON_CACHEABLE));
        String second = bodyOf(fire(API_KEY, key, NON_CACHEABLE));   // same key → replay

        assertThat(second).isEqualTo(first);

        await().atMost(LEDGER_TIMEOUT).untilAsserted(() -> {
            List<LedgerEntity> rows = ledgerRowsFor(key);
            assertThat(rows).hasSize(2);
            assertThat(rows.stream().map(LedgerEntity::getServedFrom).toList())
                    .containsExactlyInAnyOrder("upstream", "replay");
            LedgerEntity replay = rows.stream()
                    .filter(r -> r.getServedFrom().equals("replay")).findFirst().orElseThrow();
            assertThat(replay.getCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(replay.getStatus()).isEqualTo("completed");
        });
    }

    @Test
    void inProgressKey_returns409() {
        String key = newKey();
        // Seed an in_progress reservation (not stale) so the request collides with it.
        idempotencyRepository.reserve(API_KEY, key, UUID.randomUUID(),
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().minus(Duration.ofMinutes(10)));

        fire(API_KEY, key, NON_CACHEABLE).expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reserve_isAtomic_secondCallIsInProgress() {
        String key = newKey();

        ReservationResult first = idempotencyService.reserve(API_KEY, key, UUID.randomUUID()).block();
        ReservationResult second = idempotencyService.reserve(API_KEY, key, UUID.randomUUID()).block();

        assertThat(first).isInstanceOf(ReservationResult.Owned.class);
        assertThat(second).isInstanceOf(ReservationResult.InProgress.class);
    }

    // ---- helpers ----

    private static String newKey() {
        return "it-" + UUID.randomUUID();
    }

    private WebTestClient.ResponseSpec fire(String apiKey, String idempotencyKey, String json) {
        WebTestClient.RequestBodySpec req = webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            req = req.header("Idempotency-Key", idempotencyKey);
        }
        return req.bodyValue(json).exchange();
    }

    private String bodyOf(WebTestClient.ResponseSpec spec) {
        List<String> chunks = spec.expectStatus().isOk()
                .returnResult(String.class).getResponseBody()
                .collectList().block(Duration.ofSeconds(30));
        return chunks == null ? "" : String.join("", chunks);
    }

    private List<LedgerEntity> ledgerRowsFor(String idempotencyKey) {
        return ledgerRepository.findAll().stream()
                .filter(r -> idempotencyKey.equals(r.getIdempotencyKey()))
                .toList();
    }
}