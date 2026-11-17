package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.service.FacturaService;
import com.example.msvc_ventas.infrastructure.persistence.entity.ClienteEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.DetalleVentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.ClienteJpaRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.DetalleVentaJpaRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresión de generación asíncrona de factura PDF fuera de petición HTTP (sin open-in-view).
 */
@SpringBootTest
@ActiveProfiles("test")
class FacturaServiceTransaccionalTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void facturaProps(DynamicPropertyRegistry registry) {
        registry.add("factura.directorio", () -> tempDir.toAbsolutePath().toString());
    }

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private ClienteJpaRepository clienteRepository;

    @Autowired
    private VentaJpaRepository ventaRepository;

    @Autowired
    private DetalleVentaJpaRepository detalleVentaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @AfterEach
    void limpiar() {
        detalleVentaRepository.deleteAll();
        pagoRepository.deleteAll();
        ventaRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    @Test
    void generaYObtieneFacturaPdfDesdeHiloAsincronoSinLazyInitializationException() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();

        ClienteEntity cliente = new ClienteEntity();
        cliente.setNombre("Juan");
        cliente.setApellido("Pérez");
        cliente.setEmail("factura-async@test.com");
        cliente.setDocumento("1710034065");
        cliente.setTelefono("0999999999");
        cliente.setActivo(true);
        cliente.setFechaCreacion(ahora);
        cliente.setFechaActualizacion(ahora);
        cliente = clienteRepository.save(cliente);

        VentaEntity venta = VentaEntity.builder()
                .numeroFactura("FACT-ASYNC-TEST-1")
                .cliente(cliente)
                .subtotal(new BigDecimal("2.40"))
                .impuesto(new BigDecimal("0.36"))
                .total(new BigDecimal("7.76"))
                .estado(VentaEntity.EstadoVenta.COMPLETADA)
                .metodoPago(VentaEntity.MetodoPago.TARJETA)
                .estadoPago(VentaEntity.EstadoPago.APROBADO)
                .emprendedorId(2L)
                .fechaVenta(ahora)
                .fechaCreacion(ahora)
                .fechaActualizacion(ahora)
                .build();
        Long ventaId = ventaRepository.save(venta).getId();

        DetalleVentaEntity detalle = DetalleVentaEntity.builder()
                .ventaId(ventaId)
                .productoId(14L)
                .nombreProducto("Queso fresco | 500g")
                .cantidad(1)
                .precioUnitario(new BigDecimal("2.40"))
                .subtotal(new BigDecimal("2.40"))
                .emprendedorId(2L)
                .build();
        detalleVentaRepository.save(detalle);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            String rutaGenerada = pool.submit(() -> facturaService.generarFacturaPDF(ventaId))
                    .get(15, TimeUnit.SECONDS);
            Path pdf = Path.of(rutaGenerada);
            assertTrue(Files.exists(pdf), "Debe existir el PDF generado en disco");
            assertTrue(Files.size(pdf) > 0, "El PDF generado no debe estar vacío");

            Files.delete(pdf);
            File pdfBajoDemanda = pool.submit(() -> facturaService.obtenerFacturaPDF(ventaId))
                    .get(15, TimeUnit.SECONDS);
            assertTrue(pdfBajoDemanda.exists() && pdfBajoDemanda.length() > 0,
                    "Debe regenerar y devolver el PDF desde hilo sin sesión HTTP");
        } finally {
            pool.shutdownNow();
        }
    }
}
