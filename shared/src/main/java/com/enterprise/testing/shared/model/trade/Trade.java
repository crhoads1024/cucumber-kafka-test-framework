package com.enterprise.testing.shared.model.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an executed trade in the system.
 *
 * A Trade captures: what was bought/sold, how much, at what price,
 * and links to the Settlement that clears it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {

    @JsonProperty("tradeId")
    private String tradeId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("side")
    private TradeSide side;

    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("totalValue")
    private BigDecimal totalValue;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("orderType")
    private OrderType orderType;

    @JsonProperty("status")
    private TradeStatus status;

    @JsonProperty("executedAt")
    private Instant executedAt;

    @JsonProperty("settlementId")
    private String settlementId;

    // Market data snapshot at time of trade
    @JsonProperty("marketBid")
    private BigDecimal marketBid;

    @JsonProperty("marketAsk")
    private BigDecimal marketAsk;

    @JsonProperty("marketVolume")
    private long marketVolume;

    public Trade() {
        this.tradeId = "TRD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        this.executedAt = Instant.now();
        this.status = TradeStatus.EXECUTED;
        this.currency = "USD";
    }

    public void calculateTotalValue() {
        this.totalValue = price.multiply(BigDecimal.valueOf(quantity));
    }

    // --- Getters and Setters ---

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public TradeSide getSide() { return side; }
    public void setSide(TradeSide side) { this.side = side; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public TradeStatus getStatus() { return status; }
    public void setStatus(TradeStatus status) { this.status = status; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public String getSettlementId() { return settlementId; }
    public void setSettlementId(String settlementId) { this.settlementId = settlementId; }

    public BigDecimal getMarketBid() { return marketBid; }
    public void setMarketBid(BigDecimal marketBid) { this.marketBid = marketBid; }

    public BigDecimal getMarketAsk() { return marketAsk; }
    public void setMarketAsk(BigDecimal marketAsk) { this.marketAsk = marketAsk; }

    public long getMarketVolume() { return marketVolume; }
    public void setMarketVolume(long marketVolume) { this.marketVolume = marketVolume; }

    @Override
    public String toString() {
        return "Trade{" + tradeId + " " + side + " " + quantity + " " + symbol +
                " @ " + price + " = " + totalValue + " [" + status + "]}";
    }
}
