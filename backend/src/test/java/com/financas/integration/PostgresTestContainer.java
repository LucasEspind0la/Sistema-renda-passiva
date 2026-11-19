package com.financas.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Um único PostgreSQL (o mesmo da produção: 16) compartilhado por todos os testes de integração.
 * O container sobe uma vez por execução do Maven e é removido ao final pelo Ryuk do Testcontainers.
 * Requer o Docker rodando.
 */
final class PostgresTestContainer {

    private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {}

    static void registrar(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", INSTANCE::getUsername);
        registry.add("spring.datasource.password", INSTANCE::getPassword);
        // o application.yml não tem mais valor padrão para o segredo do JWT
        registry.add("app.jwt.secret", () -> "segredo-de-teste-com-mais-de-32-bytes-123456");
    }
}