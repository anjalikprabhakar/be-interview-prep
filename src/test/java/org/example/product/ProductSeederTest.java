package org.example.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductSeederTest {

    @Autowired
    ProductSeeder seeder;

    @Autowired
    ProductRepository repository;

    @Test
    void run_emptyTable_seeds100ProductsAcrossAllCategories() {
        repository.deleteAll();

        seeder.run(null);

        assertThat(repository.count()).isEqualTo(ProductSeeder.PRODUCT_COUNT);
        assertThat(repository.findAll()).extracting(Product::getCategory)
                .containsOnlyElementsOf(ProductSeeder.CATEGORIES)
                .containsAll(ProductSeeder.CATEGORIES);
        assertThat(repository.findAll()).anyMatch(p -> p.getStock() == 0);
    }

    @Test
    void run_tableAlreadyHasProducts_addsNothing() {
        repository.deleteAll();
        seeder.run(null);

        seeder.run(null);

        assertThat(repository.count()).isEqualTo(ProductSeeder.PRODUCT_COUNT);
    }
}
