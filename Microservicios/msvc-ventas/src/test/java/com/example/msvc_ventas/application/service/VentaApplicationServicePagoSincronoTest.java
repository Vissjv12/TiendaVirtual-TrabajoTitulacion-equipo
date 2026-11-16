package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.application.client.ConfiguracionPagosClient;
import com.example.msvc_ventas.application.client.ProductoClient;
import com.example.msvc_ventas.application.dto.CostoEnvioResponseDto;
import com.example.msvc_ventas.application.dto.EmpresaDto;
import com.example.msvc_ventas.application.dto.ProductoDto;
import com.example.msvc_ventas.application.dto.VentaItemRequestDto;
import com.example.msvc_ventas.application.dto.VentaRequestDto;
import com.example.msvc_ventas.application.mapper.VentaMapper;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.domain.service.VentaService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-05: el pago PENDIENTE se crea en la misma transacción que la venta (antes: hilo con sleep(500)).
 * Si crear el pago falla, la excepción se propaga y la venta hace rollback; ya no quedan ventas sin pago.
 */
class VentaApplicationServicePagoSincronoTest {

    private final VentaService ventaService = mock(VentaService.class);
    private final VentaMapper ventaMapper = mock(VentaMapper.class);
    private final PagoService pagoService = mock(PagoService.class);
    private final ClienteRepository clienteRepository = mock(ClienteRepository.class);
    private final ProductoClient productoClient = mock(ProductoClient.class);
    private final ConfiguracionPagosClient configuracionPagosClient = mock(ConfiguracionPagosClient.class);
    private final VentaApplicationService service = new VentaApplicationService(
            ventaService, ventaMapper, pagoService, clienteRepository, productoClient, configuracionPagosClient);

    @BeforeEach
    void setUp() {
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(Cliente.builder().id(10L).build()));
        ProductoDto producto = ProductoDto.builder().id(1L).precio(new BigDecimal("5.00"))
                .empresa(new EmpresaDto(3L)).build();
        when(productoClient.obtenerProducto(1L)).thenReturn(producto);
        when(configuracionPagosClient.obtenerCostoEnvio(3L))
                .thenReturn(new CostoEnvioResponseDto(3L, new BigDecimal("2.00")));
        when(ventaMapper.toEntity(any(), any(), any())).thenReturn(Venta.builder().build());
        when(ventaService.crearVenta(any())).thenReturn(Venta.builder().id(50L).build());
    }

    @Test
    void elPagoSeCreaAntesDeResponderSinHilos() {
        service.crearVenta(solicitud("deuna"));

        // Si se creara en otro hilo con espera, esta verificación inmediata fallaría
        verify(pagoService).crearPago(eq(50L), eq(PagoEntity.MetodoPago.DEUNA), isNull());
    }

    @Test
    void siFallaCrearElPagoLaVentaNoSeConfirma() {
        when(pagoService.crearPago(anyLong(), any(), any())).thenThrow(new IllegalStateException("fallo BD"));

        assertThrows(IllegalStateException.class, () -> service.crearVenta(solicitud("TRANSFERENCIA")));
    }

    @Test
    void metodoDesconocidoNoCreaPago() {
        service.crearVenta(solicitud("CHEQUE"));
        verify(pagoService, never()).crearPago(anyLong(), any(), any());
    }

    private static VentaRequestDto solicitud(String metodo) {
        return VentaRequestDto.builder()
                .clienteId(10L)
                .items(List.of(VentaItemRequestDto.builder().productoId(1L).cantidad(1).build()))
                .metodoPago(metodo)
                .build();
    }
}
