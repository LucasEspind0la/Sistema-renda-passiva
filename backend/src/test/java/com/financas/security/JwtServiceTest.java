package com.financas.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-123456";

    private final JwtService jwt = new JwtService(SEGREDO, 60);

    @Test
    void tokenValidoDevolveOIdDoUsuario() {
        UUID id = UUID.randomUUID();
        String token = jwt.gerar(id, "ana@teste.com", jwt.calcularExpiracao());

        assertThat(jwt.validar(token)).contains(id);
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        String token = jwt.gerar(UUID.randomUUID(), "ana@teste.com", Instant.now().minusSeconds(60));

        assertThat(jwt.validar(token)).isEmpty();
    }

    @Test
    void tokenAssinadoComOutraChaveEhRejeitado() {
        JwtService outro = new JwtService("outro-segredo-totalmente-diferente-32-bytes!!", 60);
        String token = outro.gerar(UUID.randomUUID(), "ana@teste.com", outro.calcularExpiracao());

        assertThat(jwt.validar(token)).isEmpty();
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        String token = jwt.gerar(UUID.randomUUID(), "ana@teste.com", jwt.calcularExpiracao());
        int meio = token.indexOf('.') + 5; // dentro do payload
        char trocado = token.charAt(meio) == 'a' ? 'b' : 'a';
        String adulterado = token.substring(0, meio) + trocado + token.substring(meio + 1);

        assertThat(jwt.validar(adulterado)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "a.b.c", "isto-nao-e-um-jwt"})
    void lixoNaoEhTokenValido(String lixo) {
        assertThat(jwt.validar(lixo)).isEmpty();
    }

    @Test
    void expiracaoSegueAValidadeConfigurada() {
        Instant expira = jwt.calcularExpiracao();

        assertThat(expira).isBetween(
                Instant.now().plus(59, ChronoUnit.MINUTES),
                Instant.now().plus(61, ChronoUnit.MINUTES));
    }
}