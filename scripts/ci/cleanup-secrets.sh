#!/usr/bin/env bash
# Deletes the signing key, keystore properties and the generated local.properties at the end of a CI job,
# and restores the committed app/google-services.json if it was replaced from a secret.
# Runs with `if: always()`, so it must never fail the job. CI only: locally it does nothing.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if [[ -z "${GITHUB_ACTIONS:-}" ]]; then
  log "Not on GitHub Actions: leaving local files alone."
  exit 0
fi

dir=${XGRAM_SIGNING_DIR:-${RUNNER_TEMP:-/tmp}/xgram-signing}
rm -rf -- "$dir" || true
rm -f -- "$XGRAM_ROOT/local.properties" || true

if [[ "${XGRAM_GOOGLE_SERVICES_FROM_SECRET:-}" == true ]]; then
  git -C "$XGRAM_ROOT" checkout -- app/google-services.json 2> /dev/null ||
    rm -f -- "$XGRAM_ROOT/app/google-services.json" || true
fi

log "Signing material and generated config removed."
exit 0
