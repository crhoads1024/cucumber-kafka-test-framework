package com.enterprise.testing.datagen;

import com.enterprise.testing.shared.model.KafkaEvent;
import com.enterprise.testing.shared.model.trade.*;

import java.util.*;

/**
 * Generates expected Kafka events for the trade/settlement lifecycle.
 *
 * Trade lifecycle events:
 *   TRADE_EXECUTED    -> trade.events (when a trade executes)
 *   TRADE_REJECTED    -> trade.events (when a trade is rejected)
 *
 * Settlement lifecycle events:
 *   SETTLEMENT_CREATED   -> settlement.events (settlement record created)
 *   SETTLEMENT_MATCHED   -> settlement.events (counterparty confirmed)
 *   SETTLEMENT_CLEARED   -> settlement.events (clearinghouse processed)
 *   SETTLEMENT_COMPLETED -> settlement.events (funds/securities transferred)
 *   SETTLEMENT_FAILED    -> settlement.events (settlement failed)
 */
public class TradeKafkaEventGenerator {

    private static final String TRADE_TOPIC = "trade.events";
    private static final String SETTLEMENT_TOPIC = "settlement.events";
    private static final String SOURCE = "trade-service";

    public static List<KafkaEvent> fromTrades(List<Trade> trades, List<Settlement> settlements) {
        List<KafkaEvent> events = new ArrayList<>();

        for (Trade trade : trades) {
            events.add(createTradeEvent(trade));
        }

        for (Settlement settlement : settlements) {
            events.addAll(createSettlementEvents(settlement));
        }

        return events;
    }

    private static KafkaEvent createTradeEvent(Trade trade) {
        String eventType = trade.getStatus() == TradeStatus.REJECTED
                ? "TRADE_REJECTED"
                : "TRADE_EXECUTED";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", trade.getTradeId());
        payload.put("symbol", trade.getSymbol());
        payload.put("side", trade.getSide().name());
        payload.put("quantity", trade.getQuantity());
        payload.put("price", trade.getPrice());
        payload.put("totalValue", trade.getTotalValue());
        payload.put("orderType", trade.getOrderType().name());
        payload.put("accountId", trade.getAccountId());
        payload.put("exchange", trade.getExchange());

        return new KafkaEvent(eventType, TRADE_TOPIC, SOURCE, payload)
                .withCorrelationId(trade.getTradeId());
    }

    private static List<KafkaEvent> createSettlementEvents(Settlement settlement) {
        List<KafkaEvent> events = new ArrayList<>();

        // Every settlement gets a CREATED event
        events.add(createSettlementEvent("SETTLEMENT_CREATED", settlement));

        // Status-dependent events
        switch (settlement.getStatus()) {
            case MATCHED -> {
                events.add(createSettlementEvent("SETTLEMENT_MATCHED", settlement));
            }
            case CLEARING -> {
                events.add(createSettlementEvent("SETTLEMENT_MATCHED", settlement));
                events.add(createSettlementEvent("SETTLEMENT_CLEARED", settlement));
            }
            case SETTLED -> {
                events.add(createSettlementEvent("SETTLEMENT_MATCHED", settlement));
                events.add(createSettlementEvent("SETTLEMENT_CLEARED", settlement));
                events.add(createSettlementEvent("SETTLEMENT_COMPLETED", settlement));
            }
            case FAILED -> {
                events.add(createSettlementEvent("SETTLEMENT_FAILED", settlement));
            }
            default -> {}
        }

        return events;
    }

    private static KafkaEvent createSettlementEvent(String eventType, Settlement settlement) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("settlementId", settlement.getSettlementId());
        payload.put("tradeId", settlement.getTradeId());
        payload.put("symbol", settlement.getSymbol());
        payload.put("side", settlement.getSide().name());
        payload.put("quantity", settlement.getQuantity());
        payload.put("settlementAmount", settlement.getSettlementAmount());
        payload.put("tradeDate", settlement.getTradeDate().toString());
        payload.put("settlementDate", settlement.getSettlementDate().toString());
        payload.put("counterpartyId", settlement.getCounterpartyId());
        payload.put("clearingHouse", settlement.getClearingHouse());
        payload.put("status", settlement.getStatus().name());

        if (settlement.getFailReason() != null) {
            payload.put("failReason", settlement.getFailReason());
        }

        return new KafkaEvent(eventType, SETTLEMENT_TOPIC, SOURCE, payload)
                .withCorrelationId(settlement.getTradeId());
    }
}
