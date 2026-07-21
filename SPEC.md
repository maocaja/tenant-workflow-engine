# Design Proposal — Tenant Workflow Engine

## Problem

A company processes billing documents on behalf of several clients. The workflow is the
same for all of them — a document is drafted, submitted, approved or rejected, archived.
What differs is the conditions:

```
Client A                          Client B
────────────────────────          ────────────────────────
Requires a "diagnosis" field      Does not require it
Auto-approves up to 5,000,000     Auto-approves up to 20,000,000
One signature                     Two signatures
```

The naive implementation is `if (client == A) ... else if (client == B) ...`. Adding client
C means touching code, redeploying, and risking A and B. At thirty clients it is
unmaintainable.

Second requirement: money moves, so someone will eventually ask *"who approved this, when,
and why did it pass the rules?"* — and the answer has to survive even if the business
operation was later rolled back.

**This service models both:** rules are data, not code, and every transition writes an
immutable record.

```
Client A  →  [ RequiredField("diagnosis"), AmountThreshold(5_000_000), ApprovalGate(1) ]
Client B  →  [ AmountThreshold(20_000_000), ApprovalGate(2) ]
Client C  →  a new row. No code change.
```

It is a learning lab. The domain is deliberately small so the engineering can be deep.

---

## Domain model

```
   Tenant
     │ 1
     │
     │ *
   Document ───────────┬──────────────┐
   ├ id                │ *            │ *
   ├ tenantId          │              │
   ├ reference         ▼              ▼
   ├ amount        AuditEvent        Rule  (per tenant, configurable)
   ├ currency      ├ documentId      │
   ├ status        ├ from → to       ├─ RequiredField(name)
   └ createdAt     ├ actor           ├─ AmountThreshold(max, currency)
                   ├ reason          └─ ApprovalGate(role)
                   └ occurredAt
                   (append-only —
                    never updated,
                    never deleted)
```

`Rule` is a **sealed interface**. The compiler enforces that every rule variant is handled
wherever rules are evaluated — adding a variant breaks the build until it is addressed.

---

## State machine

```
                    ┌──────────────────────────────┐
                    │                              │
                    ▼                              │
   ┌───────┐    ┌───────────┐    ┌──────────┐    ┌──────────┐
   │ DRAFT │───►│ SUBMITTED │───►│ APPROVED │───►│ ARCHIVED │
   └───────┘    └───────────┘    └──────────┘    └──────────┘
                      │
                      ▼
                 ┌──────────┐
                 │ REJECTED │
                 └──────────┘

   Every arrow:  rules are evaluated  →  transition happens  →  AuditEvent is written
```

Four states plus one rejection branch. More states would not teach more — they would only
cost time.

---

## Architecture — ports and adapters

```
                          ┌─────────────────────────────────┐
                          │          INFRASTRUCTURE         │
                          │                                 │
    HTTP ──► DocumentController                             │
                          │         │                       │
                          └─────────┼───────────────────────┘
                                    ▼
                          ┌─────────────────────────────────┐
                          │          APPLICATION            │
                          │   SubmitDocument                │
                          │   ApproveDocument               │
                          │   ─────────────────────────     │
                          │   ports (interfaces):           │
                          │     DocumentRepository          │
                          │     EventPublisher              │
                          │     Clock                       │
                          └─────────────────────────────────┘
                                    ▼
                          ┌─────────────────────────────────┐
                          │            DOMAIN               │
                          │   Document · Status · Rule      │
                          │   RuleEvaluator · AuditEvent    │
                          │                                 │
                          │   ZERO Spring imports           │
                          └─────────────────────────────────┘
                                    ▲
                          ┌─────────┼───────────────────────┐
                          │  INFRASTRUCTURE (adapters)      │
                          │   JpaDocumentRepository         │
                          │   OutboxEventPublisher          │
                          │   SystemClock                   │
                          └─────────────────────────────────┘
```

**Three ports, no more.** A port exists only where there is a real technology decision to
invert: how documents are stored, how events leave the process, what "now" means (so time
can be controlled in tests). Anything else is ceremony.

**The rule that keeps it honest:**

```
grep -r "org.springframework" src/main/java/**/domain/   →   must return nothing
```

**Why this matters beyond tidiness:** a domain that does not import Spring can be tested
without starting an `ApplicationContext`. That is what makes the fast layer of the test
pyramid possible. Architecture and test strategy are the same decision.

---

## Use cases, in build order

| # | Use case | Day |
|---|---|---|
| 1 | Submit a document — evaluate rules, transition, write audit | 1 |
| 2 | Approve or reject — same path, different rules | 1 |
| 3 | List documents with their audit history (the N+1 surface) | 2 |
| 4 | Publish an event on approval via the outbox | 3 |
| 5 | Consume that event idempotently in a second service | 3 |

---

## Out of scope

The most important section. Each exclusion is deliberate:

| Not building | Why |
|---|---|
| Any UI | Nothing about a frontend exercises what this lab is for |
| Authentication / authorization | Solved elsewhere; adds setup, teaches nothing new here |
| A gateway, service discovery, shared contract module | Buys topology at the cost of depth. Two processes are enough to produce partial failure |
| Real payments or accounting rules | The amount exists to give idempotency real stakes, not to be correct accounting |
| Caching | No performance problem here that a cache is the answer to |
| More than 4 states | Cost without additional learning |
| Kafka | Same problems as SQS with a different broker. Learn the problems once |

---

## What each piece exercises

Nothing here is decoration. Every element exists to make a specific mechanism observable:

| Domain element | Mechanism it exercises |
|---|---|
| `sealed interface Rule` + `switch` with record patterns | Java 21 idioms; compiler-enforced exhaustiveness; Strategy / open-closed |
| Append-only `AuditEvent` | Transaction propagation — the audit must survive a business rollback (`REQUIRES_NEW`) |
| Rules configurable per tenant | Open-closed in practice: a new tenant is configuration, not code |
| `Document` → `AuditEvent` as a lazy collection | The N+1 problem, fetch strategies, execution plans |
| Domain with zero Spring imports | The fast layer of the test pyramid; dependency inversion |
| Outbox table written in the business transaction | The dual-write problem; at-least-once delivery |
| Second service consuming the event | Idempotency, dead-letter queues, partial failure |
| `Clock` as a port | Deterministic tests over time-dependent logic |

---

## Definition of done

- [ ] `./gradlew build` green from a clean clone
- [ ] `grep -r "org.springframework" domain/` returns nothing
- [ ] Adding a `Rule` variant breaks the build until handled
- [ ] A test asserts the N+1 by counting statements, not by reading logs
- [ ] Three transactional failure modes reproduced in tests, then fixed
- [ ] The same message published twice produces one effect
- [ ] Consumer killed mid-flight recovers and drains its backlog
- [ ] `PERFORMANCE.md` holds real measurements, not estimates
- [ ] Zero explanatory comments in `src/main`
- [ ] This document plus the ADRs stay shorter than the code
