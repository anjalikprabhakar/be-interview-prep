package org.example.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull Long productId,
        // The upper bound keeps the merged quantity of duplicate lines far from int overflow.
        @NotNull @Positive @Max(1000) Integer quantity
) {
}
