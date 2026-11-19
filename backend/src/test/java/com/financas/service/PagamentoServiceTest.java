package com.financas.service;

import com.financas.domain.Despesa;
import com.financas.domain.Divida;
import com.financas.domain.Pagamento;
import com.financas.domain.StatusDespesa;
import com.financas.dto.PagamentoDto;
import com.financas.exception.RecursoNaoEncontradoException;
import com.financas.exception.RegraNegocioException;
import com.financas.repository.DespesaRepository;
import com.financas.repository.DividaRepository;
import com.financas.repository.PagamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID DIVIDA_ID = UUID.randomUUID();
    private static final UUID DESPESA_ID = UUID.randomUUID();
    private static final LocalDate DATA = LocalDate.of(2026, 10, 10);

    @Mock PagamentoRepository pagamentos;
    @Mock DividaRepository dividas;
    @Mock DespesaRepository despesas;
    @InjectMocks PagamentoService service;

    @Test
    void pagamentoDeDividaAbateOValorRemanescente() {
        Divida divida = divida("1000");
        when(dividas.findByIdAndUsuarioId(DIVIDA_ID, USUARIO)).thenReturn(Optional.of(divida));
        when(pagamentos.save(any(Pagamento.class))).thenAnswer(inv -> inv.getArgument(0));

        PagamentoDto.Response r = service.registrar(USUARIO, paraDivida("300"));

        assertThat(divida.getValorRemanescente()).isEqualByComparingTo("700");
        assertThat(r.valorPago()).isEqualByComparingTo("300");
        assertThat(r.dividaId()).isEqualTo(DIVIDA_ID);
        assertThat(r.despesaId()).isNull();
    }

    @Test
    void pagamentoIgualAoRemanescenteQuitaADivida() {
        Divida divida = divida("500");
        when(dividas.findByIdAndUsuarioId(DIVIDA_ID, USUARIO)).thenReturn(Optional.of(divida));
        when(pagamentos.save(any(Pagamento.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(USUARIO, paraDivida("500"));

        assertThat(divida.getValorRemanescente()).isEqualByComparingTo("0");
    }

    @Test
    void pagamentoMaiorQueORemanescenteEhRejeitadoENadaMuda() {
        Divida divida = divida("100");
        when(dividas.findByIdAndUsuarioId(DIVIDA_ID, USUARIO)).thenReturn(Optional.of(divida));

        assertThatThrownBy(() -> service.registrar(USUARIO, paraDivida("100.01")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Pagamento maior que o valor remanescente da dívida.");

        assertThat(divida.getValorRemanescente()).isEqualByComparingTo("100");
        verify(pagamentos, never()).save(any());
    }

    @Test
    void pagamentoDeDespesaMarcaComoPagaEGravaOPagamento() {
        Despesa despesa = new Despesa();
        despesa.setStatus(StatusDespesa.PENDENTE);
        when(despesas.findByIdAndUsuarioId(DESPESA_ID, USUARIO)).thenReturn(Optional.of(despesa));
        when(pagamentos.save(any(Pagamento.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(USUARIO, paraDespesa("150"));

        assertThat(despesa.getStatus()).isEqualTo(StatusDespesa.PAGO);
        ArgumentCaptor<Pagamento> salvo = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentos).save(salvo.capture());
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(USUARIO);
        assertThat(salvo.getValue().getDespesaId()).isEqualTo(DESPESA_ID);
        assertThat(salvo.getValue().getDividaId()).isNull();
    }

    @Test
    void semDestinoEhRejeitado() {
        PagamentoDto.Request semDestino = new PagamentoDto.Request(null, null, new BigDecimal("10"), DATA, null);

        assertThatThrownBy(() -> service.registrar(USUARIO, semDestino))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Informe exatamente um destino: dívida ou despesa.");
        verifyNoInteractions(dividas, despesas, pagamentos);
    }

    @Test
    void comDoisDestinosEhRejeitado() {
        PagamentoDto.Request doisDestinos = new PagamentoDto.Request(DIVIDA_ID, DESPESA_ID, new BigDecimal("10"), DATA, null);

        assertThatThrownBy(() -> service.registrar(USUARIO, doisDestinos))
                .isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(dividas, despesas, pagamentos);
    }

    @Test
    void dividaDeOutroUsuarioOuInexistenteNaoEhEncontrada() {
        when(dividas.findByIdAndUsuarioId(DIVIDA_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(USUARIO, paraDivida("10")))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Dívida não encontrada.");
        verify(pagamentos, never()).save(any());
    }

    @Test
    void despesaDeOutroUsuarioOuInexistenteNaoEhEncontrada() {
        when(despesas.findByIdAndUsuarioId(DESPESA_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(USUARIO, paraDespesa("10")))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Despesa não encontrada.");
        verify(pagamentos, never()).save(any());
    }

    // ---------- fixtures ----------
    private Divida divida(String remanescente) {
        Divida d = new Divida();
        d.setId(DIVIDA_ID);
        d.setUsuarioId(USUARIO);
        d.setValorTotal(new BigDecimal("2000"));
        d.setValorRemanescente(new BigDecimal(remanescente));
        return d;
    }

    private PagamentoDto.Request paraDivida(String valor) {
        return new PagamentoDto.Request(DIVIDA_ID, null, new BigDecimal(valor), DATA, "parcela");
    }

    private PagamentoDto.Request paraDespesa(String valor) {
        return new PagamentoDto.Request(null, DESPESA_ID, new BigDecimal(valor), DATA, null);
    }
}