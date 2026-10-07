package org.example.product;

import jakarta.validation.Valid;
import org.example.common.web.PageResponse;
import org.example.product.dto.ProductFilter;
import org.example.product.dto.ProductResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /**
     * ?category=books&minPrice=10&maxPrice=50&inStock=true&q=lamp&page=0&size=20&sort=price,desc
     * The filter record is bound from the query parameters; size above 100 is clamped (spring.data.web.pageable).
     */
    @GetMapping
    public PageResponse<ProductResponse> list(@Valid ProductFilter filter,
                                              @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.list(filter, pageable);
    }
}
