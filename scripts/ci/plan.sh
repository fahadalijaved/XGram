#!/usr/bin/env bash
# Decides what a nightly or release run builds and publishes. Runs in the "Plan" job, which has the full
# commit history (fetch-depth: 0, filter: blob:none) but no submodules.
#
# Env:
#   XGRAM_MODE                 nightly | release
#   GITHUB_EVENT_NAME, GITHUB_REF_NAME, GITHUB_REF_TYPE, GITHUB_REPOSITORY, GITHUB_WORKFLOW_REF, GH_TOKEN
#   IN_FLAVOR, IN_BUILD_TYPE, IN_PUBLISH, IN_ANNOUNCE, IN_CHANGELOG_SINCE, IN_APP_VERSION   (workflow_dispatch inputs)
#   XGRAM_NIGHTLY_FLAVOR (arm64), XGRAM_NIGHTLY_BUILD_TYPE (release), XGRAM_NIGHTLY_PUBLISH (true),
#   XGRAM_NIGHTLY_ANNOUNCE (false), XGRAM_NIGHTLY_FORCE (false)                              (scheduled runs)
#   XGRAM_DEFAULT_BRANCH (fallback when the API is unavailable)
# Flavor (IN_FLAVOR, XGRAM_NIGHTLY_FLAVOR): arm64 (arm64-v8a, most phones), arm32 (armeabi-v7a, older 32-bit
# phones) or both.
#
# Outputs: skip, flavor, flavors (JSON array), build_type, publish, announce, since, app_version, head, short,
#          default_branch, link_base (commit link prefix, only for public repos)
#
# Rules:
#  - A scheduled nightly is skipped when HEAD is the commit of the last successful scheduled nightly
#    (no new commits). Set the repo variable XGRAM_NIGHTLY_FORCE=true to build anyway.
#  - Publishing to Telegram only happens from the default branch (or, for releases, from a tag).
#  - Debug builds are never published (they stay workflow artifacts): see the comment at the check below.
#  - The changelog starts at the last successful run of the same workflow (nightly: scheduled runs on the
#    default branch; release: any successful release run), unless IN_CHANGELOG_SINCE is given.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$XGRAM_ROOT"
here="$XGRAM_ROOT/scripts/ci"

mode=${XGRAM_MODE:-}
event=${GITHUB_EVENT_NAME:-workflow_dispatch}
ref_name=${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}
ref_type=${GITHUB_REF_TYPE:-branch}
head=$(git rev-parse HEAD)
short=$(git rev-parse --short=7 HEAD)

case "$mode" in
  nightly | release) ;;
  *) die "XGRAM_MODE must be nightly or release, got: '$mode'" ;;
esac

# Repository facts (the schedule event payload has no repository object, so ask the API).
default_branch=""
visibility=""
if [[ -n "${GITHUB_REPOSITORY:-}" && -n "${GH_TOKEN:-}" ]] && command -v gh > /dev/null 2>&1; then
  if repo_info=$(gh api "repos/$GITHUB_REPOSITORY" --jq '[.default_branch, .visibility] | @tsv' 2> /dev/null); then
    IFS=$'\t' read -r default_branch visibility <<< "$repo_info"
  else
    warn "Could not read repository info from the GitHub API."
  fi
fi
default_branch=${default_branch:-${XGRAM_DEFAULT_BRANCH:-main}}

link_base=""
if [[ "$visibility" == public && -n "${GITHUB_REPOSITORY:-}" ]]; then
  link_base="${GITHUB_SERVER_URL:-https://github.com}/$GITHUB_REPOSITORY/commit/"
fi

# What to build.
if [[ "$mode" == nightly && "$event" == schedule ]]; then
  flavor=${XGRAM_NIGHTLY_FLAVOR:-arm64}
  build_type=${XGRAM_NIGHTLY_BUILD_TYPE:-release}
  publish=${XGRAM_NIGHTLY_PUBLISH:-true}
elif [[ "$mode" == nightly ]]; then
  flavor=${IN_FLAVOR:-arm64}
  build_type=${IN_BUILD_TYPE:-release}
  publish=${IN_PUBLISH:-false}
else
  flavor=${IN_FLAVOR:-arm64}
  build_type=release
  publish=${IN_PUBLISH:-true}
fi
# Never an extra announcement: CI builds stay in the CI channel, stable releases are posted in Updates itself.
announce=false

