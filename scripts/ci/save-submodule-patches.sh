#!/usr/bin/env bash
# Run this on your own machine (Git Bash), not in CI.
#
# Saves the uncommitted changes inside each submodule as scripts/ci/patches/<submodule path>/xgram.patch,
# so CI (apply-patches.sh) can re-apply them on a fresh checkout. Commit the patch files afterwards.
#
#   bash scripts/ci/save-submodule-patches.sh            # all submodules with changes
#   bash scripts/ci/save-submodule-patches.sh tdlib      # only these paths
#
# Only tracked files are saved. New (untracked) files inside a submodule are reported, not saved:
# `git -C <submodule> add -N <file>` first if they belong in the patch.
# It writes only inside scripts/ci/patches/ and never changes the submodules.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
patch_root="scripts/ci/patches"

if (($# > 0)); then
  paths=("$@")
else
  paths=()
  # The single-quoted script is run by git for each submodule, which sets $displaypath.
  # shellcheck disable=SC2016
  while IFS= read -r path; do
    [[ -n "$path" ]] && paths+=("$path")
  done < <(git submodule foreach --quiet --recursive 'printf "%s\n" "$displaypath"')
fi

saved=0
for path in "${paths[@]}"; do
  [[ -e "$path/.git" ]] || die "$path is not a checked-out submodule."
  untracked=$(git -C "$path" ls-files --others --exclude-standard)
  if [[ -n "$untracked" ]]; then
    warn "$path has untracked files that are NOT saved in the patch:"
    printf '  %s\n' "$untracked" >&2
  fi
  if git -C "$path" diff --quiet HEAD --; then
    continue
  fi
  out="$patch_root/$path/xgram.patch"
  mkdir -p "$(dirname "$out")"
  # Uses the submodule's own line-ending settings: with core.autocrlf=true (Windows) git normalizes CRLF
  # working files before diffing, so the patch has LF lines like the files in the upstream repository.
  git -C "$path" diff --binary --no-color --no-ext-diff HEAD -- > "$out"
  log "Saved $out ($(wc -l < "$out") lines)"
  saved=$((saved + 1))
done

log "Patches written: $saved. Check them with: git diff --stat -- $patch_root (then commit)."
