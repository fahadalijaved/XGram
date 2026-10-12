#!/usr/bin/env bash
# Prints the commit SHA of the newest successful run of the current workflow (empty line if there is none
# or the GitHub API can't be reached). Used to skip a nightly when nothing changed, and as the start of the
# changelog.
#
#   last-successful-run.sh [--event schedule|workflow_dispatch|...] [--branch NAME] [--workflow FILE]
#
# Needs: the gh CLI (preinstalled on GitHub runners), GH_TOKEN with `actions: read`, GITHUB_REPOSITORY, and
# GITHUB_WORKFLOW_REF (or --workflow). The run that is in progress is never "successful", so it isn't counted.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

event=""
branch=""
workflow=""
while (($# > 0)); do
  case "$1" in
    --event) event=${2:?}; shift 2 ;;
    --branch) branch=${2:?}; shift 2 ;;
    --workflow) workflow=${2:?}; shift 2 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

if [[ -z "$workflow" ]]; then
  # owner/repo/.github/workflows/xgram-nightly.yml@refs/heads/main -> xgram-nightly.yml
  ref=${GITHUB_WORKFLOW_REF:-}
  ref=${ref%@*}
  workflow=${ref##*/}
fi

if [[ -z "$workflow" || -z "${GITHUB_REPOSITORY:-}" || -z "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]]; then
  warn "Can't look up earlier runs (needs GITHUB_REPOSITORY, GITHUB_WORKFLOW_REF and GH_TOKEN)."
  printf '\n'
  exit 0
fi
if ! command -v gh > /dev/null 2>&1; then
  warn "gh CLI not found: can't look up earlier runs."
  printf '\n'
  exit 0
fi

args=(-X GET "repos/$GITHUB_REPOSITORY/actions/workflows/$workflow/runs" -f status=success -f per_page=1)
if [[ -n "$event" ]]; then
  args+=(-f "event=$event")
fi
if [[ -n "$branch" ]]; then
  args+=(-f "branch=$branch")
fi

if ! sha=$(gh api "${args[@]}" --jq '.workflow_runs[0].head_sha // ""' 2> /dev/null); then
  warn "GitHub API lookup of earlier $workflow runs failed (does the job have 'actions: read'?)."
  sha=""
fi
if [[ -n "$sha" && ! "$sha" =~ ^[0-9a-f]{40}([0-9a-f]{24})?$ ]]; then
  warn "Unexpected API answer for the last run: ignoring it."
  sha=""
fi
log "Last successful $workflow run${event:+ ($event)}${branch:+ on $branch}: ${sha:-none}"
printf '%s\n' "$sha"
