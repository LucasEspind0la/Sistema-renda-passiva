package com.financas.service;

import com.financas.domain.Usuario;
import com.financas.dto.AuthDto;
import com.financas.exception.CredenciaisInvalidasException;
import com.financas.exception.RegraNegocioException;
import com.financas.repository.UsuarioRepository;
import com.financas.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
class AuthServiceTest {

    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder encoder;

    // JwtService real: é uma classe pequena e sem I/O, então não vale a pena mockar.
    private final JwtService jwt = new JwtService("segredo-de-teste-com-mais-de-32-bytes-123456", 60);

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(usuarios, encoder, jwt);
    }

    @Test
    void registrarNormalizaDadosCodificaSenhaEEmiteToken() {
        UUID id = UUID.randomUUID();
        when(usuarios.existsByEmail("ana@teste.com")).thenReturn(false);
        when(encoder.encode("senha-segura")).thenReturn("HASH");
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(id);
            return u;
        });

        AuthDto.TokenResponse resposta = service.registrar(
                new AuthDto.RegistroRequest("  Ana  ", "  ANA@Teste.com ", "senha-segura"));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(salvo.capture());
        assertThat(salvo.getValue().getNome()).isEqualTo("Ana");
        assertThat(salvo.getValue().getEmail()).isEqualTo("ana@teste.com");
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("HASH"); // nunca a senha em texto puro

        assertThat(resposta.nome()).isEqualTo("Ana");
        assertThat(jwt.validar(resposta.token())).contains(id);
    }

    @Test
    void registrarRejeitaEmailJaCadastrado() {
        when(usuarios.existsByEmail("ana@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(
                new AuthDto.RegistroRequest("Ana", "ANA@teste.com", "senha-segura")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("E-mail já cadastrado.");

        verify(usuarios, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void loginComCredenciaisCorretasEmiteToken() {
        UUID id = UUID.randomUUID();
        Usuario u = usuario(id, "ana@teste.com", "HASH");
        when(usuarios.findByEmail("ana@teste.com")).thenReturn(Optional.of(u));
        when(encoder.matches("senha-segura", "HASH")).thenReturn(true);

        AuthDto.TokenResponse resposta = service.login(new AuthDto.LoginRequest(" Ana@Teste.com ", "senha-segura"));

        assertThat(jwt.validar(resposta.token())).contains(id);
        assertThat(resposta.nome()).isEqualTo("Ana");
    }

    @Test
    void loginComSenhaErradaFalha() {
        Usuario u = usuario(UUID.randomUUID(), "ana@teste.com", "HASH");
        when(usuarios.findByEmail("ana@teste.com")).thenReturn(Optional.of(u));
        when(encoder.matches("errada", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new AuthDto.LoginRequest("ana@teste.com", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
    }

    @Test
    void loginComEmailInexistenteFalhaComAMesmaMensagem() {
        // Mesma mensagem para "e-mail não existe" e "senha errada": não revela quais e-mails estão cadastrados.
        when(usuarios.findByEmail("ninguem@teste.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AuthDto.LoginRequest("ninguem@teste.com", "qualquer")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verifyNoInteractions(encoder);
    }

    private Usuario usuario(UUID id, String email, String hash) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Ana");
        u.setEmail(email);
        u.setSenhaHash(hash);
        return u;
    }
}