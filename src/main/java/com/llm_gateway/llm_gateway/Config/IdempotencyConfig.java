package com.llm_gateway.llm_gateway.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Idempotency tuning, bound from {@code gateway.idempotency.*}.
 *
 * @param ttl                 how long a key stays claimed/replayable before the sweep may reclaim it
 * @param inProgressStaleness after this long, an {@code in_progress} row is presumed dead (crashed
 *                            request) and may be reclaimed by a retry. Set above your p99.9 request
 *                            duration to avoid stealing a legitimately slow request; set to {@code ttl}
 *                            to disable early reclaim (pure expiry-based recovery).
 * @param sweepBatch          max rows deleted per sweep statement (bounds lock duration)
 * @param sweepIntervalMs     how often the sweep runs (referenced by the scheduler via property)
 */
@ConfigurationProperties(prefix = "gateway.idempotency")
public record IdempotencyConfig(Duration ttl, Duration inProgressStaleness,
                                int sweepBatch, long sweepIntervalMs) {
}