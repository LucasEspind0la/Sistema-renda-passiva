package com.financas.service;

import com.financas.domain.Ativo;
import com.financas.domain.TipoAtivo;
import com.financas.dto.AtivoDto;
import com.financas.repository.AtivoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtivoServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Mock AtivoRepository repository;
    @InjectMocks AtivoService service;

    @Test
    void proventosNulosViramZero() {
        when(repository.save(any(Ativo.class))).thenAnswer(inv -> inv.getArgument(0));

        AtivoDto.Response r = service.criar(USUARIO, request(null));

        assertThat(r.proventosMensais()).isEqualByComparingTo("0");
    }

    @Test
    void proventosInformadosSaoPreservados() {
        when(repository.save(any(Ativo.class))).thenAnswer(inv -> inv.getArgument(0));

        AtivoDto.Response r = service.criar(USUARIO, request(new BigDecimal("85.50")));

        assertThat(r.proventosMensais()).isEqualByComparingTo("85.50");
        assertThat(r.tipo()).isEqualTo(TipoAtivo.FIIS);
    }

    private AtivoDto.Request request(BigDecimal proventos) {
        return new AtivoDto.Request("HGLG11", TipoAtivo.FIIS, new BigDecimal("9000"), new BigDecimal("10000"),
                null, null, proventos);
    }
}