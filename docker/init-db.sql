-- ============================================================
-- Test Database Schema
-- Used by database tests and as the baseline for the app under test.
-- ============================================================

CREATE TABLE IF NOT EXISTS orders (
    id              VARCHAR(64) PRIMARY KEY,
    customer_id     VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_amount    DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_items (
    id              VARCHAR(64) PRIMARY KEY,
    order_id        VARCHAR(64) NOT NULL REFERENCES orders(id),
    product_id      VARCHAR(64) NOT NULL,
    product_name    VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_price      DECIMAL(12, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_log (
    id              SERIAL PRIMARY KEY,
    entity_id       VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64),
    details         JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS shipments (
    id              VARCHAR(64) PRIMARY KEY,
    order_id        VARCHAR(64) NOT NULL REFERENCES orders(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    tracking_number VARCHAR(128),
    shipped_at      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for common test queries
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity_id ON audit_log(entity_id);
CREATE INDEX IF NOT EXISTS idx_shipments_order_id ON shipments(order_id);

-- ============================================================
-- Trade & Settlement Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS trades (
    trade_id         VARCHAR(64) PRIMARY KEY,
    symbol           VARCHAR(32) NOT NULL,
    exchange         VARCHAR(32),
    side             VARCHAR(8) NOT NULL,
    quantity         INTEGER NOT NULL,
    price            DECIMAL(18, 8) NOT NULL,
    total_value      DECIMAL(18, 2) NOT NULL,
    currency         VARCHAR(8) NOT NULL DEFAULT 'USD',
    account_id       VARCHAR(64) NOT NULL,
    order_type       VARCHAR(16) NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'EXECUTED',
    market_bid       DECIMAL(18, 8),
    market_ask       DECIMAL(18, 8),
    market_volume    BIGINT,
    settlement_id    VARCHAR(64),
    executed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settlements (
    settlement_id       VARCHAR(64) PRIMARY KEY,
    trade_id            VARCHAR(64) NOT NULL REFERENCES trades(trade_id),
    symbol              VARCHAR(32) NOT NULL,
    side                VARCHAR(8) NOT NULL,
    quantity            INTEGER NOT NULL,
    settlement_amount   DECIMAL(18, 2) NOT NULL,
    currency            VARCHAR(8) NOT NULL DEFAULT 'USD',
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    trade_date          DATE NOT NULL,
    settlement_date     DATE NOT NULL,
    actual_settlement   DATE,
    counterparty_id     VARCHAR(64),
    clearing_house      VARCHAR(64),
    custodian_id        VARCHAR(64),
    account_id          VARCHAR(64),
    fail_reason         VARCHAR(128),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_account_id ON trades(account_id);
CREATE INDEX IF NOT EXISTS idx_trades_executed_at ON trades(executed_at);
CREATE INDEX IF NOT EXISTS idx_settlements_trade_id ON settlements(trade_id);
CREATE INDEX IF NOT EXISTS idx_settlements_status ON settlements(status);
CREATE INDEX IF NOT EXISTS idx_settlements_settlement_date ON settlements(settlement_date);
