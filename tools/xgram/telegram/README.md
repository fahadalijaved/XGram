# XGram Build Bot

`xgram_bot.py` posts to the **XGram** group (forum topics) and the **XGram CI** channel as **XGram Build Bot**.
It needs Python 3.9 or newer and only the standard library. Telethon is optional and is only needed for files over
the Bot API upload limit.

**Nothing is sent unless you add `--send`.** Without it, every command is a dry run. A dry run prints the exact
requests (method, chat, topic, text and visible length) and makes no network calls.

| File | What it is |
|---|---|
| `xgram_bot.py` | The bot script (all commands below) |
| `telegram.env.example` | Config template with placeholders. Copy it outside the repo. |
| `posts/*.html` | Posts in Telegram HTML, one message per file. Long posts are split into `name.1.html`, `name.2.html`, ... |
| `posts/bot/` | `commands.txt` (BotFather format `command - description`), `description.txt`, `short_description.txt`, and `help.html`, the `/help` reply (and `/start`, unless you add a `start.html`) |

```sh
python tools/xgram/telegram/xgram_bot.py --help
python tools/xgram/telegram/xgram_bot.py selftest     # offline self-test
python tools/xgram/telegram/xgram_bot.py posts        # offline check of every post (HTML, length, placeholders)
```

## 1. Config file (outside the repo)

```sh
# Git Bash
mkdir -p ~/.xgram && cp tools/xgram/telegram/telegram.env.example ~/.xgram/telegram.env
```
```powershell
# PowerShell
New-Item -ItemType Directory -Force "$HOME\.xgram"; Copy-Item tools\xgram\telegram\telegram.env.example "$HOME\.xgram\telegram.env"
```

Then edit `~/.xgram/telegram.env` (on Windows that is `%USERPROFILE%\.xgram\telegram.env`):

- `XGRAM_BOT_TOKEN`: from @BotFather. You got it when you created the bot; `/token` shows it again.
  If it ever leaks, use `/revoke`.
- `XGRAM_GROUP_ID`, `XGRAM_CHANNEL_ID`, `XGRAM_TOPIC_UPDATES`, `XGRAM_TOPIC_SUPPORT`: see step 4.

Notes:
- Environment variables with the same names override the file. CI uses them.
- A value that still starts with `<` counts as unset.
- The script never prints the token. It masks the token, and `TELEGRAM_API_HASH`, in all output and error messages.
- `--env-file PATH` uses a different file.

## 2. BotFather

- **Allow groups** (`/setjoingroups`) must be on while you add the bot to the group. You can turn it off afterwards
  so nobody else can add it. Turning it off does not remove the bot from groups it is already in.
- **Privacy mode** can stay as it is. A bot that is an admin receives all group messages anyway.
- Commands and descriptions come from `posts/bot/`:
  ```sh
  python tools/xgram/telegram/xgram_bot.py setup-bot            # dry run: shows what will be set
  python tools/xgram/telegram/xgram_bot.py setup-bot --send     # setMyCommands, setMyDescription, setMyShortDescription
  ```
  `setup-bot --rights --send` also sets the bot's *suggested* admin rights (`setMyDefaultAdministratorRights`), so the
  "add admin" screen comes pre-ticked with the rights in step 3.

## 3. Add the bot as an admin

The right names below are the Bot API names. The labels in Telegram apps are close to these but can differ a little
between apps and versions.

