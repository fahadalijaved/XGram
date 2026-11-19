# XGram: notes for Claude

XGram (`com.fahadali.xgram`) is a Telegram X fork: Telegram X's Java/Kotlin app with XGram's own features (`com.fahadali.xgram.*`, and `// XGram:` comments in Telegram X files) and a Material 3 Expressive look ("Expressive mode").

## How work happens now

- No local build machine. Edit, commit, push. **GitHub Actions builds** (`.github/workflows/`).
- Branches: work on **`m3e`**. When a change is done, push it to both: `git push origin m3e m3e:main` (fast-forward only, never force).
- **Nightly** (`xgram-nightly.yml`, every 2 days): builds both APKs (arm64 + arm32 release) when there are new commits and posts them to the **CI channel only**. Start one by hand:
  `gh workflow run xgram-nightly.yml --ref main -f flavor=both -f build_type=release -f publish=true`
  For a build check without posting: `-f flavor=arm64 -f publish=false`. After pushing code, run that check and fix any error until it passes.
- **Stable releases** (`xgram-release.yml`, posts to the Updates topic) only when the owner says "release". CI builds never go to Updates.
- **Weekly upstream check** (`xgram-upstream-check.yml`, Mondays): the bot tells the owner what Telegram X / TDLib has that XGram hasn't reviewed (markers in `.github/upstream-sync.txt`). On "sync upstream": take Telegram X commits one by one with `git cherry-pick -x` (never merge upstream), adapt TDLib upgrades (they need code changes), check the build, then move the markers forward.
- Version: `0.29.2.1818.N`, where N = 341 + `git rev-list --count 628f896e..HEAD` (needs full history: `git fetch --unshallow` if the clone is shallow). The in-app updater only offers a higher N.
- Submodules carry XGram changes as patches in `scripts/ci/patches/<submodule>/xgram.patch` (applied by CI). Changing code inside a submodule means regenerating its patch.

## Owner rules

- No `Co-Authored-By` or other Claude trailers in commits. No mentions of Claude in history.
- Never commit secrets: `app/google-services.json` is a placeholder on purpose; the real one, the signing key, the bot token and chat IDs are GitHub secrets. Never print them in logs.
- One feature at a time: finish, push, build check, then the next.
- Fix things directly; no large audit or review fan-outs (one quick review of a risky change is fine).
- Expressive mode must be Material 3 Expressive everywhere; a Telegram X leftover in it is a bug.
- Write user-facing text plainly (sentence case, no jargon). Commit messages say what changed for the user and why.
- The owner tests on a Samsung Galaxy Fold and installs from the CI channel; ask for screenshots or a recording when a UI report is unclear.

## Code map (starting points)

- XGram features: `app/src/main/java/com/fahadali/xgram/` (settings pages in Compose: `compose/pages/`, `compose/XgramPage.kt`; enum order is saved, add pages at the end).
- Telegram X core: `app/src/main/java/org/thunderdog/challegram/` (`telegram/Tdlib.java`, `TdlibUi.java`; `ui/` controllers; `data/TGMessage*.java`).
- TDLib API: `tdlib/src/main/java/org/drinkless/tdlib/TdApi.java` (prebuilt TDLib, pinned submodule).
- XGram settings: `com.fahadali.xgram.XgramSettings`.
