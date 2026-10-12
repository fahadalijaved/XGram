#!/usr/bin/env bash
# Puts the right app/google-services.json in place for a CI build and checks it.
#
# A non-experimental build applies the Google services plugin (fails if no client matches app.id), and
# release builds run validateApiTokens (package must be the XGram app id, and TGX's Firebase app id is
# rejected). The google-services.json committed from upstream is TGX's, so either commit XGram's file or
# supply it as the GOOGLE_SERVICES_JSON secret. Push notifications only work with a real Firebase file.
#
# Env (optional secret): GOOGLE_SERVICES_JSON = the file's JSON text, or the same as base64.
#   base64 in PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\google-services.json'))
# Env (optional): XGRAM_APP_ID (default com.fahadali.xgram)
# Exports: XGRAM_GOOGLE_SERVICES_FROM_SECRET=true when the file came from the secret (cleanup restores it).
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

target="$XGRAM_ROOT/app/google-services.json"
app_id=${XGRAM_APP_ID:-com.fahadali.xgram}

if [[ -n "${GOOGLE_SERVICES_JSON:-}" ]]; then
  [[ -n "${GITHUB_ACTIONS:-}" ]] || die "Refusing to overwrite app/google-services.json outside CI."
  tmp="$target.ci-tmp"
  content=$GOOGLE_SERVICES_JSON
  trimmed=${content#"${content%%[![:space:]]*}"}
  umask 077
  if [[ "$trimmed" == "{"* ]]; then
    printf '%s\n' "$content" > "$tmp"
  elif ! printf '%s' "$content" | tr -d ' \t\r\n' | base64 -d > "$tmp" 2> /dev/null; then
    rm -f -- "$tmp"
    die "GOOGLE_SERVICES_JSON is neither JSON nor valid base64."
  fi
  mv -f -- "$tmp" "$target"
  gh_env XGRAM_GOOGLE_SERVICES_FROM_SECRET true
  log "app/google-services.json written from the GOOGLE_SERVICES_JSON secret."
else
  log "GOOGLE_SERVICES_JSON is not set: using the committed app/google-services.json."
fi

[[ -f "$target" ]] || die "app/google-services.json is missing. Commit XGram's file or add the GOOGLE_SERVICES_JSON secret."

py=$(python_cmd)
"$py" - "$target" "$app_id" << 'PY'
import json
import sys

path, app_id = sys.argv[1], sys.argv[2]
TGX_FIREBASE_APP_ID = "1:1037154859800:android:683d617a5fe76437"
try:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
except ValueError as e:
    sys.exit("app/google-services.json is not valid JSON: %s" % e)

found = []
ok = False
for client in data.get("client", []):
    info = client.get("client_info", {})
    package = info.get("android_client_info", {}).get("package_name", "")
    found.append(package)
    if package == app_id and info.get("mobilesdk_app_id") != TGX_FIREBASE_APP_ID:
        ok = True

if not ok:
    sys.exit(
        "app/google-services.json has no Firebase client for %s (found: %s). The build would fail. "
        "Commit XGram's file or set the GOOGLE_SERVICES_JSON secret." % (app_id, ", ".join(found) or "none")
    )

project = str(data.get("project_info", {}).get("project_id", ""))
if "placeholder" in project.lower():
    print("::warning::app/google-services.json is a placeholder (project '%s'): the build works, "
          "but push notifications will not." % project, file=sys.stderr)
print("google-services.json OK for %s." % app_id, file=sys.stderr)
PY
