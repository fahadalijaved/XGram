#!/usr/bin/env bash
# Applies XGram's own changes to submodules after checkout.
#
# A submodule is pinned to an upstream commit, so local edits inside it never reach CI. Such edits are kept
# as patch files instead:  scripts/ci/patches/<submodule path>/<name>.patch
# e.g. scripts/ci/patches/tdlib/xgram.patch (Client.QueryInterceptor, used by XGram's ghost mode).
#
# Each patch is applied with `git apply`. A patch that is already applied is skipped; a patch that neither
# applies nor is already applied stops the build (regenerate it with scripts/ci/save-submodule-patches.sh).
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

patch_root="$XGRAM_ROOT/scripts/ci/patches"
if [[ ! -d "$patch_root" ]]; then
  log "No patches folder: nothing to apply."
  exit 0
fi

applied=0
skipped=0
while IFS= read -r -d '' patch; do
  rel=${patch#"$patch_root"/}
  submodule=${rel%/*}
  if [[ "$submodule" == "$rel" ]]; then
    die "Patch $rel must be inside a folder named after the submodule path (e.g. patches/tdlib/x.patch)."
  fi
  target="$XGRAM_ROOT/$submodule"
  [[ -e "$target/.git" ]] || die "Patch $rel: submodule $submodule is not checked out."

  if git -C "$target" apply --check "$patch" 2> /dev/null; then
    git -C "$target" apply --whitespace=nowarn "$patch"
    log "Applied $rel"
    applied=$((applied + 1))
  elif git -C "$target" apply --check --reverse "$patch" 2> /dev/null; then
    log "Already applied: $rel"
    skipped=$((skipped + 1))
  else
    git -C "$target" apply --check "$patch" || true
    die "Patch $rel no longer applies to $submodule (did the submodule move to a new commit?). Regenerate it with scripts/ci/save-submodule-patches.sh."
  fi
done < <(find "$patch_root" -type f -name '*.patch' -print0 | sort -z)

log "Patches applied: $applied, already present: $skipped."
