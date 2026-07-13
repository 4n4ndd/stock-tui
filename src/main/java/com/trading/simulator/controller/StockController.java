package com.trading.simulator.controller;

import com.trading.simulator.dto.StockDto;
import com.trading.simulator.dto.StockHistoryPointDto;
import com.trading.simulator.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping
    public ResponseEntity<List<StockDto>> getAllStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<StockDto> getStockBySymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getStockBySymbol(symbol));
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<StockHistoryPointDto>> getStockHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getStockHistory(symbol));
    }
}
