package org.example.order;

import org.example.order.dto.OrderResponse;

/** created = false means the request was a retry and the order is the one stored the first time. */
public record PlaceOrderResult(OrderResponse order, boolean created) {
}
