# Banking Management System — Spring Boot Backend

A robust, intermediate-level simulation of a core banking backend built using **Java 21**, **Spring Boot 3**, **Spring Security**, **JWT (JSON Web Tokens)**, **Spring Data JPA**, and **MySQL**.

This project demonstrates clean layered architecture, atomicity in banking operations, thread-safe balance manipulation via pessimistic locking, and role-based access control suitable for fresher to junior Java Backend Developer portfolio showcases.

---

## 1. Project Overview & Architecture

### Architectural Pattern
The application follows standard Spring Boot monolithic layered architecture:

```
[ HTTP Requests / Postman ]
            │
            ▼
┌───────────────────────┐
│     Filter Chain      │  JwtAuthenticationFilter (Validates JWT & populates SecurityContext)
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│   Controller Layer    │  Thin REST endpoints, handles HTTP status codes & DTO validation
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│     Service Layer     │  Encapsulates banking business logic, @Transactional boundaries, locks
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│   Repository Layer    │  Spring Data JPA with customized queries and pessimistic locking
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│     MySQL Database    │  Stores Users, Accounts, and Transaction audit records
└───────────────────────┘
```

---

## 2. Technology Stack

- **Language:** Java 21 LTS
- **Framework:** Spring Boot 3.3.5
- **Data Persistence:** Spring Data JPA / Hibernate ORM
- **Database:** MySQL 8.x
- **Security:** Spring Security 6.x + JJWT 0.12.6 (HMAC-SHA256)
- **Validation:** Jakarta Bean Validation (Hibernate Validator)
- **Build System:** Apache Maven (with Maven Wrapper `mvnw`)
- **Testing:** JUnit 5, Mockito, Spring Test (isolated in-memory H2 database for test isolation)
- **API Testing:** Postman Collection v2.1

---

## 3. Database Schema & Entity Relationships

The relational model consists of three core entities:

```
┌───────────────────────┐       1 : N       ┌────────────────────────┐
│         User          │ ───────────────── │        Account         │
│───────────────────────│                   │────────────────────────│
│ id (PK)               │                   │ id (PK)                │
│ full_name             │                   │ account_number (UK)    │
│ email (UK)            │                   │ account_type           │
│ password (BCrypt)     │                   │ balance (DECIMAL 19,2) │
│ role (CUSTOMER/ADMIN) │                   │ user_id (FK)           │
│ created_at            │                   │ active (BOOLEAN)       │
└───────────────────────┘                   │ created_at             │
                                            └───────────┬────────────┘
                                                        │
                                                        │ 1 : N
                                                        ▼
                                            ┌────────────────────────┐
                                            │      Transaction       │
                                            │────────────────────────│
                                            │ id (PK)                │
                                            │ transaction_reference  │
                                            │ transaction_type       │
                                            │ amount (DECIMAL 19,2)  │
                                            │ source_account_id (FK) │
                                            │ destination_account_id │
                                            │ transaction_date       │
                                            │ description            │
                                            └────────────────────────┘
```

---

## 4. Concurrency Control & Transaction Management

Core banking operations demand strict atomicity and protection against race conditions:

1. **Pessimistic Locking (`PESSIMISTIC_WRITE`)**:
   During balance updates (`deposit`, `withdraw`, `transfer`), accounts are fetched with an explicit database-level row lock (`SELECT ... FOR UPDATE`). This guarantees that concurrent transactions cannot read or update stale balance states.

2. **Deadlock Prevention in Transfers**:
   When transferring funds between two accounts, lock acquisition could cause a deadlock if Thread A locks Account 1 then Account 2, while Thread B locks Account 2 then Account 1.
   **Solution:** Accounts are always locked in ascending numerical order of their Primary Key IDs:
   ```java
   Long firstId = Math.min(sourceId, destId);
   Long secondId = Math.max(sourceId, destId);
   accountRepository.findByIdWithLock(firstId);
   accountRepository.findByIdWithLock(secondId);
   ```

3. **Transaction Rollback (`@Transactional`)**:
   If an exception occurs during debit or credit operations, the transaction rolls back cleanly, leaving account balances completely consistent.

