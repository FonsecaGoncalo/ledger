package io.gfonseca.ledger.api.mapper;

import io.gfonseca.ledger.api.dto.BalanceResponse;
import io.gfonseca.ledger.api.dto.CreateAccountResponse;
import io.gfonseca.ledger.api.dto.ListAccountsResponse;
import io.gfonseca.ledger.api.dto.PostingDto;
import io.gfonseca.ledger.api.dto.TransactionHistoryResponse;
import io.gfonseca.ledger.api.dto.TransactionResponse;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.BalanceView;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.domain.model.Posting;
import io.gfonseca.ledger.domain.model.Transaction;
import java.util.List;

public final class DtoMapper {

    private DtoMapper() {}

    public static CreateAccountResponse toCreateAccountResponse(Account account) {
        return new CreateAccountResponse(
                account.id(),
                account.externalRef(),
                account.type().name(),
                account.currency(),
                account.metadata(),
                account.createdAt());
    }

    public static ListAccountsResponse toListAccountsResponse(PagedResult<Account> page) {
        List<CreateAccountResponse> items =
                page.items().stream().map(DtoMapper::toCreateAccountResponse).toList();
        return new ListAccountsResponse(items, page.nextCursor(), page.hasMore());
    }

    public static TransactionResponse toTransactionResponse(Transaction t) {
        return new TransactionResponse(
                t.id(),
                t.type().name(),
                t.description(),
                t.idempotencyKey(),
                t.reversesTransactionId(),
                t.postings().stream().map(DtoMapper::toPostingDto).toList(),
                t.metadata(),
                t.createdAt());
    }

    public static BalanceResponse toBalanceResponse(BalanceView view) {
        return new BalanceResponse(
                view.accountId(), view.balance().amount(), view.balance().currency(), view.asOf(), view.postingCount());
    }

    public static TransactionHistoryResponse toTransactionHistoryResponse(
            String accountId, PagedResult<Transaction> page) {
        List<TransactionHistoryResponse.TransactionItem> items = page.items().stream()
                .map(t -> new TransactionHistoryResponse.TransactionItem(
                        t.id(),
                        t.type().name(),
                        t.description(),
                        t.postings().stream().map(DtoMapper::toPostingDto).toList(),
                        t.metadata(),
                        t.createdAt()))
                .toList();
        return new TransactionHistoryResponse(accountId, items, page.nextCursor(), page.hasMore());
    }

    private static PostingDto toPostingDto(Posting p) {
        return new PostingDto(
                p.id(), p.accountId(), p.money().amount(), p.money().currency(), p.sequenceNo());
    }
}
