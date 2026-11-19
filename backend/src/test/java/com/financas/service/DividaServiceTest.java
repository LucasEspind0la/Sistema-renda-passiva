package com.financas.service;

import com.financas.domain.Divida;
import com.financas.dto.DividaDto;
import com.financas.exception.RegraNegocioException;
import com.financas.repository.DividaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividaServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Mock DividaRepository repository;
    @InjectMocks DividaService service;

    @Test
    void criarRejeitaRemanescenteMaiorQueOTotal() {
        assertThatThrownBy(() -> service.criar(USUARIO, request("1000", "1000.01")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O valor remanescente não pode exceder o valor total da dívida.");

        verify(repository, never()).save(any());
    }

    @Test
    void criarAceitaRemanescenteIgualAoTotal() {
        when(repository.save(any(Divida.class))).thenAnswer(inv -> inv.getArgument(0));

        DividaDto.Response r = service.criar(USUARIO, request("1000", "1000"));

        assertThat(r.valorTotal()).isEqualByComparingTo("1000");
        assertThat(r.valorRemanescente()).isEqualByComparingTo("1000");
    }

    @Test
    void atualizarRejeitaRemanescenteMaiorQueOTotalSemAlterarARegistro() {
        UUID id = UUID.randomUUID();
        Divida existente = new Divida();
        existente.setDescricao("Cartão");
        existente.setValorTotal(new BigDecimal("1000"));
        existente.setValorRemanescente(new BigDecimal("400"));
        when(repository.findByIdAndUsuarioId(id, USUARIO)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizar(USUARIO, id, request("500", "600")))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(existente.getValorTotal()).isEqualByComparingTo("1000");
        assertThat(existente.getValorRemanescente()).isEqualByComparingTo("400");
    }

    private DividaDto.Request request(String total, String remanescente) {
        return new DividaDto.Request("Cartão", new BigDecimal(total), new BigDecimal(remanescente),
                BigDecimal.ZERO, LocalDate.of(2027, 12, 31));
    }
}