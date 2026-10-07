package org.example.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Scoped to the customer: another user's order is reported as not found. */
    Optional<Order> findByIdAndCustomer(Long id, String customer);

    Optional<Order> findByCustomerAndIdempotencyKey(String customer, String idempotencyKey);

    /**
     * Only a PLACED order moves to CANCELLED, in one statement: of two concurrent cancels exactly one gets 1,
     * so stock is returned once. clearAutomatically drops the now-stale entity from the persistence context.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = org.example.order.OrderStatus.CANCELLED "
            + "where o.id = :id and o.customer = :customer and o.status = org.example.order.OrderStatus.PLACED")
    int cancel(Long id, String customer);
}
