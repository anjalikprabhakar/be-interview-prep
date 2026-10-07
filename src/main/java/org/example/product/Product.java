package org.example.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    // BigDecimal, never double: 0.1 + 0.2 must be exactly 0.3 for money.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String name, String category, BigDecimal price, int stock, BigDecimal rating) {
        replace(name, category, price, stock, rating);
    }

    @PrePersist
    void onCreate() {
        // Truncate to the column precision (microseconds) so the value returned on create
        // matches what is later read back from the database.
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void replace(String name, String category, BigDecimal price, int stock, BigDecimal rating) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.rating = rating;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
