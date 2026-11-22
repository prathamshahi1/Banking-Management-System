# Complete Beginner's Step-by-Step Guide (Start.md)

This guide takes you through everything step by step from scratch, even if you have never run a Spring Boot project or used Postman before.

---

## Table of Contents
1. [Prerequisites Checklist](#1-prerequisites-checklist)
2. [Step 1: Setup MySQL Database (Do This First!)](#2-step-1-setup-mysql-database-do-this-first)
3. [Step 2: Open Project in IntelliJ IDEA](#3-step-2-open-project-in-intellij-idea)
4. [Step 3: Run the Spring Boot Application](#4-step-3-run-the-spring-boot-application)
5. [Step 4: Setup Postman](#5-step-4-setup-postman)
6. [Step 5: Step-by-Step API Execution Order](#6-step-by-step-api-execution-order)
7. [Step 6: Admin Operations Walkthrough](#7-step-6-admin-operations-walkthrough)
8. [Step 7: How to Run Unit & Integration Tests](#8-step-7-how-to-run-unit--integration-tests)
9. [Troubleshooting & Common Errors](#9-troubleshooting--common-errors)

---

## 1. Prerequisites Checklist

Before starting, ensure you have:
1. **Java 21 installed:** Open your macOS Terminal and run:
   ```bash
   java -version
   ```
   You should see `java version "21.x.x"`.
2. **MySQL 8.x installed and running:**
   ```bash
   mysqladmin ping -u root -p
   ```
   Enter password `Pratham@05`. If it prints `mysqld is alive`, your database is running!
3. **IntelliJ IDEA** installed.
4. **Postman Desktop App** installed (download free from [postman.com](https://www.postman.com/downloads/)).

---

## 2. Step 1: Setup MySQL Database (Do This First!)

Before running the Java backend, the database `banking_management_db` must exist. Hibernate will automatically create all tables (`users`, `accounts`, `transactions`) when the backend starts, but the empty database must exist first.

### How to create the database:
1. Open your Terminal on macOS.
2. Log into MySQL:
   ```bash
   mysql -u root -p
   ```
3. Type your password when prompted: `Pratham@05` and press Enter.
4. Run this SQL command:
   ```sql
   CREATE DATABASE IF NOT EXISTS banking_management_db;
   ```
5. Verify it was created:
   ```sql
   SHOW DATABASES LIKE 'banking_management_db';
   ```
6. Type `exit` to close the MySQL prompt.

> **Note:** The database configuration is already configured in `src/main/resources/application.properties`:
> - Database URL: `jdbc:mysql://localhost:3306/banking_management_db`
> - Username: `root`
> - Password: `Pratham@05`

---

## 3. Step 2: Open Project in IntelliJ IDEA

1. Launch **IntelliJ IDEA**.
2. On the welcome screen, click **Open** (or from the top menu: **File** > **Open...**).
3. Select the folder:
   ```text
   /Users/pshahi/Documents/Banking-Management-System
   ```
4. Click **Open**.
5. IntelliJ will display a notification: *"Maven projects found"*. Allow IntelliJ 30–60 seconds to download dependencies and index the project (watch the progress bar in the bottom right corner).
6. **Set Java 21 as Project SDK:**
   - Go to **File** > **Project Structure...** (or press `Cmd + ;`).
   - Under **Project**, check:
     - **SDK:** Select **21** (e.g. `temurin-21`, `openjdk-21`, or `Java SE 21`).
     - **Language level:** Select **21 - Sealed types, pattern matching, records**.
   - Click **Apply** and **OK**.
7. If Maven dependencies ever show red underlines:
   - Open the **Maven** sidebar tab on the right edge of IntelliJ.
   - Click the **Reload All Maven Projects** icon (the two rotating arrows 🔄).

---

## 4. Step 3: Run the Spring Boot Application

Now that MySQL is ready and the project is open, start the backend:

1. In IntelliJ's Project Explorer panel on the left, navigate to:
   ```text
   src/main/java/com/bank/bankingmanagement/BankingManagementApplication.java
   ```
2. Double-click to open `BankingManagementApplication.java`.
3. Look for `public static void main(String[] args)`.
4. Click the **green Play button ▶** in the left gutter next to line 9 (or right-click anywhere in the file and select **Run 'BankingManagementApplication'**).
5. Open the **Run** window at the bottom of IntelliJ to watch the console output.
6. Look for these final confirmation lines:
   ```text
   Started BankingManagementApplication in 2.xxx seconds
   Default administrator account initialized with email: admin@bank.com
   ```
7. Congratulations! Your backend is now live and listening on:
   ```text
   http://localhost:8080
   ```

---

## 5. Step 4: Setup Postman

All API requests have already been prepared for you. You only need to import them:

1. Open the **Postman** application.
2. In the top-left area, click the **Import** button.
3. Click **Files** or drag and drop these two files from the `postman/` folder:
   - `postman/BankingManagement.postman_collection.json`
   - `postman/BankingManagement.postman_environment.json`
4. Click **Import**.
5. You will see a new collection called **"Banking Management System API"** in your left sidebar.
6. **CRITICAL STEP — Select Environment:**
   - Look at the **top-right corner** of Postman (near the eye icon).
   - Click the environment dropdown (which likely says *"No Environment"* by default).
   - Select **Banking Management System - Local**.
   *(If you skip this step, requests will fail because `{{baseUrl}}` and `{{jwtToken}}` won't be defined!)*

---

## 6. Step 5: Step-by-Step API Execution Order

Because banking operations depend on preceding steps (e.g. you cannot deposit money into an account before opening one), execute the requests in this exact order:

```
┌────────────────────────────────────────────────────────┐
│ 1. Register Customer (POST /api/auth/register)         │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 2. Login Customer (POST /api/auth/login)               │
│    -> Automatically saves JWT Token                    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 3. Create Account (POST /api/accounts)                 │
│    -> Automatically saves accountId                    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 4. Check Balance (GET /api/accounts/{{id}}/balance)    │
│    -> Initial balance = 0.00                           │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 5. Deposit Money (POST /api/transactions/deposit)      │
│    -> Increases balance (e.g., +1000.00)               │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 6. Withdraw Money (POST /api/transactions/withdraw)    │
│    -> Decreases balance (e.g., -250.00)                │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 7. Transfer Money (POST /api/transactions/transfer)    │
│    -> Atomic transfer to destination account           │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 8. Transaction History (GET /api/transactions/...)     │
│    -> Audit trail with date, type, reference           │
└────────────────────────────────────────────────────────┘
```

### Detailed Walkthrough for Each Step:

### Operation 1: Register Customer
- In Postman, expand: `Banking Management System API` > `Authentication` > click **Register Customer**.
- Look at the Body tab (JSON):
  ```json
  {
    "fullName": "Rahul Kumar",
    "email": "rahul@example.com",
    "password": "ExamplePass123!"
  }
  ```
- Click **Send**.
- **Expected Status:** `201 Created`
- **Response:** You will see the new user details with role `"CUSTOMER"`.

### Operation 2: Login Customer
- Click **Login Customer**.
- The request body contains the same email & password.
- Click **Send**.
- **Expected Status:** `200 OK`
- **Response:**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "role": "CUSTOMER",
    "email": "rahul@example.com",
    "fullName": "Rahul Kumar"
  }
  ```
- *Magic under the hood:* The Postman collection contains a test script that automatically copied this `token` into your environment variable `{{jwtToken}}`! You do NOT need to copy-paste tokens manually.

### Operation 3: Create Bank Account
- Expand `Account Management` > click **Create Account**.
- Body:
  ```json
  {
    "accountType": "SAVINGS"
  }
  ```
- Click **Send**.
- **Expected Status:** `201 Created`
- **Response:** A new bank account with a unique 10-digit account number, `balance: 0.00`, and `active: true`.
- *Magic under the hood:* Postman's test script automatically saved this account's `id` into `{{accountId}}`.

### Operation 4: Get My Accounts & Check Balance
- Click **Get My Accounts** > Click **Send**.
  - Returns a list containing your newly created account.
- Click **Check Balance** > Click **Send**.
  - Returns:
    ```json
    {
      "accountId": 1,
      "accountNumber": "100xxxxxxx",
      "balance": 0.00
    }
    ```

### Operation 5: Deposit Money
- Expand `Transactions` > click **Deposit Money**.
- Body:
  ```json
  {
    "accountId": 1,
    "amount": 1000.00,
    "description": "Initial account funding"
  }
  ```
- Click **Send**.
- **Expected Status:** `200 OK`
- **Response:** A transaction receipt containing reference like `DEP-1732...`, amount `1000.00`.
- If you go back and click **Check Balance** again, you will see `balance: 1000.00`.

### Operation 6: Withdraw Money
- Click **Withdraw Money**.
- Body:
  ```json
  {
    "accountId": 1,
    "amount": 250.00,
    "description": "ATM withdrawal simulation"
  }
  ```
- Click **Send**.
- **Expected Status:** `200 OK`
- If you click **Check Balance**, you will now see `750.00`.
- **Try Error Case (Insufficient Balance):** Change `amount` to `9999.00` and click Send.
  - You will see HTTP `400 Bad Request`:
    ```json
    {
      "status": 400,
      "message": "Insufficient account balance. Available: 750.00, Requested: 9999.00"
    }
    ```

### Operation 7: Fund Transfer Between Accounts
To test a real transfer, you need a destination account:
1. Open **Register Customer**, change email to `priya@example.com`, click Send.
2. Open **Login Customer**, change email to `priya@example.com`, click Send.
3. Open **Create Account**, click Send. Note the new account ID (e.g. `2`).
4. Re-login as Rahul (`rahul@example.com`).
5. Open **Transfer Funds**.
6. Set body:
   ```json
   {
     "sourceAccountId": 1,
     "destinationAccountId": 2,
     "amount": 150.00,
     "description": "Online fund transfer"
   }
   ```
7. Click **Send**.
8. **Expected Status:** `200 OK`
9. Rahul's balance is debited by 150.00 (from 750.00 to 600.00) and Priya's balance is credited with 150.00 atomically!

### Operation 8: View Transaction History
- Click **Transaction History** (`GET /api/transactions/account/{{accountId}}?page=0&size=10`).
- Click **Send**.
- Returns a paginated list of all deposits, withdrawals, and transfers in reverse chronological order (newest first).

---

## 7. Step 6: Admin Operations Walkthrough

The application automatically seeds a default administrator when it starts:
- **Email:** `admin@bank.com`
- **Password:** `Admin@123`

### How to use Admin APIs:
1. In Postman, under `Authentication`, click **Login Admin**.
2. Click **Send**. Postman automatically saves the admin token into `{{adminJwtToken}}`.
3. Expand **Admin Operations**:
   - **Get All Users:** View all customers registered in the bank.
   - **Get All Accounts:** View all bank accounts across all users.
   - **Get All Transactions:** View global transaction logs.
   - **Deactivate Account:** Run `PATCH /api/admin/accounts/1/deactivate`.
     - Now go try to deposit or withdraw from Account 1.
     - You will receive: `400 Bad Request: Account is currently inactive/deactivated`.
   - **Activate Account:** Run `PATCH /api/admin/accounts/1/activate`. Account 1 is active again!

---

## 8. Step 7: How to Run Unit & Integration Tests

The project includes 23 automated tests covering business logic, security permissions, rollback behavior, and multi-threaded concurrent withdrawal safety.

### Option A: Inside IntelliJ IDEA
1. In the project sidebar, expand `src/test/java`.
2. Right-click the `com.bank.bankingmanagement` package.
3. Select **Run 'Tests in 'bankingmanagement''** (green Play icon).
4. IntelliJ will open a test runner window showing all 23 green checkmarks.

### Option B: From the Terminal
Open your terminal in the project directory and run:
```bash
./mvnw clean test
```
Result:
```text
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **Note:** Tests run against an isolated in-memory H2 database. They will never alter or delete your real MySQL data.

---

## 9. Troubleshooting & Common Errors

| Issue / Error | Cause | Solution |
|---|---|---|
| `Communications link failure` | MySQL is not running on port 3306. | Start MySQL in macOS Terminal using `brew services start mysql` or start it via macOS System Settings > MySQL. |
| `Access denied for user 'root'@'localhost'` | MySQL password in `application.properties` does not match your MySQL root password. | Update `spring.datasource.password` in `application.properties` or set the `DB_PASSWORD` environment variable. |
| `Unknown database 'banking_management_db'` | Database was not created before running the application. | Run `mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS banking_management_db;"` |
| `Port 8080 was already in use` | Another instance of the app (or another web server) is already running on 8080. | In macOS terminal run: `lsof -ti :8080 \| xargs kill -9` to free port 8080. |
| Postman: `401 Unauthorized: Authentication token is missing...` | You didn't select the environment or didn't run Login first. | In the top-right of Postman, select **Banking Management System - Local**, then run **Login Customer** before other calls. |
| Red underlines in Java files in IntelliJ | Maven dependencies not indexed yet. | Click the **Maven** sidebar tab on the right > click the 🔄 **Reload All Maven Projects** button. |
