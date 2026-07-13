package com.trading.simulator.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockHistoryPointDto {
    private Instant timestamp;
    private BigDecimal price;
}
