package com.trading.simulator.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioPerformanceDto {
    private List<PerformancePoint> points;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PerformancePoint {
        private LocalDateTime timestamp;
        private BigDecimal value;
    }
}