---

## 5. REST API Documentation

### Authentication Endpoints (`/api/auth`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a new customer |
| `POST` | `/api/auth/login` | Public | Authenticate user & return JWT token |

### Account Endpoints (`/api/accounts`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/accounts` | Authenticated | Create a new savings or current bank account |
| `GET` | `/api/accounts` | Authenticated | List all accounts belonging to current user |
| `GET` | `/api/accounts/{accountId}` | Owner/Admin | View account details |
| `GET` | `/api/accounts/{accountId}/balance` | Owner/Admin | Check current account balance |

### Banking Transaction Endpoints (`/api/transactions`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/transactions/deposit` | Owner | Deposit money into an active account |
| `POST` | `/api/transactions/withdraw` | Owner | Withdraw money (validates sufficient funds) |
| `POST` | `/api/transactions/transfer` | Owner | Transfer funds atomically between accounts |
| `GET` | `/api/transactions/account/{accountId}` | Owner/Admin | Paginated audit trail of account transactions |

### Admin Endpoints (`/api/admin`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/admin/users` | ADMIN | View all registered users (paginated) |
| `GET` | `/api/admin/accounts` | ADMIN | View all bank accounts across the system |
| `GET` | `/api/admin/transactions` | ADMIN | View all system transactions |
| `PATCH` | `/api/admin/accounts/{accountId}/activate` | ADMIN | Activate a bank account |
| `PATCH` | `/api/admin/accounts/{accountId}/deactivate` | ADMIN | Deactivate an account (blocks transactions) |

---

## 6. Setup and Running with IntelliJ IDEA (macOS)

### Prerequisites
- macOS with **Java 21** installed (`java -version`)
- **MySQL 8.x** running locally on port 3306
- **IntelliJ IDEA** (Community or Ultimate)

### Step 1: Prepare Database
Ensure MySQL is running and execute:
```sql
CREATE DATABASE IF NOT EXISTS banking_management_db;
```

### Step 2: Open Project in IntelliJ IDEA
1. Launch IntelliJ IDEA.
2. Select **File > Open...** and navigate to this folder:
   `/Users/pshahi/Documents/Banking-Management-System`
3. Click **Open**. IntelliJ will detect `pom.xml` and import dependencies automatically.

### Step 3: Configure Project SDK
1. Go to **File > Project Structure... > Project**.
2. Set **SDK** to **21** (e.g. `openjdk-21` or `Java SE 21`).
3. Set **Language Level** to **21 - Sealed types, pattern matching, records**.

### Step 4: Configure Run Environment Variables
1. In IntelliJ's top right, click **Edit Configurations...** (or right-click `BankingManagementApplication.java` > **Modify Run Configuration...**).
2. Under **Environment variables**, set:
   ```env
   DB_HOST=localhost;DB_PORT=3306;DB_NAME=banking_management_db;DB_USERNAME=root;DB_PASSWORD=Pratham@05;JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
   ```
   *(Note: Sensible local defaults are already baked into `application.properties`, so it will run even if run variables are omitted).*

### Step 5: Start the Application
- Click the green **Run** (or **Debug**) button next to `BankingManagementApplication.java`.
- Check the console output:
  ```text
  Started BankingManagementApplication in X.XXX seconds (process running for X.XXX)
  Default administrator account initialized with email: admin@bank.com
  ```

---

## 7. Testing with Postman

1. Open Postman.
2. Click **Import** > Select both files in the `postman/` directory:
   - `BankingManagement.postman_collection.json`
   - `BankingManagement.postman_environment.json`
3. Select the environment **Banking Management System - Local**.
4. Run requests in logical sequence:
   - **Login Customer** or **Login Admin** automatically stores the JWT token in `{{jwtToken}}` / `{{adminJwtToken}}`.
   - Subsequent authenticated requests automatically send the Bearer header.

---

## 8. Running Automated Tests

To run the complete test suite (both unit tests and integration tests with isolated H2 in-memory storage):

```bash
./mvnw clean test
```

All 23 test cases (including deadlock-free atomicity and multi-threaded race condition tests) execute without affecting real MySQL data.
