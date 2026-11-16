package com.example.demo.domain.service;

import com.example.demo.application.dto.DatosBancariosDto;
import com.example.demo.domain.model.ConfiguracionMetodosPago;
import com.example.demo.domain.repository.ConfiguracionMetodosPagoRepository;
import com.example.demo.infrastructure.config.StaticResourceConfiguration;
import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hallazgo C-01: el QR de Deuna y los datos bancarios del checkout deben resolverse
 * únicamente por empresaId, sin fallback global ni reinterpretación del id como emprendedorId.
 *
 * Escenario:
 *  - Empresa 10 -> emprendedor 100, con QR y cuenta bancaria.
 *  - Empresa 20 -> emprendedor 200, con QR y cuenta bancaria distintos.
 *  - Empresa 30 -> emprendedor 300, con configuración pero sin QR ni cuenta.
 *  - Empresa 40 -> emprendedor 400, sin fila de configuración.
 *  - Empresa 99 no existe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfiguracionPagosServiceEmpresaTest {

    @Mock
    private ConfiguracionMetodosPagoRepository configuracionRepository;

    @Mock
    private EmprendedorJpaRepository emprendedorRepository;

    @InjectMocks
    private ConfiguracionPagosService service;

    private final List<Path> archivosCreados = new ArrayList<>();
    private String qrEmpresa10;
    private String qrEmpresa20;

    @BeforeEach
    void setUp() throws IOException {
        qrEmpresa10 = crearArchivoQr();
        qrEmpresa20 = crearArchivoQr();

        // Primero el stub genérico (empresa inexistente) y luego los específicos, que lo sobrescriben
        when(emprendedorRepository.findByEmpresaId(anyLong())).thenReturn(Optional.empty());
        registrarEmprendedor(10L, 100L);
        registrarEmprendedor(20L, 200L);
        registrarEmprendedor(30L, 300L);
        registrarEmprendedor(40L, 400L);

        when(configuracionRepository.findByEmprendedorId(anyLong())).thenReturn(Optional.empty());
        when(configuracionRepository.findByEmprendedorId(100L))
                .thenReturn(Optional.of(config(100L, qrEmpresa10, "pichincha", "2200000010")));
        when(configuracionRepository.findByEmprendedorId(200L))
                .thenReturn(Optional.of(config(200L, qrEmpresa20, "guayaquil", "2200000020")));
        when(configuracionRepository.findByEmprendedorId(300L))
                .thenReturn(Optional.of(config(300L, null, null, null)));
    }

    @AfterEach
    void limpiarArchivos() throws IOException {
        for (Path archivo : archivosCreados) {
            Files.deleteIfExists(archivo);
        }
    }

    // ---------------------------------------------------------------- QR Deuna

    @Test
    void qrDeCadaEmpresaEsElPropioYNoSeMezclan() {
        assertEquals(Optional.of(qrEmpresa10), service.obtenerQrDeunaPorEmpresa(10L));
        assertEquals(Optional.of(qrEmpresa20), service.obtenerQrDeunaPorEmpresa(20L));
    }

    @Test
    void empresaConConfiguracionSinQrDevuelveVacioYNoUsaElQrDeOtra() {
        assertTrue(service.obtenerQrDeunaPorEmpresa(30L).isEmpty());
        verify(configuracionRepository, never()).findAll();
    }

    @Test
    void empresaSinFilaDeConfiguracionDevuelveVacio() {
        assertTrue(service.obtenerQrDeunaPorEmpresa(40L).isEmpty());
        verify(configuracionRepository, never()).findAll();
    }

    @Test
    void empresaInexistenteDevuelveVacio() {
        assertTrue(service.obtenerQrDeunaPorEmpresa(99L).isEmpty());
        verify(configuracionRepository, never()).findAll();
    }

    @Test
    void idQueSoloCoincideConUnEmprendedorIdNoSeReinterpreta() {
        // 100 es el emprendedorId de la empresa 10, pero no es un empresaId válido
        assertTrue(service.obtenerQrDeunaPorEmpresa(100L).isEmpty());
        verify(configuracionRepository, never()).findByEmprendedorId(100L);
    }

    @Test
    void qrRegistradoPeroSinArchivoEnDiscoDevuelveVacio() throws IOException {
        Files.deleteIfExists(rutaDe(qrEmpresa10));
        assertTrue(service.obtenerQrDeunaPorEmpresa(10L).isEmpty());
    }

    @Test
    void idNuloOInvalidoDevuelveVacio() {
        assertTrue(service.obtenerQrDeunaPorEmpresa(null).isEmpty());
        assertTrue(service.obtenerQrDeunaPorEmpresa(0L).isEmpty());
        assertTrue(service.obtenerQrDeunaPorEmpresa(-1L).isEmpty());
    }

    // ------------------------------------------------------- Datos bancarios

    @Test
    void datosBancariosDeCadaEmpresaSonLosPropios() {
        DatosBancariosDto empresa10 = service.obtenerDatosBancariosPorEmpresa(10L).orElseThrow();
        DatosBancariosDto empresa20 = service.obtenerDatosBancariosPorEmpresa(20L).orElseThrow();

        assertEquals("2200000010", empresa10.getNumeroCuenta());
        assertEquals("pichincha", empresa10.getBanco());
        assertEquals("2200000020", empresa20.getNumeroCuenta());
        assertEquals("guayaquil", empresa20.getBanco());
    }

    @Test
    void datosBancariosSinConfigurarDevuelvenVacio() {
        assertTrue(service.obtenerDatosBancariosPorEmpresa(30L).isEmpty());
        assertTrue(service.obtenerDatosBancariosPorEmpresa(40L).isEmpty());
        assertTrue(service.obtenerDatosBancariosPorEmpresa(99L).isEmpty());
    }

    @Test
    void datosBancariosNoReinterpretanElIdComoEmprendedorId() {
        assertTrue(service.obtenerDatosBancariosPorEmpresa(200L).isEmpty());
        verify(configuracionRepository, never()).findByEmprendedorId(200L);
    }

    // ---------------------------------------------------------------- helpers

    private void registrarEmprendedor(Long empresaId, Long emprendedorId) {
        EmprendedorEntity emprendedor = new EmprendedorEntity();
        emprendedor.setId(emprendedorId);
        emprendedor.setEmpresaId(empresaId);
        when(emprendedorRepository.findByEmpresaId(empresaId)).thenReturn(Optional.of(emprendedor));
    }

    private ConfiguracionMetodosPago config(Long emprendedorId, String qrUrl, String banco, String numeroCuenta) {
        ConfiguracionMetodosPago config = new ConfiguracionMetodosPago();
        config.setEmprendedorId(emprendedorId);
        config.setQrDeunaUrl(qrUrl);
        config.setBanco(banco);
        config.setNumeroCuenta(numeroCuenta);
        config.setTipoCuenta(banco == null ? null : "ahorros");
        config.setTitular(banco == null ? null : "Titular " + emprendedorId);
        config.setCedulaRuc(banco == null ? null : "1700000000");
        return config;
    }

    private String crearArchivoQr() throws IOException {
        String nombre = "qr_test_" + UUID.randomUUID() + ".png";
        Path archivo = StaticResourceConfiguration.resolveQrUploadDirectory().resolve(nombre);
        Files.write(archivo, new byte[]{1, 2, 3});
        archivosCreados.add(archivo);
        return "/uploads/qr-deuna/" + nombre;
    }

    private Path rutaDe(String qrUrl) {
        return StaticResourceConfiguration.resolveQrUploadDirectory()
                .resolve(qrUrl.substring("/uploads/qr-deuna/".length()));
    }
}
