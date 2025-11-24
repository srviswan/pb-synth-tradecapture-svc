-- Initial database schema for pb-synth-tradecapture-svc

-- SwapBlotter table
CREATE TABLE IF NOT EXISTS swap_blotter (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(255) NOT NULL UNIQUE,
    partition_key VARCHAR(255) NOT NULL,
    swap_blotter_json TEXT,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trade_id ON swap_blotter(trade_id);
CREATE INDEX idx_partition_key ON swap_blotter(partition_key);

-- Partition State table
CREATE TABLE IF NOT EXISTS partition_state (
    id BIGSERIAL PRIMARY KEY,
    partition_key VARCHAR(255) NOT NULL UNIQUE,
    position_state VARCHAR(50),
    last_sequence_number BIGINT DEFAULT 0,
    state_json TEXT,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_partition_key_unique ON partition_state(partition_key);

-- Idempotency Record table
CREATE TABLE IF NOT EXISTS idempotency_record (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    trade_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    swap_blotter_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX idx_idempotency_key ON idempotency_record(idempotency_key);
CREATE INDEX idx_trade_id ON idempotency_record(trade_id);
CREATE INDEX idx_expires_at ON idempotency_record(expires_at);

