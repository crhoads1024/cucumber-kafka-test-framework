package com.enterprise.testing.shared.model.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time snapshot of market data for a symbol.
 * Populated from real Yahoo Finance data, then used by generators
 * to create realistic trades at real market prices.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketSnapshot {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("name")
    private String name;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("bid")
    private BigDecimal bid;

    @JsonProperty("ask")
    private BigDecimal ask;

    @JsonProperty("open")
    private BigDecimal open;

    @JsonProperty("previousClose")
    private BigDecimal previousClose;

    @JsonProperty("dayHigh")
    private BigDecimal dayHigh;

    @JsonProperty("dayLow")
    private BigDecimal dayLow;

    @JsonProperty("volume")
    private long volume;

    @JsonProperty("avgVolume")
    private long avgVolume;

    @JsonProperty("marketCap")
    private BigDecimal marketCap;

    @JsonProperty("fiftyTwoWeekHigh")
    private BigDecimal fiftyTwoWeekHigh;

    @JsonProperty("fiftyTwoWeekLow")
    private BigDecimal fiftyTwoWeekLow;

    @JsonProperty("snapshotTime")
    private Instant snapshotTime;

    public MarketSnapshot() {
        this.snapshotTime = Instant.now();
    }

    /**
     * Calculate a realistic spread based on the price.
     * High-volume stocks have tighter spreads.
     */
    public BigDecimal getSpread() {
        if (bid != null && ask != null) {
            return ask.subtract(bid);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getChangeFromPreviousClose() {
        if (price != null && previousClose != null) {
            return price.subtract(previousClose);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getChangePercent() {
        if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
            return getChangeFromPreviousClose()
                    .divide(previousClose, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    // --- Getters and Setters ---

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getBid() { return bid; }
    public void setBid(BigDecimal bid) { this.bid = bid; }

    public BigDecimal getAsk() { return ask; }
    public void setAsk(BigDecimal ask) { this.ask = ask; }

    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }

    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }

    public BigDecimal getDayHigh() { return dayHigh; }
    public void setDayHigh(BigDecimal dayHigh) { this.dayHigh = dayHigh; }

    public BigDecimal getDayLow() { return dayLow; }
    public void setDayLow(BigDecimal dayLow) { this.dayLow = dayLow; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public long getAvgVolume() { return avgVolume; }
    public void setAvgVolume(long avgVolume) { this.avgVolume = avgVolume; }

    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }

    public BigDecimal getFiftyTwoWeekHigh() { return fiftyTwoWeekHigh; }
    public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) { this.fiftyTwoWeekHigh = fiftyTwoWeekHigh; }

    public BigDecimal getFiftyTwoWeekLow() { return fiftyTwoWeekLow; }
    public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) { this.fiftyTwoWeekLow = fiftyTwoWeekLow; }

    public Instant getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Instant snapshotTime) { this.snapshotTime = snapshotTime; }

    @Override
    public String toString() {
        return "MarketSnapshot{" + symbol + " $" + price + " vol=" + volume + "}";
    }
}
