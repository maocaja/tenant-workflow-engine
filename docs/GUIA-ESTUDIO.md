# Guía de estudio — conceptos de entrevista, aplicados a este código

> **El método.** Por cada concepto, cuatro pasos:
> 1. **Verlo** en el código (el archivo exacto).
> 2. **El mecanismo** en tus palabras (si no lo puedes explicar, solo viste un síntoma).
> 3. **La pregunta** típica de entrevista.
> 4. **Tu respuesta de ≤90s** (mecanismo → dónde lo usaste → trade-off → callarte).
>
> Nada aquí es teoría suelta: cada tema apunta a un archivo que construiste. El detalle
> con predicción/observación está en `LEARNINGS.md`.

---

## Módulo 1 — JVM, compilación y runtime

| Tema | Dónde vive | Pregunta típica |
|---|---|---|
| Compilar ≠ ejecutar; bytecode | `LEARNINGS.md #1`, `build.gradle.kts` (toolchain 21) | "¿Diferencia entre compilar y ejecutar?" |
| Toolchain vs JDK del PATH | `build.gradle.kts` | "¿Por qué un `.jar` compilado en 21 no corre en una JVM 17?" |
| `UnsupportedClassVersionError`, major version | `LEARNINGS.md #1` | "¿Qué es la JVM? ¿Qué hace el JIT?" |

**Ideas ancla:** la JVM ejecuta *bytecode*, no tu `.java`. `javac` compila a bytecode (major version 65 = Java 21). Una JVM nueva corre bytecode viejo (compatibilidad hacia atrás), pero una vieja NO entiende bytecode nuevo. El *toolchain* de Gradle elige el JDK de compilación, independiente del de tu terminal.

---

## Módulo 2 — Java: interfaces, clases abstractas, generics, patrones

| Tema | Dónde vive | Pregunta típica |
|---|---|---|
| Interface vs clase abstracta | `application/port/*.java` (interfaces puras) | "¿Cuándo interface y cuándo abstract class?" |
| `sealed interface` + records + record patterns | `domain/Rule.java`, `RuleEvaluator.java` | "¿Qué es un sealed type? ¿Por qué el compilador te obliga a manejar todos los casos?" |
| Generics y type erasure | `JpaRepository<T,ID>`, `Optional<Document>`, `List<Rule>` | "¿Qué son los generics? ¿Qué es type erasure?" |
| Patrón Strategy | `Rule` + `RuleEvaluator` (una regla = una estrategia) | "¿Qué patrón de diseño reconoces aquí?" |
| Records e inmutabilidad; copia defensiva | `domain/Document.java`, `LEARNINGS.md` | "`final` ¿protege el objeto o la referencia?" |

**Ideas ancla:** *interface* = contrato (qué), sin estado; *abstract class* = contrato + estado/implementación compartida. `sealed` cierra el conjunto de subtipos → el `switch` sobre él es exhaustivo y el compilador rompe el build si falta un caso. Generics dan seguridad de tipos en compilación; en runtime se *borran* (erasure).

---

## Módulo 3 — Spring y Spring Boot

| Tema | Dónde vive | Pregunta típica |
|---|---|---|
| IoC / Inyección de dependencias | `infrastructure/config/BeanConfig.java` | "¿Qué es inversión de control? ¿Quién crea tus objetos?" |
| Framework vs Boot; auto-configuración | `LEARNINGS.md` (concepto), `@SpringBootApplication` | "¿Diferencia entre Spring Framework y Spring Boot?" |
| `@ConditionalOnMissingBean` (opinionado pero sobreescribible) | `MessagingConfig` (`@ConditionalOnProperty`) | "¿Cómo sabe Boot qué configurar?" |
| Proxies y `@Transactional`; self-invocation | `DocumentWorkflowService`, `LEARNINGS.md` | "¿Cómo funciona `@Transactional` por dentro? ¿Por qué `this.metodo()` la rompe?" |
| Actuator | `application.properties` (`health,info`) | "¿Para qué sirve Actuator en producción?" |

