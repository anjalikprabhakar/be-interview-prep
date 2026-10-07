package org.example.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Check and decrement in ONE statement: the row lock makes a concurrent order wait, then re-check
     * stock >= qty against the committed value, so stock can never be oversold or go negative.
     * Returns the rows updated: 1 = reserved, 0 = not enough stock.
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int reserve(Long id, int qty);

    /** Gives stock back (on cancel). Also a single statement, so it cannot lose a concurrent update. */
    @Modifying
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int release(Long id, int qty);
}
