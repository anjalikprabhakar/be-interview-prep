package org.example.shortener;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    /**
     * Increments inside the database in one statement, so concurrent visits can't overwrite each other
     * the way a read-then-save in Java would (both threads read 5, both write 6).
     */
    @Modifying
    @Query("update ShortLink s set s.visitCount = s.visitCount + 1 where s.id = :id")
    int incrementVisits(@Param("id") Long id);
}
