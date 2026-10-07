package org.example.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "How do you know it's cached?": the repository is a Mockito spy around the real one, so every DB lookup
 * is counted. If GET hits the cache, findById is not called again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class ProductCacheTest {

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    ProductRepository repository;

    @Autowired
    CacheManager cacheManager;

    private Long id;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(ProductService.CACHE).clear();
        id = repository.save(ProductListTest.product("Reading Lamp", "home", "25.00", 5)).getId();
        clearInvocations(repository);
    }

    @Test
    void getById_calledThreeTimes_hitsRepositoryOnce() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Reading Lamp"));
        }

        verify(repository, times(1)).findById(id);
    }

    @Test
    void getById_afterUpdate_returnsNewValue() throws Exception {
        mvc.perform(get("/api/products/{id}", id)).andExpect(jsonPath("$.price").value(25.00));

        mvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Reading Lamp", "category": "home", "price": 19.99, "stock": 5, "rating": 4.5}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(19.99));

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.rating").value(4.5));
        // One read for the first GET, one inside the update; the last GET is served by @CachePut's fresh value.
        verify(repository, times(2)).findById(id);
    }

    @Test
    void getById_afterDelete_returns404() throws Exception {
        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());

        mvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product " + id + " not found"));
    }
}
