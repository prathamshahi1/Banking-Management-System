package com.bank.bankingmanagement.service;

import com.bank.bankingmanagement.dto.request.DepositRequest;
import com.bank.bankingmanagement.dto.request.TransferRequest;
import com.bank.bankingmanagement.dto.request.WithdrawRequest;
import com.bank.bankingmanagement.dto.response.TransactionResponse;
import com.bank.bankingmanagement.entity.Account;
import com.bank.bankingmanagement.entity.Transaction;
import com.bank.bankingmanagement.entity.User;
import com.bank.bankingmanagement.enums.Role;
import com.bank.bankingmanagement.enums.TransactionType;
import com.bank.bankingmanagement.exception.*;
import com.bank.bankingmanagement.repository.AccountRepository;
import com.bank.bankingmanagement.repository.TransactionRepository;
import com.bank.bankingmanagement.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse deposit(DepositRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        BigDecimal amount = validateAndFormatAmount(request.getAmount());

        // Acquire pessimistic write lock to protect concurrent updates
        Account account = accountRepository.findByIdWithLock(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));

        validateAccountOwnership(account, user);
        validateAccountIsActive(account);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        String reference = generateTransactionReference("DEP");
        Transaction transaction = new Transaction(
                reference,
                TransactionType.DEPOSIT,
                amount,
                null,
                account,
                request.getDescription() != null ? request.getDescription().trim() : "Deposit to account"
        );
        Transaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(savedTransaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdraw(WithdrawRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        BigDecimal amount = validateAndFormatAmount(request.getAmount());

        // Acquire pessimistic write lock to prevent race conditions & overspending
        Account account = accountRepository.findByIdWithLock(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));

        validateAccountOwnership(account, user);
        validateAccountIsActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient account balance. Available: " + account.getBalance() + ", Requested: " + amount
            );
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        String reference = generateTransactionReference("WTH");
        Transaction transaction = new Transaction(
                reference,
                TransactionType.WITHDRAWAL,
                amount,
                account,
                null,
                request.getDescription() != null ? request.getDescription().trim() : "Withdrawal from account"
        );
        Transaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(savedTransaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(TransferRequest request, String userEmail) {
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }

        User user = getUserByEmail(userEmail);
        BigDecimal amount = validateAndFormatAmount(request.getAmount());

        // Deadlock Prevention: Acquire locks in consistent numerical order of account IDs
        Long firstId = Math.min(request.getSourceAccountId(), request.getDestinationAccountId());
        Long secondId = Math.max(request.getSourceAccountId(), request.getDestinationAccountId());

        Account firstAccount = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + firstId));
        Account secondAccount = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + secondId));

        Account sourceAccount = firstAccount.getId().equals(request.getSourceAccountId()) ? firstAccount : secondAccount;
        Account destinationAccount = firstAccount.getId().equals(request.getDestinationAccountId()) ? firstAccount : secondAccount;

        validateAccountOwnership(sourceAccount, user);
        validateAccountIsActive(sourceAccount);
        validateAccountIsActive(destinationAccount);

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient account balance in source account. Available: " + sourceAccount.getBalance() + ", Requested: " + amount
            );
        }

        // Atomically debit source and credit destination
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        String reference = generateTransactionReference("TRF");
        Transaction transaction = new Transaction(
                reference,
                TransactionType.TRANSFER,
                amount,
                sourceAccount,
                destinationAccount,
                request.getDescription() != null ? request.getDescription().trim() : "Fund transfer between accounts"
        );
        Transaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(savedTransaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(Long accountId, String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        if (user.getRole() != Role.ADMIN && !account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("You do not have permission to view transactions for account ID: " + accountId);
        }

        Page<Transaction> transactions = transactionRepository.findByAccountId(accountId, pageable);
        return transactions.map(this::mapToTransactionResponse);
    }

    private void validateAccountOwnership(Account account, User user) {
        if (user.getRole() != Role.ADMIN && !account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("You are not authorized to perform operations on account ID: " + account.getId());
        }
    }

    private void validateAccountIsActive(Account account) {
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new AccountInactiveException("Account " + account.getAccountNumber() + " is currently inactive/deactivated");
        }
    }

    private BigDecimal validateAndFormatAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be strictly greater than zero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private String generateTransactionReference(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountNumber() : null,
                transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getAccountNumber() : null,
                transaction.getTransactionDate(),
                transaction.getDescription()
        );
    }
}
