package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.domain.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;

    @Override
    @Transactional
    public Cliente crearCliente(Cliente cliente) {
        cliente.setFechaCreacion(LocalDateTime.now());
        cliente.setFechaActualizacion(LocalDateTime.now());
        cliente.setActivo(true);
        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public Cliente actualizarCliente(Long id, Cliente cliente) {
        Cliente clienteExistente = obtenerClientePorId(id);

        clienteExistente.setNombre(cliente.getNombre());
        clienteExistente.setApellido(cliente.getApellido());
        clienteExistente.setEmail(cliente.getEmail());
        clienteExistente.setTelefono(cliente.getTelefono());
        clienteExistente.setDocumento(cliente.getDocumento());
        clienteExistente.setFechaActualizacion(LocalDateTime.now());

        return clienteRepository.save(clienteExistente);
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente obtenerClientePorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cliente no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente obtenerClientePorEmail(String email) {
        return clienteRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Cliente no encontrado con email: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Cliente> listarClientesPaginado(Pageable pageable) {
        return clienteRepository.findPaginado(pageable);
    }

    @Override
    @Transactional
    public void eliminarCliente(Long id) {
        Cliente cliente = obtenerClientePorId(id);
        cliente.setActivo(false);
        cliente.setFechaActualizacion(LocalDateTime.now());
        clienteRepository.save(cliente);
    }
}