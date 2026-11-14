package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.service.ProductoCatalogoInvalidacionService;
import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.repository.CategoriaRepository;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import com.example.msvc_producto.domain.service.ProductoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("redis-test")
class ProductoCatalogoHttpIT {

    private static final String GENERATION_KEY = "feriadigital:v1:{catalogo}:epoch";
    private static final String CACHE_KEY_PREFIX = "feriadigital:v1:{catalogo}:productos:listado";
    private static final String CATEGORIAS_LIST_CACHE_KEY_PREFIX = "feriadigital:v1:{catalogo}:categorias:listado";
    private static final String CATEGORIAS_DETAIL_CACHE_KEY_PREFIX = "feriadigital:v1:{catalogo}:categorias:detalle:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private ProductoCatalogoInvalidacionService invalidacionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private InventarioClient inventarioClient;

    @MockitoSpyBean
    private ProductoRepository productoRepository;

    @MockitoSpyBean
    private CategoriaRepository categoriaRepository;

    @BeforeEach
    void clearOnlyTheSyntheticCatalogNamespace() {
        clearCatalogRedisNamespace();
        clearInvocations(productoRepository, categoriaRepository, inventarioClient);
    }

    @AfterEach
    void removeSyntheticCatalogKeys() {
        clearCatalogRedisNamespace();
    }

