package io.gfonseca.ledger.scenario

import io.gfonseca.ledger.AbstractIntegrationSpec
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.service.AccountsService
import io.gfonseca.ledger.domain.service.LedgerService
import org.springframework.beans.factory.annotation.Autowired

/**
 * A card payment arrives at the payment processor. The settlement account receives the
 * funds, the merchant is credited, and a platform fee is deducted. The payment is then
 * reversed. Balances must return to zero everywhere.
 */
class CardPaymentScenarioSpec extends AbstractIntegrationSpec {

    @Autowired
    LedgerService ledgerService

    @Autowired
    AccountsService accountsService

    def "card payment, fee deduction, and full reversal leave all balances at zero"() {
        given: "accounts are set up"
        def settlement = accountsService.createAccount("settlement", AccountType.ASSET, "EUR", [:])
        def capital = accountsService.createAccount("capital", AccountType.LIABILITY, "EUR", [:])
        def merchant = accountsService.createAccount("merchant-acme", AccountType.LIABILITY, "EUR", [:])
        def revenue = accountsService.createAccount("platform-revenue", AccountType.REVENUE, "EUR", [:])

        and: "the settlement account is funded (the processor holds funds on behalf of merchants)"
        ledgerService.submitTransaction(
                "fund-settlement", TransactionType.DEPOSIT, "Initial processor funding",
                [
                    new PostingRequest(settlement.id(), new Money(100_000, "EUR")),
                    new PostingRequest(capital.id(), new Money(-100_000, "EUR"))
                ],
                null, [:]
                )

        when: "a €100 card payment arrives"
        def payment = ledgerService.submitTransaction(
                "pay-001", TransactionType.DEPOSIT, "Card payment - terminal T456",
                [
                    new PostingRequest(settlement.id(), new Money(10000, "EUR")),
                    new PostingRequest(merchant.id(), new Money(-10000, "EUR"))
                ],
                null, [terminalId: "T456", cardLast4: "1234"]
                )

        then: "merchant balance increases by €100"
        ledgerService.getBalance(merchant.id()).balance().amount() == 10000

        when: "a €2 platform fee is applied"
        def fee = ledgerService.submitTransaction(
                "fee-001", TransactionType.FEE, "Platform fee 2%",
                [
                    new PostingRequest(merchant.id(), new Money(200, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-200, "EUR"))
                ],
                null, [:]
                )

        then: "merchant net balance is €98, revenue is €2"
        ledgerService.getBalance(merchant.id()).balance().amount() == 9800
        ledgerService.getBalance(revenue.id()).balance().amount() == 200

        when: "the fee is reversed first so the merchant is fully funded for the payment reversal"
        ledgerService.submitTransaction(
                "rev-fee-001", TransactionType.REVERSAL, "Fee reversal - chargeback",
                [
                    new PostingRequest(merchant.id(), new Money(-200, "EUR")),
                    new PostingRequest(revenue.id(), new Money(200, "EUR"))
                ],
                fee.id(), [:]
                )

        and: "the card payment is reversed (chargeback)"
        ledgerService.submitTransaction(
                "rev-pay-001", TransactionType.REVERSAL, "Chargeback - card payment T456",
                [
                    new PostingRequest(settlement.id(), new Money(-10000, "EUR")),
                    new PostingRequest(merchant.id(), new Money(10000, "EUR"))
                ],
                payment.id(), [:]
                )

        then: "all balances return to pre-payment state"
        ledgerService.getBalance(merchant.id()).balance().amount() == 0
        ledgerService.getBalance(revenue.id()).balance().amount() == 0
        ledgerService.getBalance(settlement.id()).balance().amount() == 100_000

        and: "all transaction history is recorded"
        ledgerService.getTransactionHistory(merchant.id(), null, 10).items().size() == 4
    }
}
