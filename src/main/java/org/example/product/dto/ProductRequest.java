package org.example.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** PUT body (full replace). Limits mirror the columns: DECIMAL(10,2) price, DECIMAL(2,1) rating. */
public record ProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotBlank(message = "category is required")
        @Size(max = 50, message = "category must be at most 50 characters")
        String category,

        @NotNull(message = "price is required")
        @PositiveOrZero(message = "price must be zero or more")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 digits and 2 decimals")
        BigDecimal price,

        @NotNull(message = "stock is required")
        @PositiveOrZero(message = "stock must be zero or more")
        Integer stock,

        @NotNull(message = "rating is required")
        @PositiveOrZero(message = "rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "rating must be between 0 and 5")
        @Digits(integer = 1, fraction = 1, message = "rating must have at most 1 decimal")
        BigDecimal rating
) {
}
