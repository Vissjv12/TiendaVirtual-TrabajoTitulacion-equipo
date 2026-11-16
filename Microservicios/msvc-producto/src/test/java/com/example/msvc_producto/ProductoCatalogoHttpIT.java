package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.dto.CategoriaRequestDto;
import com.example.msvc_producto.application.dto.EmpresaRequestDto;
import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.application.service.ProductoCatalogoInvalidacionService;
import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.repository.CategoriaRepository;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import com.example.msvc_producto.domain.service.ProductoService;
import com.example.msvc_producto.infrastructure.cache.RedisProductoCatalogoStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
    private MeterRegistry meterRegistry;

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

    @MockitoSpyBean
    private RedisProductoCatalogoStore redisStore;

    @MockitoSpyBean
    private RedisConnectionFactory redisConnectionFactory;

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
    void productCreateAndDeactivateEachInvalidateTheCachedListing() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 0));
            return quantities;
        });

        readListing();
        String generationBeforeCreate = redisTemplate.opsForValue().get(GENERATION_KEY);
        Producto newProduct = new Producto();
        newProduct.setNombre("Producto Redis A17 R08 " + System.nanoTime());
        newProduct.setDescripcion("Fixture sintético local para invalidación");
        newProduct.setPrecio(java.math.BigDecimal.valueOf(1.25));
        newProduct.setImagen("https://example.invalid/redis-a17-r08.png");
        newProduct.setEmpresa(empresaService.obtenerEmpresaPorId(1L));
        newProduct.setCategoria(categoriaService.obtenerCategoriaPorId(1L));
        newProduct.setEmprendedorId(productoService.obtenerProductoPorId(1L).getEmprendedorId());

        Producto created = productoService.crearProducto(newProduct);
        try {
            String generationAfterCreate = redisTemplate.opsForValue().get(GENERATION_KEY);
            assertThat(generationAfterCreate).isNotEqualTo(generationBeforeCreate);
            assertThat(findById(readListing(), created.getId()).path("nombre").asText())
                    .isEqualTo(created.getNombre());

            String generationBeforeDeactivate = redisTemplate.opsForValue().get(GENERATION_KEY);
            productoService.eliminarProducto(created.getId());
            String generationAfterDeactivate = redisTemplate.opsForValue().get(GENERATION_KEY);

            assertThat(generationAfterDeactivate).isNotEqualTo(generationBeforeDeactivate);
            assertThat(containsProductId(readListing(), created.getId())).isFalse();
            verify(productoRepository, times(3)).findAllOptimized();
        } finally {
            productoRepository.deleteById(created.getId());
        }
    }

    @Test
    @WithAnonymousUser
    void catalogFromTwoCompaniesIsSharedAcrossPublicAndEntrepreneurRolesWithoutPrivateFields() throws Exception {
        doReturn(List.of(
                new ProductoListadoDto(91001L, "Sintético empresa A", "Público", 1.0,
                        "a.png", "Categoría pública", "Empresa A", 910L),
                new ProductoListadoDto(92002L, "Sintético empresa B", "Público", 2.0,
                        "b.png", "Categoría pública", "Empresa B", 920L)))
                .when(productoRepository).findAllOptimized();
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 3));
            return quantities;
        });

        JsonNode anonymousResponse = readListing();
        var entrepreneurResponse = mockMvc.perform(get("/api/productos/listado")
                        .with(asEntrepreneur())
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        JsonNode authenticatedResponse = objectMapper.readTree(entrepreneurResponse.getContentAsString());
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        String cachedJson = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ":g" + generation);

        assertThat(entrepreneurResponse.getStatus()).isEqualTo(200);
        assertThat(anonymousResponse).hasSize(2);
        assertThat(authenticatedResponse).hasSize(2);
        assertThat(anonymousResponse.get(0).path("empresaId").asLong()).isEqualTo(910L);
        assertThat(anonymousResponse.get(1).path("empresaId").asLong()).isEqualTo(920L);
        assertThat(authenticatedResponse).isEqualTo(anonymousResponse);
        assertThat(cachedJson).doesNotContain("inventarioCantidad", "emprendedorId", "ruc", "telefono");
        verify(productoRepository, times(1)).findAllOptimized();
        verify(inventarioClient, times(2)).obtenerCantidadesPorProductos(anyList());
    }

    @Test
    @WithAnonymousUser
    void categoryDeactivationInvalidatesItsCachedListAndDetail() throws Exception {
        Categoria original = categoriaService.obtenerCategoriaPorId(1L);
        readJson("/api/categorias");
        readJson("/api/categorias/1");
        String generationBefore = redisTemplate.opsForValue().get(GENERATION_KEY);

        try {
            Categoria inactive = copyWithName(original, original.getNombre());
            inactive.setActivo(false);
            categoriaService.actualizarCategoria(1L, inactive);

            String generationAfter = redisTemplate.opsForValue().get(GENERATION_KEY);
            assertThat(generationAfter).isNotEqualTo(generationBefore);
            assertThat(readJson("/api/categorias/1").path("activo").asBoolean()).isFalse();
            assertThat(redisTemplate.opsForValue().get(
                    CATEGORIAS_DETAIL_CACHE_KEY_PREFIX + "1:g" + generationAfter)).isNotBlank();
            assertThat(readJson("/api/categorias").get(0).path("activo").asBoolean()).isFalse();
        } finally {
            categoriaService.actualizarCategoria(1L, copyWithName(original, original.getNombre()));
        }
    }

    @Test
    @WithAnonymousUser
    void companyDeactivationInvalidatesTheCachedProductListing() throws Exception {
        Producto product = productoService.obtenerProductoPorId(1L);
        Long companyId = product.getEmpresa().getId();
        Empresa original = empresaService.obtenerEmpresaPorId(companyId);
        readListing();
        String generationBefore = redisTemplate.opsForValue().get(GENERATION_KEY);

        try {
            Empresa inactive = copyWithName(original, original.getNombre());
            inactive.setActivo(false);
            empresaService.actualizarEmpresa(companyId, inactive);

            String generationAfter = redisTemplate.opsForValue().get(GENERATION_KEY);
            assertThat(generationAfter).isNotEqualTo(generationBefore);
            readListing();
            assertThat(redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ":g" + generationAfter)).isNotBlank();
            verify(productoRepository, times(2)).findAllOptimized();
        } finally {
            empresaService.actualizarEmpresa(companyId, copyWithName(original, original.getNombre()));
        }
    }

    @Test
    @WithAnonymousUser
    void failedPostCommitInvalidationKeepsOldEntryUntilItsExistingTtlAndCountsFailure() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 12));
            return quantities;
        });

        Producto original = productoService.obtenerProductoPorId(1L);
        String originalName = original.getNombre();
        String updatedName = originalName + " - invalidation-failure-it";
        readListing();
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        String cacheKey = CACHE_KEY_PREFIX + ":g" + generation;
        assertThat(redisTemplate.expire(cacheKey, Duration.ofSeconds(2))).isTrue();
        long syntheticTtlStartedAt = System.nanoTime();
        Long ttlBeforeMutation = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS);
        var invalidationFailures = meterRegistry.counter(
                "feriadigital.redis.cache.failures",
                "resource", "catalogo",
                "operation", "invalidation");
        double failuresBefore = invalidationFailures.count();

        doThrow(new RedisConnectionFailureException("synthetic generation-write failure"))
                .doCallRealMethod()
                .when(redisStore).advanceGeneration(GENERATION_KEY);

        try {
            productoService.actualizarProducto(1L, copyWithName(original, updatedName));

            assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isEqualTo(generation);
            Long ttlAfterMutation = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS);
            assertThat(ttlAfterMutation).isPositive().isLessThanOrEqualTo(ttlBeforeMutation);
            assertThat(findById(readListing(), 1L).path("nombre").asText()).isEqualTo(originalName);
            Long ttlAfterHit = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS);
            assertThat(ttlAfterHit).isPositive().isLessThanOrEqualTo(ttlAfterMutation);
            assertThat(invalidationFailures.count()).isEqualTo(failuresBefore + 1.0);
            assertThat(productoService.obtenerProductoPorId(1L).getNombre()).isEqualTo(updatedName);

            long expiryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey)) && System.nanoTime() < expiryDeadline) {
                Thread.sleep(25);
            }
            long observedStaleLifetimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - syntheticTtlStartedAt);
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
            assertThat(observedStaleLifetimeMillis).isBetween(1_500L, 5_000L);
            assertThat(findById(readListing(), 1L).path("nombre").asText()).isEqualTo(updatedName);
        } finally {
            productoService.actualizarProducto(1L, copyWithName(original, originalName));
        }
    }

    @Test
    @WithAnonymousUser
    void inFlightOldCatalogReadCannotPublishAfterCommittedUpdateAdvancesGeneration() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 14));
            return quantities;
        });

        Producto original = productoService.obtenerProductoPorId(1L);
        String originalName = original.getNombre();
        String updatedName = originalName + " - generation-race-it";
        String initialGeneration = redisStore.getGeneration(GENERATION_KEY);
        CountDownLatch sourceLoaded = new CountDownLatch(1);
        CountDownLatch allowOldReadToContinue = new CountDownLatch(1);
        AtomicBoolean pauseFirstRead = new AtomicBoolean(true);
        CompletableFuture<JsonNode> oldRequest = null;

        doAnswer(invocation -> {
            Object rows = invocation.callRealMethod();
            if (pauseFirstRead.compareAndSet(true, false)) {
                sourceLoaded.countDown();
                if (!allowOldReadToContinue.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to resume the synthetic catalog read");
                }
            }
            return rows;
        }).when(productoRepository).findAllOptimized();

        try {
            oldRequest = CompletableFuture.supplyAsync(() -> {
                try {
                    return readListing();
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            });
            assertThat(sourceLoaded.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(redisTemplate.opsForValue().get(GENERATION_KEY)).isEqualTo(initialGeneration);

            productoService.actualizarProducto(1L, copyWithName(original, updatedName));
            String committedGeneration = redisTemplate.opsForValue().get(GENERATION_KEY);
            assertThat(committedGeneration).isNotEqualTo(initialGeneration);

            allowOldReadToContinue.countDown();
            JsonNode inFlightResponse = oldRequest.get(15, TimeUnit.SECONDS);
            assertThat(findById(inFlightResponse, 1L).path("nombre").asText()).isEqualTo(originalName);
            assertThat(redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ":g" + committedGeneration)).isNull();

            JsonNode freshResponse = readListing();
            assertThat(findById(freshResponse, 1L).path("nombre").asText()).isEqualTo(updatedName);
            assertThat(redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ":g" + committedGeneration)).isNotBlank();
        } finally {
            allowOldReadToContinue.countDown();
            productoService.actualizarProducto(1L, copyWithName(original, originalName));
            if (oldRequest != null) {
                oldRequest.cancel(true);
            }
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

    @Test
    @WithMockUser(authorities = "ROLE_EMP")
    void nonAdminCannotMutateCategoriesOrCompaniesOrAdvanceTheCatalogGeneration() throws Exception {
        Categoria originalCategoria = categoriaService.obtenerCategoriaPorId(1L);
        Empresa originalEmpresa = empresaService.obtenerEmpresaPorId(1L);
        String nombreCategoria = originalCategoria.getNombre();
        String nombreEmpresa = originalEmpresa.getNombre();
        int categoryStatus;
        int companyStatus;
        boolean categoryUnchanged;
        boolean companyUnchanged;
        boolean redisUntouched;

        try {
            categoryStatus = mockMvc.perform(put("/api/categorias/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CategoriaRequestDto(
                                    nombreCategoria + " redis-auth-check",
                                    originalCategoria.getDescripcion(),
                                    originalCategoria.getActivo()))))
                    .andReturn().getResponse().getStatus();
            companyStatus = mockMvc.perform(put("/api/empresas/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EmpresaRequestDto(
                                    nombreEmpresa + " redis-auth-check",
                                    "9999999999999",
                                    "Synthetic test only",
                                    "0999999999",
                                    "redis-auth-check@example.invalid",
                                    true))))
                    .andReturn().getResponse().getStatus();
            categoryUnchanged = nombreCategoria.equals(categoriaService.obtenerCategoriaPorId(1L).getNombre());
            companyUnchanged = nombreEmpresa.equals(empresaService.obtenerEmpresaPorId(1L).getNombre());
            redisUntouched = redisTemplate.opsForValue().get(GENERATION_KEY) == null;
        } finally {
            categoriaService.actualizarCategoria(1L, copyWithName(originalCategoria, nombreCategoria));
            empresaService.actualizarEmpresa(1L, copyWithName(originalEmpresa, nombreEmpresa));
        }

        assertThat(categoryStatus).isEqualTo(403);
        assertThat(companyStatus).isEqualTo(403);
        assertThat(categoryUnchanged).isTrue();
        assertThat(companyUnchanged).isTrue();
        assertThat(redisUntouched).isTrue();
    }

    @Test
    @WithAnonymousUser
    void livenessAndReadinessRemainPublicWhileMetricsRequireAdmin() throws Exception {
        var liveness = mockMvc.perform(get("/actuator/health/liveness")).andReturn().getResponse();
        var readiness = mockMvc.perform(get("/actuator/health/readiness")).andReturn().getResponse();
        var anonymousMetrics = mockMvc.perform(get("/actuator/metrics/feriadigital.redis.cache.requests"))
                .andReturn().getResponse();
        var entrepreneurMetrics = mockMvc.perform(get("/actuator/metrics/feriadigital.redis.cache.requests")
                        .with(asEntrepreneur()))
                .andReturn().getResponse();

        assertThat(liveness.getStatus()).isEqualTo(200);
        assertThat(readiness.getStatus()).isEqualTo(200);
        assertThat(anonymousMetrics.getStatus()).isEqualTo(401);
        assertThat(entrepreneurMetrics.getStatus()).isEqualTo(403);
    }

    @Test
    @WithAnonymousUser
    void redisHealthDegradesWithoutTakingApplicationLivenessOrReadinessDown() throws Exception {
        doThrow(new RedisConnectionFailureException("synthetic Redis outage"))
                .when(redisConnectionFactory).getConnection();

        try {
            var redisHealth = mockMvc.perform(get("/actuator/health/cache"))
                    .andReturn().getResponse();
            var liveness = mockMvc.perform(get("/actuator/health/liveness"))
                    .andReturn().getResponse();
            var readiness = mockMvc.perform(get("/actuator/health/readiness"))
                    .andReturn().getResponse();

            assertThat(redisHealth.getStatus()).isEqualTo(503);
            assertThat(redisHealth.getContentAsString()).contains("DOWN")
                    .doesNotContain("synthetic Redis outage", "password", "secret", "host", "port");
            assertThat(liveness.getStatus()).isEqualTo(200);
            assertThat(readiness.getStatus()).isEqualTo(200);
        } finally {
            reset(redisConnectionFactory);
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanQueryCatalogCacheMetricsWithoutExposingRedisDetails() throws Exception {
        when(inventarioClient.obtenerCantidadesPorProductos(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            Map<String, Integer> quantities = new HashMap<>();
            ids.forEach(id -> quantities.put(String.valueOf(id), 1));
            return quantities;
        });
        readListing();
        readListing();

        var response = mockMvc.perform(get("/actuator/metrics/feriadigital.redis.cache.requests")
                        .param("tag", "resource:catalogo")
                        .param("tag", "result:hit"))
                .andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.path("name").asText()).isEqualTo("feriadigital.redis.cache.requests");
        assertThat(body.path("measurements").get(0).path("value").asDouble()).isGreaterThanOrEqualTo(1.0);
        assertThat(response.getContentAsString()).doesNotContain("password", "secret", "host", "port");
    }

    private JsonNode readListing() throws Exception {
        return readJson("/api/productos/listado");
    }

    private RequestPostProcessor asEntrepreneur() {
        return user("synthetic-entrepreneur").authorities(new SimpleGrantedAuthority("ROLE_EMP"));
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

    private boolean containsProductId(JsonNode products, long id) {
        for (JsonNode product : products) {
            if (product.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
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
