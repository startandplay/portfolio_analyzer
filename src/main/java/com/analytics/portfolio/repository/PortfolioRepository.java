package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByNameContainingIgnoreCase(String name);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.positions WHERE p.id = :id")
    Optional<Portfolio> findByIdWithPositions(Long id);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.transactions WHERE p.id = :id")
    Optional<Portfolio> findByIdWithTransactions(Long id);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.dividends WHERE p.id = :id")
    Optional<Portfolio> findByIdWithDividends(Long id);
}
