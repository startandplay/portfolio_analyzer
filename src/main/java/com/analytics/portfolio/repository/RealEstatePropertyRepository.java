package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.PropertyStrategy;
import com.analytics.portfolio.model.RealEstateProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RealEstatePropertyRepository extends JpaRepository<RealEstateProperty, Long> {
    List<RealEstateProperty> findByPortfolioId(Long portfolioId);
    List<RealEstateProperty> findByPortfolioIdAndStrategy(Long portfolioId, PropertyStrategy strategy);
}
