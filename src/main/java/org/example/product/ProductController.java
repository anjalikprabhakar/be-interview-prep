package org.example.product;

import org.example.common.web.PageResponse;
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

    /** ?page=0&size=20&sort=price,desc&sort=name; size above 100 is clamped (spring.data.web.pageable). */
    @GetMapping
    public PageResponse<ProductResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.list(pageable);
    }
}
