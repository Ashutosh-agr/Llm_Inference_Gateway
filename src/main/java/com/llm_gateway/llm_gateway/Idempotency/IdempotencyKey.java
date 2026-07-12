package com.llm_gateway.llm_gateway.Idempotency;

import java.io.Serializable;
import java.util.Objects;

public class IdempotencyKey implements Serializable {

    private String apiKey;
    private String idempotencyKey;

    public IdempotencyKey() {
    }

    public IdempotencyKey(String apiKey, String idempotencyKey) {
        this.apiKey = apiKey;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey that)) return false;
        return Objects.equals(apiKey, that.apiKey)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, idempotencyKey);
    }
}