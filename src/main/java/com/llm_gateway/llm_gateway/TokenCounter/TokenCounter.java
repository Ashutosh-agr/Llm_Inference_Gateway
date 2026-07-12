package com.llm_gateway.llm_gateway.TokenCounter;

import tools.jackson.databind.JsonNode;

public interface TokenCounter {
    long countInput(String model, JsonNode request);
    long countOutput(String model, String responseText);
}