**Ideas ancla:** el *contenedor* de Spring crea y cablea tus beans (IoC). Boot = Framework + auto-config (no cablear a mano). `@Transactional` se implementa con un *proxy* que envuelve el bean; si llamas un método con `this`, esquivas el proxy y la transacción no aplica.

---

## Módulo 4 — Bases de datos: relacionales y no relacionales

| Tema | Dónde vive | Pregunta típica |
|---|---|---|
| JPA/Hibernate, mapeo entidad↔dominio | `infrastructure/persistence/*Entity.java` + adapters | "¿Qué hace un ORM? ¿Entity vs dominio?" |
| El problema **N+1** | `DocumentQueryService`, `DocumentListingN1Test` | "¿Qué es el N+1 y cómo lo detectas/arreglas?" |
| ACID y transacciones; propagación | `JpaAuditRepository` (`REQUIRES_NEW`), `AuditSurvivesRollbackTest` | "¿Qué es ACID? ¿Qué es `REQUIRES_NEW`?" |
| Migraciones y esquema versionado | `db/migration/V1..V3` (Flyway) | "¿Cómo versionas el esquema?" |
| Híbrido SQL + documento (`jsonb`) | `documents.fields`, `outbox.payload` | "¿Cuándo columnas vs JSON en una relacional?" |
| No relacional: documento, key-value, wide-column; ACID vs BASE, CAP | (contraste — dónde encajaría) | "¿SQL vs NoSQL, cuándo cada una?" |

**Ideas ancla:** relacional = esquema fijo, joins, transacciones ACID (nuestro caso). NoSQL = esquema flexible, escala horizontal, consistencia eventual (BASE) — se elige por *patrón de acceso*, no por moda. Nuestro `processed_events` (key-value por eventId) o el `outbox` serían candidatos naturales a un store NoSQL.

---

## Módulo 5 — Colas y sistemas distribuidos

| Tema | Dónde vive | Pregunta típica |
|---|---|---|
| Problema del *dual-write* / patrón Outbox | `OutboxEventPublisher`, `LEARNINGS.md #8` | "¿Cómo escribes en BD y publicas un evento de forma atómica?" |
| At-least-once vs exactly-once | `OutboxRelay` (send-before-mark) | "¿Qué garantía de entrega tiene una cola?" |
| Idempotencia | `consumer-service/ApprovalConsumer`, `IdempotencyTest`, `LEARNINGS.md #9` | "¿Cómo evitas procesar dos veces el mismo mensaje?" |
| Dead-letter queue (DLQ) y redrive | `MessagingConfig` (redrive, maxReceiveCount 3) | "¿Qué haces con un mensaje que siempre falla?" |
| SQS / LocalStack / AWS | `docs/architecture.html` (Fig 3-4) | "¿Cómo pruebas AWS local sin pagar?" |

**Ideas ancla:** dos sistemas (BD + broker) no comparten transacción → el outbox lo vuelve *una* escritura. La entrega es *al menos una vez* (puede duplicar) → el consumidor es *idempotente* (dedup por id) → el efecto es *exactamente una vez*. Lo que siempre falla va a la DLQ.

---

## Módulo 6 — Patrones y arquitectura (síntesis)

| Tema | Dónde vive |
|---|---|
| Hexagonal / Ports & Adapters | Toda la estructura; `docs/c4-es.html` Fig C4-3 |
| Repository | `application/port/DocumentRepository` + `JpaDocumentRepository` |
| Strategy | `Rule` + `RuleEvaluator` |
| Outbox | `OutboxEventPublisher` + `OutboxRelay` |
| Clock como puerto (tests deterministas) | `Clock` inyectado; `SubmitDocumentTest` con `Clock.fixed` |
| Pirámide de tests (dominio sin Spring) | tests puros vs `@SpringBootTest` |

---

### Orden recomendado
1 → 2 → 3 → 4 → 5 → 6. Los primeros dos son cimientos (JVM + lenguaje); los últimos son lo que te distingue como senior (transacciones, colas, arquitectura). Ritmo sugerido: **1–2 módulos por sesión**, cada uno cerrando con preguntas donde tú ensayas la respuesta en voz alta.
