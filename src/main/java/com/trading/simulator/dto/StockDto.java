package com.trading.simulator.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDto {
    private Long id;
    private String symbol;
    private String name;
    private BigDecimal currentPrice;
    private LocalDateTime lastUpdated;
}
