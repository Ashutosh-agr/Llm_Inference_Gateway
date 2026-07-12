package com.llm_gateway.llm_gateway.Ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        UUID ledgerId, String idempotencyKey, String apiKey, String model, String provider,
      long inputTokens, long outputTokens,
      BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion,
      BigDecimal costUsd, String tokenSource,
      String servedFrom, String status, Instant createdAt
) {}
