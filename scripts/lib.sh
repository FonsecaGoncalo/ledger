#!/usr/bin/env bash
# Shared helpers for ledger demo scripts. Source this from another script:
#
#   #!/usr/bin/env bash
#   set -euo pipefail
#   source "$(dirname "$0")/lib.sh"
#   ledger_init "$@"   # sets BASE and RUN_ID, waits for the app
#
# Provides:
#   - green / blue / dim / red                 colour helpers
#   - log_request  METHOD PATH [BODY]           dimmed request echo
#   - log_response BODY                         pretty-printed JSON response
#   - post PATH BODY                            POST, logs request + response, returns body
#   - get  PATH                                 GET,  logs request + response, returns body
#   - wait_for_app                              polls /actuator/health
#   - ledger_init [BASE_URL]                    sets BASE, RUN_ID; calls wait_for_app

# ── Colours ───────────────────────────────────────────────────────────────────
green() { printf '\033[32m%b\033[0m\n' "$*"; }
blue()  { printf '\033[34m%b\033[0m\n' "$*"; }
dim()   { printf '\033[2m%b\033[0m\n'  "$*"; }
red()   { printf '\033[31m%b\033[0m\n' "$*"; }

# ── Logging ───────────────────────────────────────────────────────────────────
log_request() {
  local method="$1" path="$2" body="${3:-}"
  printf '\033[2m  → %s %s\033[0m\n' "$method" "$BASE$path"
  if [[ -n "$body" ]]; then
    printf '\033[2m'
    echo "$body" | jq . 2>/dev/null | sed 's/^/    /' || echo "    $body"
    printf '\033[0m'
  fi
}

log_response() {
  local body="$1" status="${2:-}"
  if [[ -n "$status" ]]; then
    printf '\033[2m  ← HTTP %s\033[0m\n' "$status"
  fi
  printf '\033[2m'
  echo "$body" | jq . 2>/dev/null | sed 's/^/    /' || echo "    $body"
  printf '\033[0m'
}

# ── HTTP ──────────────────────────────────────────────────────────────────────
post() {
  local path="$1" body="$2"
  log_request "POST" "$path" "$body" >&2
  local response status
  response=$(curl -sS -X POST "$BASE$path" \
    -H "Content-Type: application/json" \
    -d "$body" \
    -w $'\n__HTTP__%{http_code}')
  status="${response##*__HTTP__}"
  response="${response%__HTTP__*}"
  response="${response%$'\n'}"
  log_response "$response" "$status" >&2
  if [[ -z "$status" || "$status" -lt 200 || "$status" -ge 300 ]]; then
    red "POST $path failed (HTTP ${status:-???})" >&2
    return 1
  fi
  echo "$response"
}

# Like `post`, but expects a 4xx/5xx. Returns the body on expected failure and
# fails the script if the call unexpectedly succeeds. Optional third arg pins
# the expected HTTP status exactly (e.g. 409).
post_expect_fail() {
  local path="$1" body="$2" expected_status="${3:-}"
  log_request "POST" "$path" "$body" >&2
  local response status
  response=$(curl -sS -X POST "$BASE$path" \
    -H "Content-Type: application/json" \
    -d "$body" \
    -w $'\n__HTTP__%{http_code}')
  status="${response##*__HTTP__}"
  response="${response%__HTTP__*}"
  response="${response%$'\n'}"
  log_response "$response" "$status" >&2
  if [[ -n "$expected_status" ]]; then
    if [[ "$status" != "$expected_status" ]]; then
      red "POST $path expected HTTP $expected_status, got ${status:-???}" >&2
      return 1
    fi
  elif [[ -z "$status" || "$status" -lt 400 ]]; then
    red "POST $path expected failure, got HTTP ${status:-???}" >&2
    return 1
  fi
  echo "$response"
}

get() {
  local path="$1"
  log_request "GET" "$path" >&2
  local response status
  response=$(curl -sS "$BASE$path" -w $'\n__HTTP__%{http_code}')
  status="${response##*__HTTP__}"
  response="${response%__HTTP__*}"
  response="${response%$'\n'}"
  log_response "$response" "$status" >&2
  if [[ -z "$status" || "$status" -lt 200 || "$status" -ge 300 ]]; then
    red "GET $path failed (HTTP ${status:-???})" >&2
    return 1
  fi
  echo "$response"
}

# ── App readiness ─────────────────────────────────────────────────────────────
wait_for_app() {
  blue "Waiting for app at $BASE ..."
  local i
  for i in $(seq 1 30); do
    if curl -sf "${BASE%/api/v1}/actuator/health" 2>/dev/null | grep -q '"UP"'; then
      green "App is up."
      return 0
    fi
    sleep 1
  done
  red "App not ready after 30s — is it running?"
  return 1
}

# ── Convenience: balance printer ──────────────────────────────────────────────
print_balance() {
  local label="$1" id="$2"
  local response result
  response=$(get "/accounts/$id/balance")
  result=$(echo "$response" | jq -r '"\(.balance) \(.currency)"')
  printf '  %-12s %s\n' "$label" "$result"
}

# ── Init ──────────────────────────────────────────────────────────────────────
# Sets BASE and RUN_ID (timestamp + PID for re-runnable idempotency keys)
# and waits for the app to be up.
ledger_init() {
  BASE="${1:-http://localhost:8080}/api/v1"
  RUN_ID="$(date +%Y%m%d-%H%M%S)-$$"
  wait_for_app
}
