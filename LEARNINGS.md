# Learnings

Working notebook for this lab. One entry per experiment.

The rule: **write the prediction before running anything.** A prediction that turns
out wrong is worth more than one that turns out right — it marks something I thought
I knew and didn't.

---

## Concepts

Reference notes. These are not experiments — no prediction, no failure observed.
Kept separate on purpose: the numbered entries below are things I *ran*, these
are things I *looked up*. Only the first kind survives an interview.

### Spring Framework vs. Spring Boot

They are two different products. The version numbers prove it — in this project
Boot is `3.5.3` and Framework is `6.2.8`.

Evidence from the jars on this project's classpath:

| Annotation | Jar it comes from | Which product |
|---|---|---|
| `@Autowired` | `spring-beans-6.2.8.jar` | Framework |
| `@Service` | `spring-context-6.2.8.jar` | Framework |
| `@RestController` | `spring-web-6.2.8.jar` | Framework |
| `@Transactional` | `spring-tx-6.2.8.jar` | Framework |
| `@SpringBootApplication` | `spring-boot-autoconfigure-3.5.3.jar` | Boot |
| `@ConditionalOnMissingBean` | `spring-boot-autoconfigure-3.5.3.jar` | Boot |

**Almost every annotation I use daily is Framework, not Boot.**

Rule that always works — read the package:
- `org.springframework.*` → Framework
- `org.springframework.boot.*` → Boot

**Framework gives the tools:** the IoC container (`ApplicationContext`), dependency
injection, the AOP proxy machinery behind `@Transactional`, MVC and the
`DispatcherServlet`, `JdbcTemplate`.

**Boot gives me not having to wire them:** auto-configuration, `application.yml`
binding, embedded Tomcat, starters, executable jar.

**The mechanism behind auto-configuration** is `@ConditionalOnMissingBean`. Boot ships
hundreds of configuration classes that say, in effect: *"if PostgreSQL is on the
classpath **and the developer did not declare a `DataSource`**, create one."* That
second condition is why Boot is opinionated but overridable — the moment I declare my
own bean, Boot steps aside.

Without Boot I would hand-write ~100-150 lines of plumbing (`DataSource`,
`EntityManagerFactory`, `JpaTransactionManager`, `DispatcherServlet`, `ObjectMapper`)
and pick every compatible version myself. That is what Spring with XML looked like —
which is what I actually did at Mapfre from 2013 to 2018. I have worked both sides of
this line.

### Defensive copies: `final` protects the reference, not the contents

A record gives me `final` fields. That is not the same as immutable.

```java
Map<String, String> fields = new HashMap<>();
fields.put("diagnosis", "fracture");

var doc = new Document(..., fields, ...);

fields.put("approved", "true");     // I mutate MY map, afterwards
doc.fields();                       // → { diagnosis, approved }  the document changed
```

Nobody touched the document. Both variables point at **the same map in memory** — I never
passed a copy, I passed the address.

**Analogy — Google Doc vs PDF:**

| Passing the map as-is | Sharing the Google Doc **link**. One document, two viewers. I edit, what they see changes. |
|---|---|
| `Map.copyOf(fields)` | Exporting a **PDF** and sending that. They get their own frozen copy. I keep editing mine; theirs does not move. And they cannot edit it either. |

`final` means *"this link cannot be replaced with another link"*. It says nothing about the
document the link points to. Protecting the reference does not protect the contents.

**The fix, last line of the compact constructor:**

```java
public Document {
    // ... validations ...
    fields = Map.copyOf(fields);
}
```

`Map.copyOf` does two things: copies the contents into a new map (breaking the link to the
original) and returns an **immutable** map (`put` throws `UnsupportedOperationException`).

**The real bug this prevents** — reusing a variable, which is the most ordinary thing in the world:

```java
Map<String, String> f = new HashMap<>();

f.put("diagnosis", "fracture");
var doc1 = new Document(..., f, ...);      // patient A

f.clear();
f.put("diagnosis", "sprain");
var doc2 = new Document(..., f, ...);      // patient B
```

Without `copyOf`, `doc1` and `doc2` share one map and **both end up saying "sprain"**.
Patient A's diagnosis is gone, and `doc1` was never touched. In a healthcare system that is
an incident, and no test that merely constructs one document will ever catch it.

