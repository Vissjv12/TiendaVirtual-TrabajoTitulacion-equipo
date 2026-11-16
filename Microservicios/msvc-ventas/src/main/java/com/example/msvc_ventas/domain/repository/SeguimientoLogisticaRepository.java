package com.example.msvc_ventas.domain.repository;

import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SeguimientoLogisticaRepository {

    SeguimientoLogistica save(SeguimientoLogistica seguimiento);

    Optional<SeguimientoLogistica> findById(Long id);

    List<SeguimientoLogistica> findByVentaId(Long ventaId);

    Optional<SeguimientoLogistica> findUltimoEstadoByVentaId(Long ventaId);

    List<SeguimientoLogistica> findByEstadoLogistica(SeguimientoLogistica.EstadoLogistica estadoLogistica);

    List<SeguimientoLogistica> findByEmprendedorId(Long emprendedorId);

    List<SeguimientoLogistica> findByEmprendedorIdAndEstado(Long emprendedorId, SeguimientoLogistica.EstadoLogistica estado);

    Page<SeguimientoLogistica> findPaginadoPorEmprendedor(Long emprendedorId, SeguimientoLogistica.EstadoLogistica estado, Pageable pageable);

    boolean existsByVentaId(Long ventaId);

    long countByVentaId(Long ventaId);

    void deleteById(Long id);
}
