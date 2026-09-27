#!/usr/bin/env bash
# Rotate the WhatsApp Meta Cloud API access token (and optionally the phone
# number id) without a rebuild. The token is a RUNTIME env var for
# notification-service, so we just rewrite .env and recreate that one container.
#
# Usage:
#   ./scripts/rotate-whatsapp-token.sh                 # prompts for the new token
#   ./scripts/rotate-whatsapp-token.sh <TOKEN>         # token as an argument
#   ./scripts/rotate-whatsapp-token.sh <TOKEN> <PHONE_NUMBER_ID>
#
# Run it from the repo root, on the machine where the stack runs.
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root, regardless of where it's called from

ENV_FILE=".env"
COMPOSE_FILE="docker-compose.prod.yml"
SERVICE="notification-service"

# ---- checks ----
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found. Copy .env.example to .env first." >&2
  exit 1
fi

# ---- gather new values ----
NEW_TOKEN="${1:-}"
if [[ -z "$NEW_TOKEN" ]]; then
  read -rsp "Paste the new WhatsApp access token: " NEW_TOKEN
  echo
fi
if [[ -z "$NEW_TOKEN" ]]; then
  echo "ERROR: no token provided." >&2
  exit 1
fi
NEW_PHONE_ID="${2:-}"   # optional

# ---- back up .env (keep the previous token recoverable) ----
cp "$ENV_FILE" "${ENV_FILE}.bak"

# ---- rewrite the key(s) in place; add the line if missing ----
update_kv() {
  local key="$1" val="$2"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    # use a non-/ delimiter — tokens contain no '|' but may contain '/'
    sed -i "s|^${key}=.*|${key}=${val}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
  fi
}

update_kv "WHATSAPP_ACCESS_TOKEN" "$NEW_TOKEN"
[[ -n "$NEW_PHONE_ID" ]] && update_kv "WHATSAPP_PHONE_NUMBER_ID" "$NEW_PHONE_ID"

echo "Updated $ENV_FILE (previous saved as ${ENV_FILE}.bak)."

# ---- recreate only notification-service, picking up the new env ----
# --no-deps leaves postgres/kafka/other services untouched; no rebuild needed
# because the token is injected at container start, not baked into the image.
docker compose -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE"

echo
echo "Recreated $SERVICE with the new token. Confirm which provider is active:"
sleep 3
if docker compose -f "$COMPOSE_FILE" logs --tail 40 "$SERVICE" 2>&1 | grep -qi "MOCK WhatsApp"; then
  echo "  -> notification-service is in MOCK mode (token empty or not picked up)."
else
  echo "  -> live token loaded. It is exercised on the next ACK / Call Ambulance."
fi
echo "Health:"
docker compose -f "$COMPOSE_FILE" ps "$SERVICE"
