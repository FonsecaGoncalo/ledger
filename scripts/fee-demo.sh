#!/usr/bin/env bash
# Demo a transaction with an applied fee. Alice pays €50.00 to a merchant with
# a €1.00 platform fee. The whole thing is one atomic transaction with three
# postings: Alice is debited €51.00, the merchant is credited €50.00, and the
# remaining €1.00 lands on a fee-revenue account.
# Requires: curl, jq
# Usage: ./scripts/fee-demo.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
#
# Why one transaction? The payment and the fee have to post or fail together.
# Splitting them would open a window where the customer is charged but the fee
# never lands (or vice versa). A single balanced transaction makes that
# impossible by construction — all three postings commit atomically or none do.
#
# Sign convention (positive = debit, negative = credit):
#   ASSET     (debit-normal):  +amount → balance increases
#   LIABILITY (credit-normal): -amount → balance increases
#   REVENUE   (credit-normal): -amount → balance increases

set -euo pipefail
source "$(dirname "$0")/lib.sh"
ledger_init "$@"

# ── Create accounts ───────────────────────────────────────────────────────────
blue "\nCreating accounts..."

SETTLEMENT=$(post /accounts "{
  \"externalRef\": \"settlement-$RUN_ID\",
  \"type\": \"ASSET\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Settlement Pool\" }
}")
SETTLEMENT_ID=$(echo "$SETTLEMENT" | jq -r .accountId)
dim "  Settlement:  $SETTLEMENT_ID"

ALICE=$(post /accounts "{
  \"externalRef\": \"personal-alice-$RUN_ID\",
  \"type\": \"LIABILITY\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Alice\" }
}")
ALICE_ID=$(echo "$ALICE" | jq -r .accountId)
dim "  Alice:       $ALICE_ID"

MERCHANT=$(post /accounts "{
  \"externalRef\": \"merchant-cafe-$RUN_ID\",
  \"type\": \"LIABILITY\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Café Mercado\" }
}")
MERCHANT_ID=$(echo "$MERCHANT" | jq -r .accountId)
dim "  Merchant:    $MERCHANT_ID"

FEE_REVENUE=$(post /accounts "{
  \"externalRef\": \"revenue-fees-$RUN_ID\",
  \"type\": \"REVENUE\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Platform Fee Revenue\" }
}")
FEE_REVENUE_ID=$(echo "$FEE_REVENUE" | jq -r .accountId)
dim "  Fee Revenue: $FEE_REVENUE_ID"

green "Accounts created."

# ── Deposit €100.00 into Alice ────────────────────────────────────────────────
# Settlement ASSET +10000 (cash enters pool); Alice LIABILITY -10000 (we owe Alice).
blue "\nDepositing €100.00 into Alice..."
DEPOSIT=$(post /transactions "{
  \"idempotencyKey\": \"deposit-alice-$RUN_ID\",
  \"type\": \"DEPOSIT\",
  \"description\": \"Deposit €100.00 into Alice's account\",
  \"postings\": [
    { \"accountId\": \"$SETTLEMENT_ID\", \"amount\":  10000, \"currency\": \"EUR\" },
    { \"accountId\": \"$ALICE_ID\",      \"amount\": -10000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
echo "$DEPOSIT" | jq -r '"  txn: " + .transactionId'
green "Deposit posted."

# ── Alice pays merchant €50.00 with a €1.00 fee ──────────────────────────────
# Alice LIABILITY    +5100 (we owe Alice €51.00 less — she's paying the full amount, fee included)
# Merchant LIABILITY -5000 (we owe the merchant €50.00 more — they receive the net)
# Fee Revenue        -100  (REVENUE, credit-normal: -€1.00 raw → +€1.00 visible — platform earns the fee)
# Check: (+5100) + (-5000) + (-100) = 0 EUR ✓
blue "\nAlice pays €50.00 to the merchant with a €1.00 platform fee..."
PAYMENT=$(post /transactions "{
  \"idempotencyKey\": \"payment-alice-cafe-$RUN_ID\",
  \"type\": \"TRANSFER\",
  \"description\": \"Card payment: Alice → Café Mercado €50.00 (fee €1.00)\",
  \"postings\": [
    { \"accountId\": \"$ALICE_ID\",       \"amount\":  5100, \"currency\": \"EUR\" },
    { \"accountId\": \"$MERCHANT_ID\",    \"amount\": -5000, \"currency\": \"EUR\" },
    { \"accountId\": \"$FEE_REVENUE_ID\", \"amount\":  -100, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\", \"feeBps\": 200, \"feeAmount\": 100 }
}")
echo "$PAYMENT" | jq -r '"  txn: " + .transactionId'
green "Payment posted."

# ── Final balances ────────────────────────────────────────────────────────────
blue "\nFinal balances:"
print_balance "Settlement"  "$SETTLEMENT_ID"
print_balance "Alice"       "$ALICE_ID"
print_balance "Merchant"    "$MERCHANT_ID"
print_balance "Fee Revenue" "$FEE_REVENUE_ID"

green "\nDone. Alice: 4900 (€49.00), Merchant: 5000 (€50.00), Fee Revenue: 100 (€1.00)."
