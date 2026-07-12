package com.llm_gateway.llm_gateway.Ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger")
@Getter
@Setter
public class LedgerEntity {

    @Id
    @Column(name = "ledger_id", nullable = false, updatable = false)
    private UUID ledgerId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "api_key", nullable = false, length = 255)
    private String apiKey;

    @Column(name = "model", nullable = false, length = 255)
    private String model;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "input_price_per_million", nullable = false, precision = 12, scale = 6)
    private BigDecimal inputPricePerMillion;

    @Column(name = "output_price_per_million", nullable = false, precision = 12, scale = 6)
    private BigDecimal outputPricePerMillion;

    @Column(name = "cost_usd", nullable = false, precision = 14, scale = 8)
    private BigDecimal costUsd;

    @Column(name = "token_source", nullable = false, length = 16)
    private String tokenSource;

    @Column(name = "served_from", nullable = false, length = 16)
    private String servedFrom;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static LedgerEntity from(LedgerEntry entry) {
        LedgerEntity entity = new LedgerEntity();
        entity.setLedgerId(entry.ledgerId());
        entity.setIdempotencyKey(entry.idempotencyKey());
        entity.setApiKey(entry.apiKey());
        entity.setModel(entry.model());
        entity.setProvider(entry.provider());
        entity.setInputTokens(entry.inputTokens());
        entity.setOutputTokens(entry.outputTokens());
        entity.setInputPricePerMillion(entry.inputPricePerMillion());
        entity.setOutputPricePerMillion(entry.outputPricePerMillion());
        entity.setCostUsd(entry.costUsd());
        entity.setTokenSource(entry.tokenSource());
        entity.setServedFrom(entry.servedFrom());
        entity.setStatus(entry.status());
        entity.setCreatedAt(entry.createdAt());
        return entity;
    }
}