**XGram group** (group settings → Administrators → Add admin → the bot's username):

| Right | Bot API | Why |
|---|---|---|
| Manage topics | `can_manage_topics` | **Required.** Lets the bot post in closed topics (a read-only Updates topic) and create topics (`topic-create`). |
| Pin messages | `can_pin_messages` | **Required** for `--pin`. |
| Delete messages | `can_delete_messages` | Optional, for cleaning up its own mistakes. |
| Everything else | | Off: change info, ban users, invite users, manage video chats, add admins. Keep **Remain anonymous** off. |

**XGram CI channel** (channel settings → Administrators → Add admin):

| Right | Bot API | Why |
|---|---|---|
| Post messages | `can_post_messages` | **Required.** |
| Edit messages of others | `can_edit_messages` | **Required** for `--pin`: in channels, pinning needs this right. |
| Delete messages of others | `can_delete_messages` | Optional. |
| Everything else | | Off: stories, add admins, and so on. |

Then check. The dry run lists your config; `--send` makes only read-only calls (`getMe`, `getWebhookInfo`, `getChat`,
`getChatMember`):
```sh
python tools/xgram/telegram/xgram_bot.py check --send
```

## 4. Chat and topic ids

General needs no id: the script never sends `message_thread_id` for General, because the Bot API rejects
`message_thread_id=1`. Bots cannot list a group's topics (no Bot API method does that), so get the ids in one of
these ways.

**A. Topic links (simplest).** In XGram, the topic's menu has **Copy link**. Paste the link straight into the config:
the script reads chat and topic ids from links.
```
XGRAM_TOPIC_UPDATES=https://t.me/c/1234567890/12     # private group: topic 12, group id -1001234567890
XGRAM_TOPIC_SUPPORT=https://t.me/xgram_group/14      # public group with a username
XGRAM_GROUP_ID=https://t.me/c/1234567890/12          # a link also works for the group id
```
The private form `t.me/c/<N>/...` means Bot API chat id `-100<N>`. XGram's profile ID sheet can also copy a chat's
full id.

**B. `discover`.** Run it after the bot is an admin:
1. Post one message in General, in Updates, in Support and in the CI channel. By default a bot doesn't see other
   bots' messages, so Rose's messages don't count. Updates are kept for 24 hours.
2. Run `python tools/xgram/telegram/xgram_bot.py discover --send`.

It prints every chat id, the topic ids with their names when Telegram includes them, and suggested config lines.
`discover` does not confirm (delete) updates. It fails with *409 Conflict* while `serve` is running or a webhook is set.

## 5. Posting

```sh
python tools/xgram/telegram/xgram_bot.py post --file general_intro --to general --pin          # dry run first
python tools/xgram/telegram/xgram_bot.py post --file general_intro --to general --pin --send
python tools/xgram/telegram/xgram_bot.py post --file updates_intro --to updates --pin --send
python tools/xgram/telegram/xgram_bot.py post --file support_intro --to support --pin --send
python tools/xgram/telegram/xgram_bot.py post --file features --to updates --send             # features.1..4.html
```

- **`--to`** is `general`, `updates`, `support` or `channel`. Any `XGRAM_TOPIC_<NAME>` you add to the config also works
  as a target, e.g. `XGRAM_TOPIC_RELEASES` → `--to releases`.
- **Other options:** `--silent` sends without a notification. `--preview` allows link previews (off by default).
- **Before sending**, every part is checked:
  - Only Telegram's HTML tags are allowed (there is no `<br>` or `<p>`: newlines are literal).
  - `<`, `>` and `&` must be escaped.
  - Tags must be closed and nested properly.
  - The visible text must be at most 4096 characters, counted after the HTML is parsed. The script counts UTF-16
    units, so an emoji counts 2. That can only over-count.
- **Pacing:** parts are sent about 3.5 s apart in the group, because Telegram limits bots to about 20 messages per
  minute in a group.
- **Pinning:** `--pin` pins the first part.

**Placeholders.** Posts can contain `{{NAME}}`. Each one is filled from `XGRAM_<NAME>` in the config, or computed:

| Placeholder | Filled with |
|---|---|
| `{{UPDATES_TOPIC_LINK}}`, `{{SUPPORT_TOPIC_LINK}}`, `{{<TOPIC>_TOPIC_LINK}}` | `t.me/c/<N>/<topic id>`, from `XGRAM_GROUP_ID` and `XGRAM_TOPIC_<TOPIC>` |
| `{{CI_CHANNEL_LINK}}` | `t.me/<username>` if `XGRAM_CHANNEL_ID` is the public channel's `@username`. A **private** channel needs `XGRAM_CI_CHANNEL_LINK=https://t.me/+...` (an invite link), because `t.me/c/...` links only open for members. |

Links are inserted without `https://`, because the posts write `href="https://{{...}}"`. A placeholder that can't be
filled is a warning in a dry run and an error with `--send`. **Don't fill the placeholders by editing the post files**:
they are in the open-source repo, and the config file is private.

Release notes generated by `scripts/ci/changelog.sh` write `{` as `&#123;`, so a commit subject that mentions
`{{NAME}}` is shown as text and isn't taken for a placeholder.

**Read-only Updates topic.** Close the topic in XGram (topic menu → Close). Members can no longer post there. Admins
with *Manage topics* still can, and so can the bot.

## 6. New topics

```sh
python tools/xgram/telegram/xgram_bot.py topic-create --name "Releases" --emoji 📦 --color blue --send
```

- **The new topic's id** is printed. Put it in the config as `XGRAM_TOPIC_RELEASES=<id>`.
- **Icon color** is one of blue, yellow, violet, green, pink or red. It is fixed once the topic exists.
- **Icon emoji: bots can only use Telegram's own topic icon set** (`getForumTopicIconStickers`), not the XGram brand
  emoji pack. `--emoji` looks the emoji up in that set, and if it isn't there, the error lists the ones that are.
  To use the brand pack (`docs/xgram/brand/`), set the icon in the app as an admin with Telegram Premium.

## 7. Publishing builds

```sh
python tools/xgram/telegram/xgram_bot.py publish \
  --apk app/build/outputs/apk/latestArm64/release/XGram-0.29.1.1817-arm64-v8a.apk \
  --version "1.0" --commit "$(git rev-parse HEAD)" \
  --notes tools/xgram/telegram/posts/release_notes.example.html \
  --announce updates --pin            # add --send to really post
```

**What gets posted:**
- Each `--apk` (repeat the flag for several) is posted as a document to `--to` (default `channel`). Its caption has:
  - the version and ABI
  - the file name
  - the size (for example *49.8 MB (52,267,500 bytes)*)
  - the commit (12 characters)
  - the SHA-256
  - `XGRAM_SOURCE_URL`, if you set it (GPLv3: a link to the source of that build)
- **Release notes** go into the caption when there is one APK and everything fits in 1024 characters. Otherwise they
  follow as a separate message that replies to the first APK.
  - A `.html` notes file is Telegram HTML.
  - Any other file is plain text.
  - `--notes -` reads the notes from stdin.
- **`--announce updates`** posts "XGram 1.0 is out." with the notes and a Download link to the release post.
  - If the channel is private, that link only opens for channel members. Prefer a public channel, or tell people to
    join it.
- **`--pin`** pins the release post.
- **State file:** after a real publish, `~/.xgram/telegram_state.json` stores the version, links and checksums.
  `/latest` reads it. `--state-file` uses a different path.

**Big files.** Up to 49 MB (49,000,000 bytes) is uploaded through the normal Bot API. You can change that cutoff
with `XGRAM_CLOUD_UPLOAD_MB` (in MB of 1,000,000 bytes). Bigger files use one of these two routes, and `publish
--send` checks the route of every file before it posts any, so a missing route never leaves a release half-posted:
1. **Telethon (recommended):** `pip install telethon` (written against 1.45). Also set `TELEGRAM_API_ID` and
   `TELEGRAM_API_HASH` from <https://my.telegram.org> (*API development tools*).
   - The script logs in *as the bot* with an in-memory session, uploads (up to about 2000 MiB) and logs out.
     Nothing is saved.
   - The message id it gets back is used for the notes reply and for pinning through the Bot API.
2. **Self-hosted `telegram-bot-api --local`:** set `XGRAM_BOT_API_URL=http://127.0.0.1:8081`.
   - The script then uses that server for **every** call, with a 2000 MB limit.
   - The server needs `--api-id`/`--api-hash`.
   - Telegram requires a one-time `logOut` from the cloud Bot API before you use a local server, and after that the
     bot cannot log back into the cloud for 10 minutes. That makes this route awkward in CI.

**Current file sizes** (local builds):
- Release arm64: 52,267,500 bytes = 49.8 MiB. That is over 49,000,000 bytes, so by default it goes through Telethon.
- Debug: about 72.9 MiB.

The dry run shows which route each file takes, whether Telethon is installed, and whether the API id and hash are set.

## 8. Replies: `serve`

```sh
python tools/xgram/telegram/xgram_bot.py serve            # dry run: shows the current /start, /help, /latest replies
python tools/xgram/telegram/xgram_bot.py serve --send     # long polling, Ctrl+C stops
```

- **Where it answers:**
  - In private chats: `/start`, `/help` and `/latest`.
  - In the group: `/latest`, and `/start@<bot>` and `/help@<bot>`.
  - A bare `/start` or `/help` in the group is left to Rose.
- **Where the reply goes:** into the topic the command came from.
- **Reply text:**
  - `/help` uses `posts/bot/help.html` (install steps and how to check the SHA-256). `/start` uses
    `posts/bot/start.html` if you add one, otherwise `help.html` too. Without either file, both use a short built-in
    text.
  - `/latest` is built from the state file.
- **Only one `getUpdates` reader at a time.** A second `serve`, or `discover`, gets *409 Conflict*.
- `serve --send --once` handles one batch and exits.

## 9. CI (daily builds, later)

In CI, set the config as environment variables. `XGRAM_BOT_TOKEN` and `TELEGRAM_API_HASH` go in the CI secret store.
After the build step:
```sh
pip install telethon        # only if an APK is over 49 MB (49,000,000 bytes)
python tools/xgram/telegram/xgram_bot.py publish --send --to channel \
  --apk path/to/XGram-...-arm64-v8a.apk --version "$VERSION" --commit "$COMMIT_SHA" --notes notes.txt
```
The state file lives in the runner's home directory and is lost after the job. If `serve` runs on another machine, it
won't see CI publishes unless you keep and share that file (`--state-file`).

## 10. Rose

An admin types the Rose commands (`docs/xgram/telegram/ROSE.md`); this bot doesn't. Rose ignores commands sent by
other bots unless an admin changes `/bot2bot`, which is `off` by default, and by default a bot doesn't see other
bots' messages. Telegram's bot-to-bot communication is an opt-in BotFather setting; XGram Build Bot doesn't need it.

## 11. Not verified

These come from the Bot API docs and the source, but have not been tried against Telegram:
- A pinned message in a topic shows as that topic's pin. `pinChatMessage` has no topic parameter; this is inferred from
  the per-topic unpin methods.
- Telethon and the cloud Bot API using the same bot token at the same time. This is common practice, but there is no
  official statement.
- The exact cloud upload cutoff ("50 MB": decimal or binary, before or after multipart overhead). That is why the
  default limit is 49,000,000 bytes, below 50 MB in either unit. A file over the real limit gets *413* and is not
  retried through Telethon.
- Whether Telegram counts the 4096/1024 limits in UTF-16 units or code points. The script uses UTF-16, the stricter
  of the two.
- Which emoji `getForumTopicIconStickers` offers.
- Raw `>` in text: the docs say to escape it, so the checker rejects it, even though Telegram may accept it.
