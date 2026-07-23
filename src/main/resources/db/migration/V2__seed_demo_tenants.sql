-- Two demo tenants. Adding a third client is a set of rows here, not a code change.

-- Client A: requires a "diagnostico" field, auto-approves up to 5,000,000 COP, one signature.
insert into tenant_rules (id, tenant_id, gate, rule_type, field_name)
values (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'SUBMIT', 'REQUIRED_FIELD', 'diagnostico');
insert into tenant_rules (id, tenant_id, gate, rule_type, max_amount, currency)
values (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'SUBMIT', 'AMOUNT_THRESHOLD', 5000000, 'COP');
insert into tenant_rules (id, tenant_id, gate, rule_type, required_signatures)
values (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'APPROVE', 'APPROVAL_GATE', 1);

-- Client B: no required field, auto-approves up to 20,000,000 COP, two signatures.
insert into tenant_rules (id, tenant_id, gate, rule_type, max_amount, currency)
values (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'SUBMIT', 'AMOUNT_THRESHOLD', 20000000, 'COP');
insert into tenant_rules (id, tenant_id, gate, rule_type, required_signatures)
values (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'APPROVE', 'APPROVAL_GATE', 2);
