package org.example.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may read and write the catalog, and the error paths of PUT and DELETE. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductWriteTest {

    private static final String VALID_BODY = """
            {"name": "Lamp", "category": "home", "price": 10.00, "stock": 1, "rating": 3.0}
            """;
    private static final long UNKNOWN_ID = Long.MAX_VALUE;

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository repository;

    private Long id;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        id = repository.save(ProductListTest.product("Reading Lamp", "home", "25.00", 5)).getId();
    }

    @Test
    void get_noToken_returns401Json() throws Exception {
        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/products/" + id));
    }

    @Test
    void update_noToken_returns401Json() throws Exception {
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser
    void update_asUser_returns403AndChangesNothing() throws Exception {
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/products/" + id));

        assertThat(repository.findById(id)).get().extracting(Product::getName).isEqualTo("Reading Lamp");
    }

    @Test
    @WithMockUser
    void delete_asUser_returns403AndKeepsProduct() throws Exception {
        mvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/products/" + id));

        assertThat(repository.existsById(id)).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_invalidBody_returns400WithFieldErrors() throws Exception {
        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "category": "home", "price": -1, "stock": 1, "rating": 7.5}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("name is required"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'price')].message").value("price must be zero or more"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'rating')].message")
                        .value("rating must be between 0 and 5"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_unknownId_returns404() throws Exception {
        mvc.perform(put("/api/products/{id}", UNKNOWN_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product " + UNKNOWN_ID + " not found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_unknownId_returns404() throws Exception {
        mvc.perform(delete("/api/products/{id}", UNKNOWN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product " + UNKNOWN_ID + " not found"));
    }
}
