package com.bank.bankingmanagement.service;

import com.bank.bankingmanagement.dto.request.CreateAccountRequest;
import com.bank.bankingmanagement.dto.response.AccountResponse;
import com.bank.bankingmanagement.dto.response.BalanceResponse;
import com.bank.bankingmanagement.entity.Account;
import com.bank.bankingmanagement.entity.User;
import com.bank.bankingmanagement.enums.Role;
import com.bank.bankingmanagement.exception.ResourceNotFoundException;
import com.bank.bankingmanagement.exception.UnauthorizedOperationException;
import com.bank.bankingmanagement.repository.AccountRepository;
import com.bank.bankingmanagement.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        String generatedAccountNumber = generateUniqueAccountNumber();

        Account account = new Account();
        account.setAccountNumber(generatedAccountNumber);
        account.setAccountType(request.getAccountType());
        account.setBalance(BigDecimal.ZERO.setScale(2));
        account.setUser(user);
        account.setActive(true);

        Account savedAccount = accountRepository.save(account);
        return mapToAccountResponse(savedAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        return accounts.stream()
                .map(this::mapToAccountResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountDetails(Long accountId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        validateAccountAccess(account, user);
        return mapToAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        validateAccountAccess(account, user);
        return new BalanceResponse(account.getId(), account.getAccountNumber(), account.getBalance());
    }

    private void validateAccountAccess(Account account, User user) {
        if (user.getRole() != Role.ADMIN && !account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("You do not have permission to access account ID: " + account.getId());
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            long randomDigits = 1000000000L + (long) (secureRandom.nextDouble() * 9000000000L);
            accountNumber = String.valueOf(randomDigits);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    public AccountResponse mapToAccountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getActive(),
                account.getCreatedAt(),
                account.getUser().getFullName(),
                account.getUser().getEmail()
        );
    }
}
