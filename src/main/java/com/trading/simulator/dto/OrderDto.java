package com.trading.simulator.dto;

import com.trading.simulator.model.OrderSide;
import com.trading.simulator.model.OrderStatus;
import com.trading.simulator.model.OrderType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private Long id;
    private String symbol;
    private OrderType type;
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
