package com.example.msvc_ventas.domain.repository;

import com.example.msvc_ventas.domain.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface VentaRepository {
    Venta save(Venta venta);
    Optional<Venta> findById(Long id);
    Optional<Venta> findByNumeroFactura(String numeroFactura);
    List<Venta> findByClienteId(Long clienteId);
    List<Venta> findByEmprendedorId(Long emprendedorId);
    List<Venta> findAll();
    Page<Venta> findPaginado(String q, Long emprendedorId, Long clienteId, Venta.EstadoVenta estado, Pageable pageable);
}