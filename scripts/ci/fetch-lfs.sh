#!/usr/bin/env bash
# Downloads the Git LFS files of the tdlib submodule (prebuilt TDLib and OpenSSL .so files, ~390 MB, 42 files).
#
#   fetch-lfs.sh --cache-info   outputs key=lfs-tdlib-<tdlib commit> and path=<tdlib git dir>/lfs/objects
#                               for actions/cache (run it before restoring the cache)
#   fetch-lfs.sh                runs `git lfs pull` in tdlib and checks that no .so is still a pointer file
#
# validateGitSetup checks every ndk/api/abi folder, so all of them are needed, not just arm64. With the cache
# restored first, `git lfs pull` only downloads what is missing, which saves TGX-Android's LFS bandwidth.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
[[ -e tdlib/.git ]] || die "tdlib is not checked out. Run checkout-submodules.sh first."

if [[ "${1:-}" == --cache-info ]]; then
  gh_output key "lfs-tdlib-$(git rev-parse HEAD:tdlib)"
  gh_output path "$(git -C tdlib rev-parse --absolute-git-dir)/lfs/objects"
  exit 0
fi

git lfs version > /dev/null 2>&1 || die "git-lfs is not installed."
git -C tdlib lfs install --local > /dev/null
retry 3 20 git -C tdlib lfs pull || die "git lfs pull failed for tdlib."

# No .so may still be an LFS pointer file ("version https://git-lfs.github.com/spec/v1 ...").
pointers=()
total=0
while IFS= read -r -d '' file; do
  total=$((total + 1))
  head_bytes=$(head -c 23 "$file" | tr -d '\000')
  if [[ "$head_bytes" == "version https://git-lfs"* ]]; then
    pointers+=("$file")
  fi
done < <(find tdlib/src/main/libs tdlib/openssl -type f -name '*.so' -print0 2> /dev/null)

((total > 0)) || die "No .so files found under tdlib/src/main/libs or tdlib/openssl."
if ((${#pointers[@]} > 0)); then
  printf '  %s\n' "${pointers[@]}" >&2
  die "${#pointers[@]} of $total tdlib .so files are still LFS pointers (see above)."
fi

size=$(du -shc tdlib/src/main/libs tdlib/openssl 2> /dev/null | tail -n 1 | cut -f 1)
log "tdlib LFS files ready: $total .so files, $size."
