package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.application.dto.RegistrarPagoPasarelaRequestDto;
import com.example.msvc_ventas.application.dto.RegistrarPagoPasarelaResponseDto;
import com.example.msvc_ventas.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_ventas.application.dto.VentaResumenPagoDto;
import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.domain.service.SeguimientoLogisticaService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Finalización interna, autorizada e IDEMPOTENTE de ventas pagadas por pasarela (A-03 / A-04).
 *
 * Solo la invoca auth-service con una credencial de servicio, después de verificar el cobro
 * con la pasarela. Garantías:
 *  - El monto cobrado debe coincidir con el total calculado por el backend (A-03).
 *  - La venta se lee con bloqueo de fila: llamadas concurrentes (retorno del navegador y
 *    webhook) se serializan y el stock se descuenta una sola vez (A-04).
 *  - Repetir la misma transacción no repite efectos (YA_PROCESADA).
 *  - Si el inventario falla, el pago queda APROBADO y la venta PENDIENTE (STOCK_PENDIENTE);
 *    una nueva llamada con la misma transacción reintenta solo el descuento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagoPasarelaService {

    private final VentaJpaRepository ventaJpaRepository;
    private final PagoRepository pagoRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final InventarioClient inventarioClient;
    private final SeguimientoLogisticaService seguimientoLogisticaService;
    private final GeneradorFacturaTrasCommit generadorFactura;

    @Transactional(readOnly = true)
    public VentaResumenPagoDto obtenerResumen(Long ventaId) {
        VentaEntity venta = ventaJpaRepository.findById(ventaId)
                .orElseThrow(() -> new NoSuchElementException("Venta no encontrada: " + ventaId));
        return VentaResumenPagoDto.builder()
                .ventaId(venta.getId())
                .numeroFactura(venta.getNumeroFactura())
                .empresaId(venta.getEmprendedorId())
                .clienteEmail(venta.getCliente() != null ? venta.getCliente().getEmail() : null)
                .subtotal(venta.getSubtotal())
                .impuesto(venta.getImpuesto())
                .total(venta.getTotal())
                .estado(nombre(venta.getEstado()))
                .estadoPago(nombre(venta.getEstadoPago()))
                .metodoPago(nombre(venta.getMetodoPago()))
                .referenciaTransaccion(venta.getReferenciaTransaccion())
                .build();
    }

    @Transactional
    public RegistrarPagoPasarelaResponseDto registrarPagoAprobado(Long ventaId, RegistrarPagoPasarelaRequestDto solicitud) {
        String referencia = solicitud.getReferenciaTransaccion().trim();

        VentaEntity venta = ventaJpaRepository.findByIdForUpdate(ventaId)
                .orElseThrow(() -> new NoSuchElementException("Venta no encontrada: " + ventaId));

        // 1. Idempotencia y conflictos según el estado actual
        boolean mismaTransaccion = Objects.equals(referencia, venta.getReferenciaTransaccion());
        if (venta.getEstado() == VentaEntity.EstadoVenta.COMPLETADA) {
            if (mismaTransaccion) {
                return respuesta(venta, RegistrarPagoPasarelaResponseDto.YA_PROCESADA);
            }
            log.error("Pago duplicado: venta {} ya completada con otra transacción; la nueva requiere reembolso", ventaId);
            throw new IllegalStateException("La venta ya fue pagada con otra transacción");
        }
        if (venta.getEstado() == VentaEntity.EstadoVenta.CANCELADA) {
            log.error("Pago recibido para la venta cancelada {}; requiere revisión y reembolso", ventaId);
            throw new IllegalStateException("La venta está cancelada; el pago requiere revisión manual");
        }
        if (venta.getEstadoPago() == VentaEntity.EstadoPago.APROBADO && !mismaTransaccion) {
            log.error("Venta {} ya tiene un pago aprobado con otra transacción", ventaId);
            throw new IllegalStateException("La venta ya tiene un pago aprobado con otra transacción");
        }

        // 2. Monto autoritativo: lo cobrado debe ser exactamente el total del backend (A-03)
        long totalCentavos = aCentavos(venta.getTotal());
        if (solicitud.getMontoCentavos() == null || solicitud.getMontoCentavos() != totalCentavos) {
            log.error("Monto cobrado no coincide con la venta {} (esperado {} centavos)", ventaId, totalCentavos);
            throw new IllegalStateException("El monto cobrado no coincide con el total de la venta");
        }

        // 3. Registrar el pago aprobado (una sola fila de pago por venta)
        LocalDateTime ahora = LocalDateTime.now();
        PagoEntity pago = pagoRepository.findByVentaId(ventaId).orElseGet(() -> PagoEntity.builder()
                .venta(venta)
                .metodoPago(PagoEntity.MetodoPago.TARJETA)
                .fechaCreacion(ahora)
                .build());
        pago.setMonto(venta.getTotal());
        pago.setEstadoPago(PagoEntity.EstadoPago.APROBADO);
        pago.setReferenciaTransaccion(referencia);
        if (pago.getFechaPago() == null) {
            pago.setFechaPago(ahora);
        }
        pago.setFechaActualizacion(ahora);
        pagoRepository.save(pago);

        venta.setEstadoPago(VentaEntity.EstadoPago.APROBADO);
        venta.setReferenciaTransaccion(referencia);
        if (venta.getFechaPago() == null) {
            venta.setFechaPago(ahora);
        }
        venta.setFechaActualizacion(ahora);

        // 4. Descontar stock una sola vez (protegido por el bloqueo de fila)
        try {
            descontarStock(venta);
        } catch (RuntimeException ex) {
            // El cobro ya ocurrió: se conserva el pago aprobado y la venta queda PENDIENTE
            log.error("Pago aprobado para venta {} pero no se pudo descontar inventario: {}",
                    ventaId, ex.getClass().getSimpleName());
            ventaJpaRepository.save(venta);
            return respuesta(venta, RegistrarPagoPasarelaResponseDto.STOCK_PENDIENTE);
        }

        venta.setEstado(VentaEntity.EstadoVenta.COMPLETADA);
        ventaJpaRepository.save(venta);

        try {
            seguimientoLogisticaService.inicializarSeguimientoPorPagoAprobado(
                    venta.getId(), "Pago " + solicitud.getPasarela() + " verificado");
        } catch (Exception e) {
            log.error("Error al inicializar seguimiento logístico para venta {}", ventaId, e);
        }
        generarFacturaTrasCommit(venta.getId());

        log.info("Venta {} completada por pago de pasarela {}", ventaId, solicitud.getPasarela());
        return respuesta(venta, RegistrarPagoPasarelaResponseDto.COMPLETADA);
    }

    private void descontarStock(VentaEntity venta) {
        List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(venta.getId());
        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalStateException("La venta no tiene detalles");
        }
        List<SalidaInventarioLoteRequestDto.SalidaInventarioItemDto> items = detalles.stream()
                .map(detalle -> {
                    SalidaInventarioLoteRequestDto.SalidaInventarioItemDto item =
                            new SalidaInventarioLoteRequestDto.SalidaInventarioItemDto();
                    item.setProductoId(detalle.getProductoId());
                    item.setCantidad(detalle.getCantidad());
                    return item;
                })
                .toList();
        SalidaInventarioLoteRequestDto salida = new SalidaInventarioLoteRequestDto();
        salida.setItems(items);
        salida.setMotivo("Venta #" + venta.getNumeroFactura());
        // La credencial de servicio de la petición entrante se reenvía a inventario (FeignConfig)
        inventarioClient.procesarSalidaLote(salida);
    }

    /** La factura se genera después del commit, sin hilos con sleep (A-05). */
    private void generarFacturaTrasCommit(Long ventaId) {
        generadorFactura.programar(ventaId);
    }

    static long aCentavos(BigDecimal valor) {
        return valor == null ? 0L : valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static String nombre(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    private RegistrarPagoPasarelaResponseDto respuesta(VentaEntity venta, String resultado) {
        return RegistrarPagoPasarelaResponseDto.builder()
                .ventaId(venta.getId())
                .numeroFactura(venta.getNumeroFactura())
                .estadoVenta(nombre(venta.getEstado()))
                .estadoPago(nombre(venta.getEstadoPago()))
                .resultado(resultado)
                .build();
    }
}
