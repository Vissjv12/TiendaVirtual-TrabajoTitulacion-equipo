package com.example.demo.presentation.controller;

import com.example.demo.application.dto.EmpresaVinculadaResponseDto;
import com.example.demo.application.service.EmprendedorEmpresaService;
import com.example.demo.application.service.VinculacionEmpresaConflictoException;
import com.example.demo.infrastructure.producto.ProductoNoDisponibleException;
import com.example.demo.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T11: autorización y códigos HTTP de PUT/DELETE/GET /api/usuarios/{id}/empresa. */
@WebMvcTest(controllers = EmprendedorEmpresaController.class,
        properties = "jwt.secret=local-test-secret-for-auth-t11-vinculacion-1234")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EmprendedorEmpresaControllerTest {

    private static final String CUERPO = "{\"empresaId\":10}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EmprendedorEmpresaService service;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(put("/api/usuarios/5/empresa").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/usuarios/5/empresa")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void clienteYEmprendedorNoPuedenVincularNiConsultar() throws Exception {
        for (String rol : new String[]{"ROLE_USER", "ROLE_EMP"}) {
            mockMvc.perform(put("/api/usuarios/5/empresa")
                            .with(jwt().authorities(new SimpleGrantedAuthority(rol)).jwt(t -> t.claim("empresaId", 10L)))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/usuarios/5/empresa")
                            .with(jwt().authorities(new SimpleGrantedAuthority(rol))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/usuarios/5/empresa")
                            .with(jwt().authorities(new SimpleGrantedAuthority(rol))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminVinculaYDesvincula() throws Exception {
        when(service.vincular(5L, 10L)).thenReturn(
                new EmpresaVinculadaResponseDto(5L, 10L, "Sigcholac", null, "VINCULADA", true));
        when(service.desvincular(5L)).thenReturn(
                new EmpresaVinculadaResponseDto(5L, null, null, 10L, "DESVINCULADA", true));

        mockMvc.perform(put("/api/usuarios/5/empresa").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaId").value(10))
                .andExpect(jsonPath("$.resultado").value("VINCULADA"))
                .andExpect(jsonPath("$.requiereNuevoLogin").value(true));
        mockMvc.perform(delete("/api/usuarios/5/empresa").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("DESVINCULADA"));
    }

    @Test
    void empresaIdNuloOInvalidoEs400SinLlamarAlServicio() throws Exception {
        for (String cuerpo : new String[]{"{\"empresaId\":null}", "{}", "{\"empresaId\":0}", "{\"empresaId\":-3}"}) {
            mockMvc.perform(put("/api/usuarios/5/empresa").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void erroresDelServicioSeTraducenACodigosHttp() throws Exception {
        when(service.vincular(404L, 10L)).thenThrow(new NoSuchElementException("Usuario no encontrado"));
        when(service.vincular(6L, 10L)).thenThrow(new IllegalArgumentException("no es emprendedor"));
        when(service.vincular(7L, 10L)).thenThrow(new VinculacionEmpresaConflictoException("ya vinculada"));
        when(service.vincular(9L, 10L)).thenThrow(new ProductoNoDisponibleException("caído"));

        esperar(404L, 404);
        esperar(6L, 400);
        esperar(7L, 409);
        esperar(9L, 503);
    }

    private void esperar(long usuarioId, int codigo) throws Exception {
        mockMvc.perform(put("/api/usuarios/" + usuarioId + "/empresa").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().is(codigo));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")).jwt(t -> t.claim("userId", 1L));
    }
}
