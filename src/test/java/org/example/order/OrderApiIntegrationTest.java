package org.example.order;

import org.example.product.Product;
import org.example.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP contract end to end: status codes, headers and ApiError bodies, against the real DB. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "alice@example.com")
class OrderApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    ProductRepository products;

    private Long productId;

    @BeforeEach
    void setUp() {
        productId = products.save(new Product("Lamp", "home", new BigDecimal("19.99"), 3,
                new BigDecimal("4.0"))).getId();
    }

    /** Orders reference products (order_items FK), so remove ours: other test classes delete all products. */
    @AfterEach
    void removeOrders() {
        orders.deleteAll();
    }

    @Test
    void place_validRequest_returns201WithLocation() throws Exception {
        place("k1", productId, 2)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/orders/" + orders.findAll().get(0).getId())))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19.99));
    }

    @Test
    void place_retryWithSameKey_returns200WithSameOrder() throws Exception {
        String first = place("k1", productId, 1).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        place("k1", productId, 1)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(first));
    }

    @Test
    void place_insufficientStock_returns409WithMessage() throws Exception {
        place("k1", productId, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Insufficient stock for product " + productId + " (requested 4)"))
                .andExpect(jsonPath("$.path").value("/api/orders"));
    }

    @Test
    void place_missingIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body(productId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is not present."));
    }

    @Test
    void place_tooLongIdempotencyKey_returns400WithFieldError() throws Exception {
        place("x".repeat(65), productId, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be 1 to 64 characters"));
    }

    @Test
    void place_emptyItems_returns400WithFieldError() throws Exception {
        mvc.perform(post("/api/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
    }

    @Test
    void place_zeroQuantity_returns400WithFieldError() throws Exception {
        place("k1", productId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].quantity"));
    }

    @Test
    void place_unknownProduct_returns404() throws Exception {
        place("k1", 999_999L, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product 999999 not found"));
    }

    @Test
    @WithAnonymousUser
    void place_withoutToken_returns401() throws Exception {
        place("k1", productId, 1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void get_ownOrder_returns200() throws Exception {
        Long id = placedOrderId();

        mvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @WithMockUser(username = "mallory@example.com")
    void get_otherCustomersOrder_returns404() throws Exception {
        Long id = orders.save(new Order("alice@example.com", "k1")).getId();

        mvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order " + id + " not found"));
    }

    @Test
    void cancel_placedOrder_returns200Cancelled() throws Exception {
        Long id = placedOrderId();

        mvc.perform(post("/api/orders/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_alreadyCancelled_returns409() throws Exception {
        Long id = placedOrderId();
        mvc.perform(post("/api/orders/{id}/cancel", id)).andExpect(status().isOk());

        mvc.perform(post("/api/orders/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order " + id + " is already cancelled"));
    }

    @Test
    void cancel_unknownOrder_returns404() throws Exception {
        mvc.perform(post("/api/orders/{id}/cancel", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order 999999 not found"));
    }

    @Test
    void deleteProduct_referencedByOrder_returns409AndKeepsProduct() throws Exception {
        placedOrderId();

        mvc.perform(delete("/api/products/{id}", productId).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data"));
        assertThat(products.existsById(productId)).isTrue();
    }

    private Long placedOrderId() throws Exception {
        place("k1", productId, 1).andExpect(status().isCreated());
        return orders.findAll().get(0).getId();
    }

    private ResultActions place(String key, Long productId, int quantity) throws Exception {
        return mvc.perform(post("/api/orders").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(productId, quantity)));
    }

    private static String body(Long productId, int quantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }
}
