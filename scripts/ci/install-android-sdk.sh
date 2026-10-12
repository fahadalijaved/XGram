#!/usr/bin/env bash
# Installs the Android SDK packages XGram needs, at the exact versions in version.properties:
#   platforms;<version.sdk_package>  build-tools;<version.build_tools>  ndk;<version.ndk_primary>  cmake;<version.cmake>
# using cmdline-tools <version.cmdline_tools> (downloaded once into $ANDROID_HOME/cmdline-tools/xgram-<version>).
#
# Use this instead of scripts/setup.sh in CI: setup.sh asks questions, needs a terminal for tput, and runs
# `sdkmanager --update`, which would update every package already on the runner.
#
# Env: ANDROID_HOME (or ANDROID_SDK_ROOT), set on GitHub's Ubuntu runners.
#      XGRAM_INSTALL_LEGACY_NDK=true also installs ndk;<version.ndk_legacy> (only the legacy flavor needs it).
# Exports for later steps: ANDROID_NDK_HOME, ANDROID_NDK_ROOT, ANDROID_NDK (all = the primary NDK).
# Outputs: ndk=<primary NDK version>, sdk=<SDK path>.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n "$sdk" ]] || die "ANDROID_HOME is not set."
[[ "$sdk" != *[[:space:]]* ]] || die "The SDK path must not contain spaces: $sdk"
mkdir -p "$sdk"

vp="$XGRAM_ROOT/version.properties"
cmdline=$(prop "$vp" version.cmdline_tools)
platform=$(prop "$vp" version.sdk_package)
build_tools=$(prop "$vp" version.build_tools)
cmake=$(prop "$vp" version.cmake)
ndk=$(prop "$vp" version.ndk_primary)
ndk_legacy=$(prop "$vp" version.ndk_legacy)

case "$(uname -s)" in
  Linux*) os=linux ;;
  Darwin*) os=mac ;;
  *) die "install-android-sdk.sh supports Linux and macOS runners only." ;;
esac

tools_dir="$sdk/cmdline-tools/xgram-$cmdline"
sdkmanager="$tools_dir/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
  url="https://dl.google.com/android/repository/commandlinetools-${os}-${cmdline}_latest.zip"
  log "Downloading cmdline-tools $cmdline"
  tmp=$(mktemp -d)
  retry 3 10 curl -fsSL -o "$tmp/cmdline-tools.zip" "$url" || die "Could not download $url"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  mkdir -p "$sdk/cmdline-tools"
  rm -rf -- "$tools_dir"
  mv "$tmp/cmdline-tools" "$tools_dir"
  rm -rf -- "$tmp"
fi

packages=("platforms;$platform" "build-tools;$build_tools" "ndk;$ndk" "cmake;$cmake")
if is_true "${XGRAM_INSTALL_LEGACY_NDK:-false}"; then
  packages+=("ndk;$ndk_legacy")
fi

# sdkmanager needs Java 17+. Gradle needs JDK 25 (already JAVA_HOME), but whether this cmdline-tools version
# runs on 25 is not verified, so prefer the LTS JDKs that GitHub's Ubuntu images preinstall.
sdk_java=${JAVA_HOME:-}
for candidate in "${JAVA_HOME_21_X64:-}" "${JAVA_HOME_17_X64:-}"; do
  if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
    sdk_java=$candidate
    break
  fi
done
log "sdkmanager runs on: ${sdk_java:-the default java}"

# `yes` is cut off by SIGPIPE once sdkmanager stops reading; `|| true` keeps pipefail from failing on that.
sdk_yes() {
  { yes 2> /dev/null || true; } | JAVA_HOME="$sdk_java" "$sdkmanager" --sdk_root="$sdk" "$@"
}

sdk_log=$(mktemp)
log "Accepting SDK licenses"
sdk_yes --licenses > "$sdk_log" 2>&1 || {
  tail -n 30 "$sdk_log" >&2
  die "sdkmanager --licenses failed."
}

log "Installing: ${packages[*]}"
if ! retry 3 20 sdk_yes --install "${packages[@]}" > "$sdk_log" 2>&1; then
  tail -n 50 "$sdk_log" >&2
  die "sdkmanager could not install ${packages[*]}"
fi
rm -f -- "$sdk_log"

for dir in "platforms/$platform" "build-tools/$build_tools" "ndk/$ndk" "cmake/$cmake"; do
  [[ -d "$sdk/$dir" ]] || die "Missing after install: $sdk/$dir"
done

# The runner's default ANDROID_NDK_HOME points to its own NDK (possibly removed by free-disk-space.sh).
gh_env ANDROID_NDK_HOME "$sdk/ndk/$ndk"
gh_env ANDROID_NDK_ROOT "$sdk/ndk/$ndk"
gh_env ANDROID_NDK "$sdk/ndk/$ndk"

gh_output ndk "$ndk"
gh_output sdk "$sdk"
log "Android SDK ready: platform $platform, build-tools $build_tools, NDK $ndk, CMake $cmake."
