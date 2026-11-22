package com.bank.bankingmanagement.service;

import com.bank.bankingmanagement.dto.request.DepositRequest;
import com.bank.bankingmanagement.dto.request.TransferRequest;
import com.bank.bankingmanagement.dto.request.WithdrawRequest;
import com.bank.bankingmanagement.dto.response.TransactionResponse;
import com.bank.bankingmanagement.entity.Account;
import com.bank.bankingmanagement.entity.Transaction;
import com.bank.bankingmanagement.entity.User;
import com.bank.bankingmanagement.enums.AccountType;
import com.bank.bankingmanagement.enums.Role;
import com.bank.bankingmanagement.enums.TransactionType;
import com.bank.bankingmanagement.exception.AccountInactiveException;
import com.bank.bankingmanagement.exception.InsufficientBalanceException;
import com.bank.bankingmanagement.exception.InvalidTransactionException;
import com.bank.bankingmanagement.exception.UnauthorizedOperationException;
import com.bank.bankingmanagement.repository.AccountRepository;
import com.bank.bankingmanagement.repository.TransactionRepository;
import com.bank.bankingmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private User destinationUser;
    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        user = new User("Rahul Kumar", "rahul@example.com", "pass123", Role.CUSTOMER);
        user.setId(1L);

        destinationUser = new User("Priya Sharma", "priya@example.com", "pass456", Role.CUSTOMER);
        destinationUser.setId(2L);

        sourceAccount = new Account("1000000001", AccountType.SAVINGS, new BigDecimal("1000.00"), user, true);
        sourceAccount.setId(10L);

        destinationAccount = new Account("1000000002", AccountType.CURRENT, new BigDecimal("500.00"), destinationUser, true);
        destinationAccount.setId(20L);
    }

    @Test
    @DisplayName("Should successfully deposit money and update account balance")
    void deposit_Success() {
        DepositRequest request = new DepositRequest(10L, new BigDecimal("500.00"), "Salary deposit");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setId(100L);
            txn.setTransactionDate(LocalDateTime.now());
            return txn;
        });

        TransactionResponse response = transactionService.deposit(request, "rahul@example.com");

        assertNotNull(response);
        assertEquals(TransactionType.DEPOSIT, response.getTransactionType());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals("1000000001", response.getDestinationAccountNumber());
        assertEquals(new BigDecimal("1500.00"), sourceAccount.getBalance());
        verify(accountRepository, times(1)).save(sourceAccount);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw AccountInactiveException when depositing into deactivated account")
    void deposit_InactiveAccount_ThrowsException() {
        sourceAccount.setActive(false);
        DepositRequest request = new DepositRequest(10L, new BigDecimal("200.00"), "Deposit");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));

        assertThrows(AccountInactiveException.class,
                () -> transactionService.deposit(request, "rahul@example.com"));
        verify(accountRepository, never()).save(sourceAccount);
    }

    @Test
    @DisplayName("Should throw UnauthorizedOperationException when depositing into someone else's account")
    void deposit_Unauthorized_ThrowsException() {
        DepositRequest request = new DepositRequest(10L, new BigDecimal("200.00"), "Deposit");

        when(userRepository.findByEmail("priya@example.com")).thenReturn(Optional.of(destinationUser));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));

        assertThrows(UnauthorizedOperationException.class,
                () -> transactionService.deposit(request, "priya@example.com"));
    }

    @Test
    @DisplayName("Should throw InvalidTransactionException when deposit amount is negative or zero")
    void deposit_InvalidAmount_ThrowsException() {
        DepositRequest request = new DepositRequest(10L, new BigDecimal("0.00"), "Invalid deposit");
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));

        assertThrows(InvalidTransactionException.class,
                () -> transactionService.deposit(request, "rahul@example.com"));
    }

    @Test
    @DisplayName("Should successfully withdraw money when balance is sufficient")
    void withdraw_Success() {
        WithdrawRequest request = new WithdrawRequest(10L, new BigDecimal("400.00"), "ATM withdrawal");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setId(101L);
            txn.setTransactionDate(LocalDateTime.now());
            return txn;
        });

        TransactionResponse response = transactionService.withdraw(request, "rahul@example.com");

        assertNotNull(response);
        assertEquals(TransactionType.WITHDRAWAL, response.getTransactionType());
        assertEquals(new BigDecimal("400.00"), response.getAmount());
        assertEquals("1000000001", response.getSourceAccountNumber());
        assertEquals(new BigDecimal("600.00"), sourceAccount.getBalance());
        verify(accountRepository, times(1)).save(sourceAccount);
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when withdrawal exceeds balance")
    void withdraw_InsufficientBalance_ThrowsException() {
        WithdrawRequest request = new WithdrawRequest(10L, new BigDecimal("2500.00"), "Excess withdrawal");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));

        assertThrows(InsufficientBalanceException.class,
                () -> transactionService.withdraw(request, "rahul@example.com"));
        assertEquals(new BigDecimal("1000.00"), sourceAccount.getBalance());
        verify(accountRepository, never()).save(sourceAccount);
    }

    @Test
    @DisplayName("Should successfully transfer funds between two active accounts atomically")
    void transfer_Success() {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("300.00"), "Rent payment");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setId(102L);
            txn.setTransactionDate(LocalDateTime.now());
            return txn;
        });

        TransactionResponse response = transactionService.transfer(request, "rahul@example.com");

        assertNotNull(response);
        assertEquals(TransactionType.TRANSFER, response.getTransactionType());
        assertEquals(new BigDecimal("300.00"), response.getAmount());
        assertEquals("1000000001", response.getSourceAccountNumber());
        assertEquals("1000000002", response.getDestinationAccountNumber());
        assertEquals(new BigDecimal("700.00"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("800.00"), destinationAccount.getBalance());
        verify(accountRepository, times(1)).save(sourceAccount);
        verify(accountRepository, times(1)).save(destinationAccount);
    }

    @Test
    @DisplayName("Should throw InvalidTransactionException when transferring to the same account")
    void transfer_SameAccount_ThrowsException() {
        TransferRequest request = new TransferRequest(10L, 10L, new BigDecimal("100.00"), "Self transfer");

        assertThrows(InvalidTransactionException.class,
                () -> transactionService.transfer(request, "rahul@example.com"));
        verify(accountRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when transfer exceeds source balance")
    void transfer_InsufficientBalance_ThrowsException() {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("1500.00"), "Large transfer");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        assertThrows(InsufficientBalanceException.class,
                () -> transactionService.transfer(request, "rahul@example.com"));
        assertEquals(new BigDecimal("1000.00"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("500.00"), destinationAccount.getBalance());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AccountInactiveException when destination account is deactivated")
    void transfer_DestinationInactive_ThrowsException() {
        destinationAccount.setActive(false);
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("100.00"), "Transfer");

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        assertThrows(AccountInactiveException.class,
                () -> transactionService.transfer(request, "rahul@example.com"));
    }

    @Test
    @DisplayName("Should retrieve authorized paginated transaction history")
    void getTransactionHistory_Success() {
        Transaction transaction = new Transaction("DEP-12345", TransactionType.DEPOSIT,
                new BigDecimal("500.00"), null, sourceAccount, "Deposit");
        transaction.setId(1L);
        transaction.setTransactionDate(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> page = new PageImpl<>(List.of(transaction), pageable, 1);

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.findByAccountId(10L, pageable)).thenReturn(page);

        Page<TransactionResponse> response = transactionService.getTransactionHistory(10L, "rahul@example.com", pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals("DEP-12345", response.getContent().get(0).getTransactionReference());
    }
}
