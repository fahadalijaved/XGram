#!/usr/bin/env bash
# Prints the base version number N (local.properties -> app.version) for a CI build.
#
#   N = max(version.app, XGRAM_VERSION_BASE + number of commits reachable from HEAD)
#
# versionCode = N*1000 + 302 for arm64 and N*1000 + 300 for universal (app/build.gradle.kts), so:
#  - every new commit on main gives a higher versionCode, in the nightly and in the release workflow alike,
#    and a release built from the same commit as a nightly gets the same versionCode (installs over it);
#  - re-running a build of the same commit gives the same number;
#  - XGRAM_VERSION_BASE (repo variable, default 3000) keeps CI builds above the local 1817 builds.
# Never rewrite main's history (squash/force-push): the commit count could drop. If it ever does, raise
# XGRAM_VERSION_BASE. Android's limit is versionCode <= 2100000000, i.e. N <= 2099999.
#
# Needs the full commit history (actions/checkout with fetch-depth: 0; filter: tree:0 is enough).
# Env (optional): XGRAM_VERSION_BASE, XGRAM_APP_VERSION_OVERRIDE (use exactly this N).
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
max=2099999

if [[ -n "${XGRAM_APP_VERSION_OVERRIDE:-}" ]]; then
  n=$XGRAM_APP_VERSION_OVERRIDE
  [[ "$n" =~ ^[0-9]+$ ]] || die "App version override must be a number, got: $n"
  n=$((10#$n))
  if ((n < 1 || n > max)); then
    die "App version override must be 1..$max, got: $n"
  fi
  log "Using app version override N=$n"
  printf '%s\n' "$n"
  exit 0
fi

base=${XGRAM_VERSION_BASE:-3000}
[[ "$base" =~ ^[0-9]+$ ]] || die "XGRAM_VERSION_BASE must be a number, got: $base"
if [[ "$(git rev-parse --is-shallow-repository)" == true ]]; then
  die "version.sh needs the full commit history (check out with fetch-depth: 0)."
fi

count=$(git rev-list --count HEAD)
app=$(prop version.properties version.app)
[[ "$app" =~ ^[0-9]+$ ]] || die "version.app is not a number: $app"

n=$((10#$base + count))
app=$((10#$app))
if ((n < app)); then
  n=$app
fi
((n <= max)) || die "N=$n is above Android's limit ($max). Lower XGRAM_VERSION_BASE."

log "Version: N=$n (base $base + $count commits; version.app=$app) -> versionCode arm64 ${n}302, universal ${n}300"
printf '%s\n' "$n"
