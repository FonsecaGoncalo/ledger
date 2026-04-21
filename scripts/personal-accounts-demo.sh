#!/usr/bin/env bash
# Create two personal accounts, deposit €1000 into the first, transfer €750 to
# the second, then withdraw €300 from the second.
# Requires: curl, jq
# Usage: ./scripts/personal-accounts-demo.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
#
# Sign convention (positive = debit, negative = credit):
#   ASSET     (debit-normal):  +amount → balance increases
#   LIABILITY (credit-normal): -amount → balance increases
#
# Personal accounts are LIABILITY (we owe the customer their money).
# Deposits and withdrawals use the settlement ASSET account as counterparty.

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
dim "  Settlement: $SETTLEMENT_ID"

ALICE=$(post /accounts "{
  \"externalRef\": \"personal-alice-$RUN_ID\",
  \"type\": \"LIABILITY\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Alice\" }
}")
ALICE_ID=$(echo "$ALICE" | jq -r .accountId)
dim "  Alice:      $ALICE_ID"

BOB=$(post /accounts "{
  \"externalRef\": \"personal-bob-$RUN_ID\",
  \"type\": \"LIABILITY\",
  \"currency\": \"EUR\",
  \"metadata\": { \"ownerName\": \"Bob\" }
}")
BOB_ID=$(echo "$BOB" | jq -r .accountId)
dim "  Bob:        $BOB_ID"

green "Accounts created."

# ── Deposit €1,000.00 into Alice ──────────────────────────────────────────────
# Settlement ASSET +100000 (cash enters pool); Alice LIABILITY -100000 (we owe Alice).
blue "\nDepositing €1,000.00 into Alice..."
DEPOSIT=$(post /transactions "{
  \"idempotencyKey\": \"deposit-alice-$RUN_ID\",
  \"type\": \"DEPOSIT\",
  \"description\": \"Deposit €1,000.00 into Alice's account\",
  \"postings\": [
    { \"accountId\": \"$SETTLEMENT_ID\", \"amount\":  100000, \"currency\": \"EUR\" },
    { \"accountId\": \"$ALICE_ID\",      \"amount\": -100000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
echo "$DEPOSIT" | jq -r '"  txn: " + .transactionId'
green "Deposit posted."

# ── Transfer €750.00 from Alice to Bob ────────────────────────────────────────
# Alice LIABILITY +75000 (we owe Alice less); Bob LIABILITY -75000 (we owe Bob more).
blue "\nTransferring €750.00 from Alice to Bob..."
TRANSFER=$(post /transactions "{
  \"idempotencyKey\": \"transfer-alice-bob-$RUN_ID\",
  \"type\": \"TRANSFER\",
  \"description\": \"Transfer €750.00 from Alice to Bob\",
  \"postings\": [
    { \"accountId\": \"$ALICE_ID\", \"amount\":  75000, \"currency\": \"EUR\" },
    { \"accountId\": \"$BOB_ID\",   \"amount\": -75000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
echo "$TRANSFER" | jq -r '"  txn: " + .transactionId'
green "Transfer posted."

# ── Withdraw €300.00 from Bob ─────────────────────────────────────────────────
# Bob LIABILITY +30000 (we owe Bob less); Settlement ASSET -30000 (cash leaves pool).
blue "\nWithdrawing €300.00 from Bob..."
WITHDRAW=$(post /transactions "{
  \"idempotencyKey\": \"withdraw-bob-$RUN_ID\",
  \"type\": \"WITHDRAWAL\",
  \"description\": \"Withdraw €300.00 from Bob's account\",
  \"postings\": [
    { \"accountId\": \"$BOB_ID\",        \"amount\":  30000, \"currency\": \"EUR\" },
    { \"accountId\": \"$SETTLEMENT_ID\", \"amount\": -30000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
echo "$WITHDRAW" | jq -r '"  txn: " + .transactionId'
green "Withdrawal posted."

# ── Final balances ────────────────────────────────────────────────────────────
blue "\nFinal balances:"
print_balance "Settlement" "$SETTLEMENT_ID"
print_balance "Alice"      "$ALICE_ID"
print_balance "Bob"        "$BOB_ID"

green "\nDone. Alice: 25000 (€250.00), Bob: 45000 (€450.00)."
