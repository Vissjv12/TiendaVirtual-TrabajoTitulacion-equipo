package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoService {

    private final PagoRepository pagoRepository;
    private final VentaJpaRepository ventaJpaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final InventarioClient inventarioClient;
    private final SeguimientoLogisticaService seguimientoLogisticaService;
    private final com.example.msvc_ventas.application.service.GeneradorFacturaTrasCommit generadorFactura;

    /**
     * Crear un nuevo pago asociado a una venta
     * - TRANSFERENCIA/DEUNA: Estado PENDIENTE (requiere aprobación manual)
     * - TARJETA: también PENDIENTE; solo PagoPasarelaService lo aprueba tras verificar el cobro
     */
    @Transactional
    public PagoEntity crearPago(Long ventaId, PagoEntity.MetodoPago metodoPago, String comprobanteUrl) {
        log.info("Creando pago para venta ID: {} con método: {}", ventaId, metodoPago);
        
        VentaEntity venta = ventaJpaRepository.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + ventaId));

        // Todo pago nace PENDIENTE. Antes TARJETA se aprobaba y completaba la venta al crearla,
        // sin cobro verificado ni descuento de stock (A-03/A-04). Ahora un pago con tarjeta solo
        // se aprueba cuando la pasarela lo confirma en el servidor (PagoPasarelaService).
        if (pagoRepository.findByVentaId(ventaId).isPresent()) {
            log.info("La venta {} ya tiene un pago registrado; no se crea otro", ventaId);
            return pagoRepository.findByVentaId(ventaId).get();
        }
        PagoEntity.EstadoPago estadoInicial = PagoEntity.EstadoPago.PENDIENTE;

        PagoEntity pago = PagoEntity.builder()
                .venta(venta)
                .monto(venta.getTotal())
                .metodoPago(metodoPago)
                .estadoPago(estadoInicial)
                .comprobanteUrl(comprobanteUrl)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();

        PagoEntity pagoGuardado = pagoRepository.save(pago);
        log.info("Pago creado con ID: {} en estado {}", pagoGuardado.getId(), estadoInicial);
        return pagoGuardado;
    }

    /**
     * Aprobar un pago (emprendedor)
     */
    @Transactional
    public PagoEntity aprobarPago(Long pagoId, Long emprendedorId) {
        log.info("Aprobando pago ID: {} por emprendedor ID: {}", pagoId, emprendedorId);
        
        // Bloqueo de la fila de la venta ANTES de leer el pago: dos aprobaciones simultáneas (doble clic,
        // dos pestañas) o una aprobación junto a completarVenta/la pasarela ya no descuentan stock dos veces.
        Long ventaId = pagoRepository.findVentaIdByPagoId(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));
        VentaEntity venta = ventaJpaRepository.findByIdForUpdate(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + ventaId));
        PagoEntity pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));

        // Verificar que el pago pertenece al emprendedor
        if (!venta.getEmprendedorId().equals(emprendedorId)) {
            throw new IllegalStateException("El pago no pertenece a este emprendedor");
        }

        if (pago.getEstadoPago() != PagoEntity.EstadoPago.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden aprobar pagos en estado PENDIENTE");
        }

        // Una venta cancelada no se reactiva aprobando su pago (antes quedaba COMPLETADA sin descontar stock)
        if (venta.getEstado() == VentaEntity.EstadoVenta.CANCELADA) {
            throw new IllegalStateException("No se puede aprobar el pago de una venta cancelada");
        }

        // Descontar inventario si la venta aún estaba PENDIENTE
        if (venta.getEstado() == VentaEntity.EstadoVenta.PENDIENTE) {
            List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(venta.getId());
            if (detalles != null && !detalles.isEmpty()) {
                try {
                    List<SalidaInventarioLoteRequestDto.SalidaInventarioItemDto> items = detalles.stream()
                            .map(detalle -> {
                                SalidaInventarioLoteRequestDto.SalidaInventarioItemDto item =
                                        new SalidaInventarioLoteRequestDto.SalidaInventarioItemDto();
                                item.setProductoId(detalle.getProductoId());
                                item.setCantidad(detalle.getCantidad());
                                return item;
                            })
                            .collect(Collectors.toList());

                    SalidaInventarioLoteRequestDto salidaDto = new SalidaInventarioLoteRequestDto();
                    salidaDto.setItems(items);
                    salidaDto.setMotivo("Venta #" + venta.getNumeroFactura());

                    inventarioClient.procesarSalidaLote(salidaDto);
                } catch (FeignException e) {
                    throw new RuntimeException("Error al actualizar el inventario: " + e.getMessage(), e);
                }
            }
        }

        pago.setEstadoPago(PagoEntity.EstadoPago.APROBADO);
        pago.setFechaPago(LocalDateTime.now());
        pago.setFechaActualizacion(LocalDateTime.now());

        PagoEntity pagoActualizado = pagoRepository.save(pago);

        // Actualizar estado de la venta
        venta.setEstado(VentaEntity.EstadoVenta.COMPLETADA);
        venta.setEstadoPago(VentaEntity.EstadoPago.APROBADO);
        venta.setFechaActualizacion(LocalDateTime.now());
        ventaJpaRepository.save(venta);
        
        // 🚀 INICIALIZAR SEGUIMIENTO LOGÍSTICO
        try {
            seguimientoLogisticaService.inicializarSeguimientoPorPagoAprobado(
                    venta.getId(), 
                    "Emprendedor ID: " + emprendedorId
            );
            log.info("Seguimiento logístico inicializado para venta ID: {}", venta.getId());
        } catch (Exception e) {
            log.error("Error al inicializar seguimiento logístico para venta ID: {}", venta.getId(), e);
            // No fallar la transacción si falla el seguimiento
        }
        
        // 🧾 Factura después del commit (A-05): si la aprobación hace rollback no se genera
        generadorFactura.programar(venta.getId());

        log.info("Pago ID: {} aprobado exitosamente", pagoId);
        return pagoActualizado;
    }

    /**
     * Rechazar un pago (emprendedor)
     */
    @Transactional
    public PagoEntity rechazarPago(Long pagoId, Long emprendedorId) {
        log.info("Rechazando pago ID: {} por emprendedor ID: {}", pagoId, emprendedorId);
        
        PagoEntity pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));

        // Verificar que el pago pertenece al emprendedor
        if (!pago.getVenta().getEmprendedorId().equals(emprendedorId)) {
            throw new IllegalStateException("El pago no pertenece a este emprendedor");
        }

        if (pago.getEstadoPago() != PagoEntity.EstadoPago.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden rechazar pagos en estado PENDIENTE");
        }

        pago.setEstadoPago(PagoEntity.EstadoPago.RECHAZADO);
        pago.setFechaActualizacion(LocalDateTime.now());

        PagoEntity pagoActualizado = pagoRepository.save(pago);
        
        // Actualizar estado de la venta
        VentaEntity venta = pago.getVenta();
        venta.setEstado(VentaEntity.EstadoVenta.CANCELADA);
        venta.setEstadoPago(VentaEntity.EstadoPago.RECHAZADO);
        venta.setFechaActualizacion(LocalDateTime.now());
        ventaJpaRepository.save(venta);
        
        log.info("Pago ID: {} rechazado", pagoId);
        return pagoActualizado;
    }

    /**
     * Subir comprobante de transferencia
     */
    @Transactional
    public PagoEntity subirComprobante(Long pagoId, String comprobanteUrl) {
        log.info("Subiendo comprobante para pago ID: {}", pagoId);
        
        PagoEntity pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));

        if (pago.getMetodoPago() != PagoEntity.MetodoPago.TRANSFERENCIA) {
            throw new IllegalStateException("Solo se puede subir comprobante para pagos por transferencia");
        }

        pago.setComprobanteUrl(comprobanteUrl);
        pago.setFechaActualizacion(LocalDateTime.now());

        return pagoRepository.save(pago);
    }

    /**
     * Obtener pago por ID
     */
    public PagoEntity obtenerPagoPorId(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + id));
    }

    /**
     * Obtener pago por venta ID
     */
    public PagoEntity obtenerPagoPorVentaId(Long ventaId) {
        return pagoRepository.findByVentaId(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe pago para la venta: " + ventaId));
    }

    /**
     * Listar pagos pendientes de un emprendedor
     */
    public List<PagoEntity> listarPagosPendientesPorEmprendedor(Long emprendedorId) {
        return pagoRepository.findByEmprendedorIdAndEstadoPago(emprendedorId, PagoEntity.EstadoPago.PENDIENTE);
    }

    /**
     * Listar todos los pagos de un emprendedor
     */
    public List<PagoEntity> listarPagosPorEmprendedor(Long emprendedorId) {
        return pagoRepository.findByEmprendedorId(emprendedorId);
    }

    /**
     * Listar todos los pagos por estado
     */
    public List<PagoEntity> listarPagosPorEstado(PagoEntity.EstadoPago estado) {
        return pagoRepository.findByEstadoPago(estado);
    }

    @Transactional(readOnly = true)
    public Page<PagoEntity> listarPagosPendientesPorEmprendedorPaginado(Long emprendedorId, Pageable pageable) {
        return pagoRepository.findByEmprendedorIdAndEstadoPago(
                emprendedorId, PagoEntity.EstadoPago.PENDIENTE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PagoEntity> listarPagosPorEmprendedorPaginado(Long emprendedorId, Pageable pageable) {
        return pagoRepository.findByEmprendedorId(emprendedorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PagoEntity> listarPagosPorEstadoPaginado(PagoEntity.EstadoPago estado, Pageable pageable) {
        return pagoRepository.findByEstadoPago(estado, pageable);
    }

    // procesarPagoTarjeta (usado solo por Stripe) se eliminó: aprobaba el pago y completaba la venta sin
    // descontar inventario y generaba la factura en un hilo manual con espera. Todo pago con tarjeta se aprueba
    // únicamente con PagoPasarelaService.registrarPagoAprobado (monto verificado, stock e idempotencia).
}
