package com.enterprise.testing.datagen.generators.trade;

import com.enterprise.testing.datagen.market.MarketDataProvider;
import com.enterprise.testing.shared.model.trade.*;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Generates realistic Trade data using REAL market prices from Yahoo Finance.
 *
 * HOW THIS DIFFERS FROM THE ORDER GENERATOR:
 * - Orders use static product catalogs with fixed prices
 * - Trades use live market data: real symbols, real prices, real bid/ask spreads
 *
 * THE HYBRID APPROACH:
 *   Real data (from Yahoo Finance):
 *     - Symbol, exchange, current price, bid/ask, volume
 *   Synthetic data (from generators):
 *     - Account IDs, quantities, order types, trade timing
 *     - Settlement details, counterparties, clearing info
 *
 * This gives you test data that passes validation against real market
 * constraints (prices within day range, spreads realistic) while still
 * having controllable, reproducible synthetic elements.
 *
 * USAGE:
 *   MarketDataProvider marketData = new MarketDataProvider();
 *   TradeGenerator generator = new TradeGenerator(marketData);
 *
 *   // Single trade at real market price
 *   Trade trade = generator.generate("AAPL");
 *
 *   // Batch of trades across your watchlist
 *   List<Trade> trades = generator.generateBatch(
 *       List.of("BTC-USD", "AAPL", "TSLA"), 5
 *   );
 */
public class TradeGenerator {

    private final MarketDataProvider marketData;
    private final Faker faker;
    private final Random random;

    // Realistic account prefixes
    private static final String[] ACCOUNT_PREFIXES = {"ACCT", "IRA", "401K", "MRGN", "INST"};

    // Realistic counterparties
    private static final String[] COUNTERPARTIES = {
            "CITADEL-001", "VIRTU-002", "JANE-STREET-003", "TWO-SIGMA-004",
            "JUMP-TRADING-005", "DRW-006", "WOLVERINE-007", "IMC-008"
    };

    public TradeGenerator(MarketDataProvider marketData) {
        this.marketData = marketData;
        this.faker = new Faker();
        this.random = new Random();
    }

    public TradeGenerator(MarketDataProvider marketData, long seed) {
        this.marketData = marketData;
        this.faker = new Faker(new Random(seed));
        this.random = new Random(seed);
    }

    /**
     * Generate a single trade for a symbol using real market data.
     */
    public Trade generate(String symbol) {
        MarketSnapshot snapshot = marketData.getSnapshot(symbol);
        return generateFromSnapshot(snapshot);
    }

    /**
     * Generate a trade from a pre-fetched snapshot (avoids extra API calls).
     */
    public Trade generateFromSnapshot(MarketSnapshot snapshot) {
        Trade trade = new Trade();

        // Real market data
        trade.setSymbol(snapshot.getSymbol());
        trade.setExchange(snapshot.getExchange());
        trade.setMarketBid(snapshot.getBid());
        trade.setMarketAsk(snapshot.getAsk());
        trade.setMarketVolume(snapshot.getVolume());

        // Synthetic trade details
        trade.setSide(randomSide());
        trade.setOrderType(randomOrderType());
        trade.setAccountId(randomAccountId());
        trade.setQuantity(randomQuantity(snapshot));

        // Price derivation: use real market price with realistic slippage
        trade.setPrice(deriveExecutionPrice(snapshot, trade.getSide(), trade.getOrderType()));
        trade.calculateTotalValue();

        // Status: most trades execute successfully
        trade.setStatus(randomStatus());

        return trade;
    }

    /**
     * Generate multiple trades across multiple symbols.
     *
     * @param symbols      List of ticker symbols
     * @param tradesPerSymbol Number of trades to generate per symbol
     */
    public List<Trade> generateBatch(List<String> symbols, int tradesPerSymbol) {
        List<Trade> trades = new ArrayList<>();

        // Pre-fetch all snapshots in one batch
        Map<String, MarketSnapshot> snapshots = marketData.getSnapshots(symbols);

        for (Map.Entry<String, MarketSnapshot> entry : snapshots.entrySet()) {
            for (int i = 0; i < tradesPerSymbol; i++) {
                trades.add(generateFromSnapshot(entry.getValue()));
            }
        }

        return trades;
    }

