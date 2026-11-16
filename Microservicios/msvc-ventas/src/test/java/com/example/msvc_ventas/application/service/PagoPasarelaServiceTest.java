package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.application.dto.RegistrarPagoPasarelaRequestDto;
import com.example.msvc_ventas.application.dto.RegistrarPagoPasarelaResponseDto;
import com.example.msvc_ventas.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.domain.service.SeguimientoLogisticaService;
import com.example.msvc_ventas.infrastructure.persistence.entity.ClienteEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-03 / A-04: registro interno e idempotente de pagos de pasarela.
 * El monto debe coincidir con el total del backend y el stock se descuenta una sola vez.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoPasarelaServiceTest {

    @Mock private VentaJpaRepository ventaJpaRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private InventarioClient inventarioClient;
    @Mock private SeguimientoLogisticaService seguimientoLogisticaService;
    @Mock private GeneradorFacturaTrasCommit generadorFactura;

    @InjectMocks
    private PagoPasarelaService service;

    private VentaEntity venta;

    @BeforeEach
    void setUp() {
        ClienteEntity cliente = new ClienteEntity();
        cliente.setEmail("comprador@correo.test");
        venta = VentaEntity.builder()
                .id(77L)
                .numeroFactura("F-77")
                .cliente(cliente)
                .emprendedorId(10L)
                .subtotal(new BigDecimal("10.00"))
                .impuesto(new BigDecimal("1.50"))
                .total(new BigDecimal("12.30"))
                .estado(VentaEntity.EstadoVenta.PENDIENTE)
                .estadoPago(VentaEntity.EstadoPago.PENDIENTE)
                .metodoPago(VentaEntity.MetodoPago.TARJETA)
                .build();
        when(ventaJpaRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(venta));
        when(ventaJpaRepository.findById(77L)).thenReturn(Optional.of(venta));
        when(pagoRepository.findByVentaId(77L)).thenReturn(Optional.empty());
        when(pagoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detalleVentaRepository.findByVentaId(77L)).thenReturn(List.of(
                DetalleVenta.builder().ventaId(77L).productoId(5L).cantidad(2).build(),
                DetalleVenta.builder().ventaId(77L).productoId(6L).cantidad(1).build()));
    }

    @Test
    void pagoCorrectoCompletaLaVentaYDescuentaStockUnaVez() {
        RegistrarPagoPasarelaResponseDto r = service.registrarPagoAprobado(77L, solicitud("555", 1230L));

        assertEquals(RegistrarPagoPasarelaResponseDto.COMPLETADA, r.getResultado());
        assertEquals(VentaEntity.EstadoVenta.COMPLETADA, venta.getEstado());
        assertEquals(VentaEntity.EstadoPago.APROBADO, venta.getEstadoPago());
        assertEquals("555", venta.getReferenciaTransaccion());

        ArgumentCaptor<SalidaInventarioLoteRequestDto> salida = ArgumentCaptor.forClass(SalidaInventarioLoteRequestDto.class);
        verify(inventarioClient, times(1)).procesarSalidaLote(salida.capture());
        assertEquals(2, salida.getValue().getItems().size());

        ArgumentCaptor<PagoEntity> pago = ArgumentCaptor.forClass(PagoEntity.class);
        verify(pagoRepository).save(pago.capture());
        assertEquals(PagoEntity.EstadoPago.APROBADO, pago.getValue().getEstadoPago());
        assertEquals("555", pago.getValue().getReferenciaTransaccion());
    }

    @Test
    void repetirLaMismaTransaccionEsIdempotente() {
        service.registrarPagoAprobado(77L, solicitud("555", 1230L));
        RegistrarPagoPasarelaResponseDto segunda = service.registrarPagoAprobado(77L, solicitud("555", 1230L));

        assertEquals(RegistrarPagoPasarelaResponseDto.YA_PROCESADA, segunda.getResultado());
        verify(inventarioClient, times(1)).procesarSalidaLote(any());
    }

    @Test
    void otraTransaccionSobreVentaPagadaSeRechaza() {
        service.registrarPagoAprobado(77L, solicitud("555", 1230L));

        assertThrows(IllegalStateException.class, () -> service.registrarPagoAprobado(77L, solicitud("999", 1230L)));
        verify(inventarioClient, times(1)).procesarSalidaLote(any());
    }

    @Test
    void montoDistintoAlTotalNoRegistraNiDescuenta() {
        assertThrows(IllegalStateException.class, () -> service.registrarPagoAprobado(77L, solicitud("555", 100L)));

        assertEquals(VentaEntity.EstadoVenta.PENDIENTE, venta.getEstado());
        assertEquals(VentaEntity.EstadoPago.PENDIENTE, venta.getEstadoPago());
        verify(inventarioClient, never()).procesarSalidaLote(any());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void ventaCanceladaNoSeCompleta() {
        venta.setEstado(VentaEntity.EstadoVenta.CANCELADA);

        assertThrows(IllegalStateException.class, () -> service.registrarPagoAprobado(77L, solicitud("555", 1230L)));
        verify(inventarioClient, never()).procesarSalidaLote(any());
    }

    @Test
    void falloDeInventarioDejaPagoAprobadoYVentaPendienteReintentable() {
        doThrow(new RuntimeException("inventario caído")).when(inventarioClient).procesarSalidaLote(any());

        RegistrarPagoPasarelaResponseDto r = service.registrarPagoAprobado(77L, solicitud("555", 1230L));

        assertEquals(RegistrarPagoPasarelaResponseDto.STOCK_PENDIENTE, r.getResultado());
        assertEquals(VentaEntity.EstadoVenta.PENDIENTE, venta.getEstado());
        assertEquals(VentaEntity.EstadoPago.APROBADO, venta.getEstadoPago());

        // Reintento con la misma transacción: ahora inventario responde y la venta se completa
        org.mockito.Mockito.reset(inventarioClient);
        RegistrarPagoPasarelaResponseDto reintento = service.registrarPagoAprobado(77L, solicitud("555", 1230L));

        assertEquals(RegistrarPagoPasarelaResponseDto.COMPLETADA, reintento.getResultado());
        assertEquals(VentaEntity.EstadoVenta.COMPLETADA, venta.getEstado());
    }

    @Test
    void reutilizaElPagoPendienteExistenteSinDuplicar() {
        PagoEntity existente = PagoEntity.builder().id(9L).venta(venta)
                .metodoPago(PagoEntity.MetodoPago.TARJETA).estadoPago(PagoEntity.EstadoPago.PENDIENTE).build();
        when(pagoRepository.findByVentaId(77L)).thenReturn(Optional.of(existente));

        service.registrarPagoAprobado(77L, solicitud("555", 1230L));

        assertEquals(PagoEntity.EstadoPago.APROBADO, existente.getEstadoPago());
        verify(pagoRepository).save(existente);
    }

    @Test
    void ventaInexistenteResponde404() {
        when(ventaJpaRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.registrarPagoAprobado(1L, solicitud("1", 1L)));
    }

    @Test
    void resumenExponeLosImportesAutoritativos() {
        var r = service.obtenerResumen(77L);
        assertEquals(new BigDecimal("12.30"), r.getTotal());
        assertEquals(10L, r.getEmpresaId());
        assertEquals("comprador@correo.test", r.getClienteEmail());
        assertEquals("TARJETA", r.getMetodoPago());
    }

    @Test
    void conversionACentavosEsExacta() {
        assertEquals(1230L, PagoPasarelaService.aCentavos(new BigDecimal("12.3")));
        assertEquals(1999L, PagoPasarelaService.aCentavos(new BigDecimal("19.99")));
    }

    private RegistrarPagoPasarelaRequestDto solicitud(String referencia, long centavos) {
        return RegistrarPagoPasarelaRequestDto.builder()
                .referenciaTransaccion(referencia).montoCentavos(centavos)
                .pasarela("PAYPHONE").clientTransactionId("FD-77-abcdef").build();
    }
}