**Second thing I learned here:** a compact constructor can **reassign** a component
(`fields = ...`). It is not only for validation — it is also the place to normalize: trim a
string, upper-case a code, or freeze a collection.

### Record patterns, and `yield` vs `return`

A **type pattern** (Java 16) binds the object; a **record pattern** (Java 21) takes it apart:

```java
case RequiredField rf            -> ... rf.fieldName() ...     // type pattern
case RequiredField(String name)  -> ... name ...               // record pattern
```

The components are bound directly in the `case` — no intermediate variable, no accessor call.

**`yield`:** a switch used as an *expression* has to produce a value in every branch. With a
single-expression arrow (`->`) that is implicit. With a block `{ }` it has to be stated:

```java
return  →  exits the whole METHOD
yield   →  returns the value of THIS switch branch
```

**No `default` branch, deliberately.** Over a sealed interface the compiler already knows the
complete set. A `default` would silently swallow any variant added later — which is exactly the
failure the sealed hierarchy exists to prevent.

### `IllegalArgumentException` vs `IllegalStateException`

Two different blames, not interchangeable:

```java
findById(id).orElseThrow(() -> new IllegalArgumentException("document not found: " + id));
if (document.status() != DRAFT) throw new IllegalStateException("only DRAFT can be submitted");
```

