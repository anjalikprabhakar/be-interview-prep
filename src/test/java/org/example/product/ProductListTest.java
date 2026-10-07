package org.example.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real DB queries: each test replaces the seeded products with its own small, known data set. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ProductListTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository repository;

    @BeforeEach
    void replaceProducts() {
        repository.deleteAll();
        repository.saveAll(List.of(
                product("Reading Lamp", "home", "25.00", 5),  // matches every filter in the combined test
                product("Desk Lamp", "home", "60.00", 5),     // too expensive
                product("Floor lamp", "home", "30.00", 0),    // out of stock
                product("Lamp Oil", "books", "20.00", 3),     // wrong category
                product("Rug", "home", "20.00", 2)));         // name doesn't match
    }

    @Test
    void list_categoryPriceRangeInStockAndName_returnsOnlyMatching() throws Exception {
        mvc.perform(get("/api/products")
                        .param("category", "home")
                        .param("minPrice", "10")
                        .param("maxPrice", "50")
                        .param("inStock", "true")
                        .param("q", "lamp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Reading Lamp"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "category=home                      | 4",
            "q=LAMP                             | 4",
            "inStock=true                       | 4",
            "inStock=false                      | 5",
            "minPrice=25&maxPrice=30            | 2",
            "category=home&q=lamp               | 3",
            "category=home&inStock=true&q=lamp  | 2",
            "q=%25                              | 0",
            "q=_                                | 0"})
    void list_anyFilterCombination_returnsMatchingCount(String query, int expected) throws Exception {
        mvc.perform(get("/api/products?" + query.strip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected));
    }

    @Test
    void list_page0Size2_returnsTotalsAndPageCount() throws Exception {
        mvc.perform(get("/api/products").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void list_size500_isClampedTo100() throws Exception {
        List<Product> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(product("Item " + i, "misc", "1.00", 1));
        }
        repository.saveAll(many); // 105 products in total

        mvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(100)))
                .andExpect(jsonPath("$.totalElements").value(105))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void list_sortByPriceDescThenName_ordersResults() throws Exception {
        mvc.perform(get("/api/products").param("sort", "price,desc").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name")
                        .value(contains("Desk Lamp", "Floor lamp", "Reading Lamp", "Lamp Oil", "Rug")));
    }

    @Test
    void list_sortByPriceWithTies_breaksTiesById() throws Exception {
        // "Lamp Oil" and "Rug" both cost 20.00; id is appended as the last sort key, so insertion order wins.
        mvc.perform(get("/api/products").param("sort", "price").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Lamp Oil", "Rug")));
        mvc.perform(get("/api/products").param("sort", "price,desc").param("size", "5"))
                .andExpect(jsonPath("$.content[3].name").value("Lamp Oil"))
                .andExpect(jsonPath("$.content[4].name").value("Rug"));
    }

    @Test
    void list_sortByUnknownField_returns400WithFieldError() throws Exception {
        mvc.perform(get("/api/products").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(
                        "cannot sort by 'password'; allowed fields: [category, createdAt, id, name, price, rating, stock]"));
    }

    @Test
    void list_minPriceAboveMaxPrice_returns400WithFieldError() throws Exception {
        mvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("minPrice must not be greater than maxPrice"));
    }

    @Test
    void list_negativeMinPrice_returns400WithFieldError() throws Exception {
        mvc.perform(get("/api/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("minPrice must be zero or more"));
    }

    @Test
    void list_nonNumericMinPrice_returns400WithoutInternals() throws Exception {
        mvc.perform(get("/api/products").param("minPrice", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("invalid value 'abc'"));
    }

    static Product product(String name, String category, String price, int stock) {
        return new Product(name, category, new BigDecimal(price), stock, new BigDecimal("4.0"));
    }
}
