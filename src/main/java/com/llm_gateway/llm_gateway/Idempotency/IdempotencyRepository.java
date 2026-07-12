package com.llm_gateway.llm_gateway.Idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, IdempotencyKey> {
    
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO idempotency_records (api_key, idempotency_key, ledger_id, status, expires_at)
            VALUES (:apiKey, :idempotencyKey, :ledgerId, 'in_progress', :expiresAt)
            ON CONFLICT (api_key, idempotency_key)
            DO UPDATE SET status = 'in_progress',
                          ledger_id = EXCLUDED.ledger_id,
                          expires_at = EXCLUDED.expires_at,
                          created_at = NOW()
            WHERE idempotency_records.status = 'failed'
               OR (idempotency_records.status = 'in_progress'
                   AND idempotency_records.created_at < :staleBefore)
            """)
    int reserve(@Param("apiKey") String apiKey,
                @Param("idempotencyKey") String idempotencyKey,
                @Param("ledgerId") UUID ledgerId,
                @Param("expiresAt") Instant expiresAt,
                @Param("staleBefore") Instant staleBefore);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE idempotency_records
            SET status = 'completed', response_chunks = CAST(:responseChunks AS jsonb)
            WHERE api_key = :apiKey AND idempotency_key = :idempotencyKey
              AND ledger_id = :ledgerId AND status = 'in_progress'
            """)
    int markCompleted(@Param("apiKey") String apiKey,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("ledgerId") UUID ledgerId,
                      @Param("responseChunks") String responseChunks);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE idempotency_records
            SET status = 'failed', provider_status_code = :providerStatusCode
            WHERE api_key = :apiKey AND idempotency_key = :idempotencyKey
              AND ledger_id = :ledgerId AND status = 'in_progress'
            """)
    int markFailed(@Param("apiKey") String apiKey,
                   @Param("idempotencyKey") String idempotencyKey,
                   @Param("ledgerId") UUID ledgerId,
                   @Param("providerStatusCode") Integer providerStatusCode);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM idempotency_records
            WHERE (api_key, idempotency_key) IN (
                SELECT api_key, idempotency_key FROM idempotency_records
                WHERE expires_at < :cutoff
                LIMIT :limit
            )
            """)
    int deleteExpired(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}