package com.trading.simulator.repository;

import com.trading.simulator.model.PortfolioAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, Long> {
    Optional<PortfolioAsset> findByPortfolioIdAndStockSymbol(Long portfolioId, String symbol);
    List<PortfolioAsset> findByPortfolioId(Long portfolioId);
}
