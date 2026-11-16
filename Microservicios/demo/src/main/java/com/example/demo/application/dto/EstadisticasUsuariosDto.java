package com.example.demo.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticasUsuariosDto {
    private long totalUsuarios;
    private long usuariosActivos;
    private long usuariosInactivos;
    private long administradores;
    private long administradoresActivos;
    private long emprendedores;
    private long clientes;
}