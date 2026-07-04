package com.llm_gateway.llm_gateway.Cache;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class CacheKeyGenerator {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String generate(String apiKey, JsonNode body) {
        String canonicalBody = canonicalize(body).toString();
        String material = apiKey + '\n' + canonicalBody;
        return sha256Hex(material);
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(node.properties());
            entries.sort(Map.Entry.comparingByKey());
            ObjectNode sorted = jsonMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : entries) {
                sorted.set(entry.getKey(), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = jsonMapper.createArrayNode();
            for (JsonNode item : node) {
                array.add(canonicalize(item));
            }
            return array;
        }
        return node;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
            }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}