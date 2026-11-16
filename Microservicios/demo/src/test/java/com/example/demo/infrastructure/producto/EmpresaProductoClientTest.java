package com.example.demo.infrastructure.producto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** T11: la empresa se valida contra msvc-producto; cualquier fallo deniega (fail-closed). */
class EmpresaProductoClientTest {

    private static final String BASE = "http://producto.test";

    private MockRestServiceServer producto;
    private EmpresaProductoClient cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        producto = MockRestServiceServer.bindTo(builder).build();
        cliente = new EmpresaProductoClient(builder, BASE);
    }

    @Test
    void leeIdNombreYEstadoIgnorandoElResto() {
        producto.expect(requestTo(BASE + "/api/empresas/10")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":10,\"nombre\":\"Sigcholac\",\"ruc\":\"1790000000001\",\"activo\":true}",
                        MediaType.APPLICATION_JSON));

        var empresa = cliente.obtenerEmpresa(10L);

        assertEquals(10L, empresa.id());
        assertEquals("Sigcholac", empresa.nombre());
        assertTrue(empresa.estaActiva());
        producto.verify();
    }

    @Test
    void activoNuloSeTrataComoInactiva() {
        producto.expect(requestTo(BASE + "/api/empresas/11"))
                .andRespond(withSuccess("{\"id\":11,\"nombre\":\"X\"}", MediaType.APPLICATION_JSON));
        assertFalse(cliente.obtenerEmpresa(11L).estaActiva());
    }

    @Test
    void empresaInexistenteEsNoSuchElement() {
        producto.expect(requestTo(BASE + "/api/empresas/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(NoSuchElementException.class, () -> cliente.obtenerEmpresa(99L));
    }

    @Test
    void errorDelServidorOCaidaEsServicioNoDisponible() {
        producto.expect(requestTo(BASE + "/api/empresas/10")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(ProductoNoDisponibleException.class, () -> cliente.obtenerEmpresa(10L));

        producto.reset();
        producto.expect(requestTo(BASE + "/api/empresas/10")).andRespond(withException(new java.net.ConnectException("refused")));
        assertThrows(ProductoNoDisponibleException.class, () -> cliente.obtenerEmpresa(10L));
    }
}
