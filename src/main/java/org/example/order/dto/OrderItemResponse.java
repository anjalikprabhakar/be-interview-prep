package org.example.order.dto;

import org.example.order.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, int quantity, BigDecimal unitPrice) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getQuantity(), item.getUnitPrice());
    }
}
