package com.bank.bankingmanagement.service;

import com.bank.bankingmanagement.dto.request.CreateAccountRequest;
import com.bank.bankingmanagement.dto.response.AccountResponse;
import com.bank.bankingmanagement.dto.response.BalanceResponse;
import com.bank.bankingmanagement.entity.Account;
import com.bank.bankingmanagement.entity.User;
import com.bank.bankingmanagement.enums.AccountType;
import com.bank.bankingmanagement.enums.Role;
import com.bank.bankingmanagement.exception.ResourceNotFoundException;
import com.bank.bankingmanagement.exception.UnauthorizedOperationException;
import com.bank.bankingmanagement.repository.AccountRepository;
import com.bank.bankingmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    private User user;
    private User otherUser;
    private Account account;

    @BeforeEach
    void setUp() {
        user = new User("Rahul Kumar", "rahul@example.com", "pass123", Role.CUSTOMER);
        user.setId(1L);

        otherUser = new User("Anjali Sharma", "anjali@example.com", "pass456", Role.CUSTOMER);
        otherUser.setId(2L);

        account = new Account("1001234567", AccountType.SAVINGS, BigDecimal.ZERO, user, true);
        account.setId(10L);
        account.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should create account with zero balance and active status")
    void createAccount_Success() {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.SAVINGS);

        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(10L);
            return acc;
        });

        AccountResponse response = accountService.createAccount(request, "rahul@example.com");

        assertNotNull(response);
        assertEquals(AccountType.SAVINGS, response.getAccountType());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getBalance());
        assertTrue(response.getActive());
        assertEquals("Rahul Kumar", response.getOwnerName());
    }

    @Test
    @DisplayName("Should retrieve only accounts belonging to user")
    void getMyAccounts_Success() {
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(1L)).thenReturn(List.of(account));

        List<AccountResponse> response = accountService.getMyAccounts("rahul@example.com");

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("1001234567", response.get(0).getAccountNumber());
    }

    @Test
    @DisplayName("Should get account details when user is the owner")
    void getAccountDetails_Success() {
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccountDetails(10L, "rahul@example.com");

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("1001234567", response.getAccountNumber());
    }

    @Test
    @DisplayName("Should throw UnauthorizedOperationException when accessing another user's account")
    void getAccountDetails_Unauthorized_ThrowsException() {
        when(userRepository.findByEmail("anjali@example.com")).thenReturn(Optional.of(otherUser));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        assertThrows(UnauthorizedOperationException.class,
                () -> accountService.getAccountDetails(10L, "anjali@example.com"));
    }

    @Test
    @DisplayName("Should return balance for authorized account owner")
    void getBalance_Success() {
        account.setBalance(new BigDecimal("1500.50"));
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        BalanceResponse response = accountService.getBalance(10L, "rahul@example.com");

        assertNotNull(response);
        assertEquals(10L, response.getAccountId());
        assertEquals(new BigDecimal("1500.50"), response.getBalance());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when account does not exist")
    void getAccountDetails_NotFound_ThrowsException() {
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> accountService.getAccountDetails(999L, "rahul@example.com"));
    }
}
