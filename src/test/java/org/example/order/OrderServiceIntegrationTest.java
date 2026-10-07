package org.example.order;

import org.example.common.exception.ConflictException;
import org.example.common.exception.NotFoundException;
import org.example.order.dto.OrderItemRequest;
import org.example.order.dto.OrderRequest;
import org.example.order.dto.OrderResponse;
import org.example.product.Product;
import org.example.product.ProductRepository;
import org.example.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** Not @Transactional: a rollback must be observable in the DB after the call, as it would be in production. */
@SpringBootTest
class OrderServiceIntegrationTest {

    private static final String ALICE = "alice@example.com";

    @Autowired
    OrderService service;

    @Autowired
    ProductService productService;

    @Autowired
    OrderRepository orders;

    @Autowired
    ProductRepository products;

    private Long lampId;
    private Long bookId;

    @BeforeEach
    void setUp() {
        lampId = products.save(product("Lamp", "19.99", 5)).getId();
        bookId = products.save(product("Book", "9.50", 1)).getId();
    }

    /** Orders reference products (order_items FK), so remove ours: other test classes delete all products. */
    @AfterEach
    void removeOrders() {
        orders.deleteAll();
    }

    @Test
    void place_validItems_reservesStockAndRecordsPrice() {
        PlaceOrderResult result = service.place(ALICE, "k1", request(item(lampId, 2), item(bookId, 1)));

        assertThat(result.created()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(result.order().items()).extracting("productId", "quantity", "unitPrice").containsExactly(
                tuple(lampId, 2, new BigDecimal("19.99")),
                tuple(bookId, 1, new BigDecimal("9.50")));
        assertThat(stockOf(lampId)).isEqualTo(3);
        assertThat(stockOf(bookId)).isZero();
    }

    @Test
    void place_sameIdempotencyKeyTwice_createsOneOrder() {
        PlaceOrderResult first = service.place(ALICE, "k1", request(item(lampId, 1)));
        PlaceOrderResult retry = service.place(ALICE, "k1", request(item(lampId, 1)));

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.order()).isEqualTo(first.order());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(stockOf(lampId)).isEqualTo(4);
    }

    @Test
    void place_sameKeyDifferentCustomers_createsTwoOrders() {
        service.place(ALICE, "k1", request(item(lampId, 1)));
        service.place("bob@example.com", "k1", request(item(lampId, 1)));

        assertThat(orders.count()).isEqualTo(2);
        assertThat(stockOf(lampId)).isEqualTo(3);
    }

    @Test
    void place_secondItemOutOfStock_firstItemStockUnchanged() {
        // lampId < bookId, so the lamp is reserved first and must be rolled back when the book fails.
        assertThatThrownBy(() -> service.place(ALICE, "k1", request(item(lampId, 2), item(bookId, 2))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for product " + bookId + " (requested 2)");

        assertThat(stockOf(lampId)).isEqualTo(5);
        assertThat(stockOf(bookId)).isEqualTo(1);
        assertThat(orders.count()).isZero();
    }

    @Test
    void place_duplicateLinesForOneProduct_checksTheTotal() {
        assertThatThrownBy(() -> service.place(ALICE, "k1", request(item(lampId, 3), item(lampId, 3))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for product " + lampId + " (requested 6)");

        PlaceOrderResult result = service.place(ALICE, "k2", request(item(lampId, 2), item(lampId, 3)));
        assertThat(result.order().items()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(5));
        assertThat(stockOf(lampId)).isZero();
    }

    @Test
    void place_unknownProduct_throwsNotFoundAndReservesNothing() {
        assertThatThrownBy(() -> service.place(ALICE, "k1", request(item(lampId, 1), item(999_999L, 1))))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product 999999 not found");

        assertThat(stockOf(lampId)).isEqualTo(5);
    }

    @Test
    void place_cachedProduct_evictsSoNextGetShowsNewStock() {
        assertThat(productService.get(lampId).stock()).isEqualTo(5);

        service.place(ALICE, "k1", request(item(lampId, 2)));

        assertThat(productService.get(lampId).stock()).isEqualTo(3);
    }

    @Test
    void cancel_placedOrder_restoresStock() {
        OrderResponse order = service.place(ALICE, "k1", request(item(lampId, 2), item(bookId, 1))).order();

        OrderResponse cancelled = service.cancel(order.id(), ALICE);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(lampId)).isEqualTo(5);
        assertThat(stockOf(bookId)).isEqualTo(1);
    }

    @Test
    void cancel_twice_restoresOnce() {
        OrderResponse order = service.place(ALICE, "k1", request(item(lampId, 2))).order();
        service.cancel(order.id(), ALICE);

        assertThatThrownBy(() -> service.cancel(order.id(), ALICE))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Order " + order.id() + " is already cancelled");
        assertThat(stockOf(lampId)).isEqualTo(5);
    }

    @Test
    void cancel_otherCustomersOrder_throwsNotFoundAndKeepsStock() {
        OrderResponse order = service.place(ALICE, "k1", request(item(lampId, 2))).order();

        assertThatThrownBy(() -> service.cancel(order.id(), "mallory@example.com"))
                .isInstanceOf(NotFoundException.class);
        assertThat(service.get(order.id(), ALICE).status()).isEqualTo(OrderStatus.PLACED);
        assertThat(stockOf(lampId)).isEqualTo(3);
    }

    private int stockOf(Long productId) {
        return products.findById(productId).orElseThrow().getStock();
    }

    private static Product product(String name, String price, int stock) {
        return new Product(name, "home", new BigDecimal(price), stock, new BigDecimal("4.0"));
    }

    private static OrderItemRequest item(Long productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }

    private static OrderRequest request(OrderItemRequest... items) {
        return new OrderRequest(List.of(items));
    }
}
