package com.bank.bankingmanagement.controller;

import com.bank.bankingmanagement.dto.request.CreateAccountRequest;
import com.bank.bankingmanagement.dto.response.AccountResponse;
import com.bank.bankingmanagement.dto.response.BalanceResponse;
import com.bank.bankingmanagement.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request,
                                                         Principal principal) {
        AccountResponse response = accountService.createAccount(request, principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(Principal principal) {
        List<AccountResponse> accounts = accountService.getMyAccounts(principal.getName());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountDetails(@PathVariable Long accountId,
                                                             Principal principal) {
        AccountResponse response = accountService.getAccountDetails(accountId, principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long accountId,
                                                      Principal principal) {
        BalanceResponse response = accountService.getBalance(accountId, principal.getName());
        return ResponseEntity.ok(response);
    }
}
