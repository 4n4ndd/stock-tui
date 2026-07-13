package com.trading.simulator.service;

import com.trading.simulator.dto.OrderDto;
import com.trading.simulator.dto.PortfolioDto;
import com.trading.simulator.exception.CustomException;
import com.trading.simulator.model.*;
import com.trading.simulator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final StockRepository stockRepository;
    private final TransactionRepository transactionRepository;
    private final StockService stockService;
    private final PortfolioService portfolioService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public OrderDto placeMarketOrder(Long userId, String symbol, OrderSide side, BigDecimal quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new CustomException("Stock not found", HttpStatus.NOT_FOUND));
        BigDecimal price = stockService.getCachedPrice(symbol);
        Order order = Order.builder()
                .user(user)
                .symbol(symbol.toUpperCase())
                .type(OrderType.MARKET)
                .side(side)
                .quantity(quantity)
                .price(price)
                .status(OrderStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();
        executeOrderLogic(user.getPortfolio(), stock, side, quantity, price);
        Order saved = orderRepository.save(order);
        portfolioService.evictPortfolioCache(userId);
        portfolioService.savePortfolioSnapshot(userId);
        pushPortfolioUpdate(user.getUsername(), userId);
        return mapToDto(saved);
    }

    @Transactional
    public OrderDto placeLimitOrder(Long userId, String symbol, OrderSide side, BigDecimal quantity, BigDecimal limitPrice) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new CustomException("Stock not found", HttpStatus.NOT_FOUND));
        Order order = Order.builder()
                .user(user)
                .symbol(symbol.toUpperCase())
                .type(OrderType.LIMIT)
                .side(side)
                .quantity(quantity)
                .price(limitPrice)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        if (side == OrderSide.BUY) {
            BigDecimal totalCost = quantity.multiply(limitPrice);
            if (user.getPortfolio().getCashBalance().compareTo(totalCost) < 0) {
                throw new CustomException("Insufficient cash balance", HttpStatus.BAD_REQUEST);
            }
        } else {
            PortfolioAsset asset = portfolioAssetRepository.findByPortfolioIdAndStockSymbol(user.getPortfolio().getId(), symbol.toUpperCase())
                    .orElseThrow(() -> new CustomException("You do not own shares of " + symbol, HttpStatus.BAD_REQUEST));
            BigDecimal pendingSellQty = getPendingOrderQuantity(userId, symbol.toUpperCase(), OrderSide.SELL);
            BigDecimal availableQty = asset.getQuantity().subtract(pendingSellQty);
            if (availableQty.compareTo(quantity) < 0) {
                throw new CustomException("Insufficient shares available to sell", HttpStatus.BAD_REQUEST);
            }
        }
        Order saved = orderRepository.save(order);
        portfolioService.evictPortfolioCache(userId);
        return mapToDto(saved);
    }

    @Transactional
    public OrderDto placeStopLossOrder(Long userId, String symbol, OrderSide side, BigDecimal quantity, BigDecimal stopPrice) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new CustomException("Stock not found", HttpStatus.NOT_FOUND));
        Order order = Order.builder()
                .user(user)
                .symbol(symbol.toUpperCase())
                .type(OrderType.STOP_LOSS)
                .side(side)
                .quantity(quantity)
                .price(stopPrice)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        if (side == OrderSide.BUY) {
            BigDecimal totalCost = quantity.multiply(stopPrice);
            if (user.getPortfolio().getCashBalance().compareTo(totalCost) < 0) {
                throw new CustomException("Insufficient cash balance", HttpStatus.BAD_REQUEST);
            }
        } else {
            PortfolioAsset asset = portfolioAssetRepository.findByPortfolioIdAndStockSymbol(user.getPortfolio().getId(), symbol.toUpperCase())
                    .orElseThrow(() -> new CustomException("You do not own shares of " + symbol, HttpStatus.BAD_REQUEST));
            BigDecimal pendingSellQty = getPendingOrderQuantity(userId, symbol.toUpperCase(), OrderSide.SELL);
            BigDecimal availableQty = asset.getQuantity().subtract(pendingSellQty);
            if (availableQty.compareTo(quantity) < 0) {
                throw new CustomException("Insufficient shares available to sell", HttpStatus.BAD_REQUEST);
            }
        }
        Order saved = orderRepository.save(order);
        portfolioService.evictPortfolioCache(userId);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        List<OrderDto> dtos = new ArrayList<>();
        for (Order o : orders) {
            dtos.add(mapToDto(o));
        }
        return dtos;
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        if (!order.getUser().getId().equals(userId)) {
            throw new CustomException("Unauthorized operation", HttpStatus.UNAUTHORIZED);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CustomException("Order cannot be cancelled in its current state", HttpStatus.BAD_REQUEST);
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        portfolioService.evictPortfolioCache(userId);
    }

    @Transactional
    public void processPendingOrders() {
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
        for (Order order : pendingOrders) {
            try {
                Stock stock = stockRepository.findBySymbol(order.getSymbol())
                        .orElseThrow(() -> new CustomException("Stock not found", HttpStatus.NOT_FOUND));
                BigDecimal currentPrice = stock.getCurrentPrice();
                boolean shouldExecute = false;
                if (order.getType() == OrderType.LIMIT) {
                    if (order.getSide() == OrderSide.BUY && currentPrice.compareTo(order.getPrice()) <= 0) {
                        shouldExecute = true;
                    } else if (order.getSide() == OrderSide.SELL && currentPrice.compareTo(order.getPrice()) >= 0) {
                        shouldExecute = true;
                    }
                } else if (order.getType() == OrderType.STOP_LOSS) {
                    if (order.getSide() == OrderSide.BUY && currentPrice.compareTo(order.getPrice()) >= 0) {
                        shouldExecute = true;
                    } else if (order.getSide() == OrderSide.SELL && currentPrice.compareTo(order.getPrice()) <= 0) {
                        shouldExecute = true;
                    }
                }
                if (shouldExecute) {
                    executeOrder(order, currentPrice);
                }
            } catch (Exception e) {
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
            }
        }
    }

    private void executeOrder(Order order, BigDecimal price) {
        Portfolio portfolio = order.getUser().getPortfolio();
        Stock stock = stockRepository.findBySymbol(order.getSymbol()).orElseThrow();
        executeOrderLogic(portfolio, stock, order.getSide(), order.getQuantity(), price);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPrice(price);
        orderRepository.save(order);
        portfolioService.evictPortfolioCache(order.getUser().getId());
        portfolioService.savePortfolioSnapshot(order.getUser().getId());
        pushPortfolioUpdate(order.getUser().getUsername(), order.getUser().getId());
    }

    private void executeOrderLogic(Portfolio portfolio, Stock stock, OrderSide side, BigDecimal quantity, BigDecimal price) {
        BigDecimal totalAmount = quantity.multiply(price);
        if (side == OrderSide.BUY) {
            if (portfolio.getCashBalance().compareTo(totalAmount) < 0) {
                throw new CustomException("Insufficient funds for trade", HttpStatus.BAD_REQUEST);
            }
            portfolio.setCashBalance(portfolio.getCashBalance().subtract(totalAmount));
            PortfolioAsset asset = portfolioAssetRepository.findByPortfolioIdAndStockSymbol(portfolio.getId(), stock.getSymbol()).orElse(null);
            if (asset != null) {
                BigDecimal oldTotalCost = asset.getQuantity().multiply(asset.getCostBasis());
                BigDecimal newQuantity = asset.getQuantity().add(quantity);
                BigDecimal newTotalCost = oldTotalCost.add(totalAmount);
                BigDecimal newCostBasis = newTotalCost.divide(newQuantity, 4, RoundingMode.HALF_UP);
                asset.setQuantity(newQuantity);
                asset.setCostBasis(newCostBasis);
                portfolioAssetRepository.save(asset);
            } else {
                PortfolioAsset newAsset = PortfolioAsset.builder()
                        .portfolio(portfolio)
                        .stock(stock)
                        .quantity(quantity)
                        .costBasis(price)
                        .build();
                portfolioAssetRepository.save(newAsset);
            }
        } else {
            PortfolioAsset asset = portfolioAssetRepository.findByPortfolioIdAndStockSymbol(portfolio.getId(), stock.getSymbol())
                    .orElseThrow(() -> new CustomException("You do not own shares of " + stock.getSymbol(), HttpStatus.BAD_REQUEST));
            if (asset.getQuantity().compareTo(quantity) < 0) {
                throw new CustomException("Insufficient shares available", HttpStatus.BAD_REQUEST);
            }
            portfolio.setCashBalance(portfolio.getCashBalance().add(totalAmount));
            BigDecimal remainingQty = asset.getQuantity().subtract(quantity);
            if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
                portfolioAssetRepository.delete(asset);
            } else {
                asset.setQuantity(remainingQty);
                portfolioAssetRepository.save(asset);
            }
        }
        portfolioRepository.save(portfolio);
        Transaction transaction = Transaction.builder()
                .user(portfolio.getUser())
                .symbol(stock.getSymbol())
                .type(side == OrderSide.BUY ? TransactionType.BUY : TransactionType.SELL)
                .quantity(quantity)
                .price(price)
                .totalAmount(totalAmount)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);
    }

    private BigDecimal getPendingOrderQuantity(Long userId, String symbol, OrderSide side) {
        List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING);
        BigDecimal total = BigDecimal.ZERO;
        for (Order o : pendingOrders) {
            if (o.getSymbol().equalsIgnoreCase(symbol) && o.getSide() == side) {
                total = total.add(o.getQuantity());
            }
        }
        return total;
    }

    private void pushPortfolioUpdate(String username, Long userId) {
        try {
            PortfolioDto portfolioDto = portfolioService.getPortfolioDtoByUserId(userId);
            messagingTemplate.convertAndSendToUser(username, "/queue/portfolio", portfolioDto);
        } catch (Exception e) {
        }
    }

    private OrderDto mapToDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .symbol(order.getSymbol())
                .type(order.getType())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
