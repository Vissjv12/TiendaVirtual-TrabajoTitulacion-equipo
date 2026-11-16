package com.example.demo.domain.service;

import com.example.demo.application.dto.ConfiguracionPagosDto;
import com.example.demo.application.dto.PayphoneDto;
import com.example.demo.domain.model.ConfiguracionMetodosPago;
import com.example.demo.domain.repository.ConfiguracionMetodosPagoRepository;
import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hallazgo C-02: el token de Payphone es de solo escritura. Se guarda y lo usa el backend,
 * pero ninguna respuesta (ni la del propio emprendedor) ni toString() lo contiene.
 */
@ExtendWith(MockitoExtension.class)
class PayphoneTokenNoExpuestoTest {

    private static final String TOKEN = "tok-secreto-NO-DEBE-SALIR-123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ConfiguracionMetodosPagoRepository configuracionRepository;

    @Mock
    private EmprendedorJpaRepository emprendedorRepository;

    @InjectMocks
    private ConfiguracionPagosService service;

    @Test
    void configuracionDelEmprendedorNoDevuelveElTokenSoloSiEstaConfigurado() throws Exception {
        when(configuracionRepository.findByEmprendedorId(100L)).thenReturn(Optional.of(configConToken()));

        ConfiguracionPagosDto dto = service.obtenerConfiguracion(100L).orElseThrow();
        String json = objectMapper.writeValueAsString(dto);

        assertFalse(json.contains(TOKEN), json);
        assertFalse(json.contains("payphoneToken"), json);
        assertTrue(dto.getPayphone().isTokenConfigurado());
        assertEquals("app-id-publico", dto.getPayphone().getPayphoneAppId());
    }

    @Test
    void guardarDevuelveRespuestaSinToken() throws Exception {
        when(configuracionRepository.findByEmprendedorId(100L)).thenReturn(Optional.empty());
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionPagosDto dto = service.guardarPayphone(100L, new PayphoneDto("app-id-publico", TOKEN));
        String json = objectMapper.writeValueAsString(dto);

        assertFalse(json.contains(TOKEN), json);
        assertTrue(dto.getPayphone().isTokenConfigurado());
    }

    @Test
    void tokenVacioConservaElTokenGuardado() {
        ConfiguracionMetodosPago existente = configConToken();
        when(configuracionRepository.findByEmprendedorId(100L)).thenReturn(Optional.of(existente));
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.guardarPayphone(100L, new PayphoneDto("nuevo-app-id", "   "));

        ArgumentCaptor<ConfiguracionMetodosPago> guardado = ArgumentCaptor.forClass(ConfiguracionMetodosPago.class);
        verify(configuracionRepository).save(guardado.capture());
        assertEquals(TOKEN, guardado.getValue().getPayphoneToken());
        assertEquals("nuevo-app-id", guardado.getValue().getPayphoneAppId());
    }

    @Test
    void primeraConfiguracionSinTokenEsRechazada() {
        when(configuracionRepository.findByEmprendedorId(100L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.guardarPayphone(100L, new PayphoneDto("app-id", null)));
        verify(configuracionRepository, never()).save(any());
    }

    @Test
    void payphoneDtoAceptaElTokenDeEntradaPeroNuncaLoSerializa() throws Exception {
        PayphoneDto entrada = objectMapper.readValue(
                "{\"payphoneAppId\":\"app\",\"payphoneToken\":\"" + TOKEN + "\"}", PayphoneDto.class);
        assertEquals(TOKEN, entrada.getPayphoneToken());

        String salida = objectMapper.writeValueAsString(entrada);
        assertFalse(salida.contains(TOKEN), salida);
        assertFalse(salida.contains("payphoneToken"), salida);
        assertFalse(entrada.toString().contains(TOKEN), "toString() no debe incluir el token");
    }

    @Test
    void entidadNoIncluyeElTokenEnToString() {
        assertFalse(configConToken().toString().contains(TOKEN));
    }

    @Test
    void disponibilidadPublicaPorEmpresaNoRequiereExponerElToken() {
        EmprendedorEntity emprendedor = new EmprendedorEntity();
        emprendedor.setId(100L);
        emprendedor.setEmpresaId(10L);
        when(emprendedorRepository.findByEmpresaId(10L)).thenReturn(Optional.of(emprendedor));
        when(configuracionRepository.findByEmprendedorId(100L)).thenReturn(Optional.of(configConToken()));
        when(emprendedorRepository.findByEmpresaId(20L)).thenReturn(Optional.empty());

        assertTrue(service.payphoneDisponiblePorEmpresa(10L));
        assertFalse(service.payphoneDisponiblePorEmpresa(20L));
        assertFalse(service.payphoneDisponiblePorEmpresa(null));
    }

    private ConfiguracionMetodosPago configConToken() {
        ConfiguracionMetodosPago config = new ConfiguracionMetodosPago();
        config.setId(1L);
        config.setEmprendedorId(100L);
        config.setPayphoneAppId("app-id-publico");
        config.setPayphoneToken(TOKEN);
        return config;
    }
}
