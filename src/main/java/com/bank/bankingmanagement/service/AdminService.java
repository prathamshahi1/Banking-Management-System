package com.bank.bankingmanagement.service;

import com.bank.bankingmanagement.dto.response.AccountResponse;
import com.bank.bankingmanagement.dto.response.TransactionResponse;
import com.bank.bankingmanagement.dto.response.UserResponse;
import com.bank.bankingmanagement.entity.Account;
import com.bank.bankingmanagement.entity.Transaction;
import com.bank.bankingmanagement.entity.User;
import com.bank.bankingmanagement.exception.ResourceNotFoundException;
import com.bank.bankingmanagement.repository.AccountRepository;
import com.bank.bankingmanagement.repository.TransactionRepository;
import com.bank.bankingmanagement.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;

    public AdminService(UserRepository userRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        AccountService accountService,
                        TransactionService transactionService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return users.map(user -> new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        Page<Account> accounts = accountRepository.findAll(pageable);
        return accounts.map(accountService::mapToAccountResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        Page<Transaction> transactions = transactionRepository.findAllByOrderByTransactionDateDesc(pageable);
        return transactions.map(transactionService::mapToTransactionResponse);
    }

    @Transactional
    public AccountResponse activateAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));
        account.setActive(true);
        Account savedAccount = accountRepository.save(account);
        return accountService.mapToAccountResponse(savedAccount);
    }

    @Transactional
    public AccountResponse deactivateAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));
        account.setActive(false);
        Account savedAccount = accountRepository.save(account);
        return accountService.mapToAccountResponse(savedAccount);
    }
}
