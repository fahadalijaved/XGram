#!/usr/bin/env bash
# Installs ccache and points the CMake (externalNativeBuild) compilers at it.
#
# CMake 3.17+ reads CMAKE_<LANG>_COMPILER_LAUNCHER from the environment on its first configure, and a CI run
# always configures fresh. NOT verified yet: that the Android Gradle Plugin passes these variables through to
# CMake. Check `ccache --show-stats` on the second run; with no hits, the fallback is a CMake argument in
# app/build.gradle.kts. ffmpeg and libvpx pick their own compiler, so ccache doesn't cover them (the Gradle
# build cache does).
#
# Env (optional): XGRAM_CCACHE_MAXSIZE (default 2G per flavor/build type), XGRAM_FLAVOR, XGRAM_BUILD_TYPE.
# Outputs: dir=<CCACHE_DIR>, key_prefix=ccache-<os>-ndk<version>-<flavor (arm64|arm32)>-<build type>-
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if ! command -v ccache > /dev/null 2>&1; then
  if command -v apt-get > /dev/null 2>&1; then
    log "Installing ccache"
    sudo apt-get update -qq > /dev/null
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ccache > /dev/null
  else
    die "ccache is not installed and apt-get is not available."
  fi
fi

ndk=$(prop "$XGRAM_ROOT/version.properties" version.ndk_primary)
dir=${CCACHE_DIR:-$HOME/.ccache}
mkdir -p "$dir"

gh_env CCACHE_DIR "$dir"
gh_env CCACHE_BASEDIR "$XGRAM_ROOT"
gh_env CCACHE_MAXSIZE "${XGRAM_CCACHE_MAXSIZE:-2G}"
# The default check uses the compiler's mtime, which changes every time the NDK is reinstalled.
gh_env CCACHE_COMPILERCHECK "string:ndk-$ndk"
gh_env CMAKE_C_COMPILER_LAUNCHER ccache
gh_env CMAKE_CXX_COMPILER_LAUNCHER ccache

os=$(uname -s | tr '[:upper:]' '[:lower:]')
gh_output dir "$dir"
gh_output key_prefix "ccache-${os}-ndk${ndk}-${XGRAM_FLAVOR:-any}-${XGRAM_BUILD_TYPE:-any}-"
ccache --version | head -n 1 >&2 || true
