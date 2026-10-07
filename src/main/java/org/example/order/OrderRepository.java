package org.example.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Scoped to the customer: another user's order is reported as not found. */
    Optional<Order> findByIdAndCustomer(Long id, String customer);
}
