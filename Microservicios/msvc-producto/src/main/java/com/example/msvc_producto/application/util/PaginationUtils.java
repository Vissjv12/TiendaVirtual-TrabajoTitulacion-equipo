package com.example.msvc_producto.application.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Utilidad centralizada para construir {@link Pageable} seguro según el contrato A-19:
 * - page base 0 (si es negativo -> 0)
 * - size máximo estricto 50 (si <= 0 -> defaultSize, si > 50 -> 50)
 * - sort validado contra una lista blanca de propiedades permitidas
 */
public final class PaginationUtils {

    public static final int DEFAULT_STOREFRONT_SIZE = 12;
    public static final int DEFAULT_MANAGEMENT_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    private PaginationUtils() {
    }

    public static Pageable createPageable(
            Integer page,
            Integer size,
            String sortParam,
            int defaultSize,
            String defaultSortField,
            Sort.Direction defaultDirection,
            Map<String, String> allowedSortFields) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeDefault = defaultSize > 0 ? Math.min(defaultSize, MAX_PAGE_SIZE) : DEFAULT_MANAGEMENT_SIZE;
        int safeSize = (size == null || size <= 0) ? safeDefault : Math.min(size, MAX_PAGE_SIZE);
        Sort sort = parseSort(sortParam, defaultSortField, defaultDirection, allowedSortFields);
        return PageRequest.of(safePage, safeSize, sort);
    }

    public static Sort parseSort(
            String sortParam,
            String defaultSortField,
            Sort.Direction defaultDirection,
            Map<String, String> allowedSortFields) {
        if (sortParam != null && !sortParam.isBlank() && allowedSortFields != null) {
            String[] parts = sortParam.split(",");
            String requestedField = parts[0].trim();
            String mappedField = allowedSortFields.get(requestedField);
            if (mappedField != null) {
                Sort.Direction direction = defaultDirection;
                if (parts.length > 1) {
                    String dir = parts[1].trim().toLowerCase();
                    if ("asc".equals(dir)) {
                        direction = Sort.Direction.ASC;
                    } else if ("desc".equals(dir)) {
                        direction = Sort.Direction.DESC;
                    }
                }
                return Sort.by(direction, mappedField);
            }
        }
        return Sort.by(defaultDirection, defaultSortField);
    }

    public static String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }
}
