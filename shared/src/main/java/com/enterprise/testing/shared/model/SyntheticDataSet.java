package com.enterprise.testing.shared.model;

import com.enterprise.testing.shared.model.trade.MarketSnapshot;
import com.enterprise.testing.shared.model.trade.Settlement;
import com.enterprise.testing.shared.model.trade.Trade;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bundles correlated synthetic data for a single test scenario.
 * This is the key integration point: data-generator creates these,
 * and all test layers consume them to ensure consistency.
 *
 * Supports both e-commerce (orders) and financial (trades/settlements) domains.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticDataSet {

    @JsonProperty("scenarioId")
    private String scenarioId;

    @JsonProperty("user")
    private User user;

    // --- E-commerce domain ---
    @JsonProperty("orders")
    private List<Order> orders;

    // --- Financial/trading domain ---
    @JsonProperty("trades")
    private List<Trade> trades;

    @JsonProperty("settlements")
    private List<Settlement> settlements;

    @JsonProperty("marketSnapshots")
    private Map<String, MarketSnapshot> marketSnapshots;

    // --- Shared ---
    @JsonProperty("expectedEvents")
    private List<KafkaEvent> expectedEvents;

    public SyntheticDataSet() {
        this.orders = new ArrayList<>();
        this.trades = new ArrayList<>();
        this.settlements = new ArrayList<>();
        this.expectedEvents = new ArrayList<>();
    }

    public SyntheticDataSet(String scenarioId, User user, List<Order> orders, List<KafkaEvent> expectedEvents) {
        this();
        this.scenarioId = scenarioId;
        this.user = user;
        this.orders = orders;
        this.expectedEvents = expectedEvents;
    }

    // --- Getters and Setters ---

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }

    public List<Trade> getTrades() { return trades; }
    public void setTrades(List<Trade> trades) { this.trades = trades; }

    public List<Settlement> getSettlements() { return settlements; }
    public void setSettlements(List<Settlement> settlements) { this.settlements = settlements; }

    public Map<String, MarketSnapshot> getMarketSnapshots() { return marketSnapshots; }
    public void setMarketSnapshots(Map<String, MarketSnapshot> marketSnapshots) { this.marketSnapshots = marketSnapshots; }

    public List<KafkaEvent> getExpectedEvents() { return expectedEvents; }
    public void setExpectedEvents(List<KafkaEvent> expectedEvents) { this.expectedEvents = expectedEvents; }
}
