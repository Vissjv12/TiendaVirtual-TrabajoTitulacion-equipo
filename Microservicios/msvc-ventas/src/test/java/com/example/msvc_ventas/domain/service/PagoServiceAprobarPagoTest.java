package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.application.service.GeneradorFacturaTrasCommit;
import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A-04/A-05 en la aprobación manual (transferencia / Deuna):
 * - la venta se bloquea ANTES de leer el pago (dos aprobaciones no descuentan stock dos veces);
 * - una venta cancelada no se reactiva;
 * - la factura se programa tras el commit, no con new Thread + sleep.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoServiceAprobarPagoTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private VentaJpaRepository ventaJpaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private InventarioClient inventarioClient;
    @Mock private SeguimientoLogisticaService seguimientoLogisticaService;
    @Mock private GeneradorFacturaTrasCommit generadorFactura;

    @InjectMocks
    private PagoService pagoService;

    private VentaEntity venta;
    private PagoEntity pago;

    @BeforeEach
    void setUp() {
        venta = VentaEntity.builder().id(1L).emprendedorId(4L).numeroFactura("F-1").total(new BigDecimal("10.00"))
                .estado(VentaEntity.EstadoVenta.PENDIENTE).estadoPago(VentaEntity.EstadoPago.PENDIENTE).build();
        pago = PagoEntity.builder().id(30L).venta(venta).metodoPago(PagoEntity.MetodoPago.TRANSFERENCIA)
                .estadoPago(PagoEntity.EstadoPago.PENDIENTE).build();
        when(pagoRepository.findVentaIdByPagoId(30L)).thenReturn(Optional.of(1L));
        when(ventaJpaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(venta));
        when(pagoRepository.findById(30L)).thenReturn(Optional.of(pago));
        when(pagoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DetalleVenta detalle = new DetalleVenta();
        detalle.setProductoId(5L);
        detalle.setCantidad(2);
        when(detalleVentaRepository.findByVentaId(1L)).thenReturn(List.of(detalle));
    }

    @Test
    void bloqueaLaVentaAntesDeLeerElPagoYProgramaLaFacturaTrasCommit() {
        pagoService.aprobarPago(30L, 4L);

        InOrder orden = inOrder(ventaJpaRepository, pagoRepository, inventarioClient, generadorFactura);
        orden.verify(ventaJpaRepository).findByIdForUpdate(1L);
        orden.verify(pagoRepository).findById(30L);
        orden.verify(inventarioClient).procesarSalidaLote(any());
        orden.verify(generadorFactura).programar(1L);
        assertEquals(VentaEntity.EstadoVenta.COMPLETADA, venta.getEstado());
        assertEquals(PagoEntity.EstadoPago.APROBADO, pago.getEstadoPago());
    }

    @Test
    void segundaAprobacionNoDescuentaStockOtraVez() {
        pago.setEstadoPago(PagoEntity.EstadoPago.APROBADO);
        venta.setEstado(VentaEntity.EstadoVenta.COMPLETADA);

        assertThrows(IllegalStateException.class, () -> pagoService.aprobarPago(30L, 4L));
        verifyNoInteractions(inventarioClient, generadorFactura);
    }

    @Test
    void ventaCanceladaNoSeReactiva() {
        venta.setEstado(VentaEntity.EstadoVenta.CANCELADA);

        assertThrows(IllegalStateException.class, () -> pagoService.aprobarPago(30L, 4L));
        verifyNoInteractions(inventarioClient, generadorFactura);
        verify(ventaJpaRepository, never()).save(any());
        assertEquals(PagoEntity.EstadoPago.PENDIENTE, pago.getEstadoPago());
    }

    @Test
    void otroEmprendedorNoPuedeAprobar() {
        assertThrows(IllegalStateException.class, () -> pagoService.aprobarPago(30L, 99L));
        verifyNoInteractions(inventarioClient, generadorFactura);
    }

    @Test
    void siFallaElInventarioNoSeProgramaFactura() {
        when(inventarioClient.procesarSalidaLote(any())).thenThrow(
                feign.FeignException.errorStatus("procesarSalidaLote",
                        feign.Response.builder().status(409).reason("sin stock")
                                .request(feign.Request.create(feign.Request.HttpMethod.POST, "/x",
                                        java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null))
                                .build()));

        assertThrows(RuntimeException.class, () -> pagoService.aprobarPago(30L, 4L));
        verifyNoInteractions(generadorFactura);
        assertEquals(PagoEntity.EstadoPago.PENDIENTE, pago.getEstadoPago());
    }
}
