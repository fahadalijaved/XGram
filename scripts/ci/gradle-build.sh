#!/usr/bin/env bash
# Runs the Gradle build for one APK:  :app:assembleLatest<Arm64|Universal><Debug|Release>
#
# Env: XGRAM_FLAVOR (arm64|universal), XGRAM_BUILD_TYPE (debug|release)
#      XGRAM_GRADLE_ARGS (optional extra arguments, split on spaces)
# The native part (WebRTC, tgcalls, ffmpeg, libvpx, ...) is built by Gradle itself; expect 35-80 min for the
# CMake part on a cold cache (see docs/xgram/telegram/CI.md). Release builds also download translations
# from translations.telegram.org (FetchLocalizedStringsTask), so they need network access.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_env XGRAM_FLAVOR XGRAM_BUILD_TYPE
case "$XGRAM_FLAVOR" in
  arm64) flavor=Arm64 ;;
  universal) flavor=Universal ;;
  *) die "XGRAM_FLAVOR must be arm64 or universal, got: $XGRAM_FLAVOR" ;;
esac
case "$XGRAM_BUILD_TYPE" in
  debug) build_type=Debug ;;
  release) build_type=Release ;;
  *) die "XGRAM_BUILD_TYPE must be debug or release, got: $XGRAM_BUILD_TYPE" ;;
esac
task=":app:assembleLatest${flavor}${build_type}"

cd "$XGRAM_ROOT"
[[ -f local.properties ]] || die "local.properties is missing (run write-local-properties.sh first)."
[[ -x gradlew ]] || chmod +x gradlew

extra=()
if [[ -n "${XGRAM_GRADLE_ARGS:-}" ]]; then
  read -r -a extra <<< "$XGRAM_GRADLE_ARGS"
fi

if command -v ccache > /dev/null 2>&1; then
  ccache --zero-stats > /dev/null || true
fi

log "Running ./gradlew $task"
start=$(date +%s)
./gradlew "$task" --stacktrace --console=plain ${extra[@]+"${extra[@]}"}
end=$(date +%s)

minutes=$(((end - start) / 60))
log "Gradle finished in $minutes min."
df -h / >&2 || true
summary "Gradle \`$task\`: $minutes min"
