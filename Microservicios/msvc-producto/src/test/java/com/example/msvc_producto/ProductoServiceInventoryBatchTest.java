package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.application.service.ProductoServiceImpl;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceInventoryBatchTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private EmpresaService empresaService;

    @Mock
    private CategoriaService categoriaService;

    @Mock
    private InventarioClient inventarioClient;

    @Test
    void listadoOptimizadoUsaCantidadesDevueltasPorInventario() {
        ProductoListadoDto primero = producto(1L);
        ProductoListadoDto segundo = producto(2L);
        when(productoRepository.findAllOptimized()).thenReturn(List.of(primero, segundo));
        when(inventarioClient.obtenerCantidadesPorProductos(List.of(1L, 2L)))
                .thenReturn(Map.of("1", 500, "2", 1000));

        ProductoServiceImpl service = new ProductoServiceImpl(
                productoRepository, empresaService, categoriaService, inventarioClient);

        List<ProductoListadoDto> resultado = service.obtenerProductosOptimizado();

        assertEquals(500, resultado.get(0).getInventarioCantidad());
        assertEquals(1000, resultado.get(1).getInventarioCantidad());
    }

    private ProductoListadoDto producto(Long id) {
        return new ProductoListadoDto(id, "Producto " + id, "Descripción", 10.0,
                "imagen", "Categoría", "Empresa", 1L);
    }
}
