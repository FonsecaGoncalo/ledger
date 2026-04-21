package io.gfonseca.ledger.domain

import io.gfonseca.ledger.domain.model.Money
import spock.lang.Specification

class MoneySpec extends Specification {

    def "add returns sum of amounts for matching currency"() {
        given:
        def a = new Money(10000, "EUR")
        def b = new Money(5000, "EUR")

        when:
        def result = a.add(b)

        then:
        result.amount() == 15000
        result.currency() == "EUR"
    }

    def "add with negative produces correct net amount"() {
        given:
        def debit = new Money(10000, "EUR")
        def credit = new Money(-10000, "EUR")

        expect:
        debit.add(credit).amount() == 0
    }

    def "negate flips sign"() {
        expect:
        new Money(5000, "GBP").negate().amount() == -5000
        new Money(-3000, "GBP").negate().amount() == 3000
    }

    def "add rejects currency mismatch"() {
        when:
        new Money(100, "EUR").add(new Money(100, "USD"))

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects null currency"() {
        when:
        new Money(0, null)

        then:
        thrown(NullPointerException)
    }

    def "constructor rejects non-three-char currency"() {
        when:
        new Money(0, code)

        then:
        thrown(IllegalArgumentException)

        where:
        code << ["EU", "EURO", "", "US"]
    }

    def "add throws on overflow"() {
        when:
        new Money(Long.MAX_VALUE, "EUR").add(new Money(1, "EUR"))

        then:
        thrown(ArithmeticException)
    }

    def "negate throws on Long.MIN_VALUE"() {
        when:
        new Money(Long.MIN_VALUE, "EUR").negate()

        then:
        thrown(ArithmeticException)
    }
}
