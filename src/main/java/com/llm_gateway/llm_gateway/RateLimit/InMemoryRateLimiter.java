package com.llm_gateway.llm_gateway.RateLimit;

import com.llm_gateway.llm_gateway.Config.KeyLimits;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryRateLimiter implements RateLimiter {

    private record BucketPair(Bucket rpm, Bucket tpm){};

    private final ConcurrentHashMap<String, BucketPair> map = new ConcurrentHashMap<>();

    private final KeyLimits keyLimits;

    public InMemoryRateLimiter(KeyLimits keyLimits) {
        this.keyLimits = keyLimits;
    }

    private BucketPair createBuckets(KeyLimits.KeyQuota limits) {
        Bucket rpm = Bucket.builder()
                .addLimit(Bandwidth.classic(limits.rpm(),
                        Refill.intervally(limits.rpm(), Duration.ofMinutes(1))))
                .build();
        Bucket tpm = Bucket.builder()
                .addLimit(Bandwidth.classic(limits.tpm(),
                        Refill.intervally(limits.tpm(), Duration.ofMinutes(1))))
                .build();
        return new BucketPair(rpm, tpm);
    }

    @Override
    public RateLimitResult tryAcquire(String apiKey, int estimatedTokens) {

        KeyLimits.KeyQuota limits = keyLimits.apiKeys().get(apiKey);

        if(limits == null){
            return new RateLimitResult(false, 0, 0, 0);
        }

        BucketPair bucket = map.computeIfAbsent(
                apiKey,
                k -> createBuckets(limits)
        );

        boolean rpmAllowed = bucket.rpm.tryConsume(1);
        boolean tpmAllowed = bucket.tpm.tryConsume(estimatedTokens);

        if(rpmAllowed && tpmAllowed){
            return new RateLimitResult(true, 0,
                    bucket.rpm().getAvailableTokens(),
                    bucket.tpm().getAvailableTokens());
        }

        long waitNanos = Math.max(
                rpmAllowed ? 0 : bucket.rpm().estimateAbilityToConsume(1).getNanosToWaitForRefill(),
                tpmAllowed ? 0 : bucket.tpm().estimateAbilityToConsume(estimatedTokens).getNanosToWaitForRefill()
        );
        long retryAfter = Duration.ofNanos(waitNanos).toSeconds();
        return new RateLimitResult(false, retryAfter,
                bucket.rpm().getAvailableTokens(),
                bucket.tpm().getAvailableTokens());
    }
}
