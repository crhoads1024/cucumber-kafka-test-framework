package com.enterprise.testing.shared.model.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents the settlement of a trade.
 *
 * Settlement is the actual transfer of securities and cash between parties.
 * US equities settle T+1 (trade date + 1 business day) as of May 2024.
 * Previously was T+2.
 *
 * Settlement lifecycle:
 *   PENDING -> MATCHED -> CLEARING -> SETTLED
 *                      -> FAILED
 *                      -> CANCELLED
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settlement {

    @JsonProperty("settlementId")
    private String settlementId;

    @JsonProperty("tradeId")
    private String tradeId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private TradeSide side;

    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("settlementAmount")
    private BigDecimal settlementAmount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private SettlementStatus status;

    @JsonProperty("tradeDate")
    private LocalDate tradeDate;

    @JsonProperty("settlementDate")
    private LocalDate settlementDate;

    @JsonProperty("actualSettlementDate")
    private LocalDate actualSettlementDate;

    @JsonProperty("counterpartyId")
    private String counterpartyId;

    @JsonProperty("clearingHouse")
    private String clearingHouse;

    @JsonProperty("custodianId")
    private String custodianId;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("failReason")
    private String failReason;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    public Settlement() {
        this.settlementId = "STL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        this.status = SettlementStatus.PENDING;
        this.currency = "USD";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Calculate expected settlement date.
     * US equities: T+1 (since May 2024)
     * US treasuries: T+1
     * Options: T+1
     * Mutual funds: T+1 or T+2
     */
    public void calculateSettlementDate(int tPlusDays) {
        LocalDate date = tradeDate;
        int daysAdded = 0;
        while (daysAdded < tPlusDays) {
            date = date.plusDays(1);
            // Skip weekends (simplified - doesn't handle market holidays)
            if (date.getDayOfWeek().getValue() <= 5) {
                daysAdded++;
            }
        }
        this.settlementDate = date;
    }

    public boolean isLateSettlement() {
        return actualSettlementDate != null &&
                actualSettlementDate.isAfter(settlementDate);
    }

    // --- Getters and Setters ---

    public String getSettlementId() { return settlementId; }
    public void setSettlementId(String settlementId) { this.settlementId = settlementId; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public TradeSide getSide() { return side; }
    public void setSide(TradeSide side) { this.side = side; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public void setSettlementAmount(BigDecimal settlementAmount) { this.settlementAmount = settlementAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; this.updatedAt = Instant.now(); }

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public LocalDate getActualSettlementDate() { return actualSettlementDate; }
    public void setActualSettlementDate(LocalDate actualSettlementDate) { this.actualSettlementDate = actualSettlementDate; }

    public String getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(String counterpartyId) { this.counterpartyId = counterpartyId; }

    public String getClearingHouse() { return clearingHouse; }
    public void setClearingHouse(String clearingHouse) { this.clearingHouse = clearingHouse; }

    public String getCustodianId() { return custodianId; }
    public void setCustodianId(String custodianId) { this.custodianId = custodianId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Settlement{" + settlementId + " trade=" + tradeId + " " + symbol +
                " settleDate=" + settlementDate + " [" + status + "]}";
    }
}
