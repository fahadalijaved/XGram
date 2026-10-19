#!/usr/bin/env bash
# Collects APKs with their metadata and checksums.
#
#   collect-apks.sh --out DIR                 (build job) copy the APK(s) Gradle built for $XGRAM_FLAVOR /
#                                             $XGRAM_BUILD_TYPE into DIR, with build-info.json and SHA256SUMS.txt
#   collect-apks.sh --merge ARTIFACTS --out DIR
#                                             (publish job) merge the downloaded per-flavor artifacts
#                                             (ARTIFACTS/<artifact name>/...) into DIR; outputs version_name
#
# APK locations (app/build.gradle.kts):
#   arm64: app/build/outputs/apk/latestArm64/<type>/XGram-<versionName>-arm64-v8a[-debug].apk
#   arm32: app/build/outputs/apk/latestArm32/<type>/XGram-<versionName>-armeabi-v7a[-debug].apk
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

out=""
merge_from=""
while (($# > 0)); do
  case "$1" in
    --out) out=${2:?}; shift 2 ;;
    --merge) merge_from=${2:?}; shift 2 ;;
    *) die "Unknown argument: $1" ;;
  esac
done
[[ -n "$out" ]] || die "usage: collect-apks.sh [--merge ARTIFACTS] --out DIR"

py=$(python_cmd)
here="$XGRAM_ROOT/scripts/ci"

if [[ -n "$merge_from" ]]; then
  [[ -d "$merge_from" ]] || die "Not a folder: $merge_from"
  "$py" "$here/build_info.py" merge --in-dir "$merge_from" --out-dir "$out"
else
  require_env XGRAM_FLAVOR XGRAM_BUILD_TYPE
  case "$XGRAM_FLAVOR" in
    arm64) variant=latestArm64 ;;
    arm32) variant=latestArm32 ;;
    *) die "XGRAM_FLAVOR must be arm64 or arm32, got: $XGRAM_FLAVOR" ;;
  esac
  case "$XGRAM_BUILD_TYPE" in
    debug | release) ;;
    *) die "XGRAM_BUILD_TYPE must be debug or release, got: $XGRAM_BUILD_TYPE" ;;
  esac
  apk_dir="$XGRAM_ROOT/app/build/outputs/apk/$variant/$XGRAM_BUILD_TYPE"
  "$py" "$here/build_info.py" make --apk-dir "$apk_dir" --flavor "$XGRAM_FLAVOR" \
    --build-type "$XGRAM_BUILD_TYPE" --out-dir "$out"
fi

bash "$here/checksums.sh" "$out"

summary "#### APKs" "" "| File | Size |" "|---|---|"
for apk in "$out"/*.apk; do
  [[ -f "$apk" ]] || continue
  bytes=$(wc -c < "$apk" | tr -d ' ')
  summary "| \`${apk##*/}\` | $((bytes / 1000000)) MB |"
done
summary ""
