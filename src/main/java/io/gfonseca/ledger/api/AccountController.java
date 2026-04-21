package io.gfonseca.ledger.api;

import io.gfonseca.ledger.api.dto.BalanceResponse;
import io.gfonseca.ledger.api.dto.CreateAccountRequest;
import io.gfonseca.ledger.api.dto.CreateAccountResponse;
import io.gfonseca.ledger.api.dto.ListAccountsResponse;
import io.gfonseca.ledger.api.mapper.DtoMapper;
import io.gfonseca.ledger.domain.exception.AccountNotFoundException;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.domain.service.AccountsService;
import io.gfonseca.ledger.domain.service.LedgerService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final LedgerService ledgerService;
    private final AccountsService accountsService;

    AccountController(LedgerService ledgerService, AccountsService accountsService) {
        this.ledgerService = ledgerService;
        this.accountsService = accountsService;
    }

    @GetMapping
    ListAccountsResponse listAccounts(
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int limit) {
        int clampedLimit = Math.clamp(limit, 1, 200);
        PagedResult<Account> page = accountsService.listAccounts(cursor, clampedLimit);
        return DtoMapper.toListAccountsResponse(page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateAccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountsService.createAccount(
                request.externalRef(),
                request.type(),
                request.currency(),
                request.metadata() != null ? request.metadata() : Map.of());
        return DtoMapper.toCreateAccountResponse(account);
    }

    @GetMapping("/{id}")
    CreateAccountResponse getAccount(@PathVariable String id) {
        Account account = accountsService.findAccount(id).orElseThrow(() -> new AccountNotFoundException(id));
        return DtoMapper.toCreateAccountResponse(account);
    }

    @GetMapping("/{id}/balance")
    BalanceResponse getBalance(@PathVariable String id) {
        return DtoMapper.toBalanceResponse(ledgerService.getBalance(id));
    }
}
