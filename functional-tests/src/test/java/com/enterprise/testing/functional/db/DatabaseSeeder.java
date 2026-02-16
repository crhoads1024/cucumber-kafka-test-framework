package com.enterprise.testing.functional.db;

import com.enterprise.testing.shared.model.*;
import com.enterprise.testing.shared.model.trade.Settlement;
import com.enterprise.testing.shared.model.trade.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Seeds synthetic test data into PostgreSQL tables.
 *
 * WHY THIS EXISTS:
 * WireMock stubs API responses but does NOT write to the database.
 * For @database and @e2e tests that assert against DB state, we need
 * the generated test data to actually exist in Postgres.
 *
 * This class takes a SyntheticDataSet and INSERTs all its records
 * into the appropriate tables. It is idempotent — duplicate inserts
 * are handled with ON CONFLICT DO NOTHING.
 *
 * USAGE:
 *   DatabaseSeeder seeder = new DatabaseSeeder(dbHelper);
 *   seeder.seedAll(dataSet);
 */
public class DatabaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);
    private final DatabaseHelper db;

    public DatabaseSeeder(DatabaseHelper db) {
        this.db = db;
    }

    /**
     * Seed all data from a SyntheticDataSet into the database.
     * Call this after "a database connection is established" and
     * after loading synthetic data.
     */
    public void seedAll(SyntheticDataSet dataSet) {
        int orderCount = 0;
        int itemCount = 0;
        int tradeCount = 0;
        int settlementCount = 0;
        int auditCount = 0;

        // Seed orders and their items
        if (dataSet.getOrders() != null) {
            for (Order order : dataSet.getOrders()) {
                orderCount += seedOrder(order, dataSet.getUser());
                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        itemCount += seedOrderItem(item, order.getId());
                    }
                }
                // Create audit entry for order creation
                auditCount += seedAuditEntry(
                        order.getId(), "ORDER", "ORDER_CREATED",
                        dataSet.getUser().getId());
            }
        }

        // Seed trades
        if (dataSet.getTrades() != null) {
            for (Trade trade : dataSet.getTrades()) {
                tradeCount += seedTrade(trade);
            }
        }

        // Seed settlements
        if (dataSet.getSettlements() != null) {
            for (Settlement settlement : dataSet.getSettlements()) {
                settlementCount += seedSettlement(settlement);
            }
        }

        log.info("Database seeded: {} orders, {} items, {} trades, {} settlements, {} audit entries",
                orderCount, itemCount, tradeCount, settlementCount, auditCount);
    }

    /**
     * Clean all test data from the database. Call in @After hooks
     * to ensure test isolation.
     */
    public void cleanAll() {
        // Delete in reverse FK order
        db.execute("DELETE FROM audit_log");
        db.execute("DELETE FROM shipments");
        db.execute("DELETE FROM order_items");
        db.execute("DELETE FROM orders");
        db.execute("DELETE FROM settlements");
        db.execute("DELETE FROM trades");
        log.info("Database cleaned: all test data removed");
    }

    // ===== INDIVIDUAL SEEDERS =====

    private int seedOrder(Order order, User user) {
        return db.execute(
                "INSERT INTO orders (id, customer_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (id) DO NOTHING",
                order.getId(),
                user != null ? user.getId() : "UNKNOWN",
                order.getStatus() != null ? order.getStatus().name() : "PENDING",
                order.getTotalAmount()
        );
    }

    private int seedOrderItem(OrderItem item, String orderId) {
        return db.execute(
                "INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO NOTHING",
                item.getId(),
                orderId,
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }

    private int seedTrade(Trade trade) {
        return db.execute(
                "INSERT INTO trades (trade_id, symbol, exchange, side, quantity, price, " +
                "total_value, currency, account_id, order_type, status, " +
                "market_bid, market_ask, market_volume, settlement_id, executed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (trade_id) DO NOTHING",
                trade.getTradeId(),
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
                trade.getSettlementId(),
                Timestamp.from(trade.getExecutedAt())
        );
    }

    private int seedSettlement(Settlement settlement) {
        return db.execute(
                "INSERT INTO settlements (settlement_id, trade_id, symbol, side, quantity, " +
                "settlement_amount, currency, status, trade_date, settlement_date, " +
                "actual_settlement, counterparty_id, clearing_house, custodian_id, " +
                "account_id, fail_reason, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (settlement_id) DO NOTHING",
                settlement.getSettlementId(),
                settlement.getTradeId(),
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
                settlement.getFailReason(),
                Timestamp.from(settlement.getCreatedAt() != null ? settlement.getCreatedAt() : Instant.now()),
                Timestamp.from(settlement.getUpdatedAt() != null ? settlement.getUpdatedAt() : Instant.now())
        );
    }

    private int seedAuditEntry(String entityId, String entityType, String action, String userId) {
        return db.execute(
                "INSERT INTO audit_log (entity_id, entity_type, action, user_id, details, created_at) " +
                "VALUES (?, ?, ?, ?, '{}'::jsonb, NOW())",
                entityId,
                entityType,
                action,
                userId
        );
    }

    /**
     * Seed a status-update audit entry (for confirm/cancel flows).
     */
    public int seedStatusChange(String orderId, String userId, String fromStatus, String toStatus) {
        // Update the order status
        db.execute("UPDATE orders SET status = ?, updated_at = NOW() WHERE id = ?",
                toStatus, orderId);

        // Add audit entry
        return seedAuditEntry(orderId, "ORDER", "ORDER_" + toStatus, userId);
    }

    /**
     * Mark an order as cancelled and clean up related records.
     */
    public void cancelOrder(String orderId, String userId) {
        db.execute("UPDATE orders SET status = 'CANCELLED', updated_at = NOW() WHERE id = ?", orderId);
        db.execute("UPDATE shipments SET status = 'CANCELLED' WHERE order_id = ?", orderId);
        seedAuditEntry(orderId, "ORDER", "ORDER_CANCELLED", userId);
        log.info("Order {} cancelled with audit trail", orderId);
    }
}
