package org.example.product;

import org.example.common.exception.BadRequestException;
import org.example.common.exception.NotFoundException;
import org.example.common.web.PageResponse;
import org.example.product.dto.ProductFilter;
import org.example.product.dto.ProductRequest;
import org.example.product.dto.ProductResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ProductService {

    public static final String CACHE = "products";
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
     * The first call for an id loads from the DB; later calls are served from Caffeine without running this
     * method. Caches the immutable DTO, never the entity. A 404 throws, so nothing is cached for unknown ids.
     */
    @Cacheable(cacheNames = CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(findProduct(id));
    }

    /** Writes the returned value into the cache, so the next get sees the update immediately. */
    @CachePut(cacheNames = CACHE, key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        // Managed entity: the change is flushed as an UPDATE on commit, no save() needed.
        product.replace(request.name(), request.category(), request.price(), request.stock(), request.rating());
        return ProductResponse.from(product);
    }

    /** Removes the cached entry, so the next get goes to the DB and returns 404. */
    @CacheEvict(cacheNames = CACHE, key = "#id")
    @Transactional
    public void delete(Long id) {
        repository.delete(findProduct(id));
    }

    private Product findProduct(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
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
