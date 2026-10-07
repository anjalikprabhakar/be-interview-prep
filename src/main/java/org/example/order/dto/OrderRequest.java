package org.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderRequest(
        @NotEmpty List<@NotNull @Valid OrderItemRequest> items
) {
}
