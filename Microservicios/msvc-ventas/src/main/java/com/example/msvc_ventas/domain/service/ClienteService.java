package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.domain.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClienteService {
    Cliente crearCliente(Cliente cliente);
    Cliente actualizarCliente(Long id, Cliente cliente);
    Cliente obtenerClientePorId(Long id);
    Cliente obtenerClientePorEmail(String email);
    List<Cliente> listarClientes();
    Page<Cliente> listarClientesPaginado(Pageable pageable);
    void eliminarCliente(Long id);
}