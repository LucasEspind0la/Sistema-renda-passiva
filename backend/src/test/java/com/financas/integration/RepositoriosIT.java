package com.financas.integration;

import com.financas.domain.*;
import com.financas.repository.DespesaRepository;
import com.financas.repository.ReceitaRepository;
import com.financas.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa só a camada JPA + o schema do Flyway contra PostgreSQL real (consultas derivadas e constraints do SQL).
 * Cada teste roda numa transação que é desfeita no final (@DataJpaTest).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // usa o Postgres, não o H2
class RepositoriosIT {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registrar(registry);
    }

    @Autowired DespesaRepository despesas;
    @Autowired ReceitaRepository receitas;
    @Autowired UsuarioRepository usuarios;

    private UUID usuario;
    private UUID outroUsuario;

    @BeforeEach
    void criarUsuarios() {
        usuario = novoUsuario();
        outroUsuario = novoUsuario();
    }

    @Test
    void despesasDoMesIncluemOPrimeiroEOUltimoDiaEExcluemVizinhosEOutrosUsuarios() {
        salvarDespesa(usuario, "dia 1", "2026-10-01");
        salvarDespesa(usuario, "dia 31", "2026-10-31");
        salvarDespesa(usuario, "mes anterior", "2026-09-30");
        salvarDespesa(usuario, "mes seguinte", "2026-11-01");
        salvarDespesa(outroUsuario, "de outro", "2026-10-15");

        List<Despesa> doMes = despesas.findByUsuarioIdAndDataVencimentoBetween(
                usuario, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(doMes).extracting(Despesa::getDescricao).containsExactlyInAnyOrder("dia 1", "dia 31");
    }

    @Test
    void receitasAteOLimiteIncluemODiaLimiteEExcluemDatasFuturas() {
        salvarReceita(usuario, "antiga", "2026-01-05");
        salvarReceita(usuario, "no limite", "2026-10-31");
        salvarReceita(usuario, "futura", "2026-11-01");
        salvarReceita(outroUsuario, "de outro", "2026-01-05");

        List<Receita> ate = receitas.findByUsuarioIdAndDataLessThanEqual(usuario, LocalDate.of(2026, 10, 31));

        assertThat(ate).extracting(Receita::getDescricao).containsExactlyInAnyOrder("antiga", "no limite");
    }

    @Test
    void buscarPorIdEUsuarioNaoEncontraRegistroDeOutroUsuario() {
        Despesa d = salvarDespesa(usuario, "minha", "2026-10-10");

        assertThat(despesas.findByIdAndUsuarioId(d.getId(), usuario)).isPresent();
        Optional<Despesa> alheia = despesas.findByIdAndUsuarioId(d.getId(), outroUsuario);
        assertThat(alheia).isEmpty();
    }

    @Test
    void bancoRejeitaEmailDuplicado() {
        Usuario existente = usuarios.findById(usuario).orElseThrow();

        Usuario duplicado = new Usuario();
        duplicado.setNome("Clone");
        duplicado.setEmail(existente.getEmail());
        duplicado.setSenhaHash("hash");

        assertThatThrownBy(() -> usuarios.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRejeitaDespesaComValorZero() {
        Despesa d = novaDespesa(usuario, "invalida", "2026-10-10");
        d.setValor(BigDecimal.ZERO); // CHECK (valor > 0) do V1__schema_inicial.sql

        assertThatThrownBy(() -> despesas.saveAndFlush(d))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- fixtures ----------
    private UUID novoUsuario() {
        Usuario u = new Usuario();
        u.setNome("Teste");
        u.setEmail("repo-" + UUID.randomUUID() + "@teste.com");
        u.setSenhaHash("hash");
        return usuarios.saveAndFlush(u).getId();
    }

    private Despesa novaDespesa(UUID dono, String descricao, String vencimento) {
        Despesa d = new Despesa();
        d.setUsuarioId(dono);
        d.setDescricao(descricao);
        d.setValor(new BigDecimal("100.00"));
        d.setCategoria("Moradia");
        d.setTipo(TipoDespesa.FIXO);
        d.setDataVencimento(LocalDate.parse(vencimento));
        d.setStatus(StatusDespesa.PENDENTE);
        return d;
    }

    private Despesa salvarDespesa(UUID dono, String descricao, String vencimento) {
        return despesas.saveAndFlush(novaDespesa(dono, descricao, vencimento));
    }

    private Receita salvarReceita(UUID dono, String descricao, String data) {
        Receita r = new Receita();
        r.setUsuarioId(dono);
        r.setDescricao(descricao);
        r.setValor(new BigDecimal("1000.00"));
        r.setCategoria("Trabalho");
        r.setTipo(TipoReceita.ATIVA);
        r.setRecorrencia(Recorrencia.MENSAL);
        r.setData(LocalDate.parse(data));
        return receitas.saveAndFlush(r);
    }
}