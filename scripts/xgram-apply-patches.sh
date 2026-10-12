#!/usr/bin/env bash
# XGram keeps its few changes to Telegram X's submodules as patches (tdlib/ is a submodule, so the main repository
# can't hold them). Run once after cloning or after updating submodules; already applied patches are skipped.
set -euo pipefail
cd "$(dirname "$0")/.."
for patch in patches/tdlib/*.patch; do
  [ -e "$patch" ] || continue
  if git -C tdlib apply --reverse --check "../$patch" 2>/dev/null; then
    echo "already applied: $patch"
  else
    git -C tdlib apply "../$patch"
    echo "applied: $patch"
  fi
done
