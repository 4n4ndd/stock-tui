package com.trading.simulator.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioAssetDto {
    private String symbol;
    private String name;
    private BigDecimal quantity;
    private BigDecimal costBasis;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercentage;
}
