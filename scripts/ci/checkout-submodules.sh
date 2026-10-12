#!/usr/bin/env bash
# Fetches every submodule, recursively and shallow (--depth 1), with retries.
#
# The build reads files and git HEADs from nested submodules (tdlib/source/td, tdlib/source/openssl, webrtc,
# tgcalls, ffmpeg, webp), and validateGitSetup fails if any top-level submodule is missing, so all of them
# are needed. LFS files are skipped here (GIT_LFS_SKIP_SMUDGE=1) and fetched by fetch-lfs.sh, so they can be
# cached between runs.
#
# Env (optional): XGRAM_SUBMODULE_ATTEMPTS (default 4), XGRAM_SUBMODULE_JOBS (default 8).
# The last attempt drops --depth 1, in case a server refuses a shallow fetch of the pinned commit.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
attempts=${XGRAM_SUBMODULE_ATTEMPTS:-4}
jobs=${XGRAM_SUBMODULE_JOBS:-8}

export GIT_LFS_SKIP_SMUDGE=1
export GIT_TERMINAL_PROMPT=0

git submodule sync --recursive > /dev/null

ok=false
for ((i = 1; i <= attempts; i++)); do
  depth=(--depth 1)
  if ((i == attempts && attempts > 1)); then
    depth=()
    warn "Last attempt: fetching the remaining submodules without --depth 1 (slower)."
  fi
  log "Submodules: attempt $i/$attempts"
  if git -c protocol.version=2 submodule update --init --recursive ${depth[@]+"${depth[@]}"} --jobs "$jobs"; then
    ok=true
    break
  fi
  if ((i < attempts)); then
    warn "Submodule update failed (attempt $i/$attempts). Retrying in $((i * 30)) s."
    sleep "$((i * 30))"
  fi
done
$ok || die "Could not fetch all submodules after $attempts attempts (chromium.googlesource.com is the usual culprit; re-run the job)."

# Anything still uninitialized (-), at the wrong commit (+) or conflicted (U)?
bad=$(git submodule status --recursive | grep -E '^[-+U]' || true)
if [[ -n "$bad" ]]; then
  printf '%s\n' "$bad" >&2
  die "Some submodules are not at the commit recorded in the repository (see above)."
fi

count=$(git submodule status --recursive | wc -l)
log "Submodules ready: $count (including nested ones)."
du -sh .git 2> /dev/null >&2 || true
