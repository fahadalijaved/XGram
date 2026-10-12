#!/usr/bin/env bash
# Builds release notes from `git log` between the last published build and HEAD.
#
#   changelog.sh [--since REV] [--until REV] [--title TEXT] [--link-base URL] [--out-dir DIR]
#                [--max-commits N] [--max-chars N]
#
# Writes into DIR (default ./notes):
#   notes.html  Telegram HTML (parse_mode=HTML) for the bot's --notes: short enough for a post
#               (default at most 25 commits and 800 bytes of visible text, which is always <= the
#               character count Telegram checks). Escapes & < > in commit subjects, and { as &#123; so a
#               subject is never taken for one of the bot's {{NAME}} placeholders.
#   notes.md    Markdown (up to 100 commits) for the GitHub job summary / a GitHub Release body.
#
# --since: the commit of the previous nightly/release (plan.sh finds it with the GitHub API). If it is
#          empty, unknown, or not an ancestor of --until, the latest commits are listed instead.
# --link-base: e.g. https://github.com/<OWNER>/<REPO>/commit/ (only for a public repo; links to a private
#          repo don't open for users). Without it, commits are shown as <code>abc1234</code>.
# Merge commits are left out. Works locally in Git Bash too (it only reads git history).
# Env defaults: XGRAM_NOTES_MAX_COMMITS, XGRAM_NOTES_MAX_CHARS.
set -euo pipefail
# shellcheck source=lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

since=""
until_rev=HEAD
title=""
link_base=""
out_dir=notes
max_commits=${XGRAM_NOTES_MAX_COMMITS:-25}
max_chars=${XGRAM_NOTES_MAX_CHARS:-800}
max_md=100

while (($# > 0)); do
  case "$1" in
    --since) since=${2-}; shift 2 ;;
    --until) until_rev=${2:?}; shift 2 ;;
    --title) title=${2-}; shift 2 ;;
    --link-base) link_base=${2-}; shift 2 ;;
    --out-dir) out_dir=${2:?}; shift 2 ;;
    --max-commits) max_commits=${2:?}; shift 2 ;;
    --max-chars) max_chars=${2:?}; shift 2 ;;
    -h | --help)
      sed -n '2,19p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *) die "Unknown argument: $1" ;;
  esac
done
[[ "$max_commits" =~ ^[0-9]+$ && "$max_chars" =~ ^[0-9]+$ ]] || die "--max-commits and --max-chars must be numbers."
if [[ -n "$link_base" && ! "$link_base" =~ ^https://[A-Za-z0-9./_~:-]+/$ ]]; then
  warn "Ignoring --link-base '$link_base' (expected https://.../commit/ with a trailing slash)."
  link_base=""
fi

mkdir -p "$out_dir"
out_dir=$(cd "$out_dir" && pwd)
cd "$XGRAM_ROOT"

html_escape() {
  sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'
}
# For notes.html (the bot's --notes) only: also '{' as &#123;. The bot fills {{NAME}} placeholders in .html notes,
# and an unfilled one (or one named like a secret) stops `publish --send`. So a commit subject such as
# "Use {{SUPPORT_TOPIC_LINK}} in posts" must not look like one. Telegram shows &#123; as '{'.
tg_escape() {
  html_escape | sed -e 's/{/\&#123;/g'
}
byte_len() {
  printf '%s' "$1" | wc -c | tr -d ' '
}

until_sha=$(git rev-parse --verify "${until_rev}^{commit}") || die "Unknown revision: $until_rev"
since_sha=""
if [[ -n "$since" ]]; then
  if since_sha=$(git rev-parse --verify --quiet "${since}^{commit}"); then
    if ! git merge-base --is-ancestor "$since_sha" "$until_sha"; then
      warn "$since is not an ancestor of $until_rev (history rewritten?): listing the latest commits instead."
      since_sha=""
    fi
  else
    warn "$since is not in this clone: listing the latest commits instead."
    since_sha=""
  fi
fi

if [[ -n "$since_sha" ]]; then
  range="$since_sha..$until_sha"
  heading="Changes since ${since_sha:0:7}"
else
  range="$until_sha"
  heading="Latest changes"
fi
total=$(git rev-list --no-merges --count "$range")
if [[ -z "$since_sha" ]] && ((total > max_commits)); then
  # First build (nothing to compare with): just the latest commits, no "...and N more".
  total=$max_commits
  max_md=$max_commits
fi

# "<sha>\t<subject truncated to 120 characters by git, which is UTF-8 aware>"
commits=$(git log --no-merges -n "$max_md" --format='%H%x09%<(120,trunc)%s' "$range")

html_lines=()
md_lines=()
shown=0
used=0
if [[ -n "$commits" ]]; then
  while IFS=$'\t' read -r sha subject; do
    subject=${subject%"${subject##*[![:space:]]}"}
    [[ -n "$subject" ]] || subject="(no message)"
    short=${sha:0:7}
    subject_html=$(printf '%s' "$subject" | html_escape)
    subject_tg=$(printf '%s' "$subject" | tg_escape)

    if [[ -n "$link_base" ]]; then
      md_lines+=("- $subject_html ([\`$short\`]($link_base$sha))")
    else
      md_lines+=("- $subject_html (\`$short\`)")
    fi

    if ((shown < max_commits)); then
      visible="• $subject ($short)"
      length=$(byte_len "$visible")
      if ((used + length + 1 <= max_chars)); then
        if [[ -n "$link_base" ]]; then
          html_lines+=("• $subject_tg (<a href=\"$link_base$sha\">$short</a>)")
        else
          html_lines+=("• $subject_tg (<code>$short</code>)")
        fi
        used=$((used + length + 1))
        shown=$((shown + 1))
      else
        max_commits=$shown
      fi
    fi
  done <<< "$commits"
fi

if [[ -n "$since_sha" ]]; then
  more=$((total - shown))
  md_more=$((total - ${#md_lines[@]}))
else
  more=0   # first build: "...and N more" would only count part of the history
  md_more=0
fi

{
  if [[ -n "$title" ]]; then
    printf '<b>%s</b>\n' "$(printf '%s' "$title" | tg_escape)"
  fi
  if ((total == 0)); then
    printf '<i>No code changes since %s.</i>\n' "${since_sha:0:7}"
  else
    printf '<b>%s</b>\n' "$heading"
    if ((${#html_lines[@]} > 0)); then
      printf '%s\n' "${html_lines[@]}"
    fi
    if ((more > 0)); then
      printf '<i>…and %d more commit%s.</i>\n' "$more" "$([[ $more == 1 ]] || printf s)"
    fi
  fi
} > "$out_dir/notes.html"

{
  if [[ -n "$title" ]]; then
    printf '## %s\n\n' "$(printf '%s' "$title" | html_escape)"
  fi
  if ((total == 0)); then
    printf '_No code changes since %s._\n' "\`${since_sha:0:7}\`"
  else
    printf '### %s (%d commit%s)\n\n' "$heading" "$total" "$([[ $total == 1 ]] || printf s)"
    if ((${#md_lines[@]} > 0)); then
      printf '%s\n' "${md_lines[@]}"
    fi
    if ((md_more > 0)); then
      printf -- '- …and %d more\n' "$md_more"
    fi
  fi
} > "$out_dir/notes.md"

log "Changelog: $total commit(s) in ${range}; $shown in notes.html ($used bytes of text)."
gh_output commits "$total"
gh_output notes_html "$out_dir/notes.html"
gh_output notes_md "$out_dir/notes.md"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$out_dir/notes.md" >> "$GITHUB_STEP_SUMMARY"
fi
