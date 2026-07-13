package com.trading.simulator.controller;

import com.trading.simulator.dto.PortfolioDto;
import com.trading.simulator.dto.PortfolioPerformanceDto;
import com.trading.simulator.dto.TransactionDto;
import com.trading.simulator.security.UserPrincipal;
import com.trading.simulator.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioDto> getPortfolio(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(portfolioService.getPortfolioDtoByUserId(userPrincipal.getId()));
    }

    @GetMapping("/portfolio/performance")
    public ResponseEntity<PortfolioPerformanceDto> getPortfolioPerformance(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(portfolioService.getPortfolioPerformance(userPrincipal.getId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactions(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(portfolioService.getTransactionHistory(userPrincipal.getId()));
    }
}
