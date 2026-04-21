package io.gfonseca.ledger

import io.gfonseca.ledger.domain.exception.InsufficientFundsException
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.service.AccountsService
import io.gfonseca.ledger.domain.service.LedgerService
import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class LedgerServiceConcurrencySpec extends AbstractIntegrationSpec {

    @Autowired
    LedgerService ledgerService

    @Autowired
    AccountsService accountsService

    def "exactly one thread succeeds when 10 threads race to withdraw full balance"() {
        given:
        def asset = accountsService.createAccount("race-asset", AccountType.ASSET, "EUR", [:])
        def liability = accountsService.createAccount("race-liab", AccountType.LIABILITY, "EUR", [:])

        ledgerService.submitTransaction(
                "race-fund", TransactionType.DEPOSIT, "fund for race",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(liability.id(), new Money(-10000, "EUR"))
                ],
                null, [:]
                )

        def threads = 10
        def latch = new CountDownLatch(1)
        def executor = Executors.newFixedThreadPool(threads)
        def successCount = new AtomicInteger(0)
        def failCount = new AtomicInteger(0)

        when:
        def futures = (1..threads).collect { i ->
            executor.submit {
                latch.await()
                try {
                    ledgerService.submitTransaction(
                            "race-withdraw-${i}", TransactionType.WITHDRAWAL, "race withdrawal",
                            [
                                new PostingRequest(asset.id(), new Money(-10000, "EUR")),
                                new PostingRequest(liability.id(), new Money(10000, "EUR"))
                            ],
                            null, [:]
                            )
                    successCount.incrementAndGet()
                } catch (InsufficientFundsException ignored) {
                    failCount.incrementAndGet()
                }
            }
        }

        latch.countDown()
        futures.each { it.get() }
        executor.shutdown()

        then:
        successCount.get() == 1
        failCount.get() == 9
        ledgerService.getBalance(asset.id()).balance().amount() == 0
    }
}
