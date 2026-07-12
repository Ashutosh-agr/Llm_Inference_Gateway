package com.llm_gateway.llm_gateway.support;

import com.llm_gateway.llm_gateway.Provider.LlmProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * A deterministic stand-in for a real LLM provider, so integration tests never touch Ollama/OpenAI.
 * It only claims {@link #TEST_MODEL}, which no real provider matches, so {@code ProviderRouter}
 * routes {@code test-model} requests here and everything else to the real providers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubProviderConfiguration {

    public static final String TEST_MODEL = "test-model";
    public static final List<String> STUB_CHUNKS = List.of("Hello", " from", " the", " stub");

    @Bean
    public LlmProvider stubLlmProvider() {
        return new LlmProvider() {
            @Override
            public boolean supports(String modelName) {
                return TEST_MODEL.equals(modelName);
            }

            @Override
            public Flux<String> complete(JsonNode request) {
                return Flux.fromIterable(STUB_CHUNKS);
            }
        };
    }
}