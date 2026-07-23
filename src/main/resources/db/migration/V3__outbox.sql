create table outbox (
    id            uuid primary key,
    aggregate_id  uuid not null,
    type          varchar(100) not null,
    payload       jsonb not null,
    created_at    timestamptz not null,
    published_at  timestamptz
);

-- The relay scans for unpublished rows in creation order.
create index idx_outbox_unpublished on outbox (created_at) where published_at is null;
