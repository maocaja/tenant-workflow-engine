-- The consumer's idempotency store: one row per event it has already handled.
create table processed_events (
    event_id      uuid primary key,
    document_id   uuid not null,
    processed_at  timestamptz not null
);
