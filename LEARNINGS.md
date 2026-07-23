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
