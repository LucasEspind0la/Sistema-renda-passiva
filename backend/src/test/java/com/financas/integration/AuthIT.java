package com.financas.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends AbstractIntegrationTest {

    private static final String SENHA = "senha-segura-123";

    @Test
    void registroCriaOUsuarioERetornaToken() throws Exception {
        postJson(null, "/api/auth/registro", registro("Ana", email()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.expiraEm").isNotEmpty());
    }

    @Test
    void registroComEmailJaCadastradoRetorna422IgnorandoMaiusculas() throws Exception {
        String email = email();
        postJson(null, "/api/auth/registro", registro("Ana", email)).andExpect(status().isCreated());

        postJson(null, "/api/auth/registro", registro("Outra Ana", email.toUpperCase()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado."));
    }

    @Test
    void registroComDadosInvalidosRetorna400ComOsErrosPorCampo() throws Exception {
        postJson(null, "/api/auth/registro", Map.of("nome", "", "email", "nao-e-email", "senha", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos."))
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.senha").exists());
    }

    @Test
    void loginComCredenciaisCorretasRetornaToken() throws Exception {
        String email = email();
        postJson(null, "/api/auth/registro", registro("Ana", email)).andExpect(status().isCreated());

        postJson(null, "/api/auth/login", Map.of("email", email, "senha", SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.nome").value("Ana"));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        String email = email();
        postJson(null, "/api/auth/registro", registro("Ana", email)).andExpect(status().isCreated());

        postJson(null, "/api/auth/login", Map.of("email", email, "senha", "senha-errada-999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void loginComEmailInexistenteRetorna401ComAMesmaMensagem() throws Exception {
        postJson(null, "/api/auth/login", Map.of("email", email(), "senha", SENHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void tokenEmitidoNoRegistroDaAcessoAUmaRotaProtegida() throws Exception {
        String token = novoToken();

        getAuth(token, "/api/despesas").andExpect(status().isOk());
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@teste.com";
    }

    private static Map<String, Object> registro(String nome, String email) {
        return Map.of("nome", nome, "email", email, "senha", SENHA);
    }
}