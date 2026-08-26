package com.velocity.api.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> data,
        PaginationMeta meta
) {
    public record PaginationMeta(
            int currentPage,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean isFirst,
            boolean isLast,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }

    public static <T> PaginatedResponse<T> from(Page<T> page) {
        PaginatedResponse.PaginationMeta meta = new PaginatedResponse.PaginationMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );

        return new PaginatedResponse<>(
                page.getContent(),
                meta
        );
    }
}