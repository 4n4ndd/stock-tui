package com.trading.simulator.scheduler;

import com.trading.simulator.dto.StockDto;
import com.trading.simulator.service.StockService;
import com.trading.simulator.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockService stockService;
    private final TradingService tradingService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 5000)
    public void refreshStockPrices() {
        stockService.syncExternalPrices();
        tradingService.processPendingOrders();
        List<StockDto> stocks = stockService.getAllStocks();
        messagingTemplate.convertAndSend("/topic/prices", stocks);
    }
}
