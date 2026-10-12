#!/usr/bin/env bash
# Decodes the release signing key from secrets and writes the keystore properties file the build expects.
#
# local.properties -> keystore.file is the path of a *properties* file (not of the .jks), with four keys:
#   keystore.file, keystore.password, key.alias, key.password
# Without it the build quietly changes: release comes out unsigned and without R8. So this fails fast when
# a secret is missing or wrong, before the 1-2 h build starts.
#
# Env (secrets): XGRAM_KEYSTORE_BASE64, XGRAM_KEYSTORE_PASSWORD, XGRAM_KEY_ALIAS, XGRAM_KEY_PASSWORD
#   Make the base64 text in PowerShell:
#     [Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\to\xgram-release.jks')) | Set-Clipboard
# Env (optional): XGRAM_SIGNING_DIR (default $RUNNER_TEMP/xgram-signing)
# Exports: XGRAM_KEYSTORE_PROPERTIES (path of the properties file) for later steps.
# Nothing secret is printed; cleanup-secrets.sh deletes both files at the end of the job.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_env XGRAM_KEYSTORE_BASE64 XGRAM_KEYSTORE_PASSWORD XGRAM_KEY_ALIAS XGRAM_KEY_PASSWORD
mask "$XGRAM_KEYSTORE_PASSWORD"
mask "$XGRAM_KEY_PASSWORD"
# keytool reads the passwords from these variables (-storepass:env / -keypass:env), never from argv.
export XGRAM_KEYSTORE_PASSWORD XGRAM_KEY_PASSWORD

base=${RUNNER_TEMP:-${TMPDIR:-/tmp}}
dir=${XGRAM_SIGNING_DIR:-$base/xgram-signing}
umask 077
mkdir -p "$dir"
jks="$dir/xgram-release.jks"
props="$dir/keystore.properties"

if ! printf '%s' "$XGRAM_KEYSTORE_BASE64" | tr -d ' \t\r\n' | base64 -d > "$jks" 2> /dev/null; then
  rm -f -- "$jks"
  die "XGRAM_KEYSTORE_BASE64 is not valid base64."
fi
[[ -s "$jks" ]] || die "XGRAM_KEYSTORE_BASE64 decodes to an empty file."

checker="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/CheckSigningKey.java"
if command -v java > /dev/null 2>&1 && [[ -f "$checker" ]]; then
  # Loads the key the way the build will, so all three secrets are checked. keytool can't do that for PKCS12 (the
  # type CI.md creates): it ignores -keypass there, so a wrong XGRAM_KEY_PASSWORD would only fail at the signing
  # step after the whole build. The checker reads the secrets from the environment; its messages stay visible.
  if ! java "$checker" "$jks"; then
    die "The signing secrets don't match the keystore (see the message above)."
  fi
elif command -v keytool > /dev/null 2>&1; then
  warn "java not found: only XGRAM_KEYSTORE_PASSWORD and XGRAM_KEY_ALIAS are checked, not XGRAM_KEY_PASSWORD."
  if ! keytool -list -keystore "$jks" -storepass:env XGRAM_KEYSTORE_PASSWORD -alias "$XGRAM_KEY_ALIAS" > /dev/null; then
    die "keytool cannot open the keystore with XGRAM_KEYSTORE_PASSWORD, or it has no key named by XGRAM_KEY_ALIAS."
  fi
else
  warn "java and keytool not found: the keystore and passwords are not checked before the build."
fi
if command -v keytool > /dev/null 2>&1; then
  # The certificate fingerprint is public (it is in every APK); handy for users who verify downloads.
  fingerprint=$(keytool -list -v -keystore "$jks" -storepass:env XGRAM_KEYSTORE_PASSWORD -alias "$XGRAM_KEY_ALIAS" |
    awk -F': ' '/SHA256:/ { print $2; exit }' || true)
  if [[ -n "$fingerprint" ]]; then
    log "Signing certificate SHA-256: $fingerprint"
    gh_output cert_sha256 "$fingerprint"
  fi
fi

# Java properties escaping: backslash -> \\ , and a leading space must be escaped or it is dropped.
# sed, not ${var//\\/\\\\}: bash 5.2's patsub_replacement changes how backslashes in the replacement behave.
# shellcheck disable=SC2001
prop_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/^ /\\ /'
}

{
  printf '# Written by scripts/ci/write-keystore.sh. Deleted at the end of the job.\n'
  printf 'keystore.file=%s\n' "$(prop_escape "$jks")"
  printf 'keystore.password=%s\n' "$(prop_escape "$XGRAM_KEYSTORE_PASSWORD")"
  printf 'key.alias=%s\n' "$(prop_escape "$XGRAM_KEY_ALIAS")"
  printf 'key.password=%s\n' "$(prop_escape "$XGRAM_KEY_PASSWORD")"
} > "$props"
chmod 600 "$jks" "$props"

gh_env XGRAM_KEYSTORE_PROPERTIES "$props"
gh_env XGRAM_SIGNING_DIR "$dir"
log "Signing key ready (keystore $(wc -c < "$jks" | tr -d ' ') bytes)."
