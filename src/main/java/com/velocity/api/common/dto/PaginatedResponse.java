package com.velocity.api.common.dto;

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
    ) {}
}