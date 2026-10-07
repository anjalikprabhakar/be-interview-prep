package org.example.product.dto;

import org.example.product.Product;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable, so it is safe to keep in the cache and hand to many callers. */
public record ProductResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock,
        BigDecimal rating,
        Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getCategory(),
                product.getPrice(), product.getStock(), product.getRating(), product.getCreatedAt());
    }
}
