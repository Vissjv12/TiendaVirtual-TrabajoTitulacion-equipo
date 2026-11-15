package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
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
    private final FacturaService facturaService;

    /**
     * Crear un nuevo pago asociado a una venta
     * - TRANSFERENCIA/DEUNA: Estado PENDIENTE (requiere aprobación manual)
     * - TARJETA: Estado APROBADO automático (procesado por pasarela)
     */
    @Transactional
    public PagoEntity crearPago(Long ventaId, PagoEntity.MetodoPago metodoPago, String comprobanteUrl) {
        log.info("Creando pago para venta ID: {} con método: {}", ventaId, metodoPago);
        
        VentaEntity venta = ventaJpaRepository.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + ventaId));

        // Determinar estado inicial según método de pago
        PagoEntity.EstadoPago estadoInicial = (metodoPago == PagoEntity.MetodoPago.TARJETA) 
                ? PagoEntity.EstadoPago.APROBADO 
                : PagoEntity.EstadoPago.PENDIENTE;

        PagoEntity pago = PagoEntity.builder()
                .venta(venta)
                .monto(venta.getTotal())
                .metodoPago(metodoPago)
                .estadoPago(estadoInicial)
                .comprobanteUrl(comprobanteUrl)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();

        // Si es pago con tarjeta, establecer fecha de pago inmediata
        if (metodoPago == PagoEntity.MetodoPago.TARJETA) {
            pago.setFechaPago(LocalDateTime.now());
        }

        PagoEntity pagoGuardado = pagoRepository.save(pago);
        log.info("Pago creado con ID: {} en estado {}", pagoGuardado.getId(), estadoInicial);
        
        // Si es pago con tarjeta, actualizar venta a COMPLETADA y APROBADO automáticamente
        if (metodoPago == PagoEntity.MetodoPago.TARJETA) {
            venta.setEstado(VentaEntity.EstadoVenta.COMPLETADA);
            venta.setEstadoPago(VentaEntity.EstadoPago.APROBADO);
            venta.setFechaActualizacion(LocalDateTime.now());
            ventaJpaRepository.save(venta);
            
            // Inicializar seguimiento logístico automáticamente
            try {
                seguimientoLogisticaService.inicializarSeguimientoPorPagoAprobado(
                        venta.getId(), 
                        "Pago automático con tarjeta"
                );
                log.info("Seguimiento logístico inicializado automáticamente para venta ID: {}", venta.getId());
            } catch (Exception e) {
                log.error("Error al inicializar seguimiento logístico para venta ID: {}", venta.getId(), e);
            }
        }
        
        return pagoGuardado;
    }

    /**
     * Aprobar un pago (emprendedor)
     */
    @Transactional
    public PagoEntity aprobarPago(Long pagoId, Long emprendedorId) {
        log.info("Aprobando pago ID: {} por emprendedor ID: {}", pagoId, emprendedorId);
        
        PagoEntity pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));

        // Verificar que el pago pertenece al emprendedor
        if (!pago.getVenta().getEmprendedorId().equals(emprendedorId)) {
            throw new IllegalStateException("El pago no pertenece a este emprendedor");
        }

        if (pago.getEstadoPago() != PagoEntity.EstadoPago.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden aprobar pagos en estado PENDIENTE");
        }

        // Descontar inventario si la venta aún estaba PENDIENTE
        VentaEntity venta = pago.getVenta();
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
        
        // 🧾 GENERAR FACTURA PDF DE FORMA ASÍNCRONA (no bloquea la respuesta)
        final Long ventaIdFinal = venta.getId();
        new Thread(() -> {
            try {
                Thread.sleep(100); // Pequeña pausa para asegurar que la transacción se complete
                String rutaFactura = facturaService.generarFacturaPDF(ventaIdFinal);
                log.info("✅ Factura generada en segundo plano: {}", rutaFactura);
            } catch (Exception e) {
                log.error("⚠️ Error al generar factura PDF para venta ID: {}", ventaIdFinal, e);
            }
        }).start();
        
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

    /**
     * Procesar pago con tarjeta (Stripe/pasarela de pagos)
     * Este método es específico para pagos ya procesados por la pasarela
     */
    @Transactional
    public PagoEntity procesarPagoTarjeta(Long ventaId, String referenciaTransaccion, String comprobanteUrl) {
        log.info("Procesando pago con tarjeta para venta ID: {}, referencia: {}", ventaId, referenciaTransaccion);
        
        VentaEntity venta = ventaJpaRepository.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + ventaId));

        // Crear pago directamente APROBADO
        PagoEntity pago = PagoEntity.builder()
                .venta(venta)
                .monto(venta.getTotal())
                .metodoPago(PagoEntity.MetodoPago.TARJETA)
                .estadoPago(PagoEntity.EstadoPago.APROBADO)
                .referenciaTransaccion(referenciaTransaccion)
                .comprobanteUrl(comprobanteUrl)
                .fechaPago(LocalDateTime.now())
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();

        PagoEntity pagoGuardado = pagoRepository.save(pago);
        
        // Actualizar venta a COMPLETADA
        venta.setEstado(VentaEntity.EstadoVenta.COMPLETADA);
        venta.setFechaActualizacion(LocalDateTime.now());
        ventaJpaRepository.save(venta);
        
        // Inicializar seguimiento logístico
        try {
            seguimientoLogisticaService.inicializarSeguimientoPorPagoAprobado(
                    venta.getId(), 
                    "Pago procesado con tarjeta: " + referenciaTransaccion
            );
            log.info("Seguimiento logístico inicializado para venta ID: {}", venta.getId());
        } catch (Exception e) {
            log.error("Error al inicializar seguimiento logístico para venta ID: {}", venta.getId(), e);
        }
        
        // 🧾 GENERAR FACTURA PDF DE FORMA ASÍNCRONA (no bloquea la respuesta)
        final Long ventaIdFinal = venta.getId();
        new Thread(() -> {
            try {
                Thread.sleep(100); // Pequeña pausa para asegurar que la transacción se complete
                String rutaFactura = facturaService.generarFacturaPDF(ventaIdFinal);
                log.info("✅ Factura generada en segundo plano: {}", rutaFactura);
            } catch (Exception e) {
                log.error("⚠️ Error al generar factura PDF para venta ID: {}", ventaIdFinal, e);
            }
        }).start();
        
        log.info("Pago con tarjeta procesado exitosamente. Pago ID: {}, Estado: APROBADO", pagoGuardado.getId());
        return pagoGuardado;
    }
}
