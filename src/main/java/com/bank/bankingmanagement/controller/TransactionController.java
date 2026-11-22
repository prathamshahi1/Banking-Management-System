package com.bank.bankingmanagement.controller;

import com.bank.bankingmanagement.dto.request.DepositRequest;
import com.bank.bankingmanagement.dto.request.TransferRequest;
import com.bank.bankingmanagement.dto.request.WithdrawRequest;
import com.bank.bankingmanagement.dto.response.TransactionResponse;
import com.bank.bankingmanagement.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request,
                                                       Principal principal) {
        TransactionResponse response = transactionService.deposit(request, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawRequest request,
                                                        Principal principal) {
        TransactionResponse response = transactionService.withdraw(request, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request,
                                                        Principal principal) {
        TransactionResponse response = transactionService.transfer(request, principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        Page<TransactionResponse> history = transactionService.getTransactionHistory(accountId, principal.getName(), pageable);
        return ResponseEntity.ok(history);
    }
}
