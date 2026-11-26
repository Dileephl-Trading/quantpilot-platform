CREATE TABLE broker_submission_intent (
    intent_key text PRIMARY KEY,
    fingerprint text NOT NULL,
    broker_tag varchar(20) NOT NULL UNIQUE,
    parent_order_id text,
    symbol text NOT NULL,
    payload text NOT NULL,
    flat_confirmed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp
);
