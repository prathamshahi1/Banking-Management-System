# Banking Management System — Comprehensive Technical & Interview Guide

This document is your complete study and interview preparation handbook. It breaks down every design decision, architectural pattern, Spring Boot concept, database optimization, security mechanism, and tricky interview question related to this project.

---

## Table of Contents
1. [The 30-Second & 2-Minute Project Pitch](#1-the-30-second--2-minute-project-pitch)
2. [High-Level Architecture & Layered Design](#2-high-level-architecture--layered-design)
3. [Core Banking Domain & Entity Design](#3-core-banking-domain--entity-design)
4. [Transaction Management & Concurrency Deep-Dive](#4-transaction-management--concurrency-deep-dive)
5. [Spring Security & JWT Authentication Architecture](#5-spring-security--jwt-authentication-architecture)
6. [Validation & Centralized Exception Handling](#6-validation--centralized-exception-handling)
7. [Testing Strategy (Unit, Integration & Concurrency)](#7-testing-strategy-unit-integration--concurrency)
8. [Comprehensive Interview Q&A (Categorized)](#8-comprehensive-interview-qa-categorized)
   - [Java 21 & Core Concepts](#category-1-java-21--core-concepts)
   - [Spring Boot & Architecture](#category-2-spring-boot--architecture)
   - [Spring Data JPA & Hibernate](#category-3-spring-data-jpa--hibernate)
   - [Transactions & Concurrency (Must Know)](#category-4-transactions--concurrency-must-know)
   - [Spring Security & JWT](#category-5-spring-security--jwt)
   - [System Behavior & Edge Cases](#category-6-system-behavior--edge-cases)

---

## 1. The 30-Second & 2-Minute Project Pitch

### The 30-Second Elevator Pitch
> *"I built a production-grade Banking Management System backend using Java 21, Spring Boot 3, Spring Security with stateless JWT, Spring Data JPA, and MySQL. The system simulates core banking operations including customer onboarding, account lifecycle, deposits, withdrawals, and fund transfers. The standout technical highlights are thread-safe balance manipulation using pessimistic database row locking, deadlock-free fund transfers via strict lock ordering, and an automated multi-threaded test suite verifying concurrency safety."*

### The 2-Minute In-Depth Project Walkthrough
Use the **STAR method** (Situation, Task, Action, Result):

1. **Situation & Task:**
   *"As a graduating computer science student aiming for backend engineering roles, I wanted to build a project that moved beyond basic CRUD and addressed real-world backend challenges like financial data integrity, race conditions, and role-based access security."*

2. **Architecture & Action:**
   *"I architected a modular monolithic REST application following a strict layered architecture: Controller -> Service -> Repository -> MySQL.
   - For security, I implemented stateless JWT authentication with Spring Security 6, custom filters, and BCrypt password hashing.
   - For financial operations, I used `BigDecimal` for arbitrary-precision arithmetic to avoid floating-point rounding errors.
   - To handle concurrent operations, I used JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) so multiple simultaneous withdrawal requests cannot overspend an account.
   - For fund transfers between two accounts, I implemented Dijkstra's resource hierarchy strategy by ordering lock acquisition based on account IDs to prevent database deadlocks.
   - I used `@RestControllerAdvice` for centralized exception translation and Jakarta Bean Validation for robust input defense."*

3. **Result:**
   *"The application includes 23 automated unit and integration tests using JUnit 5, Mockito, and an isolated in-memory H2 database. One of the integration tests uses `ExecutorService` and `CountDownLatch` to simulate 10 concurrent threads attempting withdrawals simultaneously, proving that pessimistic locking prevents overspending."*

---

## 2. High-Level Architecture & Layered Design

```
                     ┌─────────────────────────────┐
                     │   Client / Postman / App    │
                     └──────────────┬──────────────┘
                                    │ HTTP Request (JSON + Bearer Token)
                                    ▼
                     ┌─────────────────────────────┐
                     │    JwtAuthenticationFilter  │ (Extends OncePerRequestFilter)
                     └──────────────┬──────────────┘
                                    │ Validated SecurityContext
                                    ▼
                     ┌─────────────────────────────┐
                     │      Controller Layer       │ (AuthController, AccountController,
                     │  - Input Validation (@Valid)│  TransactionController, AdminController)
                     │  - HTTP Status Codes        │
                     └──────────────┬──────────────┘
                                    │ Request DTOs
                                    ▼
                     ┌─────────────────────────────┐
                     │       Service Layer         │ (AuthService, AccountService,
                     │  - Business Rules & Logic   │  TransactionService, AdminService)
                     │  - @Transactional Boundaries│
                     │  - Pessimistic Row Locking  │
                     └──────────────┬──────────────┘
                                    │ Entities
                                    ▼
                     ┌─────────────────────────────┐
                     │      Repository Layer       │ (UserRepository, AccountRepository,
                     │  - Spring Data JPA          │  TransactionRepository)
                     │  - Custom JPQL Queries      │
                     └──────────────┬──────────────┘
                                    │ SQL (Prepared Statements)
                                    ▼
                     ┌─────────────────────────────┐
                     │       MySQL Database        │ (users, accounts, transactions)
                     └─────────────────────────────┘
```

### Why Layered Architecture?
1. **Single Responsibility Principle (SRP):** Each layer has exactly one job:
   - Controllers handle HTTP serialization, routing, and status codes.
   - Services execute business rules, validation, and transaction boundaries.
   - Repositories handle database persistence and queries.
2. **Testability:** Service classes can be tested in isolation with Mockito without starting an HTTP server or a database.
3. **Maintainability:** Changing the database provider or migrating to another persistence strategy requires modifying only the repository layer, leaving business logic intact.

### Why DTOs (Data Transfer Objects) instead of JPA Entities?
- **Security:** Prevents mass-assignment vulnerabilities (e.g., an attacker sending `"role": "ADMIN"` or `"balance": 999999` in a JSON payload).
- **Decoupling:** Database schemas evolve independently from public API contracts.
- **Performance & Serialization:** Entities with bidirectional relationships can trigger infinite recursion during Jackson JSON serialization (`StackOverflowError`) or lazy loading exceptions (`LazyInitializationException`).

---

## 3. Core Banking Domain & Entity Design

### 1. User Entity (`users`)
- `id` (Long, PK, Auto-increment)
- `fullName` (VARCHAR 100, NOT NULL)
- `email` (VARCHAR 120, UNIQUE, NOT NULL) — Used as the principal/username for authentication.
- `password` (VARCHAR 255, NOT NULL) — Stored as a salted BCrypt hash (never plain text).
- `role` (ENUM: `CUSTOMER`, `ADMIN`, NOT NULL)
- `createdAt` (DATETIME, NOT NULL, set via `@PrePersist`)

### 2. Account Entity (`accounts`)
- `id` (Long, PK, Auto-increment)
- `accountNumber` (VARCHAR 20, UNIQUE, NOT NULL) — 10-digit unique generated number.
- `accountType` (ENUM: `SAVINGS`, `CURRENT`, NOT NULL)
- `balance` (DECIMAL 19, 2, NOT NULL) — Starts at `0.00`.
- `user_id` (FK to `users`, `@ManyToOne(fetch = FetchType.LAZY)`)
- `active` (BOOLEAN, NOT NULL) — Flags whether account can perform transactions.
- `createdAt` (DATETIME, NOT NULL)

### 3. Transaction Entity (`transactions`)
- `id` (Long, PK, Auto-increment)
- `transactionReference` (VARCHAR 64, UNIQUE, NOT NULL) — e.g., `DEP-1732...`, `TRF-...`.
- `transactionType` (ENUM: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, NOT NULL)
- `amount` (DECIMAL 19, 2, NOT NULL)
- `source_account_id` (FK to `accounts`, NULL for external cash deposits)
- `destination_account_id` (FK to `accounts`, NULL for cash withdrawals)
- `transactionDate` (DATETIME, NOT NULL)
- `description` (VARCHAR 255)

---

## 4. Transaction Management & Concurrency Deep-Dive

### Why `BigDecimal` for Money?
In Java, `double` and `float` use binary IEEE 754 floating-point representation. They cannot represent fractions like `0.1` or `0.01` with exact precision.
```java
// Danger with double:
double a = 1.00;
double b = 0.90;
System.out.println(a - b); // Prints: 0.09999999999999998 (1 cent lost!)

// Solution with BigDecimal:
BigDecimal x = new BigDecimal("1.00");
BigDecimal y = new BigDecimal("0.90");
System.out.println(x.subtract(y)); // Prints: 0.10 (Exact!)
```
In our project:
- `balance` and `amount` are `BigDecimal` with scale 2 (`RoundingMode.HALF_UP`).
- In MySQL, they map to `DECIMAL(19, 2)`.

---

### Concurrency Problem: Race Conditions & Overspending

Imagine an account with **$100.00**.
Two ATM withdrawal requests for **$100.00** arrive at the exact same millisecond across two threads:

```
Thread 1 (Withdraw $100)                     Thread 2 (Withdraw $100)
       │                                            │
       ├─ Reads Balance: $100                       ├─ Reads Balance: $100
       ├─ Balance ($100) >= $100? YES               ├─ Balance ($100) >= $100? YES
       ├─ New Balance: $100 - $100 = $0             ├─ New Balance: $100 - $100 = $0
       ├─ Saves Balance: $0                         ├─ Saves Balance: $0
       ▼                                            ▼
Outcome: $200 dispensed, but account balance only reduced by $100! (BANK LOSES $100)
```

### Solution: Pessimistic Row Locking (`PESSIMISTIC_WRITE`)
In `AccountRepository.java`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Optional<Account> findByIdWithLock(@Param("id") Long id);
```
When this query runs inside a `@Transactional` block, Hibernate generates:
```sql
SELECT * FROM accounts WHERE id = ? FOR UPDATE;
```
Now:
1. Thread 1 executes `SELECT ... FOR UPDATE` and obtains an exclusive database row lock.
2. Thread 2 attempts to read the same account and is **blocked** at the database level until Thread 1 commits.
3. Thread 1 deducts $100, balance becomes $0.00, and commits.
4. Thread 2 unblocks, reads the newly committed balance (**$0.00**), evaluates `$0.00 >= $100`, throws `InsufficientBalanceException`, and safely aborts!

---

### Concurrency Problem: Deadlocks in Mutual Fund Transfers

Suppose User 1 (Account 10) transfers to User 2 (Account 20), while at the exact same moment User 2 transfers to User 1:

```
Thread 1 (Transfer 10 -> 20)                 Thread 2 (Transfer 20 -> 10)
       │                                            │
       ├─ Locks Account 10                          ├─ Locks Account 20
       ├─ Tries to lock Account 20                  ├─ Tries to lock Account 10
       │  (Waiting for Thread 2...)                 │  (Waiting for Thread 1...)
       ▼                                            ▼
                    DEADLOCK! Both threads frozen forever!
```

### Solution: Lock Acquisition Ordering (Dijkstra's Resource Hierarchy)
In `TransactionService.java`:
```java
// Determine lock order based on numerical ID:
Long firstId = Math.min(request.getSourceAccountId(), request.getDestinationAccountId());
Long secondId = Math.max(request.getSourceAccountId(), request.getDestinationAccountId());

// Always lock the smaller ID first, then the larger ID:
Account firstAccount = accountRepository.findByIdWithLock(firstId)
        .orElseThrow(...);
Account secondAccount = accountRepository.findByIdWithLock(secondId)
        .orElseThrow(...);

Account sourceAccount = firstAccount.getId().equals(request.getSourceAccountId()) ? firstAccount : secondAccount;
Account destinationAccount = firstAccount.getId().equals(request.getDestinationAccountId()) ? firstAccount : secondAccount;
```
**Why this completely prevents deadlock:**
Both Thread 1 and Thread 2 will attempt to lock **Account 10 first**. Whoever gets Account 10 proceeds; the other waits before locking anything. Circular wait condition ($A \rightarrow B$ and $B \rightarrow A$) is mathematically impossible!

---

### What does `@Transactional` do?
`@Transactional` uses Spring AOP (Aspect-Oriented Programming).
1. Spring creates a dynamic proxy around the service bean.
2. When a transactional method is entered, the proxy begins a database transaction (`connection.setAutoCommit(false)`).
3. If the method completes normally, the proxy calls `connection.commit()`.
4. If a `RuntimeException` (or `Error`) is thrown, the proxy calls `connection.rollback()`.
   - E.g., if debiting Account 1 succeeds but crediting Account 2 throws an exception, all changes roll back; Account 1's balance is restored!

---

## 5. Spring Security & JWT Authentication Architecture

```
Incoming Request
      │
      ▼
[SecurityFilterChain]
      │
      ├──> 1. AbstractHttpConfigurer::disable (CSRF disabled for stateless REST)
      ├──> 2. SessionCreationPolicy.STATELESS (No HttpSession stored on server)
      ├──> 3. Authorize URL rules:
      │         - /api/auth/**      -> permitAll()
      │         - /api/admin/**     -> hasRole('ADMIN')
      │         - /api/accounts/**  -> authenticated()
      │         - /api/transactions/** -> authenticated()
      │
      └──> 4. JwtAuthenticationFilter (Before UsernamePasswordAuthenticationFilter)
                  │
                  ├─ Check "Authorization" header for "Bearer <token>"
                  ├─ If found:
                  │    ├─ Extract email via JwtService
                  │    ├─ Validate token signature and expiration
                  │    ├─ Load CustomUserDetails from CustomUserDetailsService
                  │    └─ Build UsernamePasswordAuthenticationToken and set
                  │       into SecurityContextHolder
                  │
                  └─ Continues filter chain
```

### Key Security Implementations:
1. **Stateless Sessions:** The server stores no session IDs in RAM or cookies. Each request is independently validated via the signature of the client's JWT token.
2. **BCrypt Password Hashing:** 
   - BCrypt automatically generates a cryptographic salt.
   - It is an adaptive function with configurable work factor (default 10 rounds), protecting passwords against brute force and rainbow table attacks.
3. **Defense Against Privilege Escalation:**
   - In `AuthService.java`, the registration method explicitly sets `user.setRole(Role.CUSTOMER)`. A client cannot pass `"role": "ADMIN"` to gain admin access.
4. **Ownership Verification (Horizontal Privilege Escalation Protection):**
   - Even if an authenticated user with a valid JWT attempts to withdraw from or view Account ID `42`, the service verifies:
     ```java
     if (user.getRole() != Role.ADMIN && !account.getUser().getId().equals(user.getId())) {
         throw new UnauthorizedOperationException("You do not have permission...");
     }
     ```

---

## 6. Validation & Centralized Exception Handling

### Input Validation with Jakarta Bean Validation
Controller parameters are marked with `@Valid`. Request DTOs define constraints:
- `@NotBlank(message = "Email is required")`
- `@Email(message = "Email must be valid")`
- `@Size(min = 6, message = "Password must be at least 6 characters")`
- `@DecimalMin(value = "0.01", message = "Amount must be greater than zero")`
- `@Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")`

### Centralized Exception Handler (`@RestControllerAdvice`)
Instead of ugly Java stack traces or 500 Internal Server Errors, `GlobalExceptionHandler.java` catches exceptions and formats them into a uniform `ErrorResponse`:

```json
{
  "status": 400,
  "message": "Insufficient account balance. Available: 200.00, Requested: 500.00",
  "timestamp": "2026-11-22T14:30:00"
}
```

| Exception Type | HTTP Status Code | Trigger Condition |
|---|---|---|
| `ResourceNotFoundException` | `404 Not Found` | Account or User ID does not exist |
| `InsufficientBalanceException` | `400 Bad Request` | Withdrawal or transfer exceeds balance |
| `InvalidTransactionException` | `400 Bad Request` | Negative amount, transfer to same account |
| `AccountInactiveException` | `400 Bad Request` | Attempting transaction on deactivated account |
| `DuplicateResourceException` | `409 Conflict` | Registering an email that already exists |
| `UnauthorizedOperationException`| `403 Forbidden` | Accessing another customer's bank account |
| `BadCredentialsException` | `401 Unauthorized` | Incorrect email or password during login |
| `MethodArgumentNotValidException`| `400 Bad Request` | Bean validation failures (e.g. invalid email) |

---

## 7. Testing Strategy (Unit, Integration & Concurrency)

Our test suite uses a dual-testing approach:

### 1. Unit Tests (`src/test/java/.../service/`)
- Uses **JUnit 5** and **Mockito**.
- Runs in milliseconds because it isolates the class under test by mocking repositories and Spring components (`@Mock`, `@InjectMocks`).
- Tests business edge cases: duplicate emails, negative amounts, inactive accounts, unauthorized access.

### 2. Integration & Concurrency Tests (`src/test/java/.../integration/`)
- Uses `@SpringBootTest` with `@ActiveProfiles("test")`.
- Automatically connects to an isolated **in-memory H2 database** (`MODE=MySQL`) so live MySQL records are untouched.
- **The Concurrency Test (`testConcurrentWithdrawalsSafety`):**
  ```java
  // Seeds an account with 500 balance.
  // Fires 10 concurrent threads simultaneously requesting 100 each.
  // 10 * 100 = 1000 requested, but only 500 exists!
  ```
  Using `ExecutorService` and `CountDownLatch`, all 10 threads hit `transactionService.withdraw()` at the same microsecond.
  - Exactly **5 threads succeed** (5 * 100 = 500 deducted).
  - Exactly **5 threads fail** with `InsufficientBalanceException`.
  - The final balance is guaranteed to be **`0.00`** and never negative.
  - This test is direct proof of the effectiveness of our pessimistic locking implementation!

---

## 8. Comprehensive Interview Q&A (Categorized)

---

### Category 1: Java 21 & Core Concepts

#### Q1: Why did you choose Java 21 for this project?
**Answer:** Java 21 is an official Long-Term Support (LTS) release. It brings performance optimizations, pattern matching improvements, record patterns, and Virtual Threads capability. For our project, it provides strong type safety, modern language constructs, and compatibility with Spring Boot 3.x.

#### Q2: Why did you use `BigDecimal` instead of `double` or `float` for balances and transactions?
**Answer:** `double` and `float` are IEEE 754 binary floating-point types designed for scientific computations where speed is prioritized over precision. They cannot accurately represent decimal fractions like `0.1` (binary representation has an infinite repeating fraction), leading to cumulative rounding errors. In banking, losing even fractions of a cent is unacceptable. `BigDecimal` provides arbitrary-precision signed decimal numbers, allowing exact arithmetic with explicit scale and rounding modes.

#### Q3: What is the difference between `BigDecimal.valueOf(double)` and `new BigDecimal("0.1")`?
**Answer:** Always prefer the String constructor `new BigDecimal("0.1")`. The `double` constructor `new BigDecimal(0.1)` actually creates `0.1000000000000000055511151231257827021181583404541015625` because `0.1` cannot be represented exactly as a double. The String constructor preserves exact decimal precision.

#### Q4: How does BCrypt hashing work in Java?
**Answer:** BCrypt is a password-hashing function based on the Blowfish cipher. It automatically generates a unique cryptographic salt and embeds it in the hash string. It includes an adjustable work factor (cost parameter) so that hashing can be made deliberately slower over time to counter increases in computer hardware speed. Unlike MD5 or SHA-256, it is immune to rainbow table attacks because of its unique salt per hash.

---

### Category 2: Spring Boot & Architecture

#### Q5: Can you explain the flow of a request in your Banking Management System?
**Answer:** 
1. The client sends an HTTP request with an `Authorization: Bearer <token>` header.
2. The request passes through `JwtAuthenticationFilter`, which verifies the token signature and expiration, retrieves the user, and populates Spring's `SecurityContextHolder`.
3. The request hits the matching `@RestController` (e.g. `TransactionController`), where `@Valid` triggers Jakarta Bean Validation on the request DTO.
4. The controller delegates the validated DTO to the `@Service` layer (e.g. `TransactionService`).
5. Inside the service, `@Transactional` opens a database transaction. The service acquires a pessimistic row lock via the repository, applies business checks, updates balances, and creates an audit record.
6. The repository executes SQL queries against MySQL.
7. The transaction commits. The entity is converted to a response DTO and returned to the client as JSON with the appropriate HTTP status code (`200 OK`, `201 Created`).

#### Q6: Why did you choose constructor injection instead of `@Autowired` field injection?
**Answer:** 
1. **Immutability:** Dependencies can be marked `final`, ensuring they are initialized once and never modified.
2. **Testability:** In unit tests, we can instantiate the service using plain Java (`new AccountService(mockAccountRepo, mockUserRepo)`) without relying on reflection or Spring test runners.
3. **Prevents NullPointerExceptions:** Constructor injection prevents objects from being created in an incomplete, un-injected state.
4. **Detects circular dependencies early:** Spring detects circular dependencies at application startup rather than at runtime.

#### Q7: Why do we have separate DTOs for requests and responses?
**Answer:** 
- Request DTOs carry only the fields the client is permitted to provide, annotated with input validation rules (`@NotBlank`, `@DecimalMin`).
- Response DTOs control exactly what information is sent back to the client. For example, `UserResponse` exposes `id`, `fullName`, `email`, and `role`, but never the `password` hash.

---

### Category 3: Spring Data JPA & Hibernate

#### Q8: What is `fetch = FetchType.LAZY`, and why did you use it on `@ManyToOne`?
**Answer:** By default, JPA `@ManyToOne` relationships use `FetchType.EAGER`, which means whenever an `Account` is loaded, Hibernate executes a join or secondary query to immediately load the associated `User`. This causes the infamous $N+1$ query problem when loading a list of accounts. `FetchType.LAZY` instructs Hibernate to load the associated `User` only when its getter is explicitly accessed, significantly optimizing memory and query count.

#### Q9: What does `@PrePersist` do in your entities?
**Answer:** `@PrePersist` is a JPA entity lifecycle callback. In `User.java` and `Account.java`, it executes right before the `INSERT` SQL statement is fired to automatically populate metadata like `createdAt = LocalDateTime.now()`, ensuring timestamps are consistent and not forgotten in service code.

#### Q10: What is the difference between `spring.jpa.hibernate.ddl-auto=update` and production database migration?
**Answer:** `ddl-auto=update` is convenient for local development because Hibernate automatically alters tables and columns to match entity classes. However, in enterprise production environments, `ddl-auto` is set to `validate` or `none`, and dedicated database migration tools like **Flyway** or **Liquibase** are used to maintain version-controlled, reproducible SQL migration scripts.

---

### Category 4: Transactions & Concurrency (Must Know)

#### Q11: What is the difference between Optimistic Locking and Pessimistic Locking?
**Answer:** 
- **Optimistic Locking (`@Version`):** Assumes conflicts are rare. It reads records without database locks and checks an integer version column before writing. If another transaction changed the version, an `OptimisticLockException` is thrown, requiring the application to retry.
- **Pessimistic Locking (`PESSIMISTIC_WRITE`):** Assumes conflicts are likely. It locks the row at the database level (`SELECT ... FOR UPDATE`) when reading. Any other transaction trying to modify or read-for-update that row is suspended until the lock is released.
- **Why Pessimistic Locking here:** In high-concurrency banking systems, retrying failed optimistic transactions during rapid balance debits can lead to poor user experience and complex retry logic. Pessimistic locking guarantees immediate strict serialization of updates.

#### Q12: How did you prevent deadlocks during fund transfers?
**Answer:** Deadlocks occur in transfers when two users simultaneously transfer money to each other, creating a circular wait condition where Thread 1 holds Account A waiting for B, and Thread 2 holds Account B waiting for A.
I eliminated this by enforcing a **consistent lock ordering protocol** using the numerical Account IDs:
```java
Long firstId = Math.min(sourceId, destId);
Long secondId = Math.max(sourceId, destId);
accountRepository.findByIdWithLock(firstId);
accountRepository.findByIdWithLock(secondId);
```
Because every thread locks accounts in ascending ID order, Thread 1 and Thread 2 both compete for the lower ID first. Circular wait is eliminated, making deadlocks impossible.

#### Q13: What happens if a transfer fails midway? (e.g. source account is debited, but destination account update crashes)?
**Answer:** Because the `transfer()` method is annotated with `@Transactional`, Spring manages the entire method as a single atomic unit of work (ACID). If an exception is thrown after the source account is debited, Spring catches the exception and issues a `ROLLBACK` to the MySQL database. All changes from that transaction are discarded, and the source account balance reverts to its original amount.

#### Q14: Does `@Transactional` work if called from another method within the same class?
**Answer:** No, this is the classic **Spring AOP Self-Invocation Problem**. Spring `@Transactional` works by wrapping the target bean in a dynamic proxy. When a method is called from *outside* the bean, the call goes through the proxy, which starts the transaction. If `methodA()` inside `AccountService` directly calls `methodB()` within the same class, it bypasses the proxy (`this.methodB()`), so `@Transactional` on `methodB()` will be ignored!

---

### Category 5: Spring Security & JWT

#### Q15: What are the three parts of a JWT?
**Answer:** 
1. **Header:** Contains the algorithm used (e.g. `{"alg": "HS256", "typ": "JWT"}`).
2. **Payload (Claims):** Contains the subject (username/email), role, issue time, and expiration time.
3. **Signature:** Calculated by taking the Base64Url-encoded header and payload, hashing them with the secret key using the specified algorithm:
   `HMACSHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)`
The signature verifies that the sender is who it says it is and ensures the content hasn't been tampered with.

#### Q16: How does Spring Security know a user's role during authorization checks?
**Answer:** 
In `CustomUserDetails.java`:
```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.singletonList(
        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
    );
}
```
Spring Security convention prefixes roles with `"ROLE_"`. When we configure `.requestMatchers("/api/admin/**").hasRole("ADMIN")`, Spring checks for the authority `ROLE_ADMIN`.

#### Q17: Why did you disable CSRF protection in `SecurityConfig`?
**Answer:** CSRF (Cross-Site Request Forgery) attacks exploit stateful browser sessions where session cookies are automatically sent with unauthorized requests. Because our REST API is completely stateless, uses JWT tokens passed in the `Authorization: Bearer` header, and stores no session cookies, browser-based CSRF attacks cannot occur. Disabling CSRF is standard industry practice for stateless JWT REST APIs.

#### Q18: What is the purpose of `OncePerRequestFilter` for JWT?
**Answer:** `JwtAuthenticationFilter` extends Spring's `OncePerRequestFilter` to guarantee that the filter is executed exactly once per incoming request dispatch, avoiding duplicate authentication overhead or issues during internal request forwarding.

---

### Category 6: System Behavior & Edge Cases

#### Q19: How do you prevent a malicious customer from transferring money from another person's account if they know their Account ID?
**Answer:** 
We use a two-step defense:
1. **Authentication:** The JWT ensures the caller is authenticated and reveals their verified email in the `SecurityContext`.
2. **Authorization / Ownership Verification:** Before performing any debit, withdrawal, or balance lookup, the service explicitly checks:
   ```java
   if (!account.getUser().getEmail().equals(authenticatedEmail)) {
       throw new UnauthorizedOperationException("You do not have permission...");
   }
   ```
Even if Customer A submits Account ID `2` (owned by Customer B) in `sourceAccountId`, the application throws HTTP `403 Forbidden`.

#### Q20: What happens if an account is deactivated?
**Answer:** The `Account` entity contains an `active` boolean flag. In both `withdraw()` and `transfer()`, the service executes `validateAccountIsActive(account)`. If `active` is `false`, an `AccountInactiveException` is thrown (`400 Bad Request`), preventing deposits, withdrawals, and transfers until an administrator reactivates the account via `/api/admin/accounts/{id}/activate`.

#### Q21: If you had more time or were moving this to a microservice architecture, what improvements would you add?
**Answer:** 
1. **Database Migrations:** Introduce **Flyway** for automated database schema versioning.
2. **Caching:** Integrate **Redis** to cache user profiles and reduce database hits for frequent lookups.
3. **Event-Driven Auditing:** Use **Kafka** or RabbitMQ to publish transaction events asynchronously to a separate notification and reporting service (e.g. sending SMS/email receipts).
4. **Rate Limiting:** Implement token bucket rate limiting (e.g., Bucket4j) on the login and transfer endpoints to protect against brute force and DDoS attacks.
5. **Two-Factor Authentication (2FA):** Require TOTP (Time-Based One-Time Password) confirmation before executing high-value transfers.

---

## 9. Quick Formula for Answering Technical Questions

When answering questions in your interview, use this 3-step formula:
1. **Direct Answer:** Give the short definition or concept immediately.
2. **Project Application:** Explain *where* and *how* you implemented it in your Banking Management System.
3. **Trade-offs / Alternatives:** Mention why you picked this approach over an alternative (e.g. pessimistic vs optimistic locking, `BigDecimal` vs `double`).
