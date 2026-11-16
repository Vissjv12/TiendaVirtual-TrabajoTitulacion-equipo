package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
import com.example.msvc_ventas.domain.repository.SeguimientoLogisticaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeguimientoLogisticaService {

    private final SeguimientoLogisticaRepository seguimientoRepository;

    /**
     * Crea un nuevo registro de seguimiento logístico
     */
    @Transactional
    public SeguimientoLogistica crearSeguimiento(Long ventaId, 
                                                 SeguimientoLogistica.EstadoLogistica estado,
                                                 String descripcion,
                                                 String ubicacion,
                                                 String responsable,
                                                 String observaciones) {
        log.info("Creando seguimiento para venta {} con estado {}", ventaId, estado);

        // Si no se proporciona descripción, usar la por defecto del estado
        String descripcionFinal = (descripcion != null && !descripcion.isBlank()) 
                ? descripcion 
                : estado.getDescripcionDefault();

        SeguimientoLogistica seguimiento = SeguimientoLogistica.builder()
                .ventaId(ventaId)
                .estadoLogistica(estado)
                .descripcion(descripcionFinal)
                .ubicacion(ubicacion)
                .responsable(responsable)
                .observaciones(observaciones)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();

        return seguimientoRepository.save(seguimiento);
    }

    /**
     * Actualiza el estado logístico de un pedido
     */
    @Transactional
    public SeguimientoLogistica actualizarEstado(Long ventaId,
                                                 SeguimientoLogistica.EstadoLogistica nuevoEstado,
                                                 String descripcion,
                                                 String ubicacion,
                                                 String responsable,
                                                 String observaciones) {
        log.info("Actualizando seguimiento de venta {} a estado {}", ventaId, nuevoEstado);

        // Validar que la venta exista en seguimiento
        if (!seguimientoRepository.existsByVentaId(ventaId)) {
            throw new IllegalStateException("No existe seguimiento para la venta con ID: " + ventaId);
        }

        // Crear nuevo registro de seguimiento (historial completo)
        return crearSeguimiento(ventaId, nuevoEstado, descripcion, ubicacion, responsable, observaciones);
    }

    /**
     * Obtiene todo el historial de seguimiento de una venta
     */
    @Transactional(readOnly = true)
    public List<SeguimientoLogistica> obtenerHistorialPorVenta(Long ventaId) {
        log.info("Obteniendo historial de seguimiento para venta {}", ventaId);
        return seguimientoRepository.findByVentaId(ventaId);
    }

    /**
     * Obtiene el último estado de seguimiento de una venta
     */
    @Transactional(readOnly = true)
    public SeguimientoLogistica obtenerUltimoEstado(Long ventaId) {
        log.info("Obteniendo último estado de seguimiento para venta {}", ventaId);
        return seguimientoRepository.findUltimoEstadoByVentaId(ventaId)
                .orElse(null);
    }

    /**
     * Obtiene todos los seguimientos de un emprendedor
     */
    @Transactional(readOnly = true)
    public List<SeguimientoLogistica> obtenerSeguimientosPorEmprendedor(Long emprendedorId) {
        log.info("Obteniendo seguimientos del emprendedor {}", emprendedorId);
        return seguimientoRepository.findByEmprendedorId(emprendedorId);
    }

    /**
     * Obtiene seguimientos de un emprendedor en un estado específico
     */
    @Transactional(readOnly = true)
    public List<SeguimientoLogistica> obtenerSeguimientosPorEmprendedorYEstado(
            Long emprendedorId, 
            SeguimientoLogistica.EstadoLogistica estado) {
        log.info("Obteniendo seguimientos del emprendedor {} en estado {}", emprendedorId, estado);
        return seguimientoRepository.findByEmprendedorIdAndEstado(emprendedorId, estado);
    }

    @Transactional(readOnly = true)
    public Page<SeguimientoLogistica> obtenerSeguimientosPorEmprendedorPaginado(
            Long emprendedorId,
            SeguimientoLogistica.EstadoLogistica estado,
            Pageable pageable) {
        return seguimientoRepository.findPaginadoPorEmprendedor(emprendedorId, estado, pageable);
    }

    /**
     * Verifica si una venta tiene seguimiento
     */
    @Transactional(readOnly = true)
    public boolean tieneSeguimiento(Long ventaId) {
        return seguimientoRepository.existsByVentaId(ventaId);
    }

    /**
     * Inicializa el seguimiento cuando un pago es aprobado
     */
    @Transactional
    public SeguimientoLogistica inicializarSeguimientoPorPagoAprobado(Long ventaId, String responsable) {
        log.info("Inicializando seguimiento para venta {} por pago aprobado", ventaId);
        
        // Solo crear si no existe ya un seguimiento
        if (seguimientoRepository.existsByVentaId(ventaId)) {
            log.warn("Ya existe seguimiento para la venta {}", ventaId);
            return obtenerUltimoEstado(ventaId);
        }

        return crearSeguimiento(
                ventaId,
                SeguimientoLogistica.EstadoLogistica.PAGO_APROBADO,
                null, // Usa descripción por defecto
                null,
                responsable,
                "Seguimiento iniciado automáticamente al aprobar el pago"
        );
    }

    /**
     * Marca un pedido como cancelado
     */
    @Transactional
    public SeguimientoLogistica cancelarPedido(Long ventaId, String motivoCancelacion, String responsable) {
        log.info("Cancelando pedido de venta {}", ventaId);
        
        return crearSeguimiento(
                ventaId,
                SeguimientoLogistica.EstadoLogistica.CANCELADO,
                motivoCancelacion,
                null,
                responsable,
                "Pedido cancelado"
        );
    }
}
