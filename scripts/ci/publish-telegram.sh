#!/usr/bin/env bash
# Posts the APKs through XGram Build Bot (tools/xgram/telegram/xgram_bot.py): CI builds to the XGram CI channel
# (--to channel, the default), Stable releases to the Updates topic of the XGram group (--kind stable --to updates).
#
#   publish-telegram.sh --dir DIR --version V --commit SHA --notes FILE [--kind ci|stable] [--to channel|updates]
#                       [--announce] [--send]
#
# Runs:  xgram_bot.py publish --apk A [--apk B] --version V --commit SHA --notes FILE --to channel|updates
#                             [--kind ci|stable] [--announce updates] [--send]
# The APKs go out as one album: arm64 (arm64-v8a) first, then arm32 (armeabi-v7a). The bot sends it with Telethon
# when that is set up (always the case when an APK is over the cutoff), else with the Bot API's sendMediaGroup.
# --kind labels the posts "CI build" or "Stable" (the bot's default in the channel is ci).
# Without --send nothing is sent: the bot prints what it would send (its default dry run).
#
# Env (secrets / variables):
#   XGRAM_BOT_TOKEN                       required for every target
#   XGRAM_CHANNEL_ID                      required for --to channel (CI builds)
#   XGRAM_GROUP_ID, XGRAM_TOPIC_UPDATES   required for --to updates (Stable releases); for --announce they are
#                                         optional (without them the announcement is dropped)
#                                         If a required one is empty, publishing is skipped with a warning, except
#                                         for a Stable release with --send: that run fails, so it can't pass unposted.
#   TELEGRAM_API_ID, TELEGRAM_API_HASH    needed for APKs over 49 MB (49,000,000 bytes): the cloud Bot API takes
#                                         at most "50 MB", so the bot uploads bigger files with Telethon (logged
#                                         in as the bot), which this script installs (pinned) into a virtualenv.
#                                         Without them such a run fails before anything is posted.
#   XGRAM_BOT_API_URL                     optional self-hosted Bot API server (read by the bot); no Telethon then
#   XGRAM_TOPIC_SUPPORT, XGRAM_CI_CHANNEL_LINK   optional, read by the bot to fill {{SUPPORT_TOPIC_LINK}} and
#                                         {{CI_CHANNEL_LINK}} in hand-written .html notes
# Env (optional): XGRAM_TELETHON_VERSION (default 1.45.0), XGRAM_BOT_SCRIPT (path of the bot script),
#                 XGRAM_BOT_STATE_FILE (passed as --state-file, so the workflow can keep the publish record),
#                 XGRAM_CLOUD_UPLOAD_MB (default 49, whole MB; the bot reads the same variable)
# The bot token is passed only through the environment and is never printed.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

dir=""
version=""
commit=""
notes=""
kind=""
to=channel
announce=false
send=false
while (($# > 0)); do
  case "$1" in
    --dir) dir=${2:?}; shift 2 ;;
    --version) version=${2:?}; shift 2 ;;
    --commit) commit=${2:?}; shift 2 ;;
    --notes) notes=${2:?}; shift 2 ;;
    --kind) kind=${2:?}; shift 2 ;;
    --to) to=${2:?}; shift 2 ;;
    --announce) announce=true; shift ;;
    --send) send=true; shift ;;
    *) die "Unknown argument: $1" ;;
  esac
done
[[ -n "$dir" && -n "$version" && -n "$commit" && -n "$notes" ]] ||
  die "usage: publish-telegram.sh --dir DIR --version V --commit SHA --notes FILE [--kind ci|stable] [--to channel|updates] [--announce] [--send]"
[[ -z "$kind" || "$kind" == ci || "$kind" == stable ]] || die "--kind must be ci or stable, got: $kind"
[[ "$to" == channel || "$to" == updates ]] || die "--to must be channel or updates, got: $to"
# CI builds go to the XGram CI channel only; the Updates topic is for stable releases.
if [[ "$kind" != stable && ( "$to" != channel || "$announce" == true ) ]]; then
  die "CI builds are posted only to the XGram CI channel (no --to updates, no --announce)."
fi
[[ -d "$dir" ]] || die "Not a folder: $dir"
[[ -f "$notes" ]] || die "Notes file not found: $notes"

# Only what this target needs: the channel id for the CI channel, the group and topic ids for the Updates topic.
missing=""
if [[ -z "${XGRAM_BOT_TOKEN:-}" ]]; then
  missing="the XGRAM_BOT_TOKEN secret"
elif [[ "$to" == channel && -z "${XGRAM_CHANNEL_ID:-}" ]]; then
  missing="the XGRAM_CHANNEL_ID variable"
elif [[ "$to" == updates && (-z "${XGRAM_GROUP_ID:-}" || -z "${XGRAM_TOPIC_UPDATES:-}") ]]; then
  missing="the XGRAM_GROUP_ID and XGRAM_TOPIC_UPDATES variables"
fi
if [[ -n "$missing" ]]; then
  # A Stable release that posts nothing must not end green.
  if [[ "$send" == true && "$kind" == stable ]]; then
    die "Stable release not posted: add $missing (docs/xgram/telegram/CI.md)."
  fi
  warn "Telegram publishing skipped: add $missing (docs/xgram/telegram/CI.md)."
  exit 0
fi
mask "$XGRAM_BOT_TOKEN"

if [[ "$announce" == true && (-z "${XGRAM_GROUP_ID:-}" || -z "${XGRAM_TOPIC_UPDATES:-}") ]]; then
  warn "Announcement skipped: XGRAM_GROUP_ID and XGRAM_TOPIC_UPDATES are needed to post in the Updates topic."
  announce=false
fi

bot=${XGRAM_BOT_SCRIPT:-$XGRAM_ROOT/tools/xgram/telegram/xgram_bot.py}
[[ -f "$bot" ]] || die "Bot script not found: $bot (commit tools/xgram/telegram/ first)."

shopt -s nullglob
found=("$dir"/*.apk)
shopt -u nullglob
((${#found[@]} > 0)) || die "No APKs in $dir"

# Album order (one caption per file, labelled with its ABI by the bot): arm64-v8a for most phones first, then
# armeabi-v7a (arm32) for 32-bit ARM phones (Android 13+), then anything else.
apks=()
for rank in arm64-v8a armeabi-v7a other; do
  for apk in "${found[@]}"; do
    case "${apk##*/}" in
      *-arm64-v8a*) abi=arm64-v8a ;;
      *-armeabi-v7a*) abi=armeabi-v7a ;;
      *) abi=other ;;
    esac
    if [[ "$abi" == "$rank" ]]; then
      apks+=("$apk")
    fi
  done
done

py=$(python_cmd)

# Files over the cutoff need Telethon and the API credentials (unless a self-hosted Bot API server takes them).
# Same cutoff as the bot: XGRAM_CLOUD_UPLOAD_MB MB of 1,000,000 bytes, default 49, below "50 MB" in either unit.
limit_mb=${XGRAM_CLOUD_UPLOAD_MB:-49}
if [[ ! "$limit_mb" =~ ^[0-9]+$ ]]; then
  warn "XGRAM_CLOUD_UPLOAD_MB must be a whole number of MB here, got '$limit_mb': using 49."
  limit_mb=49
fi
limit=$((limit_mb * 1000 * 1000))
big=()
for apk in "${apks[@]}"; do
  bytes=$(wc -c < "$apk" | tr -d ' ')
  if ((bytes > limit)); then
    big+=("${apk##*/} ($((bytes / 1000000)) MB)")
  fi
done
if ((${#big[@]} > 0)) && [[ -z "${XGRAM_BOT_API_URL:-}" ]]; then
  if [[ -n "${TELEGRAM_API_ID:-}" && -n "${TELEGRAM_API_HASH:-}" ]]; then
    mask "$TELEGRAM_API_HASH"
    telethon=${XGRAM_TELETHON_VERSION:-1.45.0}
    venv="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/xgram-bot-venv"
    log "Over $limit_mb MB: ${big[*]}. Installing Telethon $telethon for the upload."
    if "$py" -m venv "$venv" > /dev/null 2>&1 && [[ -x "$venv/bin/python" ]]; then
      py="$venv/bin/python"
    else
      warn "Could not create a virtualenv; installing Telethon for the current user instead."
    fi
    retry 3 10 "$py" -m pip install --quiet --disable-pip-version-check "telethon==$telethon" ||
      die "pip could not install telethon==$telethon."
  elif [[ "$send" == true ]]; then
    # Stop before anything is posted: otherwise the smaller APKs would go out, the run would fail at the big one
    # with no notes and no saved state, and a re-run would post the smaller ones again.
    die "Over $limit_mb MB: ${big[*]}. The cloud Bot API can't take these, and uploading them needs the TELEGRAM_API_ID and TELEGRAM_API_HASH secrets (docs/xgram/telegram/CI.md). Nothing was posted."
  else
    warn "Over $limit_mb MB: ${big[*]}. A real run needs the TELEGRAM_API_ID and TELEGRAM_API_HASH secrets for these."
  fi
fi

args=(publish)
for apk in "${apks[@]}"; do
  args+=(--apk "$apk")
done
args+=(--version "$version" --commit "$commit" --notes "$notes" --to "$to")
if [[ -n "$kind" ]]; then
  args+=(--kind "$kind")
fi
if [[ "$announce" == true ]]; then
  args+=(--announce updates)
fi

sub_help=$("$py" "$bot" publish --help 2>&1 || true)
top_help=$("$py" "$bot" --help 2>&1 || true)

# The bot remembers the last publish (for /latest) in a state file; on a runner that file is lost after the
# job unless it is written somewhere the workflow uploads (XGRAM_BOT_STATE_FILE).
if [[ -n "${XGRAM_BOT_STATE_FILE:-}" ]]; then
  if [[ "$sub_help" == *--state-file* ]]; then
    args+=(--state-file "$XGRAM_BOT_STATE_FILE")
  else
    warn "This bot version has no --state-file option: XGRAM_BOT_STATE_FILE ignored."
  fi
fi

# The contract says every command defaults to a dry run and --send really sends. Put --send where this
# version of the bot's argument parser accepts it (after the subcommand, or before it).
before=()
after=()
if [[ "$send" == true ]]; then
  if [[ "$sub_help" == *--send* ]]; then
    after=(--send)
  elif [[ "$top_help" == *--send* ]]; then
    before=(--send)
  else
    die "The bot script has no --send option, so it can't publish. Check $bot."
  fi
else
  log "Dry run (no --send): the bot only prints what it would post."
fi

log "Publishing ${#apks[@]} APK(s), version $version, commit ${commit:0:7}, announce=$announce, send=$send"
"$py" "$bot" ${before[@]+"${before[@]}"} "${args[@]}" ${after[@]+"${after[@]}"}

case "$to" in
  channel) where="the CI channel" ;;
  *) where="the Updates topic" ;;
esac
verb=posted
if [[ "$send" != true ]]; then
  verb="dry run, would post"
fi
summary "Telegram: $verb ${#apks[@]} APK(s) to $where (kind ${kind:-default}, announce in Updates: $announce)."