- **IllegalArgumentException** → the *caller* passed something wrong (an id that doesn't exist).
- **IllegalStateException** → the *object* is in a state that doesn't allow this operation
  (already approved, can't be submitted again).

Argument = the input is bad. State = the timing is bad. Picking the right one tells the caller
whether to fix what they sent or whether they called at the wrong moment.

`orElseThrow` is the "if it doesn't exist, fail" in one line: hands back the value if the
`Optional` is present, throws if it's empty.

### Why self-invocation breaks `@Transactional` (the proxy mechanism)

`@Transactional` is implemented with a **proxy** — Spring injects a wrapper around the bean, not
the bean itself. The transaction logic (open, commit, rollback) lives in the **wrapper**. The
bean itself knows nothing about transactions.

```
otherBean → PROXY.submit()        ← proxy INTERCEPTS: opens the transaction
                ↓
           realBean.submit()      ✅ transaction active
```

Self-invocation — a method calling another method on the same bean with `this`:

```
realBean.submit() {
    this.writeAudit();     ← goes straight object-to-object, never reaches the proxy
        ↓
    writeAudit()           ❌ proxy never saw the call → no transaction
}
```

**The exact why:** `this` is the real object, not the proxy. A `this.method()` call never leaves
the object, so the proxy is not in the path to intercept it. The annotation is there, but the
proxy — the only thing that reads it — was bypassed. It is NOT that "the proxy can't see the
annotation"; it's that the *call* never passes through the proxy.

**Fixes:** move the transactional method to a separate bean (the call then goes through *its*
proxy); self-inject the proxy; or use `TransactionTemplate` (programmatic, no proxy involved).

**Interview line:**
> "Spring implements `@Transactional` with a proxy that wraps the bean. The transaction lives in
> the proxy. When a method calls another method on the same bean with `this`, the call goes
> straight object-to-object and never passes through the proxy — so the annotation is there but
> nothing applies it. The fix is to move the transactional method to a separate bean."

### Why an audit trail needs `REQUIRES_NEW`, not just `@Transactional`

Two writes in a row, no transaction around them → each is independent, one can land and the
other not:

```java
auditRepository.save(...);              // write 1: "document moved to SUBMITTED"
documentRepository.save(withStatus());  // write 2: fails, DB went down
```

Result: an audit event saying something happened, and the document still in DRAFT. **The audit
lies.** In an audit system that is the exact failure that cannot happen.

`@Transactional` fixes *that* case — the two writes become one operation, both land or neither.
But it creates the opposite problem:

```
Scenario A — technical failure (DB down):   both writes should roll back.  @Transactional ✅
Scenario B — business rejection:            I want to record "rejected: amount exceeds limit"
                                            → if it's all one transaction and the reject rolls
                                              back, the audit of the rejection is erased too.  ❌
```

An audit that only records successes is not an audit — it's a log of the happy path.

**The combination:**

```
@Transactional               on the business method  → covers A (both roll back together)
@Transactional(REQUIRES_NEW)  on the audit write      → covers B (audit survives the rollback)
```

**The precise mechanism:** `REQUIRES_NEW` **commits the audit in its own transaction, before**
the business transaction decides whether to roll back. So it's not that the rollback "leaves the
audit alone" — the audit already left the business transaction and was committed, so by the time
the rollback happens there is nothing of it left inside to revert.

**Where this lives:** `@Transactional` belongs in the **infrastructure** layer, not in
`SubmitDocument`. The application layer stays Spring-free (the `grep` guard). Order of work:
interfaces → JPA adapters + a real DB → only then transactions, because `@Transactional` needs a
real database to have anything to commit or roll back.

**My anchor:** MOC has an append-only audit trail in a regulated domain — this is exactly the
`REQUIRES_NEW` case, not theory.

### `BigDecimal`: `compareTo`, never `equals`

```java
new BigDecimal("2.0").equals(new BigDecimal("2.00"))     → false
new BigDecimal("2.0").compareTo(new BigDecimal("2.00"))  → 0
```

`equals` compares value **and scale**. `2.0` and `2.00` are the same amount of money with
different scale, so `equals` says they differ. With money that is a silent bug.

---

## Day 1 — Build, toolchains, dependency management

### 1. Gradle toolchains vs. the JDK on my PATH

**Setup:** My terminal was using JDK17, but the gradle build was configured to use a java 21 toolchain

**Prediction:** I thought the JDK configured in my shell and the jsd used by Gradle had to be the same. +
I expected I would need to change my PATH or JAVA_HOME to java 21

**Observed:** The build succeeded even though my shell was using 17. Gradle compiled the project with java 21 because of the configured toolchain. However,  
When I tried to run the generated classes using a java 17 runtime, I got an UnsupportedClassVersionError because the classes had been compiled as major version 65 (Java 21). 

**Takeaway:** Compiling and running are independent process. Gradle toolchains determine wich JDK is used for compilation, while the runtime JVM depends on the environtmet that launches the application. A newer JVM can run older bytecode, but an older JVM can not run bytecode compiled by a newer JDK

**How I'd say it in an interview (≤90s):**
Before learning about toolchains, I thought the Java version configured in my terminal had to match the version used by the build. But that’s not how it works.

A Gradle toolchain specifies which JDK Gradle should use for compilation, independently of the JDK configured in my shell. For example, my terminal can still be using Java 17 while Gradle compiles the project with Java 21.

The important distinction is that compilation and execution are two different processes. The toolchain only affects the compiler (javac), not the runtime (java).

If I compile with Java 21, the generated classes use major version 65. Running those classes on a Java 17 JVM fails with UnsupportedClassVersionError, because Java 17 doesn’t understand that newer bytecode. On the other hand, running Java 17 bytecode on Java 21 works because Java maintains backward compatibility.
---

### 2. Removing the `io.spring.dependency-management` plugin

**Setup:**

**Prediction:**

**Observed:**

**What I got wrong:**

**Takeaway:**

**How I'd say it in an interview (≤90s):**

---

## Day 2 — Java 21 domain modelling

### 3. Implementing a sealed interface without being in `permits`

**Setup:** wrote `WeekendBlock`, a record declaring `implements Rule`, but did not add it
to the `permits` clause of `Rule`.

**Prediction:**

**Observed:** the build failed, and the error appeared in **`WeekendBlock.java`** — the file
of the class trying to join the hierarchy — not in `Rule.java`. The compiler rejected it for
not being listed in the `permits` clause.

**Takeaway:**

**How I'd say it in an interview (≤90s):**

---

### 4. Putting a fact about the document inside the rule

**Setup:** `ApprovalGate` needs to compare "how many signatures are required" against "how many
signatures this document has". I added **both** as components of `ApprovalGate`.

**Observed:** the compiler caught it, though not for the conceptual reason:

```
RuleEvaluator.java:27: error: incorrect number of nested patterns
            case ApprovalGate(int requiredSignatures) ->
                 ^
  required: int,int
  found: int
```

The record pattern in the switch no longer matched the record's arity.

**What I got wrong:** I mixed two things that live on different sides and change at different
rates.

```
ApprovalGate  =  the client's CONFIGURATION  →  one instance, shared
Document      =  one concrete document       →  thousands, each different
```

A rule is one per client. Signature counts vary per document — FAC-001 has 2, FAC-002 has 0.
They cannot live in the rule without creating a new rule instance per document, which destroys
the whole "rules are configuration" idea.

**Rule of thumb I take from this:**
- Does the value change per document? → it belongs on `Document`
- Is it the same for every document of that client? → it belongs on the `Rule`

**Takeaway:** the real mechanism is **data that changes at different rates belongs on
different sides.** A `Rule` is shared configuration — one instance per client, read by every
document that client submits. `signatures` is per-document state — a different value on every
document. Fusing them forces a *new rule instance per document*, which collapses the whole
"rules are configuration, not code" design back into per-document logic. The rule stays the
strategy (*how many signatures are required*); the document carries the fact (*how many it
has*); the evaluator compares them.

What made this cheap to catch was accidental but worth naming: the mistake surfaced as a
**compile error, not a runtime bug**. The record pattern `case ApprovalGate(int required)`
is tied to the record's arity, so the moment I added a second component the switch stopped
compiling. The type system turned a modelling error — mixing configuration with state — into
`javac` refusing to build. I didn't design that guard on purpose, but sealed interface +
record patterns gave it to me for free.

**How I'd say it in an interview (≤90s):**
My rules are data, one row per client — a rule is shared configuration. I almost put the
number of signatures a document *has* inside the `ApprovalGate` rule, next to the number it
*requires*. That's wrong: the requirement is the same for every document of that client, but
the count of signatures changes per document. Putting per-document state in the rule would
force a new rule instance for every document and destroy the "rules are configuration" idea.
The heuristic I use now is: if a value changes per document it lives on the document; if it's
the same for every document of that client it lives on the rule. And the thing that caught me
wasn't a test — it was the compiler. Because `Rule` is a sealed interface matched with record
patterns, adding a second field to the record broke the switch's arity and the build failed.
The type system enforced the separation of concerns for me.

---

## Day 2 — Scoping rules per transition

### 5. Changing the `TenantRules` port signature to scope rules by gate

**Setup:** "Same path, different rules" means the rules evaluated at *submit* are not the
same ones evaluated at *approve* — an `ApprovalGate(2)` must not reject a document at submit
time, because signatures are added *between* SUBMITTED and APPROVED. Introducing
`enum Gate { SUBMIT, APPROVE }` and changing the port from `forTenant(UUID)` to
`forTenant(UUID, Gate)`, so each use case asks only for the rules of its own gate.

**Prediction:**
- Compile: **1 call site breaks** — the line `tenantRules.forTenant(document.tenantId())`
  in `SubmitDocument`.
- Behavior: a Client-B document (`ApprovalGate(2)`, 0 signatures) submitted today lands in
  **REJECTED**; after moving `ApprovalGate` to the APPROVE gate it will land in **SUBMITTED**.

**Observed:**
- Compile: **2 sites, not 1**, and they surfaced in *two separate compilation phases*.
  First `compileJava` failed at the **caller**:

  ```
  SubmitDocument.java:45: error: method forTenant in interface TenantRules
      cannot be applied to given types;
  ```

  Gradle stopped there — so the count *looked* like 1. After fixing the caller,
  `compileTestJava` then failed at the **implementer**, the test fake:

  ```
  SubmitDocumentTest.java:207: error: StubTenantRules is not abstract and does not
      override abstract method forTenant(UUID,Gate) in TenantRules
  ```
- Behavior: **prediction held.** Before the change, `SubmitDocument` evaluated *every* tenant
  rule, so a Client-B document (`ApprovalGate(2)`, 0 signatures) failed the gate at submit →
  **REJECTED** — the latent bug. After scoping, submit asks only for `Gate.SUBMIT` rules, so
  `ApprovalGate` is no longer in its path and the same document reaches **SUBMITTED**. The gate
  now bites at *approve* instead: `ApproveDocumentTest` shows a SUBMITTED doc with 2 signatures
  → APPROVED, and with 0 signatures → REJECTED *at approval*. The rejection didn't disappear —
  it moved to the transition where signatures actually exist.

**What I got wrong:** I predicted **1 site** and counted only **callers** — the line that
*invokes* `forTenant`. The second break is a different kind: an **implementer**, the
`StubTenantRules` fake whose method signature no longer matches the interface. Changing a
method on an interface breaks both sides of it, not just the call. And I only saw one at a
time because Gradle compiles `main` before `test` and aborts at the first failing task.

**Takeaway:** an interface is a contract with **two kinds of dependants** — the code that
*calls* the method (callers) and the code that *implements* it (implementers). A signature
change breaks both. In production the implementers are the adapters; in tests they are the
fakes — and the fakes are the ones easy to forget, because I think of tests as "code that
uses" the port, not "code that implements" it. The compiler finds every one of them, but
Gradle's task ordering (`compileJava` → `compileTestJava`, abort on first failure) means the
break count is revealed incrementally, not all at once — so "it compiled after I fixed the
obvious one" is not the same as "I found them all".

**How I'd say it in an interview (≤90s):**
I changed one method on a port interface — added a parameter. My instinct was that it breaks
"the call sites", and I pictured the one line that calls it. But an interface has two kinds
of dependants: callers and implementers. The caller broke as expected, but so did the test
fake that *implements* the interface — a break I didn't count, because I think of a fake as
something that uses the port, not something bound to its exact signature. The other detail:
Gradle compiles main before test and stops at the first failure, so I only saw one error,
fixed it, and only then did the second surface. The lesson is that the compiler will find
every dependant of a contract, but the build tool reveals them one phase at a time — so a
green compile after fixing the first isn't proof you'd found them all.

---

## Day 3 — Persistence and the N+1 problem

### 6. Counting SQL statements to prove the N+1 when listing documents with history

**Setup:** `GET /api/documents` returns every document with its audit history. The mapping
reads each document's lazy `auditEvents` collection. With Hibernate statistics on, seed **N**
documents (each with ≥1 audit event), reset the counter, call the naive listing, and read
`Statistics.getPrepareStatementCount()`. Then add an `@EntityGraph(attributePaths =
"auditEvents")` listing and count again.

**Prediction:** (mine — revisit in the study session)
- Naive listing over N documents fires **1 + N** statements (1 to load the documents, then 1
  per document to lazy-load its audit collection).
- With the entity graph it drops to **1** statement (a single left join, roots de-duplicated).

**Observed:** measured with Hibernate `Statistics.getPrepareStatementCount()`:

```
>>> naive listing of 12 documents used 13 statements
>>> fetch-joined listing of 7 documents used 1 statements
```

Naive = **13 for 12 documents** = exactly `1 + N`. Fetch-joined = **1**, flat. Prediction held.

**Takeaway:** the N+1 is not a query that is *slow*, it is a *count* of queries that grows
with the data. The lazy `@OneToMany` makes each `document.getAuditEvents()` a separate
`select`, so a list endpoint that reads the collection per row does 1 query to load the roots
plus 1 per root — invisible on 2 rows in a demo, fatal on 10,000 in production. The fix is a
single `left join fetch ... distinct`: one statement loads roots and children together, and
`distinct` collapses the roots that the join multiplied. The lever is *how many round-trips to
the database*, and I can prove the fix with a number (`getPrepareStatementCount()`), not by
squinting at logs. Trade-off to name out loud: fetch-joining a collection can't be paginated
at the DB (the join multiplies rows), so for large collections you reach for `@BatchSize` or a
second batched query instead — the join fetch wins when the collection per root is small and
bounded, which an audit trail is.

**How I'd say it in an interview (≤90s):**
N+1 is when loading N things costs 1 query for the list plus 1 more for each item's lazy
relationship — N+1 round-trips instead of one. I hit it listing documents with their audit
history: the audit collection is a lazy `@OneToMany`, so building the response touched it once
per document. I proved it with Hibernate statistics — 12 documents produced 13 statements,
exactly 1+N. The fix was one JPQL `left join fetch` with `distinct`, which loads everything in
a single statement; re-measured, the same listing dropped to 1. The nuance I'd add is that a
fetch-join can't be paginated because the join multiplies rows, so for big child collections
I'd use `@BatchSize` or a separate batched load instead. Here the audit trail per document is
small and bounded, so the join fetch is the right call.

---

## Day 3 — Transaction propagation

### 7. Making the audit survive a business rollback with REQUIRES_NEW

**Setup:** Annotate `JpaAuditRepository.save` with `@Transactional(propagation = REQUIRES_NEW)`
so the audit commits in its own transaction, suspended from the caller's. Then, inside a
`TransactionTemplate` (the outer "business" transaction): write an audit for a committed
DRAFT document, change the document to SUBMITTED, and throw — rolling the outer transaction
back. Assert what remains in the database.

**Prediction:** (mine — revisit in the study session)
- The document change is **reverted** → still DRAFT (it lived in the outer tx that rolled back).
- The audit row **survives** → present (REQUIRES_NEW committed it in its own tx before the
  rollback happened). If the audit were plain `REQUIRED`, it would join the outer tx and be
  erased too.

**Observed:** test green. After the outer `TransactionTemplate` threw and rolled back, the
document was still `DRAFT` (the `withStatus(SUBMITTED)` update reverted) and exactly one new
audit row was present for that document. Prediction held on both counts.

**Takeaway:** propagation is about *transaction boundaries*, not about the annotation being
"on" or "off". `REQUIRED` (the default) means "join the caller's transaction if there is one" —
so an audit written inside the business transaction shares its fate and is erased on rollback.
`REQUIRES_NEW` means "suspend the caller's transaction and run in a brand-new one that commits
independently". The precise mechanism for why the audit survives: it is *not* that the rollback
"spares" the audit — the audit already left the business transaction and was committed to the
database in its own transaction, so by the time the outer rollback runs there is nothing of it
left inside to revert. The trade-off to name: this is the right choice for auditing a
*decision* (append-only truth about what was decided), but it means a "moved to SUBMITTED"
audit can commit even if the subsequent business save fails — so what you audit with
REQUIRES_NEW should be the decision/attempt, not a claim about final state. Two dangers this
also surfaces: self-invocation (calling the REQUIRES_NEW method through `this` bypasses the
proxy, so no new transaction is created — see the concepts note), and connection exhaustion
(the new tx borrows a *second* pooled connection while the first is suspended).

**How I'd say it in an interview (≤90s):**
An audit trail has to record that a decision was made even if the business operation that
triggered it rolls back. If the audit write joins the same transaction — propagation REQUIRED,
the default — a rollback erases the audit along with the business change, so you end up with an
audit that only ever records successes. The fix is REQUIRES_NEW on the audit write: it suspends
the outer transaction and commits the audit in its own, so it's already on disk before the
outer transaction decides to roll back. I proved it with a test — force a rollback after
writing the audit, and afterwards the document change is gone but the audit row is there. Two
gotchas I'd mention: it only works if the call goes through the Spring proxy, so a
self-invocation inside the same bean silently does nothing; and the suspended transaction holds
its connection while the new one borrows a second, so under load you can exhaust the pool.

---

## Day 3 — The dual-write problem and the outbox

### 8. Writing the event to an outbox in the business transaction

**Setup:** On approval the service must do two things: persist the APPROVED document and
publish a `DocumentApproved` event to a broker. Those are two different systems, so they cannot
share a transaction. Instead of publishing to the broker inline, `OutboxEventPublisher` inserts
a row into an `outbox` table using the caller's transaction (default REQUIRED). A separate
relay ships unpublished rows to the broker later.

**Prediction:** (mine — revisit in the study session) approving writes exactly one unpublished
`DocumentApproved` outbox row, atomically with the document and audit. If the approval
transaction rolled back, there would be no row.

**Observed:** integration test green — after approving, `outbox` holds one row for the
document, `type = "DocumentApproved"`, `published_at = null`, payload containing the document
id. (Storing the pre-serialized JSON string into a `jsonb` column via
`@JdbcTypeCode(SqlTypes.JSON)` worked without double-encoding.)

**Takeaway:** the dual-write problem is that a write to the database and a publish to a broker
can't be made atomic — whichever happens second can fail after the first succeeded, so you
either lose events (DB commits, publish crashes) or emit phantom events (publish succeeds, DB
rolls back). The outbox sidesteps it by turning the two-system write into a **one-system
write**: the event becomes a row in the *same* database, captured in the *same* transaction as
the business change, so it inherits that transaction's atomicity. Delivery to the broker is
then a *separate* concern handled by a relay reading the table — which gives **at-least-once**
delivery (the relay can crash after sending but before marking the row published, so it may
resend; consumers must therefore be idempotent). The trade-off is added latency (poll interval)
and the need for that relay; the alternative, a distributed/XA transaction across DB and
broker, is slow, poorly supported, and something almost nobody runs in practice.

**How I'd say it in an interview (≤90s):**
The dual-write problem: on approval I need to update the database and publish an event, but
they're two systems and can't share a transaction, so a crash between them either loses the
event or publishes one for a change that rolled back. The outbox pattern fixes it by writing
the event as a row in the same database, in the same transaction as the business change — now
it's a single atomic write. A separate relay polls the outbox and ships rows to the broker,
marking them published. That decouples the business commit from delivery and gives
at-least-once semantics: the relay might resend after a crash, so the consumer has to be
idempotent. The cost is a bit of latency and the relay process, but it avoids XA/two-phase
commit, which is slow and barely supported anywhere.

---

## Day 3 — Idempotent consumption across services

### 9. Consuming approval events exactly-once in effect (idempotency + DLQ)

**Setup:** A second service (`consumer-service`, its own module and its own `consumer`
database) polls the SQS queue the relay ships to. Each message carries an `eventId` attribute.
`ApprovalConsumer.handle(eventId, body)` checks `processed_events` for that id: if present it is
a no-op, otherwise it records the id and does the work. The message is deleted only *after*
handling; on failure it is left on the queue, redelivered, and after `maxReceiveCount` (3) moved
to the dead-letter queue by the redrive policy.

**Prediction:** (mine — revisit in the study session) calling `handle` twice with the same
`eventId` does the work once (`true` then `false`) and leaves a single `processed_events` row;
a message sent to SQS is received, handled, and recorded end to end.

**Observed:** both green. First `handle` returned true and inserted one row; the second
returned false and inserted nothing. The e2e test sent a message to SQS, `poll()` received it,
handled it, deleted it, and `processed_events` held the row.

**Takeaway:** at-least-once delivery (from the outbox relay, and from SQS itself, which can
redeliver) means a consumer *will* see duplicates — so correctness can't depend on each message
arriving once; it has to come from the *consumer* being idempotent. The pattern is a dedup key
carried with the message (here the event id) and a record of keys already handled, checked
before doing the work. Two ordering rules make it safe: record-then-delete (delete only after
the effect is durable, so a crash before delete just causes a harmless redelivery that the
dedup catches), and never delete on failure (so a poison message is retried and, past
`maxReceiveCount`, parked in the DLQ instead of blocking the queue or being lost). The DLQ is
the escape hatch that keeps one bad message from stalling everything while preserving it for
inspection. Net effect: delivery is at-least-once, but the *observable outcome* is exactly-once.

**How I'd say it in an interview (≤90s):**
In a queue-based system delivery is at-least-once — the broker can redeliver, and an outbox
relay can resend after a crash — so a consumer has to assume it'll see the same message more
than once. I make the consumer idempotent: every event carries an id, and before doing the work
I check a processed-events table for that id; if it's there, I skip. Two rules keep it correct:
I only delete the message after the effect is committed, so a crash in between just causes a
redelivery that the dedup absorbs; and I never delete a message that failed to process, so it
gets retried and, after a few attempts, the redrive policy moves it to a dead-letter queue
where it can't block the others and I can inspect it. So the transport is at-least-once but the
business effect is exactly-once.

---

<!--
TEMPLATE — copy this block for each new experiment.

### N. <short title of the experiment>

**Setup:** what I changed, in one or two lines.

**Prediction:** what I expect to happen, written BEFORE running it.
Commit to one outcome. No hedging.

**Observed:** what actually happened. Paste the real error or the real numbers.
Not a summary — the raw output.

**What I got wrong:** only if the prediction missed. Name the wrong assumption
explicitly, not just "I was wrong".

**Takeaway:** the mechanism, in my own words. If I can't explain the mechanism,
I only observed a symptom.

**How I'd say it in an interview (≤90s):** mechanism → where I hit it →
trade-off → stop talking.
-->
