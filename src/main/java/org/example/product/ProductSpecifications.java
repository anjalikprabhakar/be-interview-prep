package org.example.product;

import org.example.product.dto.ProductFilter;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * One small predicate per filter. Each returns null when its parameter is absent, and Specification.allOf
 * skips nulls, so any combination of filters becomes a single WHERE clause joined with AND.
 */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> matching(ProductFilter filter) {
        return Specification.allOf(
                hasCategory(filter.category()),
                priceAtLeast(filter.minPrice()),
                priceAtMost(filter.maxPrice()),
                inStockOnly(filter.inStock()),
                nameContains(filter.q()));
    }

    static Specification<Product> hasCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    static Specification<Product> priceAtLeast(BigDecimal min) {
        return min == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    static Specification<Product> priceAtMost(BigDecimal max) {
        return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    /** inStock=false or absent means "don't filter", not "only out-of-stock products". */
    static Specification<Product> inStockOnly(Boolean inStock) {
        return Boolean.TRUE.equals(inStock) ? (root, query, cb) -> cb.greaterThan(root.get("stock"), 0) : null;
    }

    /** Case-insensitive "contains". % and _ in the search text are escaped so they match literally. */
    static Specification<Product> nameContains(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\');
    }
}
