package com.example.demo.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contrato común de respuesta paginada (A-19).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDto<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    public static <S, T> PageResponseDto<T> fromPage(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.getContent().stream()
                .map(mapper)
                .collect(Collectors.toList());
        return new PageResponseDto<>(
                mapped,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
