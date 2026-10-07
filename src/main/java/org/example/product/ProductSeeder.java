package org.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds 100 products on startup, only when the table is empty (so a restart against a persistent DB
 * doesn't duplicate them). Values are derived from the index, not random, so the data is predictable.
 */
@Component
public class ProductSeeder implements ApplicationRunner {

    static final int PRODUCT_COUNT = 100;
    static final List<String> CATEGORIES = List.of("books", "electronics", "home", "sports", "toys");
    private static final List<String> NAMES = List.of("Novel", "Headphones", "Lamp", "Football", "Puzzle");
    private static final BigDecimal CENTS_99 = new BigDecimal("0.99");

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);

    private final ProductRepository repository;

    public ProductSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        List<Product> products = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            int kind = i % CATEGORIES.size();
            products.add(new Product(
                    NAMES.get(kind) + " " + i,
                    CATEGORIES.get(kind),
                    BigDecimal.valueOf(i).add(CENTS_99),        // 1.99 to 100.99
                    i % 7 == 0 ? 0 : (i * 3) % 50 + 1,          // every 7th is out of stock
                    BigDecimal.valueOf(10 + (i * 7) % 41, 1))); // 1.0 to 5.0
        }
        repository.saveAll(products);
        log.info("Seeded {} products", PRODUCT_COUNT);
    }
}
