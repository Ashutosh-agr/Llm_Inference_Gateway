package com.llm_gateway.llm_gateway.UsageAggregator;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Component
public class OpenAiUsageAggregator implements UsageAggregator {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public boolean supports(String modelName) {
        return modelName != null
                && (modelName.startsWith("gpt-") || modelName.startsWith("o1") || modelName.startsWith("o3"));
    }

    @Override
    public String provider() {
        return "OpenAI";
    }

    @Override
    public Usage aggregate(String model, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new Usage(null, null, provider());
        }

        Long input = null;
        Long output = null;

        for (String line : splitLines(chunks)) {
            String payload = stripDataPrefix(line);
            if (payload.isEmpty() || payload.equals("[DONE]")) {
                continue;
            }
            try {
                JsonNode node = jsonMapper.readTree(payload);
                JsonNode usage = node.get("usage");
                if (usage != null && !usage.isNull()) {
                    if (usage.has("prompt_tokens")) {
                        input = usage.get("prompt_tokens").asLong();
                    }
                    if (usage.has("completion_tokens")) {
                        output = usage.get("completion_tokens").asLong();
                    }
                }
            } catch (Exception e) {
                // partial/non-JSON line from stream framing — skip it
            }
        }

        return new Usage(input, output, provider());
    }

    private String stripDataPrefix(String line) {
        return line.startsWith("data:") ? line.substring("data:".length()).trim() : line;
    }

    private List<String> splitLines(List<String> chunks) {
        return Arrays.stream(String.join("\n", chunks).split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}