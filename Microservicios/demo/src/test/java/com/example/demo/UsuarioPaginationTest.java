package com.example.demo;

import com.example.demo.application.dto.UsuarioResponseDto;
import com.example.demo.application.mapper.UsuarioMapper;
import com.example.demo.application.service.AuthService;
import com.example.demo.application.service.PasswordResetService;
import com.example.demo.application.util.PaginationUtils;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.security.SecurityConfig;
import com.example.demo.presentation.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = "jwt.secret=local-test-secret-for-auth-pagination-123456"
)
@Import(SecurityConfig.class)
class UsuarioPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UsuarioMapper usuarioMapper;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private com.example.demo.application.service.EmprendedorEmpresaService emprendedorEmpresaService;

    @Test
    void usuariosPaginadoRequiereAdminYRechazaNoAutorizados() throws Exception {
        mockMvc.perform(get("/api/usuarios/paginado"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/usuarios/paginado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuariosPaginadoAdminAplicaFiltrosLimitesYContratoA19() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setUsername("pedro");

        UsuarioResponseDto dto = new UsuarioResponseDto();
        dto.setId(3L);
        dto.setUsername("pedro");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(usuarioService.obtenerUsuariosPaginado(eq("pedro"), eq("ROLE_EMP"), eq(true), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(usuario), inv.getArgument(3), 1));
        when(usuarioMapper.toResponseDto(usuario)).thenReturn(dto);

        mockMvc.perform(get("/api/usuarios/paginado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("page", "-1")
                        .param("size", "999")
                        .param("sort", "password,asc")
                        .param("q", "pedro")
                        .param("rol", "ROLE_EMP")
                        .param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].username").value("pedro"))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.empty").value(false));

        verify(usuarioService).obtenerUsuariosPaginado(eq("pedro"), eq("ROLE_EMP"), eq(true), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertEquals(0, captured.getPageNumber());
        assertEquals(PaginationUtils.MAX_PAGE_SIZE, captured.getPageSize());
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("id").getDirection());
    }
}
