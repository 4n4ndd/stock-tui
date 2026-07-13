package com.trading.simulator.dto;

import com.trading.simulator.model.OrderSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketOrderRequest {

    @NotBlank
    private String symbol;

    @NotNull
    private OrderSide side;

    @NotNull
    @Positive
    private BigDecimal quantity;
}
