package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.RawImportStatus;
import com.analytics.portfolio.model.RawImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawImportRecordRepository extends JpaRepository<RawImportRecord, Long> {

    List<RawImportRecord> findByBatchId(String batchId);

    List<RawImportRecord> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    long countByBatchId(String batchId);

    long countByBatchIdAndStatus(String batchId, RawImportStatus status);

    /** Apaga todos os registos de um batch (para desfazer importação) */
    @Modifying
    @Query("DELETE FROM RawImportRecord r WHERE r.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") String batchId);

    /** Lista batches distintos de um portfolio, mais recentes primeiro */
    @Query("SELECT DISTINCT r.batchId FROM RawImportRecord r " +
           "WHERE r.portfolioId = :portfolioId ORDER BY r.batchId DESC")
    List<String> findDistinctBatchIdsByPortfolioId(@Param("portfolioId") Long portfolioId);
}
