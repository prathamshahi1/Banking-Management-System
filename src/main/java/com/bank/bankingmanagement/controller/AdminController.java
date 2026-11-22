package com.bank.bankingmanagement.controller;

import com.bank.bankingmanagement.dto.response.AccountResponse;
import com.bank.bankingmanagement.dto.response.TransactionResponse;
import com.bank.bankingmanagement.dto.response.UserResponse;
import com.bank.bankingmanagement.service.AdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserResponse> users = adminService.getAllUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/accounts")
    public ResponseEntity<Page<AccountResponse>> getAllAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AccountResponse> accounts = adminService.getAllAccounts(pageable);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        Page<TransactionResponse> transactions = adminService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }

    @PatchMapping("/accounts/{accountId}/activate")
    public ResponseEntity<AccountResponse> activateAccount(@PathVariable Long accountId) {
        AccountResponse response = adminService.activateAccount(accountId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/accounts/{accountId}/deactivate")
    public ResponseEntity<AccountResponse> deactivateAccount(@PathVariable Long accountId) {
        AccountResponse response = adminService.deactivateAccount(accountId);
        return ResponseEntity.ok(response);
    }
}
