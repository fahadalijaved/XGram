#!/usr/bin/env bash
# Prints the base version number N (local.properties -> app.version) for a CI build.
#
#   N = version.app * 1000 + XGram build
#
# version.app is Telegram X's version (version.properties); the XGram build is the number of commits since Telegram X
# last changed it, counted along the first parent, so merging a new Telegram X version starts again at 0.
# app/build.gradle.kts computes the same N for local builds and shows it as 0.29.1.1817.9 (Telegram X 1817, build 9).
#
# versionCode = N*1000 + 302 for arm64 and N*1000 + 300 for universal (app/build.gradle.kts), so:
#  - every new commit on main gives a higher versionCode, in the nightly and in the release workflow alike, and a
#    local build of the same commit gets the same one (either installs over the other);
#  - re-running a build of the same commit gives the same number;
#  - a new Telegram X version always ends up higher than every build on the one before it.
# Never rewrite main's history (squash/force-push): the count could drop. At most 999 builds fit on one Telegram X
# version; Android's limit (versionCode <= 2100000000) holds up to version.app 2099.
#
# Needs the full commit history (actions/checkout with fetch-depth: 0; filter: blob:none is enough).
# Env (optional): XGRAM_APP_VERSION_OVERRIDE (use exactly this N).
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
max=2099999

if [[ -n "${XGRAM_APP_VERSION_OVERRIDE:-}" ]]; then
  n=$XGRAM_APP_VERSION_OVERRIDE
  [[ "$n" =~ ^[0-9]+$ ]] || die "App version override must be a number, got: $n"
  n=$((10#$n))
  if ((n < 1000000 || n > max)); then
    die "App version override must be 1000000..$max (version.app * 1000 + build), got: $n"
  fi
  log "Using app version override N=$n"
  printf '%s\n' "$n"
  exit 0
fi

if [[ "$(git rev-parse --is-shallow-repository)" == true ]]; then
  die "version.sh needs the full commit history (check out with fetch-depth: 0)."
fi

app=$(prop version.properties version.app)
[[ "$app" =~ ^[0-9]+$ ]] || die "version.app is not a number: $app"
app=$((10#$app))

bump=$(git log --first-parent -1 --format=%H -G'^version\.app=' -- version.properties)
[[ -n "$bump" ]] || die "No commit sets version.app in version.properties."
build=$(git rev-list --count --first-parent "$bump..HEAD")
((build <= 999)) || die "$build commits since Telegram X's last version bump, more than fit: merge Telegram X's latest version."

n=$((app * 1000 + build))
((n <= max)) || die "N=$n is above Android's limit ($max)."

log "Version: N=$n (Telegram X $app, XGram build $build since ${bump:0:8}) -> versionCode arm64 ${n}302, universal ${n}300"
printf '%s\n' "$n"