    private void clearCatalogRedisNamespace() {
        redisTemplate.delete(GENERATION_KEY);
        Set<String> keys = redisTemplate.keys("feriadigital:v1:{catalogo}:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @WithAnonymousUser
    void repeatedPublicListingUsesRedisButRefreshesInventoryOnEveryRequest() throws Exception {
        AtomicInteger inventoryCalls = new AtomicInteger();
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            int revision = inventoryCalls.incrementAndGet();
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 10 + revision));
            return quantities;
        });

        JsonNode first = readListing();
        String firstGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
        String firstCacheKey = CACHE_KEY_PREFIX + ":g" + firstGeneration;
        Long ttlBeforeHit = redisTemplate.getExpire(firstCacheKey, TimeUnit.MILLISECONDS);
        JsonNode second = readListing();
        Long ttlAfterHit = redisTemplate.getExpire(firstCacheKey, TimeUnit.MILLISECONDS);

        assertThat(first.isArray()).isTrue();
        assertThat(first.size()).isGreaterThan(0);
        assertThat(second.size()).isEqualTo(first.size());
        assertThat(inventoryCalls).hasValue(2);
        assertThat(ttlAfterHit).isLessThanOrEqualTo(ttlBeforeHit);
        verify(productoRepository, times(1)).findAllOptimized();
        verify(inventarioClient, times(2)).obtenerCantidadesPorProductos(anyList());

        JsonNode firstProduct = first.get(0);
        JsonNode secondProduct = second.get(0);
        assertThat(firstProduct.path("id").asLong()).isEqualTo(secondProduct.path("id").asLong());
        assertThat(firstProduct.path("nombre").asText()).isEqualTo(secondProduct.path("nombre").asText());
        assertThat(firstProduct.path("precio").asDouble()).isEqualTo(secondProduct.path("precio").asDouble());
        assertThat(firstProduct.path("empresaId").asLong()).isEqualTo(secondProduct.path("empresaId").asLong());
        assertThat(secondProduct.path("inventarioCantidad").asInt())
                .isEqualTo(firstProduct.path("inventarioCantidad").asInt() + 1);

        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        assertThat(generation).isNotBlank();
        String cachedJson = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ":g" + generation);
        assertThat(cachedJson).isNotBlank();
        assertThat(cachedJson).doesNotContain("inventarioCantidad", "emprendedorId", "ruc", "telefono");
        assertThat(objectMapper.readTree(cachedJson).get(0).has("empresaId")).isTrue();
    }

    @Test
    @WithAnonymousUser
    void corruptCachedJsonFallsBackToMySqlAndMovesToANewGeneration() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 15));
            return quantities;
        });

        JsonNode first = readListing();
        String oldGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + ":g" + oldGeneration, "{not-json");

        JsonNode fallback = readListing();

        assertThat(fallback.size()).isEqualTo(first.size());
        assertThat(findById(fallback, 1L).path("nombre").asText())
                .isEqualTo(findById(first, 1L).path("nombre").asText());
        assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isNotEqualTo(oldGeneration);
        verify(productoRepository, times(2)).findAllOptimized();
    }

    @Test
    @WithAnonymousUser
    void inventoryFailureDoesNotPublishAnIncompleteCatalogValue() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList()))
                .thenThrow(new IllegalStateException("synthetic inventory unavailable"));

        var response = mockMvc.perform(get("/api/productos/listado")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(500);
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        assertThat(generation).isNotBlank();
        assertThat(redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ":g" + generation)).isNull();
        verify(productoRepository, times(1)).findAllOptimized();
    }

    @Test
    @WithAnonymousUser
    void repeatedCategoryListUsesRedisAndKeepsTheHttpContract() throws Exception {
        JsonNode first = readJson("/api/categorias");
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        String cacheKey = CATEGORIAS_LIST_CACHE_KEY_PREFIX + ":g" + generation;
        JsonNode second = readJson("/api/categorias");

        assertThat(first.isArray()).isTrue();
        assertThat(first.size()).isGreaterThan(0);
        assertThat(second).isEqualTo(first);
        assertThat(redisTemplate.opsForValue().get(cacheKey)).isNotBlank();
        verify(categoriaRepository, times(1)).findAll();
    }

    @Test
    @WithAnonymousUser
    void repeatedCategoryDetailUsesRedisAndKeepsTheHttpContract() throws Exception {
        JsonNode first = readJson("/api/categorias/1");
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        String cacheKey = CATEGORIAS_DETAIL_CACHE_KEY_PREFIX + "1:g" + generation;
        JsonNode second = readJson("/api/categorias/1");

        assertThat(first.path("id").asLong()).isEqualTo(1L);
        assertThat(second).isEqualTo(first);
        assertThat(redisTemplate.opsForValue().get(cacheKey)).isNotBlank();
        verify(categoriaRepository, times(1)).findById(1L);
    }

    @Test
    @WithAnonymousUser
    void corruptCategoryJsonRotatesGenerationAndFallsBackToMySql() throws Exception {
        JsonNode first = readJson("/api/categorias");
        String oldGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
        redisTemplate.opsForValue().set(CATEGORIAS_LIST_CACHE_KEY_PREFIX + ":g" + oldGeneration, "{not-json");

        JsonNode fallback = readJson("/api/categorias");

        assertThat(fallback).isEqualTo(first);
        assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isNotEqualTo(oldGeneration);
        verify(categoriaRepository, times(2)).findAll();
    }

    @Test
    @WithAnonymousUser
    void committedCategoryRenameInvalidatesItsCachedReadRoutes() throws Exception {
        Categoria original = categoriaService.obtenerCategoriaPorId(1L);
        String originalName = original.getNombre();
        String updatedName = originalName + " - redis-it";
        readJson("/api/categorias");
        readJson("/api/categorias/1");
        String oldGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
        String oldDetailKey = CATEGORIAS_DETAIL_CACHE_KEY_PREFIX + "1:g" + oldGeneration;
        assertThat(redisTemplate.opsForValue().get(oldDetailKey)).isNotBlank();

        try {
            categoriaService.actualizarCategoria(1L, copyWithName(original, updatedName));

            String newGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
            assertThat(newGeneration).isNotEqualTo(oldGeneration);
            assertThat(readJson("/api/categorias/1").path("nombre").asText()).isEqualTo(updatedName);
            assertThat(redisTemplate.opsForValue().get(
                    CATEGORIAS_DETAIL_CACHE_KEY_PREFIX + "1:g" + newGeneration)).isNotBlank();
        } finally {
            categoriaService.actualizarCategoria(1L, copyWithName(original, originalName));
        }
    }

    @Test
    @WithAnonymousUser
    void committedProductUpdateInvalidatesTheCachedListing() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 20));
            return quantities;
        });

        Producto original = productoService.obtenerProductoPorId(1L);
        String originalName = original.getNombre();
        String updatedName = originalName + " - redis-it";
        readListing();
        String oldGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);

        try {
            productoService.actualizarProducto(1L, copyWithName(original, updatedName));

            String newGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
            assertThat(newGeneration).isNotEqualTo(oldGeneration);

            JsonNode updatedListing = readListing();
            JsonNode updatedProduct = findById(updatedListing, 1L);
            assertThat(updatedProduct.path("nombre").asText()).isEqualTo(updatedName);
        } finally {
            productoService.actualizarProducto(1L, copyWithName(original, originalName));
        }
    }

    @Test
    @WithAnonymousUser
    void committedCategoryRenameInvalidatesTheCachedListing() throws Exception {
        Producto product = productoService.obtenerProductoPorId(1L);
        Long categoryId = product.getCategoria().getId();
        Categoria original = categoriaService.obtenerCategoriaPorId(categoryId);
        String originalName = original.getNombre();
        String updatedName = originalName + " - redis-it";
        readListing();
        String oldGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);

        try {
            categoriaService.actualizarCategoria(categoryId, copyWithName(original, updatedName));

            assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isNotEqualTo(oldGeneration);
            assertThat(findById(readListing(), 1L).path("categoriaNombre").asText()).isEqualTo(updatedName);
        } finally {
            categoriaService.actualizarCategoria(categoryId, copyWithName(original, originalName));
        }
    }

    @Test
    @WithAnonymousUser
    void committedCompanyRenameInvalidatesTheCachedListing() throws Exception {
        Producto product = productoService.obtenerProductoPorId(1L);
        Long companyId = product.getEmpresa().getId();
        Empresa original = empresaService.obtenerEmpresaPorId(companyId);
        String originalName = original.getNombre();
        String updatedName = originalName + " - redis-it";
        readListing();
        String oldGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);

        try {
            empresaService.actualizarEmpresa(companyId, copyWithName(original, updatedName));

            assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isNotEqualTo(oldGeneration);
            assertThat(findById(readListing(), 1L).path("empresaNombre").asText()).isEqualTo(updatedName);
        } finally {
            empresaService.actualizarEmpresa(companyId, copyWithName(original, originalName));
        }
    }

    @Test
    @WithAnonymousUser
    void otherCatalogReadsKeepTheirExistingSourcePathAndDoNotTouchRedis() throws Exception {
        List<String> nonCachedReads = List.of(
                "/api/productos",
                "/api/productos/1",
                "/api/productos/empresa/1",
                "/api/productos/categoria/1",
                "/api/empresas",
                "/api/empresas/1");

        for (String path : nonCachedReads) {
            var response = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();
            assertThat(response.getStatus()).as("GET %s", path).isEqualTo(200);
            assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).as("Redis generation after GET %s", path)
                    .isNull();
        }

        Set<String> cacheKeys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        assertThat(cacheKeys).isNullOrEmpty();
    }

    @Test
    @WithAnonymousUser
    void rolledBackCatalogMutationDoesNotAdvanceTheCacheGeneration() throws Exception {
        readListing();
        String generationBeforeRollback = redisTemplate.opsForValue().get(GENERATION_KEY);

        new TransactionTemplate(transactionManager).execute(status -> {
            invalidacionService.invalidarDespuesDeCommit();
            status.setRollbackOnly();
            return null;
        });

        assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isEqualTo(generationBeforeRollback);
    }

    private JsonNode readListing() throws Exception {
        return readJson("/api/productos/listado");
    }

    private JsonNode readJson(String path) throws Exception {
        var response = mockMvc.perform(get(path)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString());
    }

    private JsonNode findById(JsonNode products, long id) {
        for (JsonNode product : products) {
            if (product.path("id").asLong() == id) {
                return product;
            }
        }
        throw new AssertionError("Product ID not found in synthetic listing: " + id);
    }

    private Producto copyWithName(Producto original, String name) {
        Producto copy = new Producto();
        copy.setNombre(name);
        copy.setDescripcion(original.getDescripcion());
        copy.setPrecio(original.getPrecio());
        copy.setImagen(original.getImagen());
        copy.setActivo(original.getActivo());
        copy.setEmpresa(original.getEmpresa());
        copy.setCategoria(original.getCategoria());
        copy.setEmprendedorId(original.getEmprendedorId());
        return copy;
    }

    private Categoria copyWithName(Categoria original, String name) {
        Categoria copy = new Categoria();
        copy.setNombre(name);
        copy.setDescripcion(original.getDescripcion());
        copy.setActivo(original.getActivo());
        return copy;
    }

    private Empresa copyWithName(Empresa original, String name) {
        Empresa copy = new Empresa();
        copy.setNombre(name);
        copy.setRuc(original.getRuc());
        copy.setDireccion(original.getDireccion());
        copy.setTelefono(original.getTelefono());
        copy.setEmail(original.getEmail());
        copy.setActivo(original.getActivo());
        return copy;
    }
}
