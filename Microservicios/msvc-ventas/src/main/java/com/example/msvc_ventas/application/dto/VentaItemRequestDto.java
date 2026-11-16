package com.example.msvc_ventas.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para representar un item individual en la solicitud de venta
 * Coincide exactamente con lo que envía el frontend
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VentaItemRequestDto {

    @NotNull(message = "El ID del producto es obligatorio")
    private Long productoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;

    // Campos adicionales enviados por el frontend para mejorar performance
    private String nombreProducto;
    
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal precioUnitario;
}
