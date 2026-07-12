package com.llm_gateway.llm_gateway.Idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
@IdClass(IdempotencyKey.class)
@Getter
@Setter
public class IdempotencyRecord {

    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    @Id
    @Column(name = "api_key", nullable = false, length = 255)
    private String apiKey;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "ledger_id", nullable = false)
    private UUID ledgerId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "response_chunks", columnDefinition = "jsonb")
    private String responseChunks;

    @Column(name = "provider_status_code")
    private Integer providerStatusCode;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}