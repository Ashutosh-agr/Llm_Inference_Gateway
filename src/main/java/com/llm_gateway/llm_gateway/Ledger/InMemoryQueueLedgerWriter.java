package com.llm_gateway.llm_gateway.Ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class InMemoryQueueLedgerWriter implements LedgerWriter{

    private static final Logger log = LoggerFactory.getLogger(InMemoryQueueLedgerWriter.class);

    private final LedgerRepository ledgerRepository;

    private final AtomicBoolean draining = new AtomicBoolean(false);
    ExecutorService executorService =  Executors.newSingleThreadExecutor();

    private static final int CAPACITY = 10_000;

    private static final int DRAIN_THRESHOLD = 100;

    private final LinkedBlockingQueue<LedgerEntry> queue = new LinkedBlockingQueue<>(CAPACITY);

    public InMemoryQueueLedgerWriter(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public void write(LedgerEntry entry) {
        boolean queued = queue.offer(entry);   // push (non-blocking)
        if (!queued) {
            // Backpressure — queue is at capacity. Drop and alert.
            log.error("Ledger queue full (capacity {}), dropping entry: {}", CAPACITY, entry);
        }

        // Count trigger: only kick a drain once the queue has built up.
        if (queue.size() >= DRAIN_THRESHOLD) {
            triggerDrain();
        }
    }

    // Time trigger: safety net so a small trickle still gets flushed.
    @Scheduled(fixedDelay = 1000)
    public void scheduledDrain() {
        triggerDrain();
    }

    private void triggerDrain() {
        if (!draining.compareAndSet(false, true)) {
            // Another drain is already in progress.
            return;
        }
        executorService.submit(this::drain);
    }

    private void drain() {
        try {
            List<LedgerEntry> batch = new ArrayList<>();
            queue.drainTo(batch);
            if (batch.isEmpty()) {
                return;
            }
            List<LedgerEntity> entities = batch.stream().map(LedgerEntity::from).toList();
            try {
                ledgerRepository.saveAll(entities);
            } catch (Exception e) {
                log.error("Ledger batch insert failed, {} entries lost", entities.size(), e);
            }
        } finally {
            draining.set(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        // Stop accepting new drains and wait for any in-flight one to finish...
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        drain();
    }
}
