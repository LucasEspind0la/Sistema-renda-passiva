package com.financas.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagamentoIT extends AbstractIntegrationTest {

    @Test
    void pagamentosAbatemORemanescenteDaDivida() throws Exception {
        String token = novoToken();
        String dividaId = criar(token, "/api/dividas", corpoDivida("1000.00", "1000.00"));

        postJson(token, "/api/pagamentos", pagamento(dividaId, null, "300.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dividaId").value(dividaId))
                .andExpect(jsonPath("$.valorPago").value(300.0));
        getAuth(token, "/api/dividas/" + dividaId).andExpect(jsonPath("$.valorRemanescente").value(700.0));

        postJson(token, "/api/pagamentos", pagamento(dividaId, null, "700.00")).andExpect(status().isCreated());
        getAuth(token, "/api/dividas/" + dividaId).andExpect(jsonPath("$.valorRemanescente").value(0.0));

        getAuth(token, "/api/pagamentos").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void pagamentoMaiorQueORemanescenteRetorna422ENaoAlteraADivida() throws Exception {
        String token = novoToken();
        String dividaId = criar(token, "/api/dividas", corpoDivida("100.00", "100.00"));

        postJson(token, "/api/pagamentos", pagamento(dividaId, null, "150.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Pagamento maior que o valor remanescente da dívida."));

        getAuth(token, "/api/dividas/" + dividaId).andExpect(jsonPath("$.valorRemanescente").value(100.0));
        getAuth(token, "/api/pagamentos").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pagamentoDeDespesaMarcaADespesaComoPaga() throws Exception {
        String token = novoToken();
        String despesaId = criar(token, "/api/despesas", corpoDespesa("200.00", "FIXO", "2026-10-10", "PENDENTE"));

        postJson(token, "/api/pagamentos", pagamento(null, despesaId, "200.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.despesaId").value(despesaId));

        getAuth(token, "/api/despesas/" + despesaId).andExpect(jsonPath("$.status").value("PAGO"));
    }

    @Test
    void pagamentoSemDestinoOuComDoisDestinosRetorna422() throws Exception {
        String token = novoToken();
        String dividaId = criar(token, "/api/dividas", corpoDivida("100.00", "100.00"));
        String despesaId = criar(token, "/api/despesas", corpoDespesa("50.00", "FIXO", "2026-10-10", "PENDENTE"));

        postJson(token, "/api/pagamentos", pagamento(null, null, "10.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Informe exatamente um destino: dívida ou despesa."));

        postJson(token, "/api/pagamentos", pagamento(dividaId, despesaId, "10.00"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void naoPagaDividaNemDespesaDeOutroUsuario() throws Exception {
        String dono = novoToken();
        String intruso = novoToken();
        String dividaId = criar(dono, "/api/dividas", corpoDivida("100.00", "100.00"));
        String despesaId = criar(dono, "/api/despesas", corpoDespesa("50.00", "FIXO", "2026-10-10", "PENDENTE"));

        postJson(intruso, "/api/pagamentos", pagamento(dividaId, null, "10.00")).andExpect(status().isNotFound());
        postJson(intruso, "/api/pagamentos", pagamento(null, despesaId, "50.00")).andExpect(status().isNotFound());

        getAuth(dono, "/api/dividas/" + dividaId).andExpect(jsonPath("$.valorRemanescente").value(100.0));
        getAuth(dono, "/api/despesas/" + despesaId).andExpect(jsonPath("$.status").value("PENDENTE"));
        getAuth(intruso, "/api/pagamentos").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void valorPagoZeroRetorna400() throws Exception {
        String token = novoToken();
        String dividaId = criar(token, "/api/dividas", corpoDivida("100.00", "100.00"));

        postJson(token, "/api/pagamentos", pagamento(dividaId, null, "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valorPago").exists());
    }

    @Test
    void dividaComRemanescenteMaiorQueOTotalRetorna422() throws Exception {
        String token = novoToken();

        postJson(token, "/api/dividas", corpoDivida("100.00", "150.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O valor remanescente não pode exceder o valor total da dívida."));
    }

    private static Map<String, Object> pagamento(String dividaId, String despesaId, String valor) {
        Map<String, Object> corpo = new HashMap<>(); // Map.of não aceita null
        if (dividaId != null) corpo.put("dividaId", dividaId);
        if (despesaId != null) corpo.put("despesaId", despesaId);
        corpo.put("valorPago", new BigDecimal(valor));
        corpo.put("dataPagamento", "2026-10-10");
        corpo.put("observacao", "teste");
        return corpo;
    }
}