create table documents (
    id          uuid primary key,
    tenant_id   uuid not null,
    reference   varchar(100) not null,
    amount      numeric(19, 2) not null,
    currency    varchar(3) not null,
    status      varchar(20) not null,
    signatures  int not null default 0,
    fields      jsonb not null default '{}',
    created_at  timestamptz not null
);

create table audit_events (
    id           bigserial primary key,
    document_id  uuid not null references documents (id),
    from_status  varchar(20),
    to_status    varchar(20) not null,
    actor        varchar(100) not null,
    reason       text,
    occurred_at  timestamptz not null
);

create index idx_audit_document on audit_events (document_id);

create table tenant_rules (
    id                   uuid primary key,
    tenant_id            uuid not null,
    gate                 varchar(20) not null,
    rule_type            varchar(30) not null,
    field_name           varchar(100),
    max_amount           numeric(19, 2),
    currency             varchar(3),
    required_signatures  int
);

create index idx_rules_tenant_gate on tenant_rules (tenant_id, gate);
