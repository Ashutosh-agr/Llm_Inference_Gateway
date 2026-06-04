package com.llm_gateway.llm_gateway.Exception;

public class NoProviderFoundException extends RuntimeException {

    public NoProviderFoundException(String model) {
        super("No provider found for model: " + model);
    }
}