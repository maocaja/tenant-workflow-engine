# Technical Vocabulary

Not a dictionary. The point is the **third column**: the sentence I would actually
say out loud in an interview. Translation alone does not survive pressure — a
rehearsed sentence does.

Rules:
- Say it out loud first. If it does not flow spoken, rewrite it.
- Anchor the sentence to something I actually built, not to a definition.
- Only add a section when I reach that topic. An empty table teaches nothing.

---

## 1. Build & Gradle

| English | Español | Sentence I'd say |
|---|---|---|
| toolchain | JDK que usa el build, no el del PATH | |
| dependency resolution | resolución de dependencias | |
| BOM (bill of materials) | lista curada de versiones | |

---

## 2. Spring core

| English | Español | Sentence I'd say |
|---|---|---|
| auto-configuration | autoconfiguración | |
| opinionated configuration | configuración con opinión | |
| IoC container | contenedor de inversión de control | |
| to wire / wiring | cablear / cableado (conectar los beans) | |
| to override a default | sobrescribir un valor por defecto | |
| embedded server | servidor embebido | |
| boilerplate / plumbing | código repetitivo / plomería | |

---

## 3. Transactions

| English | Español | Sentence I'd say |
|---|---|---|
| proxy | proxy (el envoltorio que Spring pone alrededor del bean) | |
| to intercept a call | interceptar una llamada | |
| self-invocation | autoinvocación (`this.metodo()`) | |
| to bypass the proxy | esquivar el proxy | |
| propagation | propagación | |
| REQUIRES_NEW | transacción independiente que sobrevive al rollback | |
| to roll back / rollback | revertir / reversión | |
| append-only audit trail | registro de auditoría de solo adición | |

---

## Coming up

Sections get added when we reach the topic — not before:

- Persistence & JPA (day 2)
- Testing (day 2)
- Messaging & distributed systems (day 3)
- Architecture (as it comes up)
- Interview connectors (day 3 — the phrases for buying time and asking for a repeat)
