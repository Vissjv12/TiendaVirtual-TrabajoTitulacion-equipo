package com.example.msvc_producto.application.dto;

/**
 * Versioned-by-key projection used only by the local catalog-cache pilot.
 * Inventory is deliberately fetched from its owning service for every request.
 */
public record ProductoCatalogoPublicoDto(
        Long id,
        String nombre,
        String descripcion,
        Double precio,
        String imagen,
        String categoriaNombre,
        String empresaNombre,
        Long empresaId) {

    public static ProductoCatalogoPublicoDto from(ProductoListadoDto producto) {
        return new ProductoCatalogoPublicoDto(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getImagen(),
                producto.getCategoriaNombre(),
                producto.getEmpresaNombre(),
                producto.getEmpresaId());
    }

    public ProductoListadoDto toListadoDto() {
        return new ProductoListadoDto(
                id,
                nombre,
                descripcion,
                precio,
                imagen,
                categoriaNombre,
                empresaNombre,
                empresaId);
    }
}
