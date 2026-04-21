package io.gfonseca.ledger

import io.gfonseca.ledger.domain.exception.AccountNotFoundException
import io.gfonseca.ledger.domain.exception.AlreadyReversedException
import io.gfonseca.ledger.domain.exception.CurrencyMismatchException
import io.gfonseca.ledger.domain.exception.InsufficientFundsException
import io.gfonseca.ledger.domain.exception.InvalidReversalException
import io.gfonseca.ledger.domain.exception.OriginalTransactionNotFoundException
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.service.AccountsService
import io.gfonseca.ledger.domain.service.LedgerService
import org.springframework.beans.factory.annotation.Autowired

class LedgerServiceSpec extends AbstractIntegrationSpec {

    @Autowired
    LedgerService ledgerService

    @Autowired
    AccountsService accountsService

    def "creates an account and retrieves it by id"() {
        when:
        def account = accountsService.createAccount("ext-001", AccountType.ASSET, "EUR", [:])

        then:
        account.id().startsWith("acct_")
        account.externalRef() == "ext-001"
        account.currency() == "EUR"
        account.type() == AccountType.ASSET

        and:
        accountsService.findAccount(account.id()).isPresent()
    }

    def "deposit increases asset account balance"() {
        given:
        def asset = accountsService.createAccount("asset-001", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-001", AccountType.REVENUE, "EUR", [:])

        when:
        ledgerService.submitTransaction(
                "idem-001", TransactionType.DEPOSIT, "Initial deposit",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        then:
        ledgerService.getBalance(asset.id()).balance().amount() == 10000
        ledgerService.getBalance(revenue.id()).balance().amount() == 10000
    }

    def "withdrawal reduces asset balance"() {
        given:
        def asset = accountsService.createAccount("asset-002", AccountType.ASSET, "EUR", [:])
        def liability = accountsService.createAccount("liab-001", AccountType.LIABILITY, "EUR", [:])

        ledgerService.submitTransaction(
                "idem-002", TransactionType.DEPOSIT, "Fund asset",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(liability.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        when:
        ledgerService.submitTransaction(
                "idem-003", TransactionType.WITHDRAWAL, "Withdraw",
                [
                    new PostingRequest(asset.id(), new Money(-5000, "EUR")),
                    new PostingRequest(liability.id(), new Money(5000, "EUR"))
                ],
                null, [:]
                )

        then:
        ledgerService.getBalance(asset.id()).balance().amount() == 5000
    }

    def "idempotent submission returns original transaction without re-executing"() {
        given:
        def asset = accountsService.createAccount("asset-003", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-002", AccountType.REVENUE, "EUR", [:])

        def first = ledgerService.submitTransaction(
                "idem-004", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        when:
        def second = ledgerService.submitTransaction(
                "idem-004", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        then:
        second.id() == first.id()
        ledgerService.getBalance(asset.id()).balance().amount() == 10000
    }

    def "throws AccountNotFoundException for unknown account"() {
        when:
        ledgerService.getBalance("acct_doesnotexist")

        then:
        thrown(AccountNotFoundException)
    }

    def "throws InsufficientFundsException when asset balance would go negative"() {
        given:
        def asset = accountsService.createAccount("asset-004", AccountType.ASSET, "EUR", [:])
        def liability = accountsService.createAccount("liab-002", AccountType.LIABILITY, "EUR", [:])

        ledgerService.submitTransaction(
                "idem-005", TransactionType.DEPOSIT, "fund",
                [
                    new PostingRequest(asset.id(), new Money(5000, "EUR")),
                    new PostingRequest(liability.id(), new Money(-5000, "EUR"))
                ],
                null, [:]
                )

        when:
        ledgerService.submitTransaction(
                "idem-006", TransactionType.WITHDRAWAL, "overdraft",
                [
                    new PostingRequest(asset.id(), new Money(-6000, "EUR")),
                    new PostingRequest(liability.id(), new Money(6000, "EUR"))
                ],
                null, [:]
                )

        then:
        thrown(InsufficientFundsException)
    }

    def "throws CurrencyMismatchException when posting currency differs from account currency"() {
        given:
        def eurAccount = accountsService.createAccount("asset-005", AccountType.ASSET, "EUR", [:])
        def usdAccount = accountsService.createAccount("asset-006", AccountType.ASSET, "USD", [:])

        when:
        ledgerService.submitTransaction(
                "idem-007", TransactionType.TRANSFER, "bad currency",
                [
                    new PostingRequest(eurAccount.id(), new Money(100, "USD")),
                    new PostingRequest(usdAccount.id(), new Money(-100, "USD"))
                ],
                null, [:]
                )

        then:
        thrown(CurrencyMismatchException)
    }

    def "transaction history returns postings in reverse sequence order"() {
        given:
        def asset = accountsService.createAccount("asset-007", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-003", AccountType.REVENUE, "EUR", [:])

        3.times { i ->
            ledgerService.submitTransaction(
                    "idem-hist-${i}", TransactionType.DEPOSIT, "deposit ${i}",
                    [
                        new PostingRequest(asset.id(), new Money(1000, "EUR")),
                        new PostingRequest(revenue.id(), new Money(-1000, "EUR"))
                    ],
                    null, [:]
                    )
        }

        when:
        def page = ledgerService.getTransactionHistory(asset.id(), null, 10)

        then:
        page.items().size() == 3
        !page.hasMore()
    }

    def "reversal transaction links to original and restores balance"() {
        given:
        def asset = accountsService.createAccount("asset-008", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-004", AccountType.REVENUE, "EUR", [:])

        def original = ledgerService.submitTransaction(
                "idem-orig", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        when:
        ledgerService.submitTransaction(
                "idem-rev", TransactionType.REVERSAL, "reversal",
                [
                    new PostingRequest(asset.id(), new Money(-10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(10000, "EUR"))
                ],
                original.id(), [:]
                )

        then:
        ledgerService.getBalance(asset.id()).balance().amount() == 0
        ledgerService.getBalance(revenue.id()).balance().amount() == 0
    }

    def "rejects REVERSAL without a link to the original transaction"() {
        given:
        def asset = accountsService.createAccount("asset-rev-1", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-1", AccountType.REVENUE, "EUR", [:])

        when:
        ledgerService.submitTransaction(
                "idem-rev-nolink", TransactionType.REVERSAL, "reversal with no link",
                [
                    new PostingRequest(asset.id(), new Money(-100, "EUR")),
                    new PostingRequest(revenue.id(), new Money(100, "EUR"))
                ],
                null, [:]
                )

        then:
        thrown(InvalidReversalException)
    }

    def "rejects non-REVERSAL transaction that sets reversesTransactionId"() {
        given:
        def asset = accountsService.createAccount("asset-rev-2", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-2", AccountType.REVENUE, "EUR", [:])

        def original = ledgerService.submitTransaction(
                "idem-rev-orig2", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(100, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-100, "EUR"))
                ],
                null, [:]
                )

        when:
        ledgerService.submitTransaction(
                "idem-rev-wrongtype", TransactionType.DEPOSIT, "deposit masquerading as reversal",
                [
                    new PostingRequest(asset.id(), new Money(-100, "EUR")),
                    new PostingRequest(revenue.id(), new Money(100, "EUR"))
                ],
                original.id(), [:]
                )

        then:
        thrown(InvalidReversalException)
    }

    def "rejects reversal that references a non-existent original"() {
        given:
        def asset = accountsService.createAccount("asset-rev-3", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-3", AccountType.REVENUE, "EUR", [:])

        when:
        ledgerService.submitTransaction(
                "idem-rev-missing", TransactionType.REVERSAL, "reversal of nothing",
                [
                    new PostingRequest(asset.id(), new Money(-100, "EUR")),
                    new PostingRequest(revenue.id(), new Money(100, "EUR"))
                ],
                "txn_doesnotexist", [:]
                )

        then:
        thrown(OriginalTransactionNotFoundException)
    }

    def "rejects reversal whose postings do not mirror the original"() {
        given:
        def asset = accountsService.createAccount("asset-rev-4", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-4", AccountType.REVENUE, "EUR", [:])

        def original = ledgerService.submitTransaction(
                "idem-rev-orig4", TransactionType.DEPOSIT, "deposit 10000",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        when: "reversal tries to reverse only half of the original"
        ledgerService.submitTransaction(
                "idem-rev-partial", TransactionType.REVERSAL, "partial reversal",
                [
                    new PostingRequest(asset.id(), new Money(-5000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(5000, "EUR"))
                ],
                original.id(), [:]
                )

        then:
        thrown(InvalidReversalException)
    }

    def "rejects reversal that uses different accounts from the original"() {
        given:
        def asset = accountsService.createAccount("asset-rev-5", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-5", AccountType.REVENUE, "EUR", [:])
        def otherRevenue = accountsService.createAccount("rev-rev-5b", AccountType.REVENUE, "EUR", [:])

        def original = ledgerService.submitTransaction(
                "idem-rev-orig5", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(1000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-1000, "EUR"))
                ],
                null, [:]
                )

        when: "reversal routes the credit to a different revenue account"
        ledgerService.submitTransaction(
                "idem-rev-wrongacc", TransactionType.REVERSAL, "reversal to wrong account",
                [
                    new PostingRequest(asset.id(), new Money(-1000, "EUR")),
                    new PostingRequest(otherRevenue.id(), new Money(1000, "EUR"))
                ],
                original.id(), [:]
                )

        then:
        thrown(InvalidReversalException)
    }

    def "rejects a second reversal of the same original transaction"() {
        given:
        def asset = accountsService.createAccount("asset-rev-6", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-6", AccountType.REVENUE, "EUR", [:])

        def original = ledgerService.submitTransaction(
                "idem-rev-orig6", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        ledgerService.submitTransaction(
                "idem-rev-first", TransactionType.REVERSAL, "first reversal",
                [
                    new PostingRequest(asset.id(), new Money(-10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(10000, "EUR"))
                ],
                original.id(), [:]
                )

        when: "a second reversal of the same original is attempted under a new idempotency key"
        ledgerService.submitTransaction(
                "idem-rev-second", TransactionType.REVERSAL, "second reversal",
                [
                    new PostingRequest(asset.id(), new Money(-10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(10000, "EUR"))
                ],
                original.id(), [:]
                )

        then:
        thrown(AlreadyReversedException)
    }

    def "rejects reversal of a reversal"() {
        given:
        def asset = accountsService.createAccount("asset-rev-7", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-7", AccountType.REVENUE, "EUR", [:])

        def original = ledgerService.submitTransaction(
                "idem-rev-orig7", TransactionType.DEPOSIT, "deposit",
                [
                    new PostingRequest(asset.id(), new Money(500, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-500, "EUR"))
                ],
                null, [:]
                )

        def reversal = ledgerService.submitTransaction(
                "idem-rev-first7", TransactionType.REVERSAL, "reversal",
                [
                    new PostingRequest(asset.id(), new Money(-500, "EUR")),
                    new PostingRequest(revenue.id(), new Money(500, "EUR"))
                ],
                original.id(), [:]
                )

        when:
        ledgerService.submitTransaction(
                "idem-rev-of-rev", TransactionType.REVERSAL, "reversal of reversal",
                [
                    new PostingRequest(asset.id(), new Money(500, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-500, "EUR"))
                ],
                reversal.id(), [:]
                )

        then:
        thrown(InvalidReversalException)
    }
}
