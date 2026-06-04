package com.llm_gateway.llm_gateway.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("ollamaWebClient")
    public WebClient ollamaWebClient(@Value("${gateway.upstream.llamaurl}") String url) {
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("openaiWebClient")
    public WebClient openaiWebClient(@Value("${gateway.upstream.openaiurl}") String url,
                                     @Value("${gateway.openai.apikey}") String openapiKey) {
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openapiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("anthropicWebClient")
    public WebClient anthropicWebClient(@Value("${gateway.upstream.anthropicurl}") String url,
                                        @Value("${gateway.anthropic.apikey}") String anthropicKey) {
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + anthropicKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

