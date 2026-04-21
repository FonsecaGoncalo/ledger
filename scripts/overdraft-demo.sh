#!/usr/bin/env bash
# Demo an overdraft attempt: Alice has €200 but tries to withdraw €500.
# The service must reject with 409 INSUFFICIENT_FUNDS and Alice's balance
# must be unchanged (postings are immutable; no partial state).
# Requires: curl, jq
# Usage: ./scripts/overdraft-demo.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
#
# The sufficient-funds rule runs inside the locked DB transaction
# (see SufficientFundsRule + docs/architecture.md).

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

green "Accounts created."

# ── Deposit €200.00 into Alice ────────────────────────────────────────────────
blue "\nDepositing €200.00 into Alice..."
DEPOSIT=$(post /transactions "{
  \"idempotencyKey\": \"deposit-alice-$RUN_ID\",
  \"type\": \"DEPOSIT\",
  \"description\": \"Deposit €200.00 into Alice's account\",
  \"postings\": [
    { \"accountId\": \"$SETTLEMENT_ID\", \"amount\":  20000, \"currency\": \"EUR\" },
    { \"accountId\": \"$ALICE_ID\",      \"amount\": -20000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}")
echo "$DEPOSIT" | jq -r '"  txn: " + .transactionId'
green "Deposit posted."

blue "\nBalance before overdraft attempt:"
print_balance "Alice" "$ALICE_ID"

# ── Attempt to withdraw €500.00 from Alice — must fail ────────────────────────
# Alice LIABILITY +50000 (tries to reduce what we owe by more than we owe);
# Settlement ASSET -50000. The SufficientFundsRule rejects this with 409.
blue "\nAttempting to withdraw €500.00 from Alice (expected: 409 INSUFFICIENT_FUNDS)..."
OVERDRAFT=$(post_expect_fail /transactions "{
  \"idempotencyKey\": \"overdraft-alice-$RUN_ID\",
  \"type\": \"WITHDRAWAL\",
  \"description\": \"Withdraw €500.00 from Alice's account (overdraft)\",
  \"postings\": [
    { \"accountId\": \"$ALICE_ID\",      \"amount\":  50000, \"currency\": \"EUR\" },
    { \"accountId\": \"$SETTLEMENT_ID\", \"amount\": -50000, \"currency\": \"EUR\" }
  ],
  \"metadata\": { \"source\": \"demo\" }
}" 409)

ERROR_CODE=$(echo "$OVERDRAFT" | jq -r .error)
if [[ "$ERROR_CODE" != "INSUFFICIENT_FUNDS" ]]; then
  red "Unexpected error code: $ERROR_CODE (expected INSUFFICIENT_FUNDS)"
  exit 1
fi
green "Rejected as expected: $ERROR_CODE."

# ── Confirm balance is unchanged ──────────────────────────────────────────────
blue "\nBalance after rejected overdraft (should be unchanged):"
print_balance "Settlement" "$SETTLEMENT_ID"
print_balance "Alice"      "$ALICE_ID"

green "\nDone. Overdraft rejected; Alice still has 20000 (€200.00)."
