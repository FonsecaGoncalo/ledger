package io.gfonseca.ledger.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.AccountType;
import io.gfonseca.ledger.domain.model.Money;
import io.gfonseca.ledger.domain.model.Posting;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.domain.model.TransactionType;
import io.gfonseca.ledger.generated.tables.records.AccountsRecord;
import io.gfonseca.ledger.generated.tables.records.PostingsRecord;
import io.gfonseca.ledger.generated.tables.records.TransactionsRecord;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.JSON;
import org.springframework.stereotype.Component;

@Component
class RecordMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper objectMapper = new ObjectMapper();

    Account toAccount(AccountsRecord r) {
        return new Account(
                r.getId(),
                r.getExternalRef(),
                AccountType.valueOf(r.getType()),
                r.getCurrency(),
                parseJson(r.getMetadata()),
                r.getCreatedAt().toInstant());
    }

    AccountsRecord toAccountRecord(Account a) {
        return new AccountsRecord(
                a.id(),
                a.externalRef(),
                a.type().name(),
                a.currency(),
                toJson(a.metadata()),
                a.createdAt().atOffset(ZoneOffset.UTC));
    }

    Posting toPosting(PostingsRecord r) {
        return new Posting(
                r.getId(),
                r.getTransactionId(),
                r.getAccountId(),
                new Money(r.getAmount(), r.getCurrency()),
                r.getSequenceNo(),
                r.getCreatedAt().toInstant());
    }

    PostingsRecord toPostingRecord(Posting p) {
        return new PostingsRecord(
                p.id(),
                p.transactionId(),
                p.accountId(),
                p.money().amount(),
                p.money().currency(),
                p.sequenceNo(),
                p.createdAt().atOffset(ZoneOffset.UTC));
    }

    Transaction toTransaction(TransactionsRecord r, List<PostingsRecord> postingRecords) {
        return new Transaction(
                r.getId(),
                TransactionType.valueOf(r.getType()),
                r.getDescription(),
                r.getIdempotencyKey(),
                r.getReversesTransactionId(),
                postingRecords.stream().map(this::toPosting).toList(),
                parseJson(r.getMetadata()),
                r.getCreatedAt().toInstant());
    }

    TransactionsRecord toTransactionRecord(Transaction t) {
        return new TransactionsRecord(
                t.id(),
                t.type().name(),
                t.description(),
                t.idempotencyKey(),
                t.reversesTransactionId(),
                toJson(t.metadata()),
                t.createdAt().atOffset(ZoneOffset.UTC));
    }

    private Map<String, Object> parseJson(JSON json) {
        if (json == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json.data(), MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private JSON toJson(Map<String, Object> map) {
        try {
            return JSON.valueOf(objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            return JSON.valueOf("{}");
        }
    }
}
