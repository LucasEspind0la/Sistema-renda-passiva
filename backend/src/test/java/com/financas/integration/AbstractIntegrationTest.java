package com.financas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe a aplicação inteira (filtros de segurança, controllers, JPA, Flyway) contra um PostgreSQL real.
 *
 * Isolamento entre testes: cada teste cria seus próprios usuários (e-mail aleatório) e, como toda consulta é
 * filtrada por usuário, os dados de um teste nunca aparecem em outro. Não precisa limpar o banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registrar(registry);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    // ---------- autenticação ----------

    /** Registra um usuário novo e devolve o JWT dele. */
    protected String novoToken() throws Exception {
        Map<String, Object> corpo = Map.of(
                "nome", "Usuário de Teste",
                "email", "user-" + UUID.randomUUID() + "@teste.com",
                "senha", "senha-segura-123");
        String resposta = postJson(null, "/api/auth/registro", corpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(resposta).get("token").asText();
    }

    // ---------- requisições ----------

    protected ResultActions postJson(String token, String url, Object corpo) throws Exception {
        return mvc.perform(comToken(MockMvcRequestBuilders.post(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    protected ResultActions putJson(String token, String url, Object corpo) throws Exception {
        return mvc.perform(comToken(MockMvcRequestBuilders.put(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    protected ResultActions getAuth(String token, String url) throws Exception {
        return mvc.perform(comToken(MockMvcRequestBuilders.get(url), token));
    }

    protected ResultActions deleteAuth(String token, String url) throws Exception {
        return mvc.perform(comToken(MockMvcRequestBuilders.delete(url), token));
    }

    private MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder req, String token) {
        return token == null ? req : req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** POST esperando 201 e devolvendo o id do recurso criado. */
    protected String criar(String token, String url, Object corpo) throws Exception {
        String resposta = postJson(token, url, corpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(resposta).get("id").asText();
    }

    // ---------- corpos de requisição ----------

    protected static Map<String, Object> corpoDespesa(String valor, String tipo, String vencimento, String status) {
        return Map.of("descricao", "Aluguel", "valor", new BigDecimal(valor), "categoria", "Moradia",
                "tipo", tipo, "dataVencimento", vencimento, "status", status);
    }

    protected static Map<String, Object> corpoReceita(String valor, String recorrencia, String data) {
        return Map.of("descricao", "Salário", "valor", new BigDecimal(valor), "categoria", "Trabalho",
                "tipo", "ATIVA", "recorrencia", recorrencia, "data", data);
    }

    protected static Map<String, Object> corpoAtivo(String tipo, String valorAtual, String proventos) {
        return Map.of("nome", "Ativo " + tipo, "tipo", tipo, "valorInvestido", new BigDecimal(valorAtual),
                "valorAtual", new BigDecimal(valorAtual), "proventosMensais", new BigDecimal(proventos));
    }

    protected static Map<String, Object> corpoDivida(String total, String remanescente) {
        return Map.of("descricao", "Cartão", "valorTotal", new BigDecimal(total),
                "valorRemanescente", new BigDecimal(remanescente), "taxaJurosMensal", BigDecimal.ZERO,
                "dataVencimentoTotal", "2027-12-31");
    }
}