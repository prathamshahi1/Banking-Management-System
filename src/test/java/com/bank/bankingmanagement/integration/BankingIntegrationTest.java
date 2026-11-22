package com.bank.bankingmanagement.integration;

import com.bank.bankingmanagement.dto.request.*;
import com.bank.bankingmanagement.dto.response.*;
import com.bank.bankingmanagement.enums.AccountType;
import com.bank.bankingmanagement.repository.AccountRepository;
import com.bank.bankingmanagement.repository.TransactionRepository;
import com.bank.bankingmanagement.repository.UserRepository;
import com.bank.bankingmanagement.service.AccountService;
import com.bank.bankingmanagement.service.AuthService;
import com.bank.bankingmanagement.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BankingIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Complete end-to-end flow: register, login, open account, deposit, withdraw, transfer")
    void testEndToEndBankingLifecycle() {
        // 1. Register users
        RegisterRequest reg1 = new RegisterRequest("User One", "user1@test.com", "SecurePwd123!");
        RegisterRequest reg2 = new RegisterRequest("User Two", "user2@test.com", "SecurePwd123!");
        UserResponse user1 = authService.register(reg1);
        UserResponse user2 = authService.register(reg2);
        assertNotNull(user1.getId());
        assertNotNull(user2.getId());

        // 2. Login
        AuthResponse login1 = authService.login(new LoginRequest("user1@test.com", "SecurePwd123!"));
        assertNotNull(login1.getToken());

        // 3. Open accounts
        AccountResponse acc1 = accountService.createAccount(new CreateAccountRequest(AccountType.SAVINGS), "user1@test.com");
        AccountResponse acc2 = accountService.createAccount(new CreateAccountRequest(AccountType.CURRENT), "user2@test.com");
        assertEquals(BigDecimal.ZERO.setScale(2), acc1.getBalance());

        // 4. Deposit into account 1
        TransactionResponse dep = transactionService.deposit(
                new DepositRequest(acc1.getId(), new BigDecimal("1000.00"), "Initial deposit"),
                "user1@test.com"
        );
        assertEquals(new BigDecimal("1000.00"), dep.getAmount());

        BalanceResponse bal1 = accountService.getBalance(acc1.getId(), "user1@test.com");
        assertEquals(new BigDecimal("1000.00"), bal1.getBalance());

        // 5. Withdraw from account 1
        TransactionResponse wth = transactionService.withdraw(
                new WithdrawRequest(acc1.getId(), new BigDecimal("250.00"), "ATM cash"),
                "user1@test.com"
        );
        assertEquals(new BigDecimal("250.00"), wth.getAmount());

        bal1 = accountService.getBalance(acc1.getId(), "user1@test.com");
        assertEquals(new BigDecimal("750.00"), bal1.getBalance());

        // 6. Transfer from account 1 to account 2
        TransactionResponse trf = transactionService.transfer(
                new TransferRequest(acc1.getId(), acc2.getId(), new BigDecimal("350.00"), "Bill share"),
                "user1@test.com"
        );
        assertEquals(new BigDecimal("350.00"), trf.getAmount());

        BalanceResponse finalBal1 = accountService.getBalance(acc1.getId(), "user1@test.com");
        BalanceResponse finalBal2 = accountService.getBalance(acc2.getId(), "user2@test.com");
        assertEquals(new BigDecimal("400.00"), finalBal1.getBalance());
        assertEquals(new BigDecimal("350.00"), finalBal2.getBalance());
    }

    @Test
    @DisplayName("Concurrent withdrawal test: ensures pessimistic lock prevents overspending")
    void testConcurrentWithdrawalsSafety() throws InterruptedException {
        // Setup user with 500 balance
        authService.register(new RegisterRequest("Payer", "payer@test.com", "SecurePwd123!"));
        AccountResponse account = accountService.createAccount(new CreateAccountRequest(AccountType.SAVINGS), "payer@test.com");
        transactionService.deposit(new DepositRequest(account.getId(), new BigDecimal("500.00"), "Seed fund"), "payer@test.com");

        int threadCount = 10;
        BigDecimal withdrawalPerThread = new BigDecimal("100.00");
        // Total requested = 10 * 100 = 1000, but only 500 is available!
        // At most 5 withdrawals must succeed, 5 must fail.

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    transactionService.withdraw(
                            new WithdrawRequest(account.getId(), withdrawalPerThread, "Concurrent withdrawal"),
                            "payer@test.com"
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // trigger all threads simultaneously
        doneLatch.await();
        executor.shutdown();

        BalanceResponse endingBalance = accountService.getBalance(account.getId(), "payer@test.com");

        assertEquals(5, successCount.get(), "Exactly 5 withdrawals of 100 should succeed for a 500 balance");
        assertEquals(5, failCount.get(), "Remaining 5 withdrawals should fail due to insufficient balance");
        assertEquals(new BigDecimal("0.00"), endingBalance.getBalance(), "Ending balance must never be negative");
    }
}
