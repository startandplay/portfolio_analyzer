package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    // ── Queries filtradas por user ────────────────────────────────
    // NOTA: usamos @Query explícito (p.user.id) em vez de métodos derivados
    // (findByUserId...) porque a derivação automática do Spring Data
    // falha a resolver 'userId' caso a entidade Portfolio não exponha
    // esse atributo diretamente (o campo real é 'user', do tipo User).
    // JPQL explícito com 'p.user.id' navega a relação sem ambiguidade.

    /**
     * Todos os portfolios de um utilizador, ordenados por nome.
     */
    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :userId ORDER BY p.name ASC")
    List<Portfolio> findByUserIdOrderByNameAsc(@Param("userId") Long userId);

    /**
     * Portfolios de um utilizador para uma source específica (ex: todos XTB).
     */
    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :userId AND p.source = :source")
    List<Portfolio> findByUserIdAndSource(@Param("userId") Long userId,
                                          @Param("source") PortfolioSource source);

    /**
     * Portfolios incluídos na agregação global do utilizador.
     */
    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :userId AND p.includeInAggregate = true")
    List<Portfolio> findByUserIdAndIncludeInAggregateTrue(@Param("userId") Long userId);

    /**
     * Busca portfolio por ID garantindo que pertence ao utilizador.
     * Essencial para evitar acesso cross-user (IDOR).
     */
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Verifica se um portfolio pertence a um utilizador.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM Portfolio p WHERE p.id = :id AND p.user.id = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Verifica se o utilizador já tem um portfolio com este nome.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM Portfolio p WHERE p.user.id = :userId AND p.name = :name")
    boolean existsByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * Verifica se o utilizador já tem um portfolio com esta source.
     * Útil para prevenir duplicados (ex: dois portfolios XTB para o mesmo user).
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM Portfolio p WHERE p.user.id = :userId AND p.source = :source")
    boolean existsByUserIdAndSource(@Param("userId") Long userId,
                                    @Param("source") PortfolioSource source);

    // ── Queries com fetch (evitam N+1) ───────────────────────────

    @Query("SELECT p FROM Portfolio p " +
            "LEFT JOIN FETCH p.positions " +
            "WHERE p.id = :id AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserIdWithPositions(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    @Query("SELECT p FROM Portfolio p " +
            "LEFT JOIN FETCH p.transactions " +
            "WHERE p.id = :id AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserIdWithTransactions(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    // ── Queries de agregação ──────────────────────────────────────

    /**
     * Conta quantos portfolios o utilizador tem por source.
     * Devolve: [source (String), count]
     */
    @Query("SELECT CAST(p.source AS string), COUNT(p) FROM Portfolio p " +
            "WHERE p.user.id = :userId " +
            "GROUP BY p.source")
    List<Object[]> countBySourceForUser(@Param("userId") Long userId);

    /**
     * Soma do capital inicial investido em todos os portfolios do utilizador.
     */
    @Query("SELECT COALESCE(SUM(p.initialCapital), 0) FROM Portfolio p " +
            "WHERE p.user.id = :userId AND p.includeInAggregate = true")
    java.math.BigDecimal getTotalInitialCapitalForUser(@Param("userId") Long userId);

    // ── Queries legacy (sem user — mantidas para compatibilidade) ─

    List<Portfolio> findByNameContainingIgnoreCase(String name);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.positions WHERE p.id = :id")
    Optional<Portfolio> findByIdWithPositions(Long id);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.transactions WHERE p.id = :id")
    Optional<Portfolio> findByIdWithTransactions(Long id);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.dividends WHERE p.id = :id")
    Optional<Portfolio> findByIdWithDividends(Long id);
}