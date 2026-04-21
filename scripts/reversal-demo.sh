#!/usr/bin/env bash
# Demo a REVERSAL: deposit €1,000 into Alice, transfer €400 to Bob, then reverse the transfer.
# Requires: curl, jq
# Usage: ./scripts/reversal-demo.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
#
# Reversal rules (see CLAUDE.md):
#   - type MUST be REVERSAL and MUST carry reversesTransactionId.
#   - Postings are an exact multiset mirror of the original: same accounts, same
#     currencies, amounts negated. No partial reversals.
#   - An original can be reversed at most once; a reversal cannot be reversed.

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

# ── Transfer €400.00 Alice → Bob ──────────────────────────────────────────────
blue "\nTransferring €400.00 from Alice to Bob..."
TRANSFER=$(post /transactions "{
  \"idempotencyKey\": \"transfer-alice-bob-$RUN_ID\",
  \"type\": \"TRANSFER\",
  \"description\": \"Transfer €400.00 from Alice to Bob\",
  \"postings\": [
    { \"accountId\": \"$ALICE_ID\", \"amount\":  40000, \"currency\": \"EUR\" },
    { \"accountId\": \"$BOB_ID\",   \"amount\": -40000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
TRANSFER_ID=$(echo "$TRANSFER" | jq -r .transactionId)
echo "  txn: $TRANSFER_ID"
green "Transfer posted."

blue "\nBalances after transfer:"
print_balance "Settlement" "$SETTLEMENT_ID"
print_balance "Alice"      "$ALICE_ID"
print_balance "Bob"        "$BOB_ID"

# ── Reverse the transfer ──────────────────────────────────────────────────────
# Mirror postings: same accounts and currencies, amounts negated.
#   Original: Alice +40000, Bob -40000
#   Reversal: Alice -40000, Bob +40000
blue "\nReversing the transfer (txn $TRANSFER_ID)..."
REVERSAL=$(post /transactions "{
  \"idempotencyKey\": \"reversal-transfer-$RUN_ID\",
  \"type\": \"REVERSAL\",
  \"description\": \"Reverse transfer Alice → Bob — made in error\",
  \"reversesTransactionId\": \"$TRANSFER_ID\",
  \"postings\": [
    { \"accountId\": \"$ALICE_ID\", \"amount\": -40000, \"currency\": \"EUR\" },
    { \"accountId\": \"$BOB_ID\",   \"amount\":  40000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"reason\": \"operator-error\" }
}")
echo "$REVERSAL" | jq -r '"  txn: " + .transactionId'
green "Reversal posted."

# ── Final balances ────────────────────────────────────────────────────────────
blue "\nFinal balances:"
print_balance "Settlement" "$SETTLEMENT_ID"
print_balance "Alice"      "$ALICE_ID"
print_balance "Bob"        "$BOB_ID"

green "\nDone. Alice back to 100000 (€1,000.00), Bob back to 0."
