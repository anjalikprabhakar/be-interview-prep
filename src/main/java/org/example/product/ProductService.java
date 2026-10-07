package org.example.product;

import org.example.common.exception.BadRequestException;
import org.example.common.web.PageResponse;
import org.example.product.dto.ProductFilter;
import org.example.product.dto.ProductResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ProductService {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "category", "price", "stock", "rating", "createdAt");

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** All filters and the page are applied in one query (plus the count(*) that gives the totals). */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductFilter filter, Pageable pageable) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new BadRequestException("minPrice", "minPrice must not be greater than maxPrice");
        }
        return PageResponse.from(
                repository.findAll(ProductSpecifications.matching(filter), withStableSort(pageable)),
                ProductResponse::from);
    }

    /**
     * Rejects unknown sort fields with a 400 (otherwise Spring Data fails with a 500), and adds id as the last
     * sort key: with ties (e.g. many products at the same price) the order would otherwise be undefined, and
     * the same product could appear on two pages.
     */
    private static Pageable withStableSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("sort", "cannot sort by '" + order.getProperty()
                        + "'; allowed fields: " + SORTABLE_FIELDS.stream().sorted().toList());
            }
        }
        Sort sort = pageable.getSort().getOrderFor("id") != null
                ? pageable.getSort()
                : pageable.getSort().and(Sort.by("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
