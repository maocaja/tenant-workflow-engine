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
