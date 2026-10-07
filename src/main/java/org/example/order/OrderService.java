package org.example.order;

import org.example.common.exception.NotFoundException;
import org.example.order.dto.OrderItemRequest;
import org.example.order.dto.OrderRequest;
import org.example.order.dto.OrderResponse;
import org.example.product.Product;
import org.example.product.ProductRepository;
import org.example.product.ProductService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final TransactionTemplate transaction;
    private final CacheManager cacheManager;

    public OrderService(OrderRepository orders, ProductRepository products,
                        TransactionTemplate transaction, CacheManager cacheManager) {
        this.orders = orders;
        this.products = products;
        this.transaction = transaction;
        this.cacheManager = cacheManager;
    }

    /**
     * The transaction is opened explicitly (TransactionTemplate) instead of with @Transactional, so this
     * method can act after the commit: the Q4 product cache is evicted only once the new stock is visible.
     */
    public OrderResponse place(String customer, String idempotencyKey, OrderRequest request) {
        SortedMap<Long, Integer> quantities = mergeByProduct(request);
        OrderResponse order = transaction.execute(status -> placeInTransaction(customer, idempotencyKey, quantities));
        evictProducts(quantities.keySet());
        return order;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, String customer) {
        return OrderResponse.from(findOrder(id, customer));
    }

    private OrderResponse placeInTransaction(String customer, String idempotencyKey,
                                             SortedMap<Long, Integer> quantities) {
        Map<Long, Product> catalog = products.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : quantities.keySet()) {
            if (!catalog.containsKey(productId)) {
                throw new NotFoundException("Product " + productId + " not found");
            }
        }

        Order order = new Order(customer, idempotencyKey);
        // Ascending productId (SortedMap): every order locks rows in the same sequence, so two orders
        // for products A and B can't each hold one row lock and wait for the other (deadlock).
        quantities.forEach((productId, quantity) -> {
            if (products.reserve(productId, quantity) == 0) {
                // Rolls back the whole transaction, including items reserved earlier in this loop.
                throw new InsufficientStockException(productId, quantity);
            }
            BigDecimal price = catalog.get(productId).getPrice();
            order.addItem(new OrderItem(productId, quantity, price));
        });
        return OrderResponse.from(orders.save(order));
    }

    private Order findOrder(Long id, String customer) {
        return orders.findByIdAndCustomer(id, customer)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    /** Two lines for the same product become one, so its stock is checked against the total quantity. */
    private static SortedMap<Long, Integer> mergeByProduct(OrderRequest request) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    /** The stock changed without going through ProductService, so drop the stale cached products. */
    private void evictProducts(Collection<Long> productIds) {
        Cache cache = cacheManager.getCache(ProductService.CACHE);
        if (cache != null) {
            productIds.forEach(cache::evict);
        }
    }
}
