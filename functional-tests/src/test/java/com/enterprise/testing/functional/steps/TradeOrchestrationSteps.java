package com.enterprise.testing.functional.steps;

import com.enterprise.testing.functional.db.DatabaseHelper;
import com.enterprise.testing.functional.db.DatabaseSeeder;
import com.enterprise.testing.functional.kafka.KafkaTestProducer;
import com.enterprise.testing.shared.model.KafkaEvent;
import com.enterprise.testing.shared.model.SyntheticDataSet;
import com.enterprise.testing.shared.model.trade.Settlement;
import com.enterprise.testing.shared.model.trade.Trade;
import io.cucumber.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Trade & Settlement Orchestration Steps
 *
 * Same pattern as E2EOrchestrationSteps but for the trade domain.
 * After a trade is submitted via WireMock, this class:
 *   1. Seeds the trade + settlement into PostgreSQL
 *   2. Publishes TRADE_EXECUTED and SETTLEMENT_CREATED Kafka events
 *   3. Creates audit log entries
 */
public class TradeOrchestrationSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeOrchestrationSteps.class);
    private final TestContext context;

    public TradeOrchestrationSteps() {
        this.context = SharedTestContext.get();
    }

    @Given("the submitted trade is persisted to the database")
    public void persistTradeToDatabase() {
        DatabaseHelper db = context.getDbHelper();
        if (db == null) return;

        SyntheticDataSet data = context.getCurrentDataSet();
        Trade trade = (Trade) context.get("selected.trade");
        String createdTradeId = (String) context.get("created.trade.id");

        if (trade == null || createdTradeId == null) {
            log.warn("No trade or created.trade.id available for DB seeding");
            return;
        }

        // Use the WireMock-returned trade ID to seed DB
        db.execute(
                "INSERT INTO trades (trade_id, symbol, exchange, side, quantity, price, " +
                "total_value, currency, account_id, order_type, status, " +
                "market_bid, market_ask, market_volume, settlement_id, executed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (trade_id) DO NOTHING",
                createdTradeId,
                trade.getSymbol(),
                trade.getExchange(),
                trade.getSide().name(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTotalValue(),
                trade.getCurrency(),
                trade.getAccountId(),
                trade.getOrderType().name(),
                trade.getStatus().name(),
                trade.getMarketBid(),
                trade.getMarketAsk(),
                trade.getMarketVolume(),
                trade.getSettlementId()
        );

        // Find and seed the matching settlement
        Settlement settlement = data.getSettlements().stream()
                .filter(s -> s.getTradeId().equals(trade.getTradeId()))
                .findFirst().orElse(null);

        if (settlement != null) {
            DatabaseSeeder seeder = new DatabaseSeeder(db);
            // We need to update the settlement's trade reference to the WireMock ID
            db.execute(
                    "INSERT INTO settlements (settlement_id, trade_id, symbol, side, quantity, " +
                    "settlement_amount, currency, status, trade_date, settlement_date, " +
                    "actual_settlement, counterparty_id, clearing_house, custodian_id, " +
                    "account_id, fail_reason, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                    "ON CONFLICT (settlement_id) DO NOTHING",
                    settlement.getSettlementId(),
                    createdTradeId,  // Use the WireMock trade ID
                    settlement.getSymbol(),
                    settlement.getSide().name(),
                    settlement.getQuantity(),
                    settlement.getSettlementAmount(),
                    settlement.getCurrency(),
                    settlement.getStatus().name(),
                    java.sql.Date.valueOf(settlement.getTradeDate()),
                    java.sql.Date.valueOf(settlement.getSettlementDate()),
                    settlement.getActualSettlementDate() != null ?
                            java.sql.Date.valueOf(settlement.getActualSettlementDate()) : null,
                    settlement.getCounterpartyId(),
                    settlement.getClearingHouse(),
                    settlement.getCustodianId(),
                    settlement.getAccountId(),
                    settlement.getFailReason()
            );
        }

        // Audit entry
        db.execute(
                "INSERT INTO audit_log (entity_id, entity_type, action, user_id, details, created_at) " +
                "VALUES (?, 'TRADE', 'TRADE_EXECUTED', ?, ?::jsonb, NOW())",
                createdTradeId,
                trade.getAccountId(),
                String.format("{\"price\": %s, \"symbol\": \"%s\", \"quantity\": %d}",
                        trade.getPrice().toPlainString(), trade.getSymbol(), trade.getQuantity())
        );

        log.info("Persisted trade {} + settlement to database", createdTradeId);
    }

    @Given("the submitted trade events are published to Kafka")
    public void publishTradeEvents() {
        KafkaTestProducer producer = context.getKafkaProducer();
        if (producer == null) return;

        Trade trade = (Trade) context.get("selected.trade");
        SyntheticDataSet data = context.getCurrentDataSet();
        String createdTradeId = (String) context.get("created.trade.id");

        if (trade == null || createdTradeId == null) {
            log.warn("No trade or created.trade.id available for Kafka publish");
            return;
        }

        // TRADE_EXECUTED event
        Map<String, Object> tradePayload = new HashMap<>();
        tradePayload.put("tradeId", createdTradeId);
        tradePayload.put("symbol", trade.getSymbol());
        tradePayload.put("side", trade.getSide().name());
        tradePayload.put("quantity", trade.getQuantity());
        tradePayload.put("price", trade.getPrice());
        tradePayload.put("exchange", trade.getExchange());
        tradePayload.put("accountId", trade.getAccountId());

        KafkaEvent tradeEvent = new KafkaEvent(
                "TRADE_EXECUTED", "trade.events", "trade-engine", tradePayload)
                .withCorrelationId(createdTradeId);
        producer.publishSync(tradeEvent);

        // SETTLEMENT_CREATED event
        Settlement settlement = data.getSettlements().stream()
                .filter(s -> s.getTradeId().equals(trade.getTradeId()))
                .findFirst().orElse(null);

        if (settlement != null) {
            Map<String, Object> settlementPayload = new HashMap<>();
            settlementPayload.put("settlementId", settlement.getSettlementId());
            settlementPayload.put("tradeId", createdTradeId);
            settlementPayload.put("symbol", settlement.getSymbol());
            settlementPayload.put("clearingHouse", settlement.getClearingHouse());
            settlementPayload.put("counterpartyId", settlement.getCounterpartyId());
            settlementPayload.put("settlementDate", settlement.getSettlementDate().toString());

            KafkaEvent settlementEvent = new KafkaEvent(
                    "SETTLEMENT_CREATED", "settlement.events", "settlement-service", settlementPayload)
                    .withCorrelationId(createdTradeId);
            producer.publishSync(settlementEvent);

            // If the settlement is fully settled, publish the full lifecycle chain
            // This simulates the progression: CREATED → MATCHED → CLEARED → COMPLETED
            String status = settlement.getStatus().name();
            if ("MATCHED".equals(status) || "CLEARING".equals(status) || "SETTLED".equals(status)) {
                publishSettlementLifecycle(producer, createdTradeId, settlementPayload);
            }
        }

        log.info("Published TRADE_EXECUTED + SETTLEMENT_CREATED events for trade {}", createdTradeId);
    }

    @Given("the rejected trade event is published to Kafka")
    public void publishRejectedTradeEvent() {
        KafkaTestProducer producer = context.getKafkaProducer();
        if (producer == null) return;

        Trade trade = (Trade) context.get("selected.trade");
        if (trade == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", trade.getTradeId());
        payload.put("symbol", trade.getSymbol());
        payload.put("reason", "VALIDATION_FAILED");

        KafkaEvent event = new KafkaEvent(
                "TRADE_REJECTED", "trade.events", "trade-engine", payload)
                .withCorrelationId(trade.getTradeId());
        producer.publishSync(event);

        log.info("Published TRADE_REJECTED event for trade {}", trade.getTradeId());
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Publish the full settlement lifecycle event chain.
     * In production, these events fire as the settlement progresses
     * through clearing. We publish them all at once for testing.
     */
    private void publishSettlementLifecycle(KafkaTestProducer producer,
                                            String tradeId,
                                            Map<String, Object> basePayload) {
        String[] stages = {"SETTLEMENT_MATCHED", "SETTLEMENT_CLEARED", "SETTLEMENT_COMPLETED"};
        for (String stage : stages) {
            Map<String, Object> payload = new HashMap<>(basePayload);
            payload.put("status", stage.replace("SETTLEMENT_", ""));

            KafkaEvent event = new KafkaEvent(
                    stage, "settlement.events", "settlement-service", payload)
                    .withCorrelationId(tradeId);
            producer.publishSync(event);

            // Small delay to ensure ordering
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        log.info("Published full settlement lifecycle chain for trade {}", tradeId);
    }
}
