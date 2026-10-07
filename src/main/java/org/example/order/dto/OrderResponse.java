package org.example.order.dto;

import org.example.order.Order;
import org.example.order.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
