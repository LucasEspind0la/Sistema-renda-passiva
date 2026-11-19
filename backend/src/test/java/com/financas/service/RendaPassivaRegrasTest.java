package com.financas.service;

import com.financas.domain.*;
import com.financas.dto.ResumoFinanceiroDto;
import com.financas.repository.AtivoRepository;
import com.financas.repository.DespesaRepository;
import com.financas.repository.DividaRepository;
import com.financas.repository.ReceitaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Complementa o RendaPassivaServiceTest: regras de recorrência, arredondamento e agrupamentos. */
@ExtendWith(MockitoExtension.class)
class RendaPassivaRegrasTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Mock ReceitaRepository receitas;
    @Mock DespesaRepository despesas;
    @Mock AtivoRepository ativos;
    @Mock DividaRepository dividas;
    @InjectMocks RendaPassivaService service;

    @ParameterizedTest(name = "{0} desde {1}, consultando {2} -> {3}")
    @CsvSource({
            "UNICA,  2026-10-15, 2026-10, true",   // só no mês da data
            "UNICA,  2026-10-15, 2026-11, false",  // mês seguinte: já passou
            "UNICA,  2026-10-15, 2026-09, false",  // mês anterior: ainda não aconteceu
            "MENSAL, 2026-01-31, 2026-10, true",   // todo mês a partir da data
            "MENSAL, 2026-10-01, 2026-10, true",   // incluindo o mês de origem
            "MENSAL, 2026-10-01, 2026-09, false",  // antes da origem não conta
            "ANUAL,  2025-10-15, 2026-10, true",   // mesmo mês, ano seguinte
            "ANUAL,  2025-10-15, 2026-11, false",  // mês diferente
            "ANUAL,  2026-10-15, 2025-10, false"   // mesmo mês, mas antes da origem
    })
    void receitaOcorreNoMesConformeARecorrencia(Recorrencia recorrencia, LocalDate origem, YearMonth mes, boolean esperado) {
        Receita r = new Receita();
        r.setRecorrencia(recorrencia);
        r.setData(origem);

        assertThat(service.ocorreNoMes(r, mes)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "renda {0} / fixos {1} = {2}%")
    @CsvSource({
            "100,  1500, 6.67",    // 6,666... arredonda para cima (HALF_UP)
            "0,    1500, 0.00",    // sem renda passiva
            "1500, 1500, 100.00",  // independência exata
            "3000, 2000, 150.00"   // pode passar de 100%
    })
    void coberturaArredondaComDuasCasas(String renda, String fixos, String esperado) {
        assertThat(service.calcularCobertura(new BigDecimal(renda), new BigDecimal(fixos)))
                .isEqualByComparingTo(esperado);
    }

    @Test
    void agrupaGastosPorCategoriaSomandoOsValores() {
        when(receitas.findByUsuarioIdAndDataLessThanEqual(any(), any())).thenReturn(List.of());
        when(despesas.findByUsuarioIdAndDataVencimentoBetween(any(), any(), any())).thenReturn(List.of(
                despesa("100", TipoDespesa.VARIAVEL, "Lazer"),
                despesa("50", TipoDespesa.VARIAVEL, "Lazer"),
                despesa("1000", TipoDespesa.FIXO, "Moradia")));
        when(ativos.findByUsuarioId(USUARIO)).thenReturn(List.of());
        when(dividas.findByUsuarioId(USUARIO)).thenReturn(List.of());

        ResumoFinanceiroDto r = service.calcularResumo(USUARIO, YearMonth.of(2026, 10));

        assertThat(r.gastosPorCategoria()).containsOnlyKeys("Lazer", "Moradia");
        assertThat(r.gastosPorCategoria().get("Lazer")).isEqualByComparingTo("150");
        assertThat(r.gastosPorCategoria().get("Moradia")).isEqualByComparingTo("1000");
    }

    @Test
    void valoresNulosNaoQuebramOsTotais() {
        Ativo semProventos = new Ativo();
        semProventos.setTipo(TipoAtivo.ACOES);
        semProventos.setValorAtual(new BigDecimal("1000"));
        semProventos.setProventosMensais(null);

        when(receitas.findByUsuarioIdAndDataLessThanEqual(any(), any())).thenReturn(List.of());
        when(despesas.findByUsuarioIdAndDataVencimentoBetween(any(), any(), any())).thenReturn(List.of());
        when(ativos.findByUsuarioId(USUARIO)).thenReturn(List.of(semProventos));
        when(dividas.findByUsuarioId(USUARIO)).thenReturn(List.of());

        ResumoFinanceiroDto r = service.calcularResumo(USUARIO, YearMonth.of(2026, 10));

        assertThat(r.totalAtivos()).isEqualByComparingTo("1000");
        assertThat(r.rendaPassivaMensal()).isEqualByComparingTo("0");
    }

    private Despesa despesa(String valor, TipoDespesa tipo, String categoria) {
        Despesa d = new Despesa();
        d.setValor(new BigDecimal(valor));
        d.setTipo(tipo);
        d.setCategoria(categoria);
        return d;
    }
}