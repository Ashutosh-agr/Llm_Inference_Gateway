package com.llm_gateway.llm_gateway.UsageAggregator;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Component
public class OllamaUsageAggregator implements UsageAggregator {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public boolean supports(String model) {
        return model != null && model.startsWith("llama");
    }

    @Override
    public String provider() {
        return "Ollama";
    }

    @Override
    public Usage aggregate(String model, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new Usage(null, null, provider());
        }

        Long input = null;
        Long output = null;

        for (String line : splitLines(chunks)) {
            try {
                JsonNode node = jsonMapper.readTree(line);
                if (node.has("prompt_eval_count")) {
                    input = node.get("prompt_eval_count").asLong();
                }
                if (node.has("eval_count")) {
                    output = node.get("eval_count").asLong();
                }
            } catch (Exception e) {
                // partial/non-JSON line from stream framing — skip it
            }
        }

        return new Usage(input, output, provider());
    }

    private List<String> splitLines(List<String> chunks) {
        return Arrays.stream(String.join("\n", chunks).split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}