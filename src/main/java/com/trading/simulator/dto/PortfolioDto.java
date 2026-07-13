package com.trading.simulator.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioDto {
    private BigDecimal cashBalance;
    private BigDecimal totalAssetValue;
    private BigDecimal totalPortfolioValue;
    private BigDecimal totalProfitLoss;
    private BigDecimal totalProfitLossPercentage;
    private List<PortfolioAssetDto> assets;
}
