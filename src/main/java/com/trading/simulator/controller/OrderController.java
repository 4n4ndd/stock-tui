package com.trading.simulator.controller;

import com.trading.simulator.dto.*;
import com.trading.simulator.security.UserPrincipal;
import com.trading.simulator.service.TradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final TradingService tradingService;

    @PostMapping("/market")
    public ResponseEntity<OrderDto> placeMarketOrder(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MarketOrderRequest request) {
        OrderDto order = tradingService.placeMarketOrder(
                userPrincipal.getId(),
                request.getSymbol(),
                request.getSide(),
                request.getQuantity()
        );
        return ResponseEntity.ok(order);
    }

    @PostMapping("/limit")
    public ResponseEntity<OrderDto> placeLimitOrder(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody LimitOrderRequest request) {
        OrderDto order = tradingService.placeLimitOrder(
                userPrincipal.getId(),
                request.getSymbol(),
                request.getSide(),
                request.getQuantity(),
                request.getPrice()
        );
        return ResponseEntity.ok(order);
    }

    @PostMapping("/stoploss")
    public ResponseEntity<OrderDto> placeStopLossOrder(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody StopLossOrderRequest request) {
        OrderDto order = tradingService.placeStopLossOrder(
                userPrincipal.getId(),
                request.getSymbol(),
                request.getSide(),
                request.getQuantity(),
                request.getPrice()
        );
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getOrders(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(tradingService.getOrders(userPrincipal.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelOrder(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        tradingService.cancelOrder(userPrincipal.getId(), id);
        return ResponseEntity.ok("Order cancelled successfully");
    }
}
