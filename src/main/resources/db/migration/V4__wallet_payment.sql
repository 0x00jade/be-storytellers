-- Wallets: one per user, stores balance
CREATE TABLE wallets (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version     BIGINT        NOT NULL DEFAULT 0,
    is_frozen   BOOLEAN       NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallets_user ON wallets(user_id);

-- Immutable transaction ledger (never UPDATE rows, only INSERT)
CREATE TABLE wallet_transactions (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID          NOT NULL REFERENCES wallets(id),
    type             VARCHAR(20)   NOT NULL CHECK (type IN ('DEPOSIT', 'PURCHASE', 'REFUND')),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    amount           NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    idempotency_key  VARCHAR(64)   UNIQUE,
    reference_id     UUID,
    description      TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallet_tx_wallet ON wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX idx_wallet_tx_idem   ON wallet_transactions(idempotency_key);
CREATE INDEX idx_wallet_tx_type   ON wallet_transactions(type, status);

-- Permanent access grants: who has unlocked which chapter
CREATE TABLE chapter_purchases (
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_id   UUID        NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    tx_id        UUID        REFERENCES wallet_transactions(id),
    purchased_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, chapter_id)
);

CREATE INDEX idx_chapter_purchases_user    ON chapter_purchases(user_id);
CREATE INDEX idx_chapter_purchases_chapter ON chapter_purchases(chapter_id);

-- Price on chapters: NULL = free, non-null = premium
ALTER TABLE chapters ADD COLUMN price NUMERIC(19,4) CHECK (price IS NULL OR price >= 0);
