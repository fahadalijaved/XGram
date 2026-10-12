#!/usr/bin/env bash
# Shared helpers for the XGram CI scripts in scripts/ci/.
# Source it, don't run it:   . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
#
# Messages go to stderr, so a script's stdout can be captured with $(...).
# On GitHub Actions they become ::warning:: / ::error:: annotations.

# Repository root (the folder that holds version.properties).
XGRAM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export XGRAM_ROOT

log() {
  printf '%s\n' "$*" >&2
}

warn() {
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    printf '::warning::%s\n' "$*" >&2
  else
    printf 'warning: %s\n' "$*" >&2
  fi
}

notice() {
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    printf '::notice::%s\n' "$*" >&2
  else
    printf 'note: %s\n' "$*" >&2
  fi
}

die() {
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    printf '::error::%s\n' "$*" >&2
  else
    printf 'error: %s\n' "$*" >&2
  fi
  exit 1
}

# is_true VALUE: true/1/yes/on (any case) -> success.
is_true() {
  case "${1,,}" in
    true | 1 | yes | on) return 0 ;;
    *) return 1 ;;
  esac
}

# prop FILE KEY: prints the value of KEY from a KEY=VALUE properties file (first match, CR stripped).
prop() {
  local file=$1 key=$2 line
  [[ -f "$file" ]] || die "Not found: $file"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    if [[ "$line" == "$key="* ]]; then
      printf '%s\n' "${line#"$key="}"
      return 0
    fi
  done < "$file"
  die "$key not found in $file"
}

# require_env NAME...: fails with one message that lists every empty variable.
require_env() {
  local name missing=()
  for name in "$@"; do
    if [[ -z "${!name:-}" ]]; then
      missing+=("$name")
    fi
  done
  if ((${#missing[@]} > 0)); then
    die "Missing or empty: ${missing[*]}. See docs/xgram/telegram/CI.md (secrets and variables)."
  fi
}

# gh_output KEY VALUE: step output on GitHub Actions; logged everywhere. Single-line values only.
gh_output() {
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
  fi
  log "output: $1=$2"
}

# gh_env KEY VALUE: exports KEY for this script and, on GitHub Actions, for the later steps of the job.
gh_env() {
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_ENV"
  fi
  export "$1=$2"
}

# gh_path DIR: prepends DIR to PATH for the later steps of the job.
gh_path() {
  if [[ -n "${GITHUB_PATH:-}" ]]; then
    printf '%s\n' "$1" >> "$GITHUB_PATH"
  fi
  PATH="$1:$PATH"
  export PATH
}

# mask VALUE: hides VALUE in GitHub Actions logs (no-op elsewhere). Workflow commands are read from stdout.
mask() {
  if [[ -n "${GITHUB_ACTIONS:-}" && -n "${1:-}" ]]; then
    printf '::add-mask::%s\n' "$1"
  fi
}

# summary TEXT...: appends lines to the job summary on GitHub Actions.
summary() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '%s\n' "$@" >> "$GITHUB_STEP_SUMMARY"
  fi
}

# python_cmd: prints the name of a Python 3 interpreter (python3, or python on Windows).
python_cmd() {
  local candidate
  for candidate in python3 python; do
    if command -v "$candidate" > /dev/null 2>&1 && "$candidate" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 6) else 1)' > /dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  die "Python 3.6+ not found"
}

# retry ATTEMPTS DELAY_SECONDS COMMAND...: runs COMMAND until it succeeds, waiting DELAY*attempt between tries.
retry() {
  local attempts=$1 delay=$2 i
  shift 2
  for ((i = 1; i <= attempts; i++)); do
    if "$@"; then
      return 0
    fi
    if ((i < attempts)); then
      warn "Attempt $i/$attempts failed: $1. Retrying in $((delay * i)) s."
      sleep "$((delay * i))"
    fi
  done
  return 1
}
