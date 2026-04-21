package io.gfonseca.ledger.store;

import static io.gfonseca.ledger.generated.Tables.ACCOUNTS;
import static io.gfonseca.ledger.generated.Tables.POSTINGS;
import static io.gfonseca.ledger.generated.Tables.TRANSACTIONS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.val;

import io.gfonseca.ledger.domain.exception.AccountNotFoundException;
import io.gfonseca.ledger.domain.exception.DuplicateAccountException;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.AccountType;
import io.gfonseca.ledger.domain.model.BalanceView;
import io.gfonseca.ledger.domain.model.Money;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.generated.tables.records.PostingsRecord;
import io.gfonseca.ledger.generated.tables.records.TransactionsRecord;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JooqLedgerStore implements LedgerStore {

    private final DSLContext dsl;
    private final RecordMapper mapper;

    JooqLedgerStore(DSLContext dsl, RecordMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    public Account insertAccount(Account account) {
        try {
            dsl.insertInto(ACCOUNTS).set(mapper.toAccountRecord(account)).execute();
        } catch (IntegrityConstraintViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("accounts_external_ref_key")) {
                throw new DuplicateAccountException(account.externalRef());
            }
            throw ex;
        }
        return account;
    }

    @Override
    public Optional<Account> findAccountById(String id) {
        return dsl.selectFrom(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(id))
                .fetchOptional()
                .map(mapper::toAccount);
    }

    @Override
    public Optional<Account> findAccountByExternalRef(String externalRef) {
        return dsl.selectFrom(ACCOUNTS)
                .where(ACCOUNTS.EXTERNAL_REF.eq(externalRef))
                .fetchOptional()
                .map(mapper::toAccount);
    }

    @Override
    public PagedResult<Account> listAccounts(String cursor, int limit) {
        int fetchLimit = limit + 1;

        var condition = cursor == null ? org.jooq.impl.DSL.noCondition() : ACCOUNTS.ID.lt(cursor);

        List<Account> rows = dsl.selectFrom(ACCOUNTS)
                .where(condition)
                .orderBy(ACCOUNTS.ID.desc())
                .limit(fetchLimit)
                .fetch()
                .map(mapper::toAccount);

        boolean hasMore = rows.size() > limit;
        List<Account> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? page.get(page.size() - 1).id() : null;

        return new PagedResult<>(page, nextCursor, hasMore);
    }

    @Override
    public List<Account> findAndLockAccounts(List<String> accountIds) {
        return dsl.selectFrom(ACCOUNTS)
                .where(ACCOUNTS.ID.in(accountIds))
                .orderBy(ACCOUNTS.ID.asc())
                .forUpdate()
                .fetch()
                .map(mapper::toAccount);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        TransactionsRecord txnRec = dsl.selectFrom(TRANSACTIONS)
                .where(TRANSACTIONS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOne();
        if (txnRec == null) return Optional.empty();

        List<PostingsRecord> postingRecs = dsl.selectFrom(POSTINGS)
                .where(POSTINGS.TRANSACTION_ID.eq(txnRec.getId()))
                .orderBy(POSTINGS.SEQUENCE_NO.asc())
                .fetch();

        return Optional.of(mapper.toTransaction(txnRec, postingRecs));
    }

    @Override
    public Optional<Transaction> findTransactionById(String transactionId) {
        TransactionsRecord txnRec = dsl.selectFrom(TRANSACTIONS)
                .where(TRANSACTIONS.ID.eq(transactionId))
                .fetchOne();
        if (txnRec == null) return Optional.empty();

        List<PostingsRecord> postingRecs = dsl.selectFrom(POSTINGS)
                .where(POSTINGS.TRANSACTION_ID.eq(transactionId))
                .orderBy(POSTINGS.SEQUENCE_NO.asc())
                .fetch();

        return Optional.of(mapper.toTransaction(txnRec, postingRecs));
    }

    @Override
    public boolean hasReversal(String originalTransactionId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(TRANSACTIONS)
                .where(TRANSACTIONS.REVERSES_TRANSACTION_ID.eq(originalTransactionId)));
    }

    @Override
    public Transaction insertTransaction(Transaction transaction) {
        dsl.insertInto(TRANSACTIONS)
                .set(mapper.toTransactionRecord(transaction))
                .execute();

        for (var posting : transaction.postings()) {
            dsl.insertInto(POSTINGS).set(mapper.toPostingRecord(posting)).execute();
        }

        return transaction;
    }

    @Override
    public Map<String, Long> getBalances(List<String> accountIds) {
        if (accountIds.isEmpty()) return Map.of();

        var rows = dsl.select(ACCOUNTS.ID, ACCOUNTS.TYPE, coalesce(sum(POSTINGS.AMOUNT), val(BigDecimal.ZERO)))
                .from(ACCOUNTS)
                .leftJoin(POSTINGS)
                .on(POSTINGS.ACCOUNT_ID.eq(ACCOUNTS.ID))
                .where(ACCOUNTS.ID.in(accountIds))
                .groupBy(ACCOUNTS.ID, ACCOUNTS.TYPE)
                .fetch();

        Map<String, Long> result = new HashMap<>();
        for (var rec : rows) {
            AccountType type = AccountType.valueOf(rec.value2());
            long rawSum = rec.value3().longValue();
            result.put(rec.value1(), type.isDebitNormal() ? rawSum : -rawSum);
        }
        return result;
    }

    @Override
    public long nextSequenceNo(String accountId) {
        Long max = dsl.select(org.jooq.impl.DSL.max(POSTINGS.SEQUENCE_NO))
                .from(POSTINGS)
                .where(POSTINGS.ACCOUNT_ID.eq(accountId))
                .fetchOne(0, Long.class);
        return max == null ? 1L : max + 1;
    }

    @Override
    public BalanceView getBalanceView(String accountId) {
        var rec = dsl.select(
                        ACCOUNTS.TYPE,
                        ACCOUNTS.CURRENCY,
                        coalesce(sum(POSTINGS.AMOUNT), val(BigDecimal.ZERO)),
                        count(POSTINGS.ID))
                .from(ACCOUNTS)
                .leftJoin(POSTINGS)
                .on(POSTINGS.ACCOUNT_ID.eq(ACCOUNTS.ID))
                .where(ACCOUNTS.ID.eq(accountId))
                .groupBy(ACCOUNTS.TYPE, ACCOUNTS.CURRENCY, ACCOUNTS.ID)
                .fetchOne();

        if (rec == null) throw new AccountNotFoundException(accountId);

        AccountType type = AccountType.valueOf(rec.value1());
        String currency = rec.value2();
        long rawSum = rec.value3().longValue();
        long postingCount = rec.value4().longValue();
        long balance = type.isDebitNormal() ? rawSum : -rawSum;

        return new BalanceView(accountId, new Money(balance, currency), Instant.now(), postingCount);
    }

    @Override
    public PagedResult<Transaction> getTransactionHistory(String accountId, String cursor, int limit) {
        int fetchLimit = limit + 1;

        var condition = POSTINGS.ACCOUNT_ID.eq(accountId);
        if (cursor != null) {
            condition = condition.and(POSTINGS.SEQUENCE_NO.lt(decodeCursor(cursor)));
        }

        List<PostingsRecord> postingRows = dsl.selectFrom(POSTINGS)
                .where(condition)
                .orderBy(POSTINGS.SEQUENCE_NO.desc())
                .limit(fetchLimit)
                .fetch();

        boolean hasMore = postingRows.size() > limit;
        List<PostingsRecord> pageRows = hasMore ? postingRows.subList(0, limit) : postingRows;

        if (pageRows.isEmpty()) {
            return new PagedResult<>(List.of(), null, false);
        }

        List<String> txnIds = pageRows.stream()
                .map(PostingsRecord::getTransactionId)
                .distinct()
                .toList();

        Map<String, TransactionsRecord> txnMap =
                dsl.selectFrom(TRANSACTIONS).where(TRANSACTIONS.ID.in(txnIds)).fetch().stream()
                        .collect(Collectors.toMap(TransactionsRecord::getId, Function.identity()));

        Map<String, List<PostingsRecord>> postingsByTxn =
                pageRows.stream().collect(Collectors.groupingBy(PostingsRecord::getTransactionId));

        List<Transaction> transactions = txnIds.stream()
                .filter(txnMap::containsKey)
                .map(id -> mapper.toTransaction(txnMap.get(id), postingsByTxn.getOrDefault(id, List.of())))
                .toList();

        long lastSeqNo = pageRows.getLast().getSequenceNo();
        String nextCursor = hasMore ? encodeCursor(lastSeqNo) : null;

        return new PagedResult<>(transactions, nextCursor, hasMore);
    }

    private String encodeCursor(long sequenceNo) {
        String json = "{\"seq\":" + sequenceNo + "}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private long decodeCursor(String cursor) {
        try {
            String json = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            int idx = json.indexOf("\"seq\":");
            if (idx < 0) throw new IllegalArgumentException("Invalid cursor format");
            String numStr = json.substring(idx + 6).replaceAll("[^0-9].*", "");
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
