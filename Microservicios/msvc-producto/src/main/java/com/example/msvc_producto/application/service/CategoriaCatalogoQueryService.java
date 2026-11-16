package com.example.msvc_producto.application.service;

import com.example.msvc_producto.application.dto.CategoriaResponseDto;
import com.example.msvc_producto.application.mapper.CategoriaMapper;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class CategoriaCatalogoQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoriaCatalogoQueryService.class);
    private static final String GENERATION_KEY = ProductoCatalogoCacheService.CATALOGO_GENERATION_KEY;
    private static final String LIST_CACHE_KEY = "feriadigital:v1:{catalogo}:categorias:listado";
    private static final String DETAIL_CACHE_KEY_PREFIX = "feriadigital:v1:{catalogo}:categorias:detalle:";

    private final CategoriaService categoriaService;
    private final CategoriaMapper categoriaMapper;
    private final ObjectMapper objectMapper;
    private final ProductoCatalogoCacheService cacheService;
    private final boolean cacheHabilitado;
    private final Duration ttl;

    public CategoriaCatalogoQueryService(
            CategoriaService categoriaService,
            CategoriaMapper categoriaMapper,
            ObjectMapper objectMapper,
            ObjectProvider<ProductoCatalogoCacheService> cacheProvider,
            @Value("${cache.redis.categorias.enabled:false}") boolean cacheHabilitado,
            @Value("${cache.redis.ttl-categorias:PT15M}") Duration ttl) {
        this.categoriaService = categoriaService;
        this.categoriaMapper = categoriaMapper;
        this.objectMapper = objectMapper;
        this.cacheService = cacheProvider.getIfAvailable();
        this.cacheHabilitado = cacheHabilitado && cacheService != null;
        this.ttl = ttl;
        if (this.cacheHabilitado && (ttl == null || ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("cache.redis.ttl-categorias debe ser una duración positiva");
        }
    }

    public List<CategoriaResponseDto> listarCategorias() {
        Supplier<List<CategoriaResponseDto>> source = () -> categoriaService.listarCategorias().stream()
                .map(categoriaMapper::toDto)
                .toList();
        if (!cacheHabilitado) {
            return source.get();
        }

        return obtenerConCache(
                LIST_CACHE_KEY,
                () -> serializar(source.get()),
                json -> deserializarLista(json),
                source);
    }

    public CategoriaResponseDto obtenerCategoriaPorId(Long id) {
        Supplier<CategoriaResponseDto> source = () -> categoriaMapper.toDto(categoriaService.obtenerCategoriaPorId(id));
        if (!cacheHabilitado) {
            return source.get();
        }

        return obtenerConCache(
                DETAIL_CACHE_KEY_PREFIX + id,
                () -> serializar(source.get()),
                this::deserializarCategoria,
                source);
    }

    private <T> T obtenerConCache(
            String cacheKey,
            Supplier<String> sourceJson,
            Function<String, T> deserializer,
            Supplier<T> sourceFallback) {
        String json = cacheService.obtenerJson(GENERATION_KEY, cacheKey, ttl, sourceJson);
        try {
            return deserializer.apply(json);
        } catch (InvalidCategoriaCacheException invalidJson) {
            try {
                cacheService.invalidarCatalogo(GENERATION_KEY);
            } catch (RuntimeException redisFailure) {
                LOGGER.warn("No se pudo rotar la generación de categorías tras JSON inválido ({})",
                        redisFailure.getClass().getSimpleName());
            }
            LOGGER.warn("El JSON de categorías no se pudo leer; se consulta MySQL directamente");
            return sourceFallback.get();
        }
    }

    private String serializar(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("No se pudo serializar la respuesta de categorías", serializationFailure);
        }
    }

    private List<CategoriaResponseDto> deserializarLista(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<CategoriaResponseDto>>() { });
        } catch (JsonProcessingException invalidJson) {
            throw new InvalidCategoriaCacheException(invalidJson);
        }
    }

    private CategoriaResponseDto deserializarCategoria(String json) {
        try {
            return objectMapper.readValue(json, CategoriaResponseDto.class);
        } catch (JsonProcessingException invalidJson) {
            throw new InvalidCategoriaCacheException(invalidJson);
        }
    }

    private static final class InvalidCategoriaCacheException extends RuntimeException {
        private InvalidCategoriaCacheException(Throwable cause) {
            super(cause);
        }
    }
}
