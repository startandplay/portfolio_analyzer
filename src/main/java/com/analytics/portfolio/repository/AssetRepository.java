package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.AssetSource;
import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findBySymbol(String symbol);
    Optional<Asset> findByTicker(String ticker);
    List<Asset> findByType(AssetType type);

    @Query("SELECT DISTINCT a.ticker FROM Asset a ORDER BY a.ticker")
    List<String> findAllTickers();


    /**
     * Busca todos os símbolos (tickers) únicos da tabela assets
     * @return Lista de símbolos
     */
    @Query("SELECT DISTINCT a.symbol FROM Asset a ORDER BY a.symbol")
    List<String> findAllSymbols();

    /**
     * Busca símbolos de assets que têm positions ativas
     * @return Lista de símbolos com positions
     */
    @Query("SELECT DISTINCT a.symbol FROM Asset a " +
            "WHERE EXISTS (SELECT 1 FROM Position p WHERE p.asset.id = a.id AND p.quantity > 0)")
    List<String> findSymbolsWithActivePositions();

    List<Asset> findBySource(AssetSource source);

    /**
     * Busca símbolos de assets de um portfolio específico
     * @param portfolioId ID do portfolio
     * @return Lista de símbolos
     */
    @Query("SELECT DISTINCT a.symbol FROM Asset a " +
            "JOIN Position p ON p.asset.id = a.id " +
            "WHERE p.portfolio.id = :portfolioId AND p.quantity > 0 " +
            "ORDER BY a.symbol")
    List<String> findSymbolsByPortfolioId(@Param("portfolioId") Long portfolioId);

    @Query("SELECT DISTINCT a.ticker FROM Asset a " +
            "JOIN Position p ON p.asset.id = a.id " +
            "WHERE p.portfolio.id = :portfolioId AND p.quantity > 0 " +
            "ORDER BY a.ticker")
    List<String> findTickersByPortfolioId(@Param("portfolioId") Long portfolioId);


}

