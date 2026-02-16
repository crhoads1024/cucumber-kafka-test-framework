package com.enterprise.testing.datagen;

import com.enterprise.testing.datagen.generators.KafkaEventGenerator;
import com.enterprise.testing.datagen.generators.OrderGenerator;
import com.enterprise.testing.datagen.generators.UserGenerator;
import com.enterprise.testing.datagen.generators.trade.SettlementGenerator;
import com.enterprise.testing.datagen.generators.trade.TradeGenerator;
import com.enterprise.testing.datagen.market.MarketDataProvider;
import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.*;
import com.enterprise.testing.shared.model.trade.MarketSnapshot;
import com.enterprise.testing.shared.model.trade.Settlement;
import com.enterprise.testing.shared.model.trade.Trade;
import com.enterprise.testing.shared.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all generated test data.
 *
 * Supports two domains:
 *   - E-commerce: Users, Orders, KafkaEvents
 *   - Financial:  Trades, Settlements, MarketSnapshots (real Yahoo Finance data)
 */
public class TestDataRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestDataRegistry.class);
    private static TestDataRegistry instance;

    private final Map<String, SyntheticDataSet> scenarios = new ConcurrentHashMap<>();
    private final UserGenerator userGenerator;
    private final OrderGenerator orderGenerator;
    private final FrameworkConfig config;

    // Trade generators (lazy-initialized — they may call Yahoo Finance)
    private MarketDataProvider marketDataProvider;
    private TradeGenerator tradeGenerator;
    private SettlementGenerator settlementGenerator;

    private TestDataRegistry() {
        this.userGenerator = new UserGenerator();
        this.orderGenerator = new OrderGenerator();
        this.config = FrameworkConfig.getInstance();
    }

    private TestDataRegistry(long seed) {
        this.userGenerator = new UserGenerator(seed);
        this.orderGenerator = new OrderGenerator(seed);
        this.config = FrameworkConfig.getInstance();
    }

    public static synchronized TestDataRegistry getInstance() {
        if (instance == null) {
            instance = new TestDataRegistry();
        }
        return instance;
    }

    public static TestDataRegistry withSeed(long seed) {
        return new TestDataRegistry(seed);
    }

    // ===== LAZY INITIALIZATION FOR TRADE GENERATORS =====

    private synchronized MarketDataProvider getMarketDataProvider() {
        if (marketDataProvider == null) {
            marketDataProvider = new MarketDataProvider();
        }
        return marketDataProvider;
    }

    private synchronized TradeGenerator getTradeGenerator() {
        if (tradeGenerator == null) {
            tradeGenerator = new TradeGenerator(getMarketDataProvider());
        }
        return tradeGenerator;
    }

    private synchronized SettlementGenerator getSettlementGenerator() {
        if (settlementGenerator == null) {
            settlementGenerator = new SettlementGenerator(getTradeGenerator());
        }
        return settlementGenerator;
    }

    // ===== E-COMMERCE SCENARIO GENERATION =====

    public SyntheticDataSet generateScenario(String scenarioId, int userCount, int ordersPerUser) {
        log.info("Generating e-commerce scenario '{}': {} users, {} orders/user",
                scenarioId, userCount, ordersPerUser);

        List<User> users = userGenerator.generateBatch(userCount);
        User primaryUser = users.get(0);

        List<Order> allOrders = new ArrayList<>();
        for (User user : users) {
            allOrders.addAll(orderGenerator.generateForUser(user, ordersPerUser));
        }

        List<KafkaEvent> expectedEvents = KafkaEventGenerator.fromOrders(allOrders);
        SyntheticDataSet dataSet = new SyntheticDataSet(scenarioId, primaryUser, allOrders, expectedEvents);
        scenarios.put(scenarioId, dataSet);

        log.info("Scenario '{}' generated: {} orders, {} expected events",
                scenarioId, allOrders.size(), expectedEvents.size());
        return dataSet;
    }

    // ===== TRADE/SETTLEMENT SCENARIO GENERATION =====

    /**
     * Generate a trade/settlement scenario using real market data from Yahoo Finance.
     *
     * @param scenarioId      Unique scenario name (used in BDD features)
     * @param symbols         Ticker symbols (e.g., "AAPL", "BTC-USD")
     * @param tradesPerSymbol Trades to generate per symbol
     */
    public SyntheticDataSet generateTradeScenario(String scenarioId, List<String> symbols, int tradesPerSymbol) {
        log.info("Generating trade scenario '{}': symbols={}, trades/symbol={}",
                scenarioId, symbols, tradesPerSymbol);

        Map<String, MarketSnapshot> snapshots = getMarketDataProvider().getSnapshots(symbols);
        User trader = userGenerator.generate();
        List<Trade> trades = getTradeGenerator().generateBatch(symbols, tradesPerSymbol);

        // Assign all trades to this trader's account
        String accountId = "ACCT-" + trader.getId().substring(0, 8).toUpperCase();
        for (Trade trade : trades) {
            trade.setAccountId(accountId);
        }

        List<Settlement> settlements = getSettlementGenerator().fromTrades(trades);

        // For failure test scenarios, guarantee at least one FAILED settlement
        if (scenarioId.contains("failure")) {
            boolean hasFailed = settlements.stream()
                    .anyMatch(s -> s.getStatus() == com.enterprise.testing.shared.model.trade.SettlementStatus.FAILED);
            if (!hasFailed && settlements.size() > 1) {
                var last = settlements.get(settlements.size() - 1);
                last.setStatus(com.enterprise.testing.shared.model.trade.SettlementStatus.FAILED);
                last.setFailReason("COUNTERPARTY_DEFAULT");
                log.info("Forced FAILED settlement for failure test scenario: {}", last.getSettlementId());
            }
        }

        List<KafkaEvent> expectedEvents = TradeKafkaEventGenerator.fromTrades(trades, settlements);

        SyntheticDataSet dataSet = new SyntheticDataSet();
        dataSet.setScenarioId(scenarioId);
        dataSet.setUser(trader);
        dataSet.setTrades(trades);
        dataSet.setSettlements(settlements);
        dataSet.setMarketSnapshots(snapshots);
        dataSet.setExpectedEvents(expectedEvents);

        scenarios.put(scenarioId, dataSet);

        log.info("Trade scenario '{}' generated: {} trades, {} settlements, {} snapshots",
                scenarioId, trades.size(), settlements.size(), snapshots.size());
        return dataSet;
    }

    /**
     * Generate a round-trip trade scenario (buy then sell same symbol).
     */
    public SyntheticDataSet generateRoundTripScenario(String scenarioId, String symbol) {
        log.info("Generating round-trip scenario '{}' for {}", scenarioId, symbol);

        User trader = userGenerator.generate();
        List<Trade> trades = getTradeGenerator().generateRoundTrip(symbol);
        List<Settlement> settlements = getSettlementGenerator().fromTrades(trades);
        Map<String, MarketSnapshot> snapshots = Map.of(symbol, getMarketDataProvider().getSnapshot(symbol));
        List<KafkaEvent> expectedEvents = TradeKafkaEventGenerator.fromTrades(trades, settlements);

        SyntheticDataSet dataSet = new SyntheticDataSet();
        dataSet.setScenarioId(scenarioId);
        dataSet.setUser(trader);
        dataSet.setTrades(trades);
        dataSet.setSettlements(settlements);
        dataSet.setMarketSnapshots(snapshots);
        dataSet.setExpectedEvents(expectedEvents);

        scenarios.put(scenarioId, dataSet);
        return dataSet;
    }

    // ===== RETRIEVAL =====

    public SyntheticDataSet getScenario(String scenarioId) {
        SyntheticDataSet data = scenarios.get(scenarioId);
        if (data == null) {
            throw new IllegalStateException("No data generated for scenario: " + scenarioId +
                    ". Available: " + scenarios.keySet());
        }
        return data;
    }

    public boolean hasScenario(String scenarioId) {
        return scenarios.containsKey(scenarioId);
    }

    // ===== PERSISTENCE =====

    public void persistToDisk() {
        persistToDisk(config.getDataOutputDir());
    }

    public void persistToDisk(String outputDir) {
        Path basePath = Paths.get(outputDir);
        basePath.toFile().mkdirs();

        for (Map.Entry<String, SyntheticDataSet> entry : scenarios.entrySet()) {
            Path scenarioFile = basePath.resolve(entry.getKey() + ".json");
            JsonUtil.writeToFile(entry.getValue(), scenarioFile);
            log.info("Persisted scenario '{}' to {}", entry.getKey(), scenarioFile);
        }

        generateJmeterCsv(basePath);
    }

    public static TestDataRegistry loadFromDisk(String inputDir) {
        TestDataRegistry registry = new TestDataRegistry();
        Path basePath = Paths.get(inputDir);

        java.io.File[] files = basePath.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (java.io.File file : files) {
                String scenarioId = file.getName().replace(".json", "");
                SyntheticDataSet data = JsonUtil.readFromFile(file.toPath(), SyntheticDataSet.class);
                registry.scenarios.put(scenarioId, data);
                log.info("Loaded scenario '{}' from {}", scenarioId, file.getPath());
            }
        }
        return registry;
    }

    private void generateJmeterCsv(Path basePath) {
        StringBuilder csv = new StringBuilder("userId,email,firstName,lastName,orderId,orderTotal\n");
        for (SyntheticDataSet dataSet : scenarios.values()) {
            User user = dataSet.getUser();
            if (dataSet.getOrders() != null) {
                for (Order order : dataSet.getOrders()) {
                    if (order.getCustomerId().equals(user.getId())) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s%n",
                                user.getId(), user.getEmail(), user.getFirstName(),
                                user.getLastName(), order.getId(), order.getTotalAmount()));
                    }
                }
            }
        }
        Path csvPath = basePath.resolve("jmeter-users.csv");
        try {
            java.nio.file.Files.writeString(csvPath, csv.toString());
        } catch (java.io.IOException e) {
            log.error("Failed to write JMeter CSV", e);
        }
    }

    public Map<String, SyntheticDataSet> getAllScenarios() {
        return Map.copyOf(scenarios);
    }
}
