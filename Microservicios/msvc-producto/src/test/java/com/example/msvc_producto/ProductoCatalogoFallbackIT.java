package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("redis-test")
@TestPropertySource(properties = {
        "spring.data.redis.port=6399",
        "spring.data.redis.connect-timeout=100ms",
        "spring.data.redis.timeout=100ms"
})
class ProductoCatalogoFallbackIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventarioClient inventarioClient;

    @MockitoSpyBean
    private ProductoRepository productoRepository;

    @Test
    @WithAnonymousUser
    void listingRemainsAvailableWhenRedisIsUnavailable() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            HashMap<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 7));
            return quantities;
        });

        var response = mockMvc.perform(get("/api/productos/listado")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"id\":1", "\"inventarioCantidad\":7");
        verify(productoRepository, times(1)).findAllOptimized();
        verify(inventarioClient, times(1)).obtenerCantidadesPorProductos(anyList());
    }
}
