package io.gfonseca.ledger.api;

import io.gfonseca.ledger.api.dto.SubmitTransactionRequest;
import io.gfonseca.ledger.api.dto.TransactionHistoryResponse;
import io.gfonseca.ledger.api.dto.TransactionResponse;
import io.gfonseca.ledger.api.mapper.DtoMapper;
import io.gfonseca.ledger.domain.model.Money;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.domain.service.LedgerService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1")
class TransactionController {

    private final LedgerService ledgerService;

    TransactionController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse submitTransaction(@Valid @RequestBody SubmitTransactionRequest request) {
        List<PostingRequest> postingRequests = request.postings().stream()
                .map(p -> new PostingRequest(p.accountId(), new Money(p.amount(), p.currency())))
                .toList();

        Transaction transaction = ledgerService.submitTransaction(
                request.idempotencyKey(),
                request.type(),
                request.description(),
                postingRequests,
                request.reversesTransactionId(),
                request.metadata() != null ? request.metadata() : Map.of());
        return DtoMapper.toTransactionResponse(transaction);
    }

    @GetMapping("/transactions/{id}")
    TransactionResponse getTransaction(@PathVariable String id) {
        Transaction transaction = ledgerService.getTransaction(id);
        return DtoMapper.toTransactionResponse(transaction);
    }

    @GetMapping("/accounts/{id}/transactions")
    TransactionHistoryResponse getTransactionHistory(
            @PathVariable String id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        int clampedLimit = Math.clamp(limit, 1, 200);
        PagedResult<Transaction> page = ledgerService.getTransactionHistory(id, cursor, clampedLimit);
        return DtoMapper.toTransactionHistoryResponse(id, page);
    }
}