    /**
     * Generate a correlated pair: a BUY and a SELL for the same symbol
     * (simulates a round-trip trade).
     */
    public List<Trade> generateRoundTrip(String symbol) {
        MarketSnapshot snapshot = marketData.getSnapshot(symbol);
        String accountId = randomAccountId();

        Trade buy = generateFromSnapshot(snapshot);
        buy.setSide(TradeSide.BUY);
        buy.setAccountId(accountId);
        buy.setPrice(deriveExecutionPrice(snapshot, TradeSide.BUY, buy.getOrderType()));
        buy.calculateTotalValue();

        // Sell at a slightly different price (simulates market movement)
        Trade sell = generateFromSnapshot(snapshot);
        sell.setSide(TradeSide.SELL);
        sell.setAccountId(accountId);
        sell.setQuantity(buy.getQuantity()); // Same quantity for round trip
        BigDecimal priceShift = snapshot.getPrice()
                .multiply(BigDecimal.valueOf(random.nextGaussian() * 0.005));
        sell.setPrice(buy.getPrice().add(priceShift).setScale(2, RoundingMode.HALF_UP));
        sell.calculateTotalValue();

        return List.of(buy, sell);
    }

    // ===== PRICE DERIVATION (the key part) =====

    /**
     * Derive a realistic execution price from market data.
     *
     * - BUY orders execute at or near the ASK
     * - SELL orders execute at or near the BID
     * - MARKET orders get slight slippage
     * - LIMIT orders execute at a price between bid and ask
     *
     * This is what makes the data realistic: a buy at $227.50
     * when the ask is $227.65 would fail validation in a real system.
     */
    private BigDecimal deriveExecutionPrice(MarketSnapshot snapshot, TradeSide side, OrderType orderType) {
        BigDecimal bid = snapshot.getBid();
        BigDecimal ask = snapshot.getAsk();
        BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal spread = ask.subtract(bid);

        return switch (orderType) {
            case MARKET -> {
                // Market orders: execute at bid (sell) or ask (buy) with small slippage
                BigDecimal slippage = spread.multiply(
                        BigDecimal.valueOf(random.nextDouble() * 0.3));
                yield side == TradeSide.BUY
                        ? ask.add(slippage).setScale(2, RoundingMode.HALF_UP)
                        : bid.subtract(slippage).setScale(2, RoundingMode.HALF_UP);
            }
            case LIMIT -> {
                // Limit orders: execute between mid and bid/ask
                BigDecimal offset = spread.multiply(
                        BigDecimal.valueOf(random.nextDouble() * 0.5));
                yield side == TradeSide.BUY
                        ? mid.subtract(offset).setScale(2, RoundingMode.HALF_UP)
                        : mid.add(offset).setScale(2, RoundingMode.HALF_UP);
            }
            case STOP, STOP_LIMIT -> {
                // Stop orders: execute at a price slightly worse than market
                BigDecimal stopSlip = spread.multiply(
                        BigDecimal.valueOf(0.5 + random.nextDouble()));
                yield side == TradeSide.BUY
                        ? ask.add(stopSlip).setScale(2, RoundingMode.HALF_UP)
                        : bid.subtract(stopSlip).setScale(2, RoundingMode.HALF_UP);
            }
        };
    }

    // ===== SYNTHETIC FIELD GENERATORS =====

    /**
     * Quantity is influenced by the stock price.
     * Expensive stocks: smaller lots. Cheap stocks / crypto: larger lots.
     */
    private int randomQuantity(MarketSnapshot snapshot) {
        BigDecimal price = snapshot.getPrice();
        if (price == null) return 100;

        double priceVal = price.doubleValue();
        if (priceVal > 10000) return 1 + random.nextInt(3);          // BTC: 1-3
        if (priceVal > 500)   return 5 + random.nextInt(50);         // High-price stocks: 5-54
        if (priceVal > 100)   return 10 + random.nextInt(100);       // Mid-price: 10-109
        if (priceVal > 10)    return 50 + random.nextInt(500);       // Low-price: 50-549
        return 100 + random.nextInt(10000);                           // Penny/micro: 100-10099
    }

    private TradeSide randomSide() {
        return random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
    }

    private OrderType randomOrderType() {
        int roll = random.nextInt(100);
        if (roll < 50) return OrderType.MARKET;
        if (roll < 80) return OrderType.LIMIT;
        if (roll < 95) return OrderType.STOP;
        return OrderType.STOP_LIMIT;
    }

    private String randomAccountId() {
        String prefix = ACCOUNT_PREFIXES[random.nextInt(ACCOUNT_PREFIXES.length)];
        return prefix + "-" + (10000 + random.nextInt(90000));
    }

    private TradeStatus randomStatus() {
        int roll = random.nextInt(100);
        if (roll < 85) return TradeStatus.EXECUTED;
        if (roll < 92) return TradeStatus.PARTIALLY_FILLED;
        if (roll < 97) return TradeStatus.REJECTED;
        return TradeStatus.CANCELLED;
    }

    public String randomCounterparty() {
        return COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
    }
}
