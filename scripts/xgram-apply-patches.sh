#!/usr/bin/env bash
# XGram keeps its few changes to Telegram X's submodules (tdlib/ is one) as patches in scripts/ci/patches/.
# Run once after cloning or after updating submodules; already applied patches are skipped.
set -euo pipefail
exec bash "$(dirname "${BASH_SOURCE[0]}")/ci/apply-patches.sh"
