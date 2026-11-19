package com.financas.service;

import com.financas.domain.Despesa;
import com.financas.domain.StatusDespesa;
import com.financas.domain.TipoDespesa;
import com.financas.dto.DespesaDto;
import com.financas.exception.RecursoNaoEncontradoException;
import com.financas.repository.DespesaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o fluxo genérico do {@link CrudService} (Template Method) usando DespesaService como representante:
 * o que vale aqui vale para Receita, Ativo e Dívida.
 */
@ExtendWith(MockitoExtension.class)
class DespesaServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID OUTRO_USUARIO = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Mock DespesaRepository repository;
    @InjectMocks DespesaService service;

    @Test
    void criarAtribuiOUsuarioDaRequisicaoEMapeiaOsCampos() {
        when(repository.save(any(Despesa.class))).thenAnswer(inv -> inv.getArgument(0));

        DespesaDto.Response r = service.criar(USUARIO, request("Aluguel", "1500"));

        ArgumentCaptor<Despesa> salva = ArgumentCaptor.forClass(Despesa.class);
        verify(repository).save(salva.capture());
        assertThat(salva.getValue().getUsuarioId()).isEqualTo(USUARIO);
        assertThat(r.descricao()).isEqualTo("Aluguel");
        assertThat(r.valor()).isEqualByComparingTo("1500");
        assertThat(r.status()).isEqualTo(StatusDespesa.PENDENTE);
    }

    @Test
    void listarConsultaSomenteOsRegistrosDoUsuario() {
        when(repository.findByUsuarioId(USUARIO)).thenReturn(List.of(entidade("Aluguel"), entidade("Internet")));

        List<DespesaDto.Response> lista = service.listar(USUARIO);

        assertThat(lista).extracting(DespesaDto.Response::descricao).containsExactly("Aluguel", "Internet");
    }

    @Test
    void buscarRegistroDeOutroUsuarioDaNaoEncontrado() {
        when(repository.findByIdAndUsuarioId(ID, OUTRO_USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(OUTRO_USUARIO, ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Registro não encontrado.");
    }

    @Test
    void atualizarAlteraOsCamposDaEntidadeEncontrada() {
        Despesa existente = entidade("Aluguel");
        when(repository.findByIdAndUsuarioId(ID, USUARIO)).thenReturn(Optional.of(existente));

        DespesaDto.Response r = service.atualizar(USUARIO, ID, request("Aluguel reajustado", "1700"));

        assertThat(existente.getDescricao()).isEqualTo("Aluguel reajustado");
        assertThat(existente.getValor()).isEqualByComparingTo("1700");
        assertThat(r.descricao()).isEqualTo("Aluguel reajustado");
    }

    @Test
    void removerApagaAEntidadeEncontrada() {
        Despesa existente = entidade("Aluguel");
        when(repository.findByIdAndUsuarioId(ID, USUARIO)).thenReturn(Optional.of(existente));

        service.remover(USUARIO, ID);

        verify(repository).delete(existente);
    }

    @Test
    void removerRegistroDeOutroUsuarioNaoApagaNada() {
        when(repository.findByIdAndUsuarioId(ID, OUTRO_USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(OUTRO_USUARIO, ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).delete(any(Despesa.class));
    }

    // ---------- fixtures ----------
    private DespesaDto.Request request(String descricao, String valor) {
        return new DespesaDto.Request(descricao, new BigDecimal(valor), "Moradia", TipoDespesa.FIXO,
                LocalDate.of(2026, 10, 10), StatusDespesa.PENDENTE);
    }

    private Despesa entidade(String descricao) {
        Despesa d = new Despesa();
        d.setUsuarioId(USUARIO);
        d.setDescricao(descricao);
        d.setValor(new BigDecimal("100"));
        d.setCategoria("Moradia");
        d.setTipo(TipoDespesa.FIXO);
        d.setDataVencimento(LocalDate.of(2026, 10, 10));
        d.setStatus(StatusDespesa.PENDENTE);
        return d;
    }
}