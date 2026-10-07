package org.example.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    // A plain id rather than a @ManyToOne Product: the order only needs to know which product, and the
    // price is copied below so later price changes don't rewrite past orders.
    @Column(nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    public OrderItem(Long productId, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