# arm64 = Gradle flavor latestArm64 (arm64-v8a), arm32 = latestArm32 (armeabi-v7a); both = the two of them.
case "$flavor" in
  arm64) flavors='["arm64"]' ;;
  arm32) flavors='["arm32"]' ;;
  both) flavors='["arm64","arm32"]' ;;
  # A repository variable left over from when CI built a universal APK.
  universal) die "There is no universal APK any more: use arm32 (older 32-bit phones) or both. Check XGRAM_NIGHTLY_FLAVOR." ;;
  *) die "flavor must be arm64, arm32 or both, got: '$flavor'" ;;
esac
case "$build_type" in
  debug | release) ;;
  *) die "build_type must be debug or release, got: '$build_type'" ;;
esac
if is_true "$publish"; then publish=true; else publish=false; fi
if is_true "$announce"; then announce=true; else announce=false; fi

# Debug builds are android:debuggable, signed with the release key and share the release builds' versionCode, so
# one installs over a release install without a warning. On a phone running a debuggable build, anyone with ADB
# access can copy the app's private data with `run-as` (without root), including the Telegram session. So they
# are never posted to Telegram, whatever the inputs or variables say. The APK is still in the run's artifacts.
if [[ "$build_type" == debug && "$publish" == true ]]; then
  warn "Debug builds are never posted to Telegram (debuggable and signed with the release key): not publishing this run. The APK is still in the run's artifacts."
  publish=false
fi

if [[ "$publish" == true ]]; then
  if [[ "$ref_type" == branch && "$ref_name" == "$default_branch" ]]; then
    :
  elif [[ "$mode" == release && "$ref_type" == tag ]]; then
    :
  else
    allowed="the $default_branch branch"
    if [[ "$mode" == release ]]; then
      allowed="$allowed or a tag"
    fi
    warn "Publishing only happens from $allowed: not publishing this run ($ref_type $ref_name)."
    publish=false
  fi
fi
if [[ "$publish" != true ]]; then
  announce=false
fi

# Last successful run of this workflow: skip check and changelog start.
if [[ "$mode" == nightly ]]; then
  last=$(bash "$here/last-successful-run.sh" --event schedule --branch "$default_branch")
else
  last=$(bash "$here/last-successful-run.sh")
fi

skip=false
if [[ "$mode" == nightly && "$event" == schedule && -n "$last" && "$last" == "$head" ]]; then
  if is_true "${XGRAM_NIGHTLY_FORCE:-false}"; then
    notice "No new commits since the last nightly, but XGRAM_NIGHTLY_FORCE is set: building anyway."
  else
    skip=true
    notice "No new commits since the last successful nightly ($short): skipping this build."
  fi
fi

since=""
if [[ -n "${IN_CHANGELOG_SINCE:-}" ]]; then
  if since=$(git rev-parse --verify --quiet "${IN_CHANGELOG_SINCE}^{commit}"); then
    log "Changelog starts at the requested ${IN_CHANGELOG_SINCE} ($since)."
  else
    warn "changelog_since '${IN_CHANGELOG_SINCE}' is not a known commit or tag: using the last successful run instead."
    since=$last
  fi
else
  since=$last
fi

app_version=$(XGRAM_APP_VERSION_OVERRIDE="${IN_APP_VERSION:-}" bash "$here/version.sh")

gh_output skip "$skip"
gh_output flavor "$flavor"
gh_output flavors "$flavors"
gh_output build_type "$build_type"
gh_output publish "$publish"
gh_output announce "$announce"
gh_output since "$since"
gh_output app_version "$app_version"
gh_output head "$head"
gh_output short "$short"
gh_output default_branch "$default_branch"
gh_output link_base "$link_base"

build_text="$flavor, $build_type"
if [[ "$skip" == true ]]; then
  build_text="skipped (no new commits)"
fi
since_text="(no earlier run: latest commits)"
if [[ -n "$since" ]]; then
  since_text="\`${since:0:7}\`"
fi
summary "### XGram $mode plan" \
  "" \
  "| | |" \
  "|---|---|" \
  "| Commit | \`$short\` ($ref_type \`$ref_name\`) |" \
  "| Build | $build_text |" \
  "| app.version (N) | $app_version (arm64 versionCode ${app_version}502, arm32 ${app_version}501) |" \
  "| Publish to Telegram | $publish (announce in Updates: $announce) |" \
  "| Changelog since | $since_text |" \
  ""
