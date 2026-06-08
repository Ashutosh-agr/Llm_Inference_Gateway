package com.llm_gateway.llm_gateway.RateLimit;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TokenEstimator {

    public int estimate(JsonNode request){
        String text = extractText(request);
        return Math.ceilDiv(text.length(), 4) + 20;
    }

    private String extractText(JsonNode request){
        // Ollama /api/generate shape: a single "prompt" string.
        JsonNode prompt = request.path("prompt");
        if (prompt.isString()) {
            return prompt.asString();
        }

        // OpenAI/Anthropic chat shape: a "messages" array of {role, content}.
        JsonNode messages = request.path("messages");
        if (messages.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode message : messages) {
                sb.append(message.path("content").asString("")).append(' ');
            }
            return sb.toString();
        }

        return "";
    }
}
