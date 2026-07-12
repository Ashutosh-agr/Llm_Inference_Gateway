package com.llm_gateway.llm_gateway.Idempotency;

import java.util.List;
import java.util.UUID;

public sealed interface ReservationResult {

    /** We own the request (fresh key, or reclaimed a failed one) — proceed to upstream. */
    record Owned(UUID ledgerId) implements ReservationResult {}

    /** A completed reservation exists — replay its stored response instead of calling upstream. */
    record Replay(List<String> chunks, UUID ledgerId) implements ReservationResult {}

    /** A live reservation is still in flight — reject the duplicate with 409. */
    record InProgress() implements ReservationResult {}

    /** Idempotency store unreachable — proceed WITHOUT enforcement (fail-open, signal to client). */
    record NotEnforced(UUID ledgerId) implements ReservationResult {}
}