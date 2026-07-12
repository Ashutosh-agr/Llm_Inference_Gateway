package com.llm_gateway.llm_gateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a real Postgres in Docker for integration tests. {@code @ServiceConnection} wires the
 * container's JDBC details into {@code spring.datasource.*} automatically, so Flyway migrates the
 * real schema and the tests exercise the genuine SQL (native upserts, JSONB, etc.).
 *
 * <p>Only Docker is required — no local Postgres or Ollama. Import this into any {@code @SpringBootTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}