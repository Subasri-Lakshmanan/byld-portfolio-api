-- V1: Initial schema for BYLD Portfolio API

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Portfolios
CREATE TABLE portfolios (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name VARCHAR(255) NOT NULL,
    risk_profile VARCHAR(50) NOT NULL,
    cash_balance NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Holdings (one row per portfolio+symbol)
CREATE TABLE holdings (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID        NOT NULL REFERENCES portfolios(id),
    symbol          VARCHAR(20) NOT NULL,
    quantity        NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    avg_cost_basis  NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_portfolio_symbol UNIQUE (portfolio_id, symbol),
    CONSTRAINT chk_quantity_non_negative CHECK (quantity >= 0)
);

-- Transactions
CREATE TABLE transactions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID        NOT NULL REFERENCES portfolios(id),
    symbol          VARCHAR(20) NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('BUY', 'SELL')),
    quantity        NUMERIC(19,4) NOT NULL,
    price           NUMERIC(19,4) NOT NULL,
    total_value     NUMERIC(19,4) NOT NULL,
    transacted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tx_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_tx_price_positive CHECK (price > 0)
);

-- Dividends
CREATE TABLE dividends (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id      UUID        NOT NULL REFERENCES portfolios(id),
    symbol            VARCHAR(20) NOT NULL,
    per_share_amount  NUMERIC(19,4) NOT NULL,
    record_date       DATE        NOT NULL,
    shares_at_record  NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    total_payout      NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dividend_per_share_positive CHECK (per_share_amount > 0)
);

-- Indexes
CREATE INDEX idx_holdings_portfolio_id ON holdings(portfolio_id);
CREATE INDEX idx_transactions_portfolio_id ON transactions(portfolio_id);
CREATE INDEX idx_dividends_portfolio_id ON dividends(portfolio_id);
CREATE INDEX idx_dividends_symbol_record_date ON dividends(symbol, record_date);
