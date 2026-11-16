package com.example.demo.presentation.controller;

import com.example.demo.application.dto.DatosBancariosDto;
import com.example.demo.domain.service.ConfiguracionPagosService;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.example.demo.infrastructure.persistence.repository.UsuarioJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hallazgo C-01: contrato HTTP de los endpoints públicos del checkout.
 * 200 con los datos de la empresa pedida; 404 cuando esa empresa no tiene configuración.
 */
@ExtendWith(MockitoExtension.class)
class ConfiguracionPagosControllerEmpresaTest {

    private static final String BASE = "/api/emprendedor/configuracion-pagos";

    @Mock
    private ConfiguracionPagosService configuracionPagosService;

    @Mock
    private UsuarioJpaRepository usuarioRepository;

    @Mock
    private EmprendedorJpaRepository emprendedorRepository;

    @InjectMocks
    private ConfiguracionPagosController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void qrDeEmpresaConfiguradaResponde200ConSuUrl() throws Exception {
        when(configuracionPagosService.obtenerQrDeunaPorEmpresa(10L))
                .thenReturn(Optional.of("/uploads/qr-deuna/qr_emp_100.png"));

        mockMvc.perform(get(BASE + "/deuna-qr/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrUrl").value("/uploads/qr-deuna/qr_emp_100.png"));
    }

    @Test
    void qrDeEmpresaSinConfiguracionResponde404() throws Exception {
        when(configuracionPagosService.obtenerQrDeunaPorEmpresa(30L)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/deuna-qr/30"))
                .andExpect(status().isNotFound());
    }

    @Test
    void datosBancariosDeEmpresaConfiguradaResponden200() throws Exception {
        DatosBancariosDto dto = new DatosBancariosDto();
        dto.setBanco("pichincha");
        dto.setNumeroCuenta("2200000020");
        when(configuracionPagosService.obtenerDatosBancariosPorEmpresa(20L)).thenReturn(Optional.of(dto));

        mockMvc.perform(get(BASE + "/bancarios/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuenta").value("2200000020"));
    }

    @Test
    void datosBancariosDeEmpresaSinConfiguracionResponden404() throws Exception {
        when(configuracionPagosService.obtenerDatosBancariosPorEmpresa(40L)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/bancarios/40"))
                .andExpect(status().isNotFound());
    }
}
