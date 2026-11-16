package com.example.msvc_inventario.domain.repository;

import com.example.msvc_inventario.domain.model.MovimientoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MovimientoInventarioRepository {
    MovimientoInventario save(MovimientoInventario movimiento);
    Optional<MovimientoInventario> findById(Long id);
    List<MovimientoInventario> findByInventarioId(Long inventarioId);
    Page<MovimientoInventario> findByInventarioId(Long inventarioId, Pageable pageable);
    List<MovimientoInventario> findAll();
    Page<MovimientoInventario> findPaginado(
            Long inventarioId,
            List<Long> inventarioIds,
            MovimientoInventario.TipoMovimiento tipo,
            String motivo,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Pageable pageable);
}