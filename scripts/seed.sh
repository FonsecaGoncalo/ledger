#!/usr/bin/env bash
# Seed the ledger with accounts and sample transactions.
# Requires: curl, jq
# Usage: ./scripts/seed.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
#
# Sign convention (positive = debit, negative = credit):
#   ASSET    (debit-normal):   +amount → balance increases
#   LIABILITY/REVENUE (credit-normal): -amount → balance increases
#
# Capital injection (both accounts start at zero, both balances increase):
#   settlement ASSET   +AMOUNT   (debit  — pool now holds funds)
#   capital    LIABILITY -AMOUNT  (credit — we owe it to the capital provider)
#
# Card payment received (customer pays, merchant gets credited):
#   settlement ASSET   +AMOUNT   (debit  — money enters pool)
#   merchant   LIABILITY -AMOUNT  (credit — we owe merchant)
#
# Fee deducted from merchant:
#   merchant   LIABILITY +FEE    (debit  — reduce what we owe)
#   fee_revenue REVENUE  -FEE    (credit — revenue increases)

set -euo pipefail
source "$(dirname "$0")/lib.sh"
ledger_init "$@"

# ── Create accounts ───────────────────────────────────────────────────────────
blue "\nCreating accounts..."

SETTLEMENT=$(post /accounts '{
  "externalRef": "settlement-main",
  "type": "ASSET",
  "currency": "EUR",
  "metadata": { "ownerName": "Settlement Pool" }
}')
SETTLEMENT_ID=$(echo "$SETTLEMENT" | jq -r .accountId)
dim "  Settlement:  $SETTLEMENT_ID"

CAPITAL=$(post /accounts '{
  "externalRef": "capital-initial",
  "type": "LIABILITY",
  "currency": "EUR",
  "metadata": { "ownerName": "Initial Capital" }
}')
CAPITAL_ID=$(echo "$CAPITAL" | jq -r .accountId)
dim "  Capital:     $CAPITAL_ID"

MERCHANT_A=$(post /accounts '{
  "externalRef": "merchant-cafe-lisboa",
  "type": "LIABILITY",
  "currency": "EUR",
  "metadata": { "ownerName": "Café Lisboa" }
}')
MERCHANT_A_ID=$(echo "$MERCHANT_A" | jq -r .accountId)
dim "  Merchant A:  $MERCHANT_A_ID"

MERCHANT_B=$(post /accounts '{
  "externalRef": "merchant-bica-express",
  "type": "LIABILITY",
  "currency": "EUR",
  "metadata": { "ownerName": "Bica Express" }
}')
MERCHANT_B_ID=$(echo "$MERCHANT_B" | jq -r .accountId)
dim "  Merchant B:  $MERCHANT_B_ID"

FEE_REVENUE=$(post /accounts '{
  "externalRef": "revenue-fees",
  "type": "REVENUE",
  "currency": "EUR",
  "metadata": { "ownerName": "Fee Revenue" }
}')
FEE_REVENUE_ID=$(echo "$FEE_REVENUE" | jq -r .accountId)
dim "  Fee Revenue: $FEE_REVENUE_ID"

green "Accounts created."

# ── Capital injection ─────────────────────────────────────────────────────────
# Both accounts start at zero. Settlement is debited (+), capital is credited (-).
# Both balances go from 0 → positive, so no insufficient-funds check fires.
blue "\nFunding settlement pool (€10,000.00)..."
CAPITAL_TXN=$(post /transactions "{
  \"idempotencyKey\": \"seed-capital-001\",
  \"type\": \"DEPOSIT\",
  \"description\": \"Initial capital injection\",
  \"postings\": [
    { \"accountId\": \"$SETTLEMENT_ID\", \"amount\":  1000000, \"currency\": \"EUR\" },
    { \"accountId\": \"$CAPITAL_ID\",    \"amount\": -1000000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"seed\" }
}")
echo "$CAPITAL_TXN" | jq -r '"  txn: " + .transactionId'
green "Settlement funded. Merchant accounts start at zero."

# ── Card payments received ────────────────────────────────────────────────────
blue "\nPosting card payments..."

for i in 1 2 3 4 5; do
  AMOUNT=$(( i * 2499 ))
  CAFE_TXN=$(post /transactions "{
    \"idempotencyKey\": \"seed-payment-cafe-$i\",
    \"type\": \"TRANSFER\",
    \"description\": \"Card payment at Café Lisboa\",
    \"postings\": [
      { \"accountId\": \"$SETTLEMENT_ID\", \"amount\":  $AMOUNT, \"currency\": \"EUR\" },
      { \"accountId\": \"$MERCHANT_A_ID\", \"amount\": -$AMOUNT, \"currency\": \"EUR\" }
    ],
    \"metadata\": { \"terminalId\": \"T001\", \"cardLast4\": \"$(( 1000 + i ))\" }
  }")
  echo "$CAFE_TXN" | jq -r '"  cafe txn: " + .transactionId'
done

for i in 1 2 3; do
  AMOUNT=$(( i * 5000 ))
  BICA_TXN=$(post /transactions "{
    \"idempotencyKey\": \"seed-payment-bica-$i\",
    \"type\": \"TRANSFER\",
    \"description\": \"Card payment at Bica Express\",
    \"postings\": [
      { \"accountId\": \"$SETTLEMENT_ID\", \"amount\":  $AMOUNT, \"currency\": \"EUR\" },
      { \"accountId\": \"$MERCHANT_B_ID\", \"amount\": -$AMOUNT, \"currency\": \"EUR\" }
    ],
    \"metadata\": { \"terminalId\": \"T002\", \"cardLast4\": \"$(( 4000 + i ))\" }
  }")
  echo "$BICA_TXN" | jq -r '"  bica txn: " + .transactionId'
done

green "Payments posted."

# ── Fee deductions ────────────────────────────────────────────────────────────
blue "\nDeducting fees..."

FEE_CAFE_TXN=$(post /transactions "{
  \"idempotencyKey\": \"seed-fee-cafe-001\",
  \"type\": \"FEE\",
  \"description\": \"Monthly platform fee — Café Lisboa\",
  \"postings\": [
    { \"accountId\": \"$MERCHANT_A_ID\",  \"amount\":  1990, \"currency\": \"EUR\" },
    { \"accountId\": \"$FEE_REVENUE_ID\", \"amount\": -1990, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"feeType\": \"platform\" }
}")
echo "$FEE_CAFE_TXN" | jq -r '"  txn: " + .transactionId'

FEE_BICA_TXN=$(post /transactions "{
  \"idempotencyKey\": \"seed-fee-bica-001\",
  \"type\": \"FEE\",
  \"description\": \"Monthly platform fee — Bica Express\",
  \"postings\": [
    { \"accountId\": \"$MERCHANT_B_ID\",  \"amount\":  1990, \"currency\": \"EUR\" },
    { \"accountId\": \"$FEE_REVENUE_ID\", \"amount\": -1990, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"feeType\": \"platform\" }
}")
echo "$FEE_BICA_TXN" | jq -r '"  txn: " + .transactionId'

green "Fees deducted."

# ── Final balances ────────────────────────────────────────────────────────────
blue "\nFinal balances:"

print_balance "Settlement"  "$SETTLEMENT_ID"
print_balance "Capital"     "$CAPITAL_ID"
print_balance "Merchant A"  "$MERCHANT_A_ID"
print_balance "Merchant B"  "$MERCHANT_B_ID"
print_balance "Fee Revenue" "$FEE_REVENUE_ID"

green "\nSeed complete."
