#!/usr/bin/env bash
# Writes DIR/SHA256SUMS.txt for every APK in DIR, checks it, and prints it.
#
#   checksums.sh DIR
#
# Users can verify a download with:  sha256sum -c SHA256SUMS.txt --ignore-missing   (Linux/macOS/Git Bash)
#   or in PowerShell:                 Get-FileHash .\XGram-<version>-arm64-v8a.apk -Algorithm SHA256
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

dir=${1:?usage: checksums.sh DIR}
[[ -d "$dir" ]] || die "Not a folder: $dir"

if command -v sha256sum > /dev/null 2>&1; then
  hasher=(sha256sum)
elif command -v shasum > /dev/null 2>&1; then
  hasher=(shasum -a 256)
else
  die "Neither sha256sum nor shasum is installed."
fi

(
  cd "$dir"
  shopt -s nullglob
  apks=(*.apk)
  ((${#apks[@]} > 0)) || die "No APK files in $dir"
  # Sorted lines of "<hash>  <file>", the format `sha256sum -c` reads.
  mapfile -t sorted < <(printf '%s\n' "${apks[@]}" | LC_ALL=C sort)
  "${hasher[@]}" -- "${sorted[@]}" > SHA256SUMS.txt
  "${hasher[@]}" -c SHA256SUMS.txt > /dev/null || die "SHA256SUMS.txt check failed."
  cat SHA256SUMS.txt >&2
)

summary "#### SHA-256" "" '```' "$(cat "$dir/SHA256SUMS.txt")" '```' ""
