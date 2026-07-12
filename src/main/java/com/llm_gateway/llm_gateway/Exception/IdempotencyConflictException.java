package com.llm_gateway.llm_gateway.Exception;

/** Raised when a request arrives with an idempotency key whose reservation is still in flight. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("A request with this idempotency key is already in progress: " + idempotencyKey);
    }
}