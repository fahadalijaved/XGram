#!/usr/bin/env bash
# Writes local.properties for a CI build (the file is gitignored; nothing tracked changes).
#
# Required env:
#   XGRAM_APP_VERSION          base version number N (see version.sh); versionCode = N*1000 + 502 (arm64) / 501 (arm32)
#   XGRAM_KEYSTORE_PROPERTIES  path written by write-keystore.sh
#   ANDROID_HOME               Android SDK (no spaces)
# Optional env:
#   XGRAM_APP_ID       default com.fahadali.xgram (an org.example.* id would make an experimental build)
#   XGRAM_APP_NAME     default XGram (also the APK file name prefix)
#   XGRAM_DOWNLOAD_URL app.download_url, e.g. https://t.me/<XGRAM_CHANNEL_USERNAME> ("Invite friends" link)
#   XGRAM_SOURCES_URL  app.sources_url (default: the origin remote, i.e. the GitHub repo)
#   XGRAM_APP_API_ID / XGRAM_APP_API_HASH  telegram.api_id / telegram.api_hash embedded in the app
#                      (both or neither; without them the build uses the TGX sample values and warns)
#
# Outside CI it refuses to overwrite an existing local.properties unless XGRAM_FORCE=true.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_env XGRAM_APP_VERSION XGRAM_KEYSTORE_PROPERTIES
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n "$sdk" ]] || die "ANDROID_HOME is not set."
[[ "$sdk" != *[[:space:]]* ]] || die "sdk.dir must not contain spaces: $sdk"
[[ -f "$XGRAM_KEYSTORE_PROPERTIES" ]] || die "Keystore properties not found: $XGRAM_KEYSTORE_PROPERTIES"

version=$XGRAM_APP_VERSION
[[ "$version" =~ ^[0-9]+$ ]] || die "XGRAM_APP_VERSION must be a number, got: $version"
version=$((10#$version))
if ((version < 1 || version > 2099999)); then
  die "XGRAM_APP_VERSION must be 1..2099999 (Android's versionCode limit), got: $version"
fi

app_id=${XGRAM_APP_ID:-com.fahadali.xgram}
app_name=${XGRAM_APP_NAME:-XGram}
[[ "$app_id" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]] || die "XGRAM_APP_ID is not a valid package name: $app_id"
if [[ "$app_id" =~ ^(com|org)\.example\. ]]; then
  die "XGRAM_APP_ID $app_id would make an experimental build (no notifications in release)."
fi

out="$XGRAM_ROOT/local.properties"
if [[ -f "$out" && -z "${GITHUB_ACTIONS:-}" ]] && ! is_true "${XGRAM_FORCE:-false}"; then
  die "local.properties already exists. This script is for CI; set XGRAM_FORCE=true to overwrite it."
fi

# Java properties escaping for values: backslash -> \\ ; a leading space must be escaped.
# sed, not ${var//\\/\\\\}: bash 5.2's patsub_replacement changes how backslashes in the replacement behave.
# shellcheck disable=SC2001
prop_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/^ /\\ /'
}

api_lines=()
api_id=${XGRAM_APP_API_ID:-}
api_hash=${XGRAM_APP_API_HASH:-}
if [[ -n "$api_id" || -n "$api_hash" ]]; then
  [[ -n "$api_id" && -n "$api_hash" ]] || die "Set both XGRAM_APP_API_ID and XGRAM_APP_API_HASH, or neither."
  [[ "$api_id" =~ ^[0-9]+$ ]] || die "XGRAM_APP_API_ID must be a number."
  [[ "$api_hash" =~ ^[0-9a-fA-F]{32}$ ]] || die "XGRAM_APP_API_HASH must be 32 hex characters."
  mask "$api_hash"
  api_lines+=("telegram.api_id=$api_id" "telegram.api_hash=$api_hash")
else
  log "No app API credentials: the build uses the TGX sample api_id/api_hash (Gradle prints a warning)."
fi

{
  printf '# Written by scripts/ci/write-local-properties.sh for a CI build. Not committed (gitignored).\n'
  printf 'sdk.dir=%s\n' "$(prop_escape "$sdk")"
  printf 'keystore.file=%s\n' "$(prop_escape "$XGRAM_KEYSTORE_PROPERTIES")"
  printf 'app.id=%s\n' "$app_id"
  printf 'app.name=%s\n' "$(prop_escape "$app_name")"
  printf 'app.version=%s\n' "$version"
  printf 'tgx.extension=none\n'
  if [[ -n "${XGRAM_DOWNLOAD_URL:-}" ]]; then
    printf 'app.download_url=%s\n' "$(prop_escape "$XGRAM_DOWNLOAD_URL")"
  fi
  if [[ -n "${XGRAM_SOURCES_URL:-}" ]]; then
    printf 'app.sources_url=%s\n' "$(prop_escape "$XGRAM_SOURCES_URL")"
  fi
  if ((${#api_lines[@]} > 0)); then
    printf '%s\n' "${api_lines[@]}"
  fi
} > "$out"
chmod 600 "$out"

if [[ -z "${XGRAM_DOWNLOAD_URL:-}" ]]; then
  warn "XGRAM_DOWNLOAD_URL is not set: the app's invite link stays at Telegram X's default (telegram.org/dlx)."
fi
log "local.properties written: app.id=$app_id, app.name=$app_name, app.version=$version"
