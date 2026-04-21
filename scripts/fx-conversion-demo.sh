#!/usr/bin/env bash
# Demo a cross-currency conversion. Alice funds her EUR account with €100, then
# converts all of it into $87 on her USD account in a single multi-currency
# transaction (4 postings). Each currency sums to zero independently.
# Requires: curl, jq
# Usage: ./scripts/fx-conversion-demo.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
#
# Why 4 postings? Accounts are single-currency, and the double-entry invariant
# requires postings to sum to zero PER CURRENCY. A direct "-100 EUR / +87 USD"
# transaction would break both rules. Instead we route through per-currency
# bridge/treasury accounts so each currency balances on its own:
#
#   EUR leg (sums to 0 EUR):   Alice EUR -100  ←→  EUR treasury +100  (leaves pool)
#   USD leg (sums to 0 USD):   USD treasury -87 (leaves pool)  ←→  Alice USD +87
#
# The treasury accounts are ASSET accounts representing our per-currency cash
# pools. In a real system the FX rate spread accumulates there (or on a
# dedicated FX P&L account) — this demo uses the user-supplied rate directly.
#
# Sign convention (positive = debit, negative = credit):
#   ASSET     (debit-normal):  +amount → balance increases
#   LIABILITY (credit-normal): -amount → balance increases

set -euo pipefail
source "$(dirname "$0")/lib.sh"
ledger_init "$@"

# ── Create accounts ───────────────────────────────────────────────────────────
blue "\nCreating accounts..."

EUR_TREASURY=$(post /accounts "{
  \"externalRef\": \"treasury-eur-$RUN_ID\",
  \"type\": \"ASSET\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"EUR Treasury\" }
}")
EUR_TREASURY_ID=$(echo "$EUR_TREASURY" | jq -r .accountId)
dim "  EUR Treasury: $EUR_TREASURY_ID"

USD_TREASURY=$(post /accounts "{
  \"externalRef\": \"treasury-usd-$RUN_ID\",
  \"type\": \"ASSET\",
  \"currency\": \"USD\",
  \"metadata\": { \"ownerName\": \"USD Treasury\" }
}")
USD_TREASURY_ID=$(echo "$USD_TREASURY" | jq -r .accountId)
dim "  USD Treasury: $USD_TREASURY_ID"

ALICE_EUR=$(post /accounts "{
  \"externalRef\": \"personal-alice-eur-$RUN_ID\",
  \"type\": \"LIABILITY\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Alice (EUR)\" }
}")
ALICE_EUR_ID=$(echo "$ALICE_EUR" | jq -r .accountId)
dim "  Alice EUR:    $ALICE_EUR_ID"

ALICE_USD=$(post /accounts "{
  \"externalRef\": \"personal-alice-usd-$RUN_ID\",
  \"type\": \"LIABILITY\",
  \"currency\": \"USD\",
  \"metadata\": { \"ownerName\": \"Alice (USD)\" }
}")
ALICE_USD_ID=$(echo "$ALICE_USD" | jq -r .accountId)
dim "  Alice USD:    $ALICE_USD_ID"

green "Accounts created."

# ── Deposit €100.00 into Alice's EUR account ──────────────────────────────────
# EUR Treasury ASSET +10000 (cash enters EUR pool); Alice EUR LIABILITY -10000 (we owe Alice).
blue "\nDepositing €100.00 into Alice's EUR account..."
DEPOSIT=$(post /transactions "{
  \"idempotencyKey\": \"deposit-alice-eur-$RUN_ID\",
  \"type\": \"DEPOSIT\",
  \"description\": \"Deposit €100.00 into Alice's EUR account\",
  \"postings\": [
    { \"accountId\": \"$EUR_TREASURY_ID\", \"amount\":  10000, \"currency\": \"EUR\" },
    { \"accountId\": \"$ALICE_EUR_ID\",    \"amount\": -10000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
echo "$DEPOSIT" | jq -r '"  txn: " + .transactionId'
green "Deposit posted."

blue "\nBalances before conversion:"
print_balance "EUR Treasury" "$EUR_TREASURY_ID"
print_balance "USD Treasury" "$USD_TREASURY_ID"
print_balance "Alice EUR"    "$ALICE_EUR_ID"
print_balance "Alice USD"    "$ALICE_USD_ID"

# ── Convert €100.00 → $87.00 in a single 4-posting transaction ────────────────
# EUR leg  (sums to 0 EUR):
#   Alice EUR    LIABILITY +10000  (we owe Alice 100 EUR less)
#   EUR Treasury ASSET     -10000  (EUR pool shrinks — EUR leaves for FX counterparty)
# USD leg  (sums to 0 USD):
#   USD Treasury ASSET     + 8700  (USD pool grows — USD arrives from FX counterparty)
#   Alice USD    LIABILITY - 8700  (we owe Alice 87 USD more)
blue "\nConverting €100.00 → \$87.00 (rate 1 EUR = 0.87 USD)..."
FX=$(post /transactions "{
  \"idempotencyKey\": \"fx-alice-eur-to-usd-$RUN_ID\",
  \"type\": \"TRANSFER\",
  \"description\": \"FX conversion: Alice €100.00 → \$87.00\",
  \"postings\": [
    { \"accountId\": \"$ALICE_EUR_ID\",    \"amount\":  10000, \"currency\": \"EUR\" },
    { \"accountId\": \"$EUR_TREASURY_ID\", \"amount\": -10000, \"currency\": \"EUR\" },
    { \"accountId\": \"$USD_TREASURY_ID\", \"amount\":   8700, \"currency\": \"USD\" },
    { \"accountId\": \"$ALICE_USD_ID\",    \"amount\":  -8700, \"currency\": \"USD\" }
  ],
  \"metadata\": { \"source\": \"demo\", \"rate\": \"0.87\", \"pair\": \"EUR/USD\" }
}")
echo "$FX" | jq -r '"  txn: " + .transactionId'
green "FX conversion posted."

# ── Final balances ────────────────────────────────────────────────────────────
blue "\nFinal balances:"
print_balance "EUR Treasury" "$EUR_TREASURY_ID"
print_balance "USD Treasury" "$USD_TREASURY_ID"
print_balance "Alice EUR"    "$ALICE_EUR_ID"
print_balance "Alice USD"    "$ALICE_USD_ID"

green "\nDone. Alice EUR: 0, Alice USD: 8700 (\$87.00)."
