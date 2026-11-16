package com.example.msvc_producto.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProductoCatalogoInvalidacionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductoCatalogoInvalidacionService.class);

    private final ObjectProvider<ProductoCatalogoCacheService> cacheProvider;

    public ProductoCatalogoInvalidacionService(ObjectProvider<ProductoCatalogoCacheService> cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    /**
     * Rotates the cache generation only after the owning MySQL transaction commits.
     * If Redis is unavailable, the source write remains committed and its cache entry
     * expires at the configured TTL.
     */
    public void invalidarDespuesDeCommit() {
        Runnable invalidation = this::invalidarAhora;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
        } else {
            invalidation.run();
        }
    }

    private void invalidarAhora() {
        ProductoCatalogoCacheService cacheService = cacheProvider.getIfAvailable();
        if (cacheService == null) {
            return;
        }
        try {
            cacheService.invalidarCatalogo(ProductoCatalogoCacheService.CATALOGO_GENERATION_KEY);
        } catch (RuntimeException redisFailure) {
            LOGGER.warn("No se pudo invalidar el catálogo tras el commit; el TTL limita la obsolescencia ({})",
                    redisFailure.getClass().getSimpleName());
        }
    }
}
