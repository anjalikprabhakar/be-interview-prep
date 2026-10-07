package org.example.product.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Query parameters of GET /api/products. Every filter is optional; any combination may be sent. */
public record ProductFilter(
        String category,

        @PositiveOrZero(message = "minPrice must be zero or more")
        BigDecimal minPrice,

        @PositiveOrZero(message = "maxPrice must be zero or more")
        BigDecimal maxPrice,

        Boolean inStock,

        String q
) {
}
