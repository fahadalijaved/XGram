#!/usr/bin/env bash
# Frees disk space on a GitHub-hosted Ubuntu runner before the XGram build.
#
# A full XGram build needs roughly 20-30 GB (submodules ~7.3 GB, SDK/NDK ~3 GB, Gradle and ccache caches,
# app/.cxx and app/build), while GitHub only promises 14 GB free. This removes large preinstalled toolchains
# XGram never uses. It keeps the Android SDK, but drops the preinstalled NDKs other than the ones in
# version.properties.
#
# CI only: it refuses to run outside GitHub Actions because it deletes system folders with sudo.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if [[ "$(uname -s)" != Linux* ]]; then
  log "Not a Linux runner: nothing to do."
  exit 0
fi
[[ -n "${GITHUB_ACTIONS:-}" ]] || die "free-disk-space.sh deletes system folders and only runs on GitHub Actions runners."

log "Disk before:"
df -h / >&2 || true

remove=(
  /usr/share/dotnet
  /usr/local/.ghcup
  /opt/ghc
  /opt/hostedtoolcache/CodeQL
  /usr/local/share/boost
  /usr/share/swift
  /usr/local/share/powershell
  /usr/local/share/chromium
  /opt/microsoft
  /opt/az
)
for dir in "${remove[@]}"; do
  if [[ -e "$dir" ]]; then
    log "Removing $dir"
    sudo rm -rf -- "$dir" || warn "Could not remove $dir"
  fi
done

# Preinstalled NDKs that XGram doesn't use (each is 1-3 GB).
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -n "$sdk" && -d "$sdk/ndk" ]]; then
  keep_primary=$(prop "$XGRAM_ROOT/version.properties" version.ndk_primary)
  keep_legacy=$(prop "$XGRAM_ROOT/version.properties" version.ndk_legacy)
  for ndk in "$sdk"/ndk/*; do
    [[ -d "$ndk" ]] || continue
    name=${ndk##*/}
    if [[ "$name" != "$keep_primary" && "$name" != "$keep_legacy" ]]; then
      log "Removing unused NDK $name"
      rm -rf -- "$ndk" 2> /dev/null || sudo rm -rf -- "$ndk" || warn "Could not remove $ndk"
    fi
  done
  # Some images link ndk-bundle to a default NDK; drop the link if it now points nowhere.
  if [[ -L "$sdk/ndk-bundle" && ! -e "$sdk/ndk-bundle" ]]; then
    rm -f -- "$sdk/ndk-bundle" 2> /dev/null || sudo rm -f -- "$sdk/ndk-bundle" || true
  fi
fi

if command -v docker > /dev/null 2>&1; then
  log "Removing Docker images"
  sudo docker image prune --all --force > /dev/null 2>&1 || true
fi

log "Disk after:"
df -h / >&2 || true

# Private-repo runners have 2 CPUs and 7 GB RAM. A release build runs R8, aapt2 and the native compiler at the same
# time, which ran out of memory and got the runner killed. Extra swap lets it finish (slower, but it finishes).
swap_gb=${XGRAM_SWAP_GB:-8}
if ((swap_gb > 0)) && [[ ! -e /xgram-swapfile ]]; then
  log "Adding ${swap_gb} GB of swap"
  if sudo fallocate -l "${swap_gb}G" /xgram-swapfile && sudo chmod 600 /xgram-swapfile &&
    sudo mkswap /xgram-swapfile > /dev/null && sudo swapon /xgram-swapfile; then
    free -h >&2 || true
  else
    warn "Could not add swap; the build may run out of memory."
  fi
fi
