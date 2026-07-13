package com.trading.simulator.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.trading.simulator.dto.StockDto;
import com.trading.simulator.dto.StockHistoryPointDto;
import com.trading.simulator.exception.CustomException;
import com.trading.simulator.model.Stock;
import com.trading.simulator.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final InfluxDBClient influxDBClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    @Value("${app.finnhub.api-key}")
    private String finnhubApiKey;

    private static final String PRICE_CACHE_KEY_PREFIX = "stock:price:";

    @Transactional
    public List<StockDto> getAllStocks() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            stocks = seedDefaultStocks();
        }
        List<StockDto> dtos = new ArrayList<>();
        for (Stock stock : stocks) {
            dtos.add(mapToDto(stock));
        }
        return dtos;
    }

    @Transactional
    public StockDto getStockBySymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new CustomException("Stock not found with symbol: " + symbol, HttpStatus.NOT_FOUND));
        return mapToDto(stock);
    }

    @Transactional
    public Stock updateStockPrice(String symbol, BigDecimal price) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new CustomException("Stock not found: " + symbol, HttpStatus.NOT_FOUND));
        stock.setCurrentPrice(price);
        stock.setLastUpdated(LocalDateTime.now());
        Stock saved = stockRepository.save(stock);
        redisTemplate.opsForValue().set(PRICE_CACHE_KEY_PREFIX + symbol, price.toString(), 1, TimeUnit.HOURS);
        writePriceToInfluxDB(symbol, price);
        return saved;
    }

    public BigDecimal getCachedPrice(String symbol) {
        String cacheKey = PRICE_CACHE_KEY_PREFIX + symbol.toUpperCase();
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            return new BigDecimal(cachedValue.toString());
        }
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new CustomException("Stock not found: " + symbol, HttpStatus.NOT_FOUND));
        redisTemplate.opsForValue().set(cacheKey, stock.getCurrentPrice().toString(), 1, TimeUnit.HOURS);
        return stock.getCurrentPrice();
    }

    public List<StockHistoryPointDto> getStockHistory(String symbol) {
        String query = String.format(
                "from(bucket: \"%s\") |> range(start: -30d) |> filter(fn: (r) => r[\"_measurement\"] == \"stock_price\") |> filter(fn: (r) => r[\"symbol\"] == \"%s\")",
                bucket, symbol.toUpperCase()
        );
        List<FluxTable> tables;
        try {
            tables = influxDBClient.getQueryApi().query(query, org);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        List<StockHistoryPointDto> history = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Double val = (Double) record.getValueByKey("_value");
                if (val != null) {
                    history.add(StockHistoryPointDto.builder()
                            .timestamp(record.getTime())
                            .price(BigDecimal.valueOf(val))
                            .build());
                }
            }
        }
        history.sort(Comparator.comparing(StockHistoryPointDto::getTimestamp));
        return history;
    }

    public void syncExternalPrices() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            stocks = seedDefaultStocks();
        }
        for (Stock stock : stocks) {
            try {
                BigDecimal price = fetchPriceFromFinnhub(stock.getSymbol());
                if (price != null) {
                    updateStockPrice(stock.getSymbol(), price);
                } else {
                    simulatePriceUpdate(stock);
                }
            } catch (Exception e) {
                simulatePriceUpdate(stock);
            }
        }
    }

    private void simulatePriceUpdate(Stock stock) {
        double changePercent = (new Random().nextDouble() * 2.0 - 1.0) * 0.005;
        BigDecimal multiplier = BigDecimal.valueOf(1.0 + changePercent);
        BigDecimal newPrice = stock.getCurrentPrice().multiply(multiplier).setScale(4, BigDecimal.ROUND_HALF_UP);
        updateStockPrice(stock.getSymbol(), newPrice);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal fetchPriceFromFinnhub(String symbol) {
        if (finnhubApiKey == null || finnhubApiKey.trim().isEmpty()) {
            return null;
        }
        String url = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", symbol, finnhubApiKey);
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response != null && response.containsKey("c")) {
            Object currentPriceObj = response.get("c");
            if (currentPriceObj != null) {
                double price = Double.parseDouble(currentPriceObj.toString());
                if (price > 0.0) {
                    return BigDecimal.valueOf(price);
                }
            }
        }
        return null;
    }

    private void writePriceToInfluxDB(String symbol, BigDecimal price) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            Point point = Point.measurement("stock_price")
                    .addTag("symbol", symbol)
                    .addField("price", price.doubleValue())
                    .time(Instant.now(), WritePrecision.NS);
            writeApi.writePoint(bucket, org, point);
        } catch (Exception e) {
        }
    }

    private List<Stock> seedDefaultStocks() {
        List<Stock> defaults = Arrays.asList(
                Stock.builder().symbol("AAPL").name("Apple Inc.").currentPrice(new BigDecimal("175.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("MSFT").name("Microsoft Corporation").currentPrice(new BigDecimal("400.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("GOOGL").name("Alphabet Inc.").currentPrice(new BigDecimal("150.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("AMZN").name("Amazon.com Inc.").currentPrice(new BigDecimal("180.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("TSLA").name("Tesla Inc.").currentPrice(new BigDecimal("170.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("NFLX").name("Netflix Inc.").currentPrice(new BigDecimal("600.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("NVDA").name("NVIDIA Corporation").currentPrice(new BigDecimal("800.0000")).lastUpdated(LocalDateTime.now()).build(),
                Stock.builder().symbol("META").name("Meta Platforms Inc.").currentPrice(new BigDecimal("480.0000")).lastUpdated(LocalDateTime.now()).build()
        );
        List<Stock> saved = stockRepository.saveAll(defaults);
        for (Stock stock : saved) {
            redisTemplate.opsForValue().set(PRICE_CACHE_KEY_PREFIX + stock.getSymbol(), stock.getCurrentPrice().toString(), 1, TimeUnit.HOURS);
            writePriceToInfluxDB(stock.getSymbol(), stock.getCurrentPrice());
        }
        return saved;
    }

    private StockDto mapToDto(Stock stock) {
        return StockDto.builder()
                .id(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .currentPrice(stock.getCurrentPrice())
                .lastUpdated(stock.getLastUpdated())
                .build();
    }
}
