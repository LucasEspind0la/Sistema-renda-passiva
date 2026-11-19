package com.financas.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo ponta a ponta: cadastros via API -> consultas no PostgreSQL -> cálculo do RendaPassivaService. */
class DashboardIT extends AbstractIntegrationTest {

    @Test
    void resumoCombinaReceitasGastosAtivosEDividasDoMes() throws Exception {
        String token = novoToken();

        // receitas: mensal e anual contam em 2026-10; única de setembro e uma futura não contam
        criar(token, "/api/receitas", corpoReceita("5000.00", "MENSAL", "2026-01-05"));
        criar(token, "/api/receitas", corpoReceita("1200.00", "ANUAL", "2025-10-15"));
        criar(token, "/api/receitas", corpoReceita("2000.00", "UNICA", "2026-09-10"));
        criar(token, "/api/receitas", corpoReceita("999.00", "MENSAL", "2026-11-01"));

        // despesas: duas em outubro (uma fixa) e uma em novembro, que fica fora
        criar(token, "/api/despesas", corpoDespesa("1500.00", "FIXO", "2026-10-10", "PENDENTE"));
        criar(token, "/api/despesas", corpoDespesa("400.00", "VARIAVEL", "2026-10-20", "PENDENTE"));
        criar(token, "/api/despesas", corpoDespesa("999.00", "FIXO", "2026-11-01", "PENDENTE"));

        criar(token, "/api/ativos", corpoAtivo("FIIS", "10000.00", "100.00"));
        criar(token, "/api/ativos", corpoAtivo("ACOES", "5000.00", "50.00"));
        criar(token, "/api/dividas", corpoDivida("3000.00", "3000.00"));

        getAuth(token, "/api/dashboard/resumo?mes=2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value("2026-10"))
                .andExpect(jsonPath("$.receitasMes").value(6200.0))
                .andExpect(jsonPath("$.gastosMes").value(1900.0))
                .andExpect(jsonPath("$.saldoAtual").value(4300.0))
                .andExpect(jsonPath("$.totalAtivos").value(15000.0))
                .andExpect(jsonPath("$.totalDividas").value(3000.0))
                .andExpect(jsonPath("$.patrimonioLiquido").value(12000.0))
                .andExpect(jsonPath("$.rendaPassivaMensal").value(150.0))
                .andExpect(jsonPath("$.gastosFixosMensais").value(1500.0))
                .andExpect(jsonPath("$.coberturaIndependenciaPct").value(10.0)) // 150 / 1500 * 100
                .andExpect(jsonPath("$.patrimonioPorTipo.FIIS").value(10000.0))
                .andExpect(jsonPath("$.patrimonioPorTipo.ACOES").value(5000.0))
                .andExpect(jsonPath("$.gastosPorCategoria.Moradia").value(1900.0));
    }

    @Test
    void pagamentoDeDividaReduzOPatrimonioNegativoNoResumo() throws Exception {
        String token = novoToken();
        criar(token, "/api/ativos", corpoAtivo("FIIS", "10000.00", "100.00"));
        String dividaId = criar(token, "/api/dividas", corpoDivida("3000.00", "3000.00"));

        postJson(token, "/api/pagamentos", java.util.Map.of(
                "dividaId", dividaId, "valorPago", new java.math.BigDecimal("1000.00"), "dataPagamento", "2026-10-10"))
                .andExpect(status().isCreated());

        getAuth(token, "/api/dashboard/resumo?mes=2026-10")
                .andExpect(jsonPath("$.totalDividas").value(2000.0))
                .andExpect(jsonPath("$.patrimonioLiquido").value(8000.0));
    }

    @Test
    void usuarioSemDadosRecebeResumoZeradoESemCobertura() throws Exception {
        String token = novoToken();

        getAuth(token, "/api/dashboard/resumo?mes=2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitasMes").value(0))
                .andExpect(jsonPath("$.patrimonioLiquido").value(0))
                .andExpect(jsonPath("$.coberturaIndependenciaPct").value(nullValue()));
    }

    @Test
    void resumoNaoMisturaDadosDeUsuariosDiferentes() throws Exception {
        String usuarioA = novoToken();
        String usuarioB = novoToken();
        criar(usuarioA, "/api/ativos", corpoAtivo("FIIS", "10000.00", "100.00"));

        getAuth(usuarioB, "/api/dashboard/resumo?mes=2026-10")
                .andExpect(jsonPath("$.totalAtivos").value(0))
                .andExpect(jsonPath("$.rendaPassivaMensal").value(0));
    }

    @Test
    void semParametroMesUsaOMesAtual() throws Exception {
        String token = novoToken();

        getAuth(token, "/api/dashboard/resumo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").isNotEmpty());
    }
}