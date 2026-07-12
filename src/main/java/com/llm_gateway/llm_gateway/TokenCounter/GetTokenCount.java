package com.llm_gateway.llm_gateway.TokenCounter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GetTokenCount implements TokenCounter {
    private final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();

    public long countInput(String model, JsonNode request) {
        return count(model, extractText(request));
    }

    public long countOutput(String model, String responseText) {
        return count(model, responseText == null ? "" : responseText);
    }

    private long count(String model, String text) {
        Encoding enc = encodingFor(model);
        if (enc != null) {
            return enc.countTokens(text);
        }
        return text.length() / 4;   // Anthropic, Ollama, unknown
    }

    private Encoding encodingFor(String model) {
        if (!isOpenAi(model)) {
            return null;
        }
        return registry.getEncodingForModel(model).orElseGet(() -> {
            EncodingType type = usesO200k(model)
                    ? EncodingType.O200K_BASE
                    : EncodingType.CL100K_BASE;
            return registry.getEncoding(type);
        });
    }

    private boolean isOpenAi(String model) {
        return model.startsWith("gpt-") || model.startsWith("o1") || model.startsWith("o3");
    }

    private boolean usesO200k(String model) {
        return model.startsWith("o1") || model.startsWith("o3")
                || model.startsWith("gpt-4o") || model.startsWith("gpt-4.1")
                || model.startsWith("gpt-5");
    }

    private String extractText(JsonNode request) {
        StringBuilder sb = new StringBuilder();
        JsonNode messages = request.get("messages");
        if (messages != null && messages.isArray()) {
            for (JsonNode msg : messages) {
                JsonNode content = msg.get("content");
                if (content != null && content.isString()) {
                    sb.append(content.asString()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}