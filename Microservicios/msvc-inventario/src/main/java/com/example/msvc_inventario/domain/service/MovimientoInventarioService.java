package com.example.msvc_inventario.domain.service;

import com.example.msvc_inventario.domain.model.MovimientoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoInventarioService {
    MovimientoInventario registrarMovimiento(MovimientoInventario movimiento);
    MovimientoInventario obtenerPorId(Long id);
    List<MovimientoInventario> listarPorInventarioId(Long inventarioId);
    Page<MovimientoInventario> listarPorInventarioIdPaginado(Long inventarioId, Pageable pageable);
    List<MovimientoInventario> listarTodos();
    Page<MovimientoInventario> listarPaginado(
            Long inventarioId,
            List<Long> inventarioIds,
            MovimientoInventario.TipoMovimiento tipo,
            String motivo,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Pageable pageable);
}