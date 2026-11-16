package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A-03/A-04: crear una venta con TARJETA ya no aprueba el pago ni completa la venta.
 * Antes el comprador obtenía una venta COMPLETADA sin cobro verificado ni descuento de stock.
 */
@ExtendWith(MockitoExtension.class)
class PagoServiceCrearPagoTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private VentaJpaRepository ventaJpaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private InventarioClient inventarioClient;
    @Mock private SeguimientoLogisticaService seguimientoLogisticaService;
    @Mock private com.example.msvc_ventas.application.service.GeneradorFacturaTrasCommit generadorFactura;

    @InjectMocks
    private PagoService pagoService;

    @Test
    void pagoConTarjetaNacePendienteYNoCompletaLaVenta() {
        VentaEntity venta = VentaEntity.builder().id(1L).total(new BigDecimal("12.30"))
                .estado(VentaEntity.EstadoVenta.PENDIENTE).estadoPago(VentaEntity.EstadoPago.PENDIENTE).build();
        when(pagoRepository.findByVentaId(1L)).thenReturn(Optional.empty());
        when(ventaJpaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(pagoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PagoEntity pago = pagoService.crearPago(1L, PagoEntity.MetodoPago.TARJETA, null);

        assertEquals(PagoEntity.EstadoPago.PENDIENTE, pago.getEstadoPago());
        assertNull(pago.getFechaPago());
        assertEquals(VentaEntity.EstadoVenta.PENDIENTE, venta.getEstado());
        assertEquals(VentaEntity.EstadoPago.PENDIENTE, venta.getEstadoPago());
        verify(ventaJpaRepository, never()).save(any());
        verifyNoInteractions(seguimientoLogisticaService, inventarioClient);
    }

    @Test
    void siYaExistePagoNoSeCreaOtro() {
        PagoEntity existente = PagoEntity.builder().id(9L).estadoPago(PagoEntity.EstadoPago.APROBADO).build();
        when(pagoRepository.findByVentaId(1L)).thenReturn(Optional.of(existente));
        when(ventaJpaRepository.findById(1L)).thenReturn(Optional.of(VentaEntity.builder().id(1L).build()));

        assertSame(existente, pagoService.crearPago(1L, PagoEntity.MetodoPago.TARJETA, null));
        verify(pagoRepository, never()).save(any());
    }
}
