package com.example.demo;

import com.example.demo.application.dto.LoginRequestDto;
import com.example.demo.application.service.AuthService;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceDisabledUserTest {

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EmprendedorJpaRepository emprendedorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginUsuarioDesactivadoDebeLanzarDisabledException() {
        UserDetails disabledUser = User.builder()
                .username("sigcholac")
                .password("hashedPassword")
                .disabled(true)
                .authorities("ROLE_EMP")
                .build();

        LoginRequestDto request = new LoginRequestDto();
        request.setUsername("sigcholac");
        request.setPassword("secret");

        when(userDetailsService.loadUserByUsername("sigcholac")).thenReturn(disabledUser);
        when(passwordEncoder.matches("secret", "hashedPassword")).thenReturn(true);

        assertThrows(DisabledException.class, () -> authService.login(request));
        verifyNoInteractions(jwtEncoder);
    }
}
