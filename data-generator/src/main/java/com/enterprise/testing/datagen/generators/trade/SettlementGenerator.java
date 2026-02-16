package com.enterprise.testing.datagen.generators.trade;

import com.enterprise.testing.shared.model.trade.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Generates Settlement records derived from Trades.
 *
 * Every executed trade produces a settlement. The settlement lifecycle
 * models the real-world clearing process:
 *
 *   Trade executed -> Settlement PENDING
 *                  -> MATCHED (counterparty confirmed)
 *                  -> CLEARING (at clearinghouse)
 *                  -> SETTLED (T+1 for US equities)
 *
 * Settlement failures are generated at realistic rates (~2-5% of trades)
 * with real failure reasons.
 *
 * USAGE:
 *   SettlementGenerator gen = new SettlementGenerator();
 *   Settlement settlement = gen.fromTrade(trade);
 *
 *   // Or generate a batch with realistic failure distribution
 *   List<Settlement> settlements = gen.fromTrades(trades);
 */
public class SettlementGenerator {

    private final Random random;
    private final TradeGenerator tradeGenerator;

    // Real clearing houses
    private static final String[] CLEARING_HOUSES = {
            "DTCC", "NSCC", "OCC", "CME-CLEARING", "ICE-CLEAR-US"
    };

    // Real custodians
    private static final String[] CUSTODIANS = {
            "BNY-MELLON", "STATE-STREET", "JP-MORGAN-CUSTODY",
            "CITIBANK-CUSTODY", "NORTHERN-TRUST"
    };

    // Real settlement failure reasons
    private static final String[] FAIL_REASONS = {
            "INSUFFICIENT_SECURITIES",
            "COUNTERPARTY_DEFAULT",
            "MISMATCHED_TRADE_DETAILS",
            "FAILED_DELIVERY",
            "REGULATORY_HOLD",
            "INSUFFICIENT_MARGIN",
            "SYSTEM_ERROR",
            "LATE_AFFIRMATION"
    };

    public SettlementGenerator(TradeGenerator tradeGenerator) {
        this.random = new Random();
        this.tradeGenerator = tradeGenerator;
    }

    public SettlementGenerator(TradeGenerator tradeGenerator, long seed) {
        this.random = new Random(seed);
        this.tradeGenerator = tradeGenerator;
    }

    /**
     * Generate a settlement from a single trade.
     */
    public Settlement fromTrade(Trade trade) {
        Settlement settlement = new Settlement();

        // Link to trade
        settlement.setTradeId(trade.getTradeId());
        settlement.setSymbol(trade.getSymbol());
        settlement.setSide(trade.getSide());
        settlement.setQuantity(trade.getQuantity());
        settlement.setAccountId(trade.getAccountId());

        // Settlement amount includes any fees/commissions
        BigDecimal fees = calculateFees(trade);
        settlement.setSettlementAmount(trade.getTotalValue().add(fees).setScale(2, RoundingMode.HALF_UP));
        settlement.setCurrency(trade.getCurrency());

        // Dates
        settlement.setTradeDate(LocalDate.now());
        settlement.calculateSettlementDate(getSettlementDays(trade.getSymbol()));

        // Counterparty and clearing
        settlement.setCounterpartyId(tradeGenerator.randomCounterparty());
        settlement.setClearingHouse(randomClearingHouse(trade.getSymbol()));
        settlement.setCustodianId(CUSTODIANS[random.nextInt(CUSTODIANS.length)]);

        // Status: most settle successfully, some fail
        assignSettlementStatus(settlement, trade);

        // Link back
        trade.setSettlementId(settlement.getSettlementId());

        return settlement;
    }

    /**
     * Generate settlements for a batch of trades.
     * Trades that are REJECTED or CANCELLED don't produce settlements.
     * First settlement per symbol is always a non-FAILED status to ensure
     * happy-path scenarios have reliable test data.
     */
    public List<Settlement> fromTrades(List<Trade> trades) {
        List<Settlement> settlements = new ArrayList<>();
        java.util.Set<String> firstSettlementSymbols = new java.util.HashSet<>();
        for (Trade trade : trades) {
            if (trade.getStatus() == TradeStatus.EXECUTED ||
                trade.getStatus() == TradeStatus.PARTIALLY_FILLED) {
                Settlement settlement = fromTrade(trade);
                // Ensure first settlement per symbol is never FAILED
                // so that happy-path smoke tests always pass
                if (!firstSettlementSymbols.contains(trade.getSymbol())) {
                    firstSettlementSymbols.add(trade.getSymbol());
                    if (settlement.getStatus() == SettlementStatus.FAILED) {
                        settlement.setStatus(SettlementStatus.CLEARING);
                        settlement.setFailReason(null);
                    }
                }
                settlements.add(settlement);
            }
        }
        return settlements;
    }

    // ===== SETTLEMENT LOGIC =====

    /**
     * Determine T+N settlement days based on instrument type.
     * US equities: T+1 (since May 2024)
     * US treasuries: T+1
     * Crypto: T+0 (settles immediately)
     * International: T+2
     */
    private int getSettlementDays(String symbol) {
        if (symbol.endsWith("-USD")) return 0;  // Crypto settles instantly
        return 1;  // US equities T+1
    }

    /**
     * Assign settlement status with realistic distribution.
     * ~88% fully settled, ~5% still in progress, ~5% failed, ~2% late
     */
    private void assignSettlementStatus(Settlement settlement, Trade trade) {
        int roll = random.nextInt(100);

        if (roll < 88) {
            // Successfully settled
            settlement.setStatus(SettlementStatus.SETTLED);
            settlement.setActualSettlementDate(settlement.getSettlementDate());
        } else if (roll < 90) {
            // Late settlement (settled 1-3 days after expected)
            settlement.setStatus(SettlementStatus.SETTLED);
            int lateDays = 1 + random.nextInt(3);
            settlement.setActualSettlementDate(settlement.getSettlementDate().plusDays(lateDays));
        } else if (roll < 93) {
            // Still in clearing
            settlement.setStatus(SettlementStatus.CLEARING);
        } else if (roll < 95) {
            // Matched but not yet cleared
            settlement.setStatus(SettlementStatus.MATCHED);
        } else {
            // Failed settlement
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailReason(FAIL_REASONS[random.nextInt(FAIL_REASONS.length)]);
        }
    }

    /**
     * Calculate realistic trading fees.
     * SEC fee: ~$0.0000278 per dollar of sells
     * TAF fee: $0.000166 per share (max $8.30)
     * Commission: varies by broker, often $0 for retail
     */
    private BigDecimal calculateFees(Trade trade) {
        BigDecimal fees = BigDecimal.ZERO;

        // SEC fee on sells only
        if (trade.getSide() == TradeSide.SELL) {
            BigDecimal secFee = trade.getTotalValue()
                    .multiply(new BigDecimal("0.0000278"))
                    .setScale(2, RoundingMode.HALF_UP);
            fees = fees.add(secFee);
        }

        // TAF fee
        BigDecimal tafFee = BigDecimal.valueOf(trade.getQuantity())
                .multiply(new BigDecimal("0.000166"))
                .min(new BigDecimal("8.30"))
                .setScale(2, RoundingMode.HALF_UP);
        fees = fees.add(tafFee);

        return fees;
    }

    private String randomClearingHouse(String symbol) {
        if (symbol.endsWith("-USD")) {
            return "CRYPTO-SELF-CUSTODY"; // Crypto doesn't use traditional clearing
        }
        return CLEARING_HOUSES[random.nextInt(CLEARING_HOUSES.length)];
    }
}
