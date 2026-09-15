#!/bin/bash
# Provisions Quota on a TRMNL e-ink display, end to end, with the TRMNL Account API.
#
#   bash trmnl/setup.sh --dry-run        # print every request, send none
#   bash trmnl/setup.sh                  # do it
#   bash trmnl/setup.sh --device 1234    # when the account has several devices
#
# It creates (or reuses) a private plugin named "Quota" with the webhook strategy, uploads
# trmnl/quota.liquid as its full-size markup, puts it on the device playlist, registers the
# resulting webhook URL with the Quota relay, and renders a preview.
#
# Credentials, never printed and never passed as process arguments:
#   TRMNL Account API key  $TRMNL_API_KEY or ~/.quota/trmnl-api-key  (Developer licence,
#                          trmnl.com -> Account -> API key, starts with user_)
#   Quota device token     ~/.quota/config.json, written by the collector
#
# Dependencies: bash, curl, python3. Nothing else.
#
# Every request below was checked against a live account on 2026-09-15, except the final
# write sequence, which was only dry-run from this repo. What that check settled:
#   - "Private Plugin" is plugin_id 37 (override with --plugin-id).
#   - The markup linter rejects a hand-written `view view--full` wrapper.
#   - Screenshots take no device_model: og and og_png are refused. Jobs expire in seconds.
#   - No endpoint exposes the plugin UUID — not the list, not /details, not /archive — and
#     POST playlist_items wants the UUID, not the integer id. So the one manual step left is
#     the human pasting the webhook URL once; --webhook-url takes it, ~/.quota/trmnl-webhook
#     remembers it. Everything else is automatic.
# Run --dry-run first; it prints the whole sequence and sends nothing.
set -euo pipefail

API="${TRMNL_API_BASE:-https://trmnl.com}"
QUOTA_API="${QUOTA_WORKER_URL:-https://quota.sceneview.dev}"
STATE_DIR="$HOME/.quota"
PLUGIN_NAME="Quota"
PLUGIN_ID=37            # INFERRED: "Private Plugin" type id, hardcoded by trmnlp too.
DEVICE_ID=""
MARKUP=""
WEBHOOK_URL=""
DRY_RUN=0
SIZE="markup_full"
WEBHOOK_FILE="$STATE_DIR/trmnl-webhook"

usage() {
  sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --device) DEVICE_ID="${2:?--device needs an id}"; shift 2 ;;
    --plugin-id) PLUGIN_ID="${2:?--plugin-id needs a number}"; shift 2 ;;
    --name) PLUGIN_NAME="${2:?--name needs a value}"; shift 2 ;;
    --markup) MARKUP="${2:?--markup needs a path or URL}"; shift 2 ;;
    --webhook-url) WEBHOOK_URL="${2:?--webhook-url needs a URL}"; shift 2 ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown option: $1" >&2; usage 1 >&2 ;;
  esac
done

say() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }
command -v curl >/dev/null || die "curl is required."
command -v python3 >/dev/null || die "python3 is required."

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --------------------------------------------------------------------------- credentials

read_key() {
  if [[ -n "${TRMNL_API_KEY:-}" ]]; then printf '%s' "$TRMNL_API_KEY"; return; fi
  [[ -r "$STATE_DIR/trmnl-api-key" ]] || die "No TRMNL API key. Put it in $STATE_DIR/trmnl-api-key (chmod 600) or set TRMNL_API_KEY."
  tr -d ' \t\r\n' < "$STATE_DIR/trmnl-api-key"
}
KEY="$(read_key)"
[[ -n "$KEY" ]] || die "The TRMNL API key is empty."
[[ "$KEY" == user_* ]] || say "warning: the key does not start with user_ — Account API keys do."

quota_token() {
  [[ -r "$STATE_DIR/config.json" ]] || return 1
  python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("token",""))' "$STATE_DIR/config.json"
}
TOKEN="$(quota_token || true)"

# --------------------------------------------------------------------------- HTTP

# Headers go through curl's stdin config, so the API key never appears in `ps` output.
curl_headers() {
  printf 'header = "Authorization: Bearer %s"\n' "$1"
  printf 'header = "Accept: application/json"\n'
}

STATUS=""
BODY=""

# api <METHOD> <path> [json-body] — sets STATUS and BODY. In --dry-run nothing is sent and
# BODY comes from dry_stub, so the whole sequence still prints in order.
api() {
  local method="$1" path="$2" body="${3:-}" out="$WORK/out.json"
  if (( DRY_RUN )); then
    say "  → $method $API$path"
    # Bodies are printed abridged: the markup alone is 9 kB of Liquid.
    if [[ -n "$body" ]]; then
      if (( ${#body} > 180 )); then say "      ${body:0:180}… (${#body} bytes)"; else say "      $body"; fi
    fi
    STATUS=200
    BODY="$(dry_stub "$method" "$path")"
    return 0
  fi
  local args=(-sS -K - -X "$method" --proto '=https' --tlsv1.2 --max-time 45 -o "$out" -w '%{http_code}')
  if [[ -n "$body" ]]; then
    printf '%s' "$body" > "$WORK/body.json"
    args+=(-H 'Content-Type: application/json' --data-binary "@$WORK/body.json")
  fi
  STATUS="$(curl_headers "$KEY" | curl "${args[@]}" "$API$path")"
  BODY="$(cat "$out")"
}

expect_ok() {
  case "$STATUS" in
    2*) ;;
    401|403) die "TRMNL refused the API key (HTTP $STATUS). A Developer licence is required for the Account API." ;;
    *) die "$1 failed (HTTP $STATUS): $BODY" ;;
  esac
}

# Fake answers so --dry-run exercises the real control flow instead of a printed wish list.
dry_stub() {
  case "$1 $2" in
    "GET /api/me") printf '{"data":{"name":"Dry Run","email":"you@example.com"}}' ;;
    "GET /api/devices") printf '{"data":[{"id":1234,"name":"TRMNL (dry run)","friendly_id":"ABC123"}]}' ;;
    "GET /api/plugin_settings") printf '{"data":[]}' ;;
    "POST /api/plugin_settings") printf '{"data":{"id":9999,"name":"%s"}}' "$PLUGIN_NAME" ;;
    *"/playlist_items") printf '{"data":[]}' ;;
    *"/screenshots") printf '{"data":{"job_id":"dry-run-job","status":"complete","preview_base64":""}}' ;;
    *) printf '{"data":{"success":true}}' ;;
  esac
}

# jsonpy <expression on d> — d is the parsed BODY; prints nothing when the path is absent.
jsonpy() { printf '%s' "$BODY" | python3 -c "$1"; }

# --------------------------------------------------------------------------- markup

# Sets MARKUP_FILE. Order: --markup, the copy next to this script, ~/.quota, the public copy.
MARKUP_FILE=""
load_markup() {
  local here candidate="$MARKUP"
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ -z "$candidate" && -r "$here/quota.liquid" ]]; then candidate="$here/quota.liquid"; fi
  if [[ -z "$candidate" && -r "$STATE_DIR/quota.liquid" ]]; then candidate="$STATE_DIR/quota.liquid"; fi
  if [[ -z "$candidate" ]]; then candidate="$QUOTA_API/downloads/quota.liquid"; fi
  say "   Markup: $candidate"
  if [[ "$candidate" == http*://* ]]; then
    curl -fsSL --proto '=https' --tlsv1.2 -o "$WORK/quota.liquid" "$candidate" || die "Could not download the markup from $candidate"
    MARKUP_FILE="$WORK/quota.liquid"
  else
    [[ -r "$candidate" ]] || die "Markup not found: $candidate"
    MARKUP_FILE="$candidate"
  fi
}

# --------------------------------------------------------------------------- run

if (( DRY_RUN )); then say "DRY RUN — every request below is printed, none is sent."; fi
say ""

say "1. Checking the API key"
api GET /api/me
expect_ok "GET /api/me"
ACCOUNT="$(jsonpy 'import json,sys;d=json.load(sys.stdin);u=d.get("data",d);print(u.get("email") or u.get("name") or "")')"
say "   Account: ${ACCOUNT:-unknown}"

say "2. Finding the device"
api GET /api/devices
expect_ok "GET /api/devices"
if [[ -z "$DEVICE_ID" ]]; then
  COUNT="$(jsonpy 'import json,sys;print(len(json.load(sys.stdin).get("data",[])))')"
  if [[ "$COUNT" == "0" ]]; then
    die "No TRMNL device on this account. Claim your device at trmnl.com first."
  elif [[ "$COUNT" == "1" ]]; then
    DEVICE_ID="$(jsonpy 'import json,sys;print(json.load(sys.stdin)["data"][0]["id"])')"
  else
    say "   Several devices — re-run with --device <id>:"
    jsonpy 'import json,sys
for dev in json.load(sys.stdin).get("data", []):
    print("     %s  %s" % (dev.get("id"), dev.get("name") or dev.get("friendly_id") or ""))'
    exit 1
  fi
fi
say "   Device: $DEVICE_ID"

say "3. Creating or reusing the private plugin \"$PLUGIN_NAME\""
api GET /api/plugin_settings
expect_ok "GET /api/plugin_settings"
PLUGIN_SETTING_ID="$(NAME="$PLUGIN_NAME" jsonpy 'import json,os,sys
want = os.environ["NAME"]
for p in json.load(sys.stdin).get("data", []):
    if p.get("name") == want:
        print(p.get("id")); break')"
if [[ -n "$PLUGIN_SETTING_ID" ]]; then
  say "   Reusing plugin $PLUGIN_SETTING_ID"
else
  api POST /api/plugin_settings "$(NAME="$PLUGIN_NAME" PID="$PLUGIN_ID" python3 -c 'import json,os;print(json.dumps({"name":os.environ["NAME"],"plugin_id":int(os.environ["PID"])}))')"
  expect_ok "POST /api/plugin_settings"
  PLUGIN_SETTING_ID="$(jsonpy 'import json,sys;d=json.load(sys.stdin);print(d.get("data",d).get("id",""))')"
  [[ -n "$PLUGIN_SETTING_ID" ]] || die "TRMNL did not return a plugin id: $BODY"
  say "   Created plugin $PLUGIN_SETTING_ID"
fi

say "4. Setting the webhook strategy"
# Every value in `fields` must be a string, or TRMNL answers 422.
api PATCH "/api/plugin_settings/$PLUGIN_SETTING_ID/settings" \
  "$(NAME="$PLUGIN_NAME" python3 -c 'import json,os;print(json.dumps({"fields":{"name":os.environ["NAME"],"strategy":"webhook","dark_mode":"no","no_screen_padding":"no"}}))')"
expect_ok "PATCH .../settings"

say "5. Uploading the full-size markup"
load_markup
api PUT "/api/plugin_settings/$PLUGIN_SETTING_ID/markup/$SIZE" \
  "$(python3 -c 'import json,sys;print(json.dumps({"content":open(sys.argv[1],encoding="utf-8").read()}))' "$MARKUP_FILE")"
expect_ok "PUT .../markup/$SIZE"

say "6. The webhook URL"
if [[ -z "$WEBHOOK_URL" && -r "$WEBHOOK_FILE" ]]; then
  WEBHOOK_URL="$(tr -d ' \t\r\n' < "$WEBHOOK_FILE")"
  if [[ -n "$WEBHOOK_URL" ]]; then say "   Reusing the one saved in $WEBHOOK_FILE."; fi
fi
if [[ -z "$WEBHOOK_URL" ]] && (( DRY_RUN )); then
  WEBHOOK_URL="$API/api/custom_plugins/00000000-0000-0000-0000-000000000000"
  say "   A real run stops here and asks you to paste the webhook URL; carrying on with a placeholder."
fi
if [[ -z "$WEBHOOK_URL" ]]; then
  # The UUID that forms the webhook URL is not exposed by any Account API endpoint (checked
  # 2026-09-15: neither the list, nor /details, nor /archive returns it), and the playlist
  # endpoint needs it. This is the single step a human still has to do by hand.
  say ""
  say "   TRMNL does not expose the plugin's UUID over the API, so copy it once:"
  say "     1. Open $API/plugin_settings/$PLUGIN_SETTING_ID/edit"
  say "     2. Copy the Webhook URL ($API/api/custom_plugins/<uuid>)"
  say "     3. Re-run:  bash \"$0\" --device $DEVICE_ID --webhook-url <the URL>"
  say ""
  say "   Treat that URL as a credential: anyone holding it can write to your display."
  exit 2
fi
PLUGIN_UUID="$(printf '%s' "$WEBHOOK_URL" | sed -n 's#.*/api/custom_plugins/\([A-Za-z0-9-]*\).*#\1#p')"
[[ -n "$PLUGIN_UUID" ]] || die "That does not look like a TRMNL webhook URL: expected $API/api/custom_plugins/<uuid>."
if (( ! DRY_RUN )); then
  umask 077
  printf '%s\n' "$WEBHOOK_URL" > "$WEBHOOK_FILE"
  chmod 600 "$WEBHOOK_FILE"
fi
say "   Have it (kept out of this log — it is a credential)."

say "7. Adding the plugin to the device playlist"
api GET "/api/devices/$DEVICE_ID/playlist_items"
if [[ "$STATUS" == 2* ]] && PID="$PLUGIN_SETTING_ID" UUID="$PLUGIN_UUID" jsonpy 'import json,os,sys
want = {str(os.environ["PID"]), str(os.environ["UUID"])}
items = json.load(sys.stdin).get("data", [])
def ident(item):
    setting = item.get("plugin_setting") or {}
    return {str(item.get("plugin_setting_id")), str(setting.get("id")), str(setting.get("uuid"))}
sys.exit(0 if any(ident(i) & want for i in items) else 1)'; then
  say "   Already on the playlist."
else
  # The integer id answers "Plugin setting not found" here: this endpoint wants the UUID.
  api POST "/api/devices/$DEVICE_ID/playlist_items" \
    "$(PID="$PLUGIN_UUID" python3 -c 'import json,os;print(json.dumps({"plugin_setting_id":os.environ["PID"]}))')"
  expect_ok "POST .../playlist_items"
  say "   Added."
fi

say "8. Registering the webhook with the Quota relay"
if [[ -z "$TOKEN" ]]; then
  say "   No device token in $STATE_DIR/config.json — install the collector first, then re-run."
  say "   Until then, paste the webhook URL into the dashboard's \"Connect TRMNL\" panel."
elif (( DRY_RUN )); then
  say "  → PUT $QUOTA_API/v1/settings"
  say '      {"trmnl_webhook_url":"https://trmnl.com/api/custom_plugins/…"}'
else
  printf '%s' "$(URL="$WEBHOOK_URL" python3 -c 'import json,os;print(json.dumps({"trmnl_webhook_url":os.environ["URL"]}))')" > "$WORK/settings.json"
  CODE="$( { printf 'header = "Authorization: Bearer %s"\n' "$TOKEN"; } | curl -sS -K - -X PUT \
    --proto '=https' --tlsv1.2 --max-time 30 -H 'Content-Type: application/json' \
    --data-binary "@$WORK/settings.json" -o "$WORK/settings-out.json" -w '%{http_code}' \
    "$QUOTA_API/v1/settings")"
  [[ "$CODE" == 2* ]] || die "The Quota relay refused the webhook (HTTP $CODE): $(cat "$WORK/settings-out.json")"
  say "   Registered. The relay pushes your limits every five minutes."
fi

say "9. Rendering a preview"
# device_model is left out on purpose: og and og_png are both refused (checked 2026-09-15).
api POST "/api/plugin_settings/$PLUGIN_SETTING_ID/screenshots" \
  "$(SZ="$SIZE" python3 -c 'import json,os;print(json.dumps({"size":os.environ["SZ"]}))')"
expect_ok "POST .../screenshots"
JOB="$(jsonpy 'import json,sys;d=json.load(sys.stdin);print(d.get("data",d).get("job_id",""))')"
PREVIEW="$STATE_DIR/trmnl-preview.png"
if (( DRY_RUN )); then
  say "  → GET $API/api/plugin_settings/$PLUGIN_SETTING_ID/screenshots/$JOB   (polled until 200)"
elif [[ -n "$JOB" ]]; then
  # The job expires within seconds, so poll tightly rather than politely.
  for _ in $(seq 1 15); do
    api GET "/api/plugin_settings/$PLUGIN_SETTING_ID/screenshots/$JOB"
    if [[ "$STATUS" == "200" ]]; then break; fi
    sleep 2
  done
  # The API returns the PNG inline, base64, not a URL: write it where the human can open it.
  if [[ "$STATUS" == "200" ]] && printf '%s' "$BODY" | python3 -c '
import base64,json,sys
d = json.load(sys.stdin).get("data", {})
raw = d.get("preview_base64") or ""
if not raw: sys.exit(1)
open(sys.argv[1], "wb").write(base64.b64decode(raw))' "$PREVIEW"; then
    chmod 600 "$PREVIEW"
    say "   Preview: file://$PREVIEW"
  else
    say "   The preview did not render in time. The plugin is set up; check it on trmnl.com."
  fi
fi

say ""
say "Done. Quota is on plugin $PLUGIN_SETTING_ID, device $DEVICE_ID."
say "The display redraws on its own schedule — usually within 15 minutes."
if (( DRY_RUN )); then say "Nothing was actually sent: this was a dry run."; fi
exit 0
