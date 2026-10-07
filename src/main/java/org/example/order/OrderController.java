package org.example.order;

import jakarta.validation.Valid;
import org.example.common.exception.BadRequestException;
import org.example.order.dto.OrderRequest;
import org.example.order.dto.OrderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 64;

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** The customer is the token's subject (authentication.getName()), never a value from the body. */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                               @Valid @RequestBody OrderRequest request,
                                               Authentication authentication) {
        requireValidKey(idempotencyKey);
        OrderResponse order = service.place(authentication.getName(), idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, authentication.getName());
    }

    private static void requireValidKey(String key) {
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException(IDEMPOTENCY_KEY, "must be 1 to " + MAX_KEY_LENGTH + " characters");
        }
    }
}
