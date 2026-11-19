package com.financas.integration;

import com.financas.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SegurancaIT extends AbstractIntegrationTest {

    @Autowired JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/despesas", "/api/receitas", "/api/ativos", "/api/dividas", "/api/pagamentos", "/api/dashboard/resumo"})
    void rotasProtegidasSemTokenRetornam401(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mvc.perform(get("/api/despesas").header(HttpHeaders.AUTHORIZATION, "Bearer isto.nao.eh-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        String expirado = jwtService.gerar(UUID.randomUUID(), "ana@teste.com", Instant.now().minusSeconds(60));

        mvc.perform(get("/api/despesas").header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void esquemaDiferenteDeBearerRetorna401() throws Exception {
        String token = novoToken();

        mvc.perform(get("/api/despesas").header(HttpHeaders.AUTHORIZATION, "Basic " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotasDeAutenticacaoSaoPublicas() throws Exception {
        // Corpo vazio: se a rota exigisse token seria 401; sendo pública, a validação responde 400.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentacaoOpenApiEhPublica() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}