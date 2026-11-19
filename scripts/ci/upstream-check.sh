#!/usr/bin/env bash
# Weekly: what Telegram X (TGX-Android/Telegram-X) and its TDLib builds (TGX-Android/tdlib) have that XGram hasn't
# reviewed yet, from the markers in .github/upstream-sync.txt. If anything is new, the bot messages the owner.
# Nothing is merged: XGram takes upstream commits one by one (cherry-pick -x), and TDLib upgrades need code changes.
#
# Env: GH_TOKEN (gh api), XGRAM_BOT_TOKEN and XGRAM_OWNER_CHAT_ID (the message; without them it only logs).
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
markers=.github/upstream-sync.txt
[[ -f "$markers" ]] || die "$markers is missing."
tgx_mark=$(awk '$1 == "telegram-x" {print $2}' "$markers")
td_mark=$(awk '$1 == "tdlib" {print $2}' "$markers")
[[ -n "$tgx_mark" && -n "$td_mark" ]] || die "$markers needs a telegram-x and a tdlib line."

# Commits on upstream main after the marker: the count and the newest subjects
report() {
  local repo=$1 mark=$2 max=$3
  local json
  json=$(gh api "repos/$repo/compare/$mark...main") || { warn "Could not compare $repo from $mark."; return 1; }
  local count
  count=$(jq -r '.ahead_by' <<< "$json")
  if [[ "$count" == "0" ]]; then
    return 0
  fi
  printf '%s\n' "$count"
  jq -r --argjson max "$max" '.commits | reverse | .[:$max][] | "- " + (.commit.message | split("\n")[0]) + " (" + .sha[0:8] + ")"' <<< "$json"
}

tgx=$(report TGX-Android/Telegram-X "$tgx_mark" 15 || true)
td=$(report TGX-Android/tdlib "$td_mark" 5 || true)

if [[ -z "$tgx" && -z "$td" ]]; then
  log "Nothing new in Telegram X or TDLib since the last sync."
  exit 0
fi

text="XGram weekly upstream check"
if [[ -n "$tgx" ]]; then
  tgx_count=$(head -n 1 <<< "$tgx")
  text+=$'\n\n'"Telegram X: $tgx_count new commits since the last sync. Newest:"$'\n'"$(tail -n +2 <<< "$tgx")"
  if (( tgx_count > 15 )); then
    text+=$'\n'"…and $((tgx_count - 15)) more."
  fi
fi
if [[ -n "$td" ]]; then
  td_count=$(head -n 1 <<< "$td")
  text+=$'\n\n'"TDLib builds: $td_count new commits:"$'\n'"$(tail -n +2 <<< "$td")"
fi
text+=$'\n\n'"Nothing was merged. Ask Claude to \"sync upstream\" to review and take them in."
printf '%s\n' "$text"

if [[ -z "${XGRAM_BOT_TOKEN:-}" || -z "${XGRAM_OWNER_CHAT_ID:-}" ]]; then
  warn "XGRAM_BOT_TOKEN or XGRAM_OWNER_CHAT_ID is not set: not sending the message."
  exit 0
fi
# Telegram messages stop at 4096 characters
curl -fsS -o /dev/null "https://api.telegram.org/bot${XGRAM_BOT_TOKEN}/sendMessage" \
  --data-urlencode "chat_id=${XGRAM_OWNER_CHAT_ID}" \
  --data-urlencode "text=${text:0:4000}" \
  --data-urlencode "disable_web_page_preview=true" || die "The bot could not send the message."
log "Sent the report to the owner."
