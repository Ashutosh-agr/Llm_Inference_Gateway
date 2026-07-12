package com.llm_gateway.llm_gateway.UsageAggregator;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Component
public class AnthropicUsageAggregator implements UsageAggregator {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public boolean supports(String modelName) {
        return modelName != null && modelName.startsWith("claude-");
    }

    @Override
    public String provider() {
        return "Anthropic";
    }

    @Override
    public Usage aggregate(String model, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new Usage(null, null, provider());
        }

        Long input = null;
        Long output = null;

        for (String line : splitLines(chunks)) {
            if (line.startsWith("event:")) {
                continue;
            }
            String payload = stripDataPrefix(line);
            if (payload.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = jsonMapper.readTree(payload);
                String type = node.path("type").asString();

                if ("message_start".equals(type)) {
                    JsonNode usage = node.path("message").path("usage");
                    if (usage.has("input_tokens")) {
                        input = usage.get("input_tokens").asLong();
                    }
                    if (usage.has("output_tokens")) {
                        output = usage.get("output_tokens").asLong();
                    }
                } else if ("message_delta".equals(type)) {
                    JsonNode usage = node.path("usage");
                    if (usage.has("output_tokens")) {
                        output = usage.get("output_tokens").asLong(); // cumulative — last wins
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