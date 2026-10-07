package org.example.order;

import org.example.common.exception.ConflictException;

/**
 * Unchecked on purpose: Spring rolls a transaction back only for RuntimeException/Error by default,
 * so throwing this undoes every item already reserved in the same order (all-or-nothing). Mapped to 409.
 */
public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(Long productId, int requested) {
        super("Insufficient stock for product " + productId + " (requested " + requested + ")");
    }
}
