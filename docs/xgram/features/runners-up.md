# Runners-up

**Status: none of these is built.** They come after [Catch Up](catch-up.md). They
are listed in the order the October 2026 research judge ranked them. If you want
quick wins first, the Drafts and Scheduled hub and Their local time are the
smallest.

Each section gives the pitch, where the idea comes from, how to build it in XGram,
its limits, the effort (S, M, L, XL) and the risks. Class names refer to the XGram
code at the time of writing; TDLib calls were checked against the bundled
`TdApi.java`. As with every XGram feature: Material 3 Expressive everywhere when
Expressive design is on, the classic Telegram X look when it is off, settings in
both settings pages and in the settings search, backed up, and erased on logout.

At the end, [Other ideas](#other-ideas-from-the-research) lists the rest of the
research in one line each.

---

## 1. Keyword and VIP alerts, even in muted chats

**Pitch.** Get a notification whenever anyone writes a word you care about (your
name, your company, "urgent", your city), even in groups and channels you muted.
People on your VIP list always come through.

**Source.** Slack "My keywords"; iOS Focus "Allowed people". Telegram can only
notify on @mentions in muted chats, and even that is a long-open request.

**How.**

- A new Keywords page in XGram's settings, also linked from Activity's settings
  and from Telegram X's notification settings: words, phrases or regular
  expressions, with case, whole-word and "ignore accents" options; a scope (all
  chats, chosen folders, channels, muted chats only); excluded chats; an optional
  sound per keyword; a VIP people list; quiet hours.
- One matcher shared with XGram's word filters (the regex code behind
  `XgramFilters` and `WordFiltersPage`). Those hide messages; this does the
  opposite.
- **Live**: hook `updateNewMessage` on the TDLib thread, as the Activity collector
  does. Match the text or caption, and the text from `XgramContentText` for polls
  and similar. On a hit, post a notification on its own "Keyword alerts" channel
  through `TdlibNotificationManager`, even when the chat is muted, with the
  matched word in bold. Add the hit to the Activity inbox under a new Keywords
  filter, and highlight it when the chat is opened.
- **In the background**: Telegram sends no push for muted chats. Unmuted chats
  ride the normal push. For muted ones, TDLib fetches all missed updates whenever
  any push wakes the app; on top of that, an optional WorkManager job (every 15
  minutes at most, built like Telegram X's existing `SyncTask`) wakes TDLib and
  runs one `searchMessages(chatList = null, query = keyword, ..., minDate =
  lastCheck)` per keyword. Results are checked again with the local matcher and
  de-duplicated by chat and message id. Cap the number of keywords (for example
  10) so the searches never cause `FLOOD_WAIT`. Telegram X's keep-alive
  background connection is the opt-in way to get alerts in real time.
- **VIP**: a message from a VIP person or chat bypasses XGram's local muting in
  `TdlibNotificationManager`. TDLib makes no notification at all for a chat that
  is muted on Telegram's side, so for those XGram posts its own notification from
  the `updateNewMessage` hook, like a keyword alert.

**Limits.** Background alerts can arrive up to about 15 minutes late unless the
keep-alive connection is on. Server search works on whole words, so regular
expressions only work live. Secret chats are matched live only (server search
does not cover them). The background job costs some battery.

**Effort.** M: the settings page, the shared matcher, the live hook, the
background worker with throttling, the notification channel, the Activity filter
and the highlight in chat.

**Risks.** Common words can flood the user with alerts: show how often a keyword
would have matched in the last week before saving it. When a chat is not muted,
do not post a second notification on top of Telegram's own; mark the normal one
instead. Searches must stay paced. Be honest in the settings about battery use
for real-time alerts.

---

## 2. Chat Lock with a hidden Locked folder and a secret code

**Pitch.** Lock single chats behind your fingerprint or a PIN. Locked chats move
into a "Locked chats" folder, and with a secret code even that folder disappears:
it only opens when you type the code into search.

**Source.** WhatsApp Chat Lock and secret code; Viber Hidden Chats. Telegram only
has a passcode for the whole app, and per-chat locks have been requested since
2019.

**How.** Most of the plumbing exists already.

1. Telegram X has a per-chat passcode (`ChatPasscode`: PIN, password, pattern,
   gesture or fingerprint, with a strong-biometrics flag), stored in TDLib's
   device-local `chat.clientData` and enforced by `TdlibUi.openChat` and
   `PasscodeController`. `Tdlib.canSetPasscode` (Tdlib.java, around line 6563)
   allows it only for secret chats. Allow it for every chat type.
2. Add a virtual "Locked chats (3)" row at the top of the chat list, or hide it
   completely. When hidden, it opens only when the secret code is typed exactly
   into chat search or universal search: no suggestion, no search history entry.
3. Take locked chats out of every list, with the same hidden-row hook in
   `TdlibChatListSlice` that Later uses: Main, Archive, folders, XGram filters,
   communities, the two-pane list, Activity, Later, Catch Up, the share sheet and
   Direct Share targets, launcher shortcuts, universal search, widgets, and
   (optionally) the unread badge.
4. Notifications of locked chats read "New message", with no sender and no
   preview. `TGChat` already hides the preview when a chat has a passcode.
5. Inside a locked chat: `FLAG_SECURE` (no screenshots, a blank recents
   thumbnail), an incognito keyboard (`IME_FLAG_NO_PERSONALIZED_LEARNING`), and
   relock when the screen turns off or the chat is left.
6. Back up the list of locked chats with `XgramBackup`, because `chat.clientData`
   lives only on this device. Back up a hash, never the PIN.
7. "Forgot code": unlock everything after the phone's own screen lock
   (`BiometricPrompt` with device credential), then ask for a new code.

**Limits.** The lock is per device, like WhatsApp: Telegram Desktop and other
phones still show the chat normally. The messages themselves are not encrypted
again; someone with a rooted phone can still read TDLib's database. This is
protection against a person holding your unlocked phone, and the settings should
say so.

**Effort.** M. Allowing the lock for all chats is S; the folder and the secret
code are S to M; the leak audit is M; settings and backup are S.

**Risks.** One forgotten leak path breaks the promise. Write a checklist of every
path in point 3, test each one, and re-run it whenever a new list or search
screen is added. Check how Telegram X versions the data in `chat.clientData`
(`CLIENT_DATA_VERSION`) before adding fields. Pinned locked chats, wearable
notifications and the What's New card must not reveal anything either.

---

## 3. Free on-device voice message transcripts

**Pitch.** Read any voice or video message as text, free and without a weekly
limit, and the audio never leaves your phone. Find what someone said later with
search.

**Source.** WhatsApp voice message transcripts (on the phone, 2024); iMessage
audio transcription. On Telegram this is a Premium feature; free accounts get
about 2 transcriptions a week. XGram's Transcribe uses TDLib's `recognizeSpeech`,
so it has the same limit today.

**How.**

1. Transcribe on the phone when `recognizeSpeech` fails with a Premium or flood
   error, in secret chats, or always when the user picks "Always on this phone".
2. Engines, cheapest first:
   - Bind to the vendor-neutral "Open Transcribe" AIDL contract
     (`org.opentranscribe.api`), which Scrib (F-Droid) and other apps implement.
     XGram only needs a small client; the user installs the engine.
   - Embed sherpa-onnx or whisper.cpp with a downloadable Moonshine or Whisper
     base model (about 75 to 150 MB). Decode the voice note with the Opus
     decoder Telegram X already ships for its voice player (`N.openOpusFile` /
     `N.readOpusFile`; opusfile gives 48 kHz PCM) and resample to 16 kHz mono.
   - ML Kit GenAI speech recognition, where the phone supports it (check the
     current ML Kit list; same `latest`-flavor-only rule as Gemini Nano in
     [catch-up.md](catch-up.md)).
3. Cache transcripts in XGram's local message history store, keyed by chat and
   message id, and show them under voice and video-note bubbles with the
   transcription card already in `XgramMessageActions`.
4. Optional: transcribe automatically on Wi-Fi or while charging; put the
   transcript in the notification ("Voice message: ..."); index transcripts in
   universal search (`XgramSearchIndex`).
5. Let the user pick the model per language. Urdu and Arabic need their own test.

**Limits.** Accuracy for Urdu and Arabic depends on the model. Models are large,
so they are downloaded, never bundled. Long voice notes take a while to
transcribe on the CPU; measure on the owner's phone before promising speed.

**Effort.** S for the Open Transcribe client and the fallback logic. M for a
built-in engine with a model download manager, auto-transcribe, search indexing
and the notification text.

**Risks.** Battery and storage, so keep auto-transcribe opt-in. Licenses: XGram
is GPL-3.0; check sherpa-onnx, whisper.cpp and every model license before
shipping, and only bind to Scrib through its contract instead of copying its code.

---

## 4. Drafts and Scheduled hub

**Pitch.** One screen with every unsent draft (including drafts typed on
Telegram Desktop), every scheduled message and every failed send, with Send now,
Reschedule and Discard.

**Source.** Slack "Drafts & sent". Telegram shows a draft only on its own chat
row and scheduled messages only inside each chat.

**How.**

- **Drafts**: `chat.draftMessage` across the chat lists, plus
  `ForumTopic.draftMessage` in forums, kept live by `updateChatDraftMessage`.
  Drafts sync through Telegram's cloud, so drafts from other devices are
  included. The Main and Archive lists must be fully loaded once (`loadChats`
  until it answers 404). Sorted by draft date. Actions: Open, Send now, Discard
  (`setChatDraftMessage(chatId, topicId, null)`).
- **Scheduled**: for each chat with `hasScheduledMessages`,
  `getChatScheduledMessages(chatId)`, showing the time and a badge for repeating
  messages (`repeatPeriod`). Actions: Send now (`editMessageSchedulingState(chatId,
  messageId, null)`), Reschedule (`editMessageSchedulingState` with a new
  `MessageSchedulingStateSendAtDate`), Edit (opens the chat's scheduled
  messages), Delete.
- **Failed**: `updateMessageSendFailed` while running, plus
  `searchChatMessages` with `SearchMessagesFilterFailedToSend` for each chat.
  Actions: Retry (`resendMessages`), Delete.
- **Where**: a third tab on the Later page ("Drafts and scheduled"), and
  optionally its own side menu row.

**Limits.** The first full load of the chat lists can take a moment on a big
account. Scheduled messages cost one request per chat that has any.

**Effort.** S to M: a list screen with sections, the data collection with live
updates, and the action sheet.

**Risks.** Other XGram features keep their own scheduled messages: Check In's
pending alert and Later's scheduled reminder copies in Saved Messages. Label
them, and never offer Send now on a Check In alert (it would send a false alarm
to the other person). Undo Send's waiting messages are not Telegram messages yet;
show them separately or not at all.

---

## 5. Their local time, plus a guard against sending at 3 AM

**Pitch.** The chat header shows "2:14 AM their time". When you are about to send
in the middle of their night, XGram offers to send it at 9:00 their time, or when
they come online.

**Source.** Slack (local time on profiles and in DMs, also shown when you
schedule a message); Discord dynamic timestamps. Telegram shows nobody's local
time, and users have asked for it on Telegram's suggestion board.

**How.**

- **Finding their time zone**, in this order, each result stored with its source:
  1. `userFullInfo.businessInfo.openingHours.timeZoneId` for Business accounts
     (exact).
  2. The country of their phone number, when the number is visible and the
     country has a single time zone (Pakistan, India, the UAE, Saudi Arabia, the
     UK, Germany and similar). libphonenumber is not in the project, so use a
     small table of calling codes to time zones; countries with several zones
     (US, Canada, Russia, Australia, Brazil and others) give no answer.
  3. A guess from activity: the quietest 6-hour window of the hours when they
     were seen active is taken as their night. Shown as "approx.". Note that
     `XgramLastSeen` keeps only the latest time per person, not a history, so
     this needs a new small per-person table of 24 hourly counters (UTC), fed
     from the same events (`updateUserStatus` online, typing, new messages) and
     used only after about two weeks of data.
  4. A manual "Set their time zone" per chat, stored locally, which always wins.
- **Display**: the header subtitle shows their time only when it differs from
  yours by an hour or more; the profile shows "Local time 2:14 AM (Asia/Karachi)"
  or "approx.".
- **Send guard**: when their local time is inside quiet hours (default 23:00 to
  07:00) and you tap Send, a sheet offers Send now, Send silently
  (`MessageSendOptions.disableNotification`), Send at 9:00 their time
  (`MessageSchedulingStateSendAtDate`) and Send when online
  (`MessageSchedulingStateSendWhenOnline`, private chats only, and only when
  their last seen is visible), plus "Don't ask in this chat". The guard runs
  before Undo Send's delay, and uses the composer's existing scheduling path.
- **Later, for groups**: "Members span 3 time zones, 4 are probably asleep".

**Limits.** Exact only for Business accounts, single-zone countries, and manual
choices. Phone numbers are often hidden; the activity guess fails for people who
hide their last seen, work at night or travel.

**Effort.** S to M: the resolver and its cache, the new activity counters, the
header and profile rows, the pre-send sheet in the composer, and the manual
override.

**Risks.** Wrong guesses annoy, so mark them "approx.", make the override one tap
away, and let the guard be turned off. Use `java.time` zone rules so daylight
saving is right. Everything is computed on this phone; nothing is shown to the
other person.

---

## Other ideas from the research

Not ranked, kept so nothing from the research is lost. Effort in brackets.

- **Events with RSVP in groups** (WhatsApp, Discord): a non-anonymous poll
  "Going / Maybe / Can't go" with a venue and a date entity, which other apps show
  as a normal poll; XGram draws an event card with a countdown, attendees, "Add to
  calendar" and a reminder. (M)
- **Read counters on group messages** (LINE, KakaoTalk): a "Read 7" pill on your
  own messages from `getMessageViewers`, which only works in groups up to about
  100 members and for messages up to 7 days old. (S to M)
- **Friends Map**: everyone sharing a live location with you on one map, from a
  bounded scan with `searchChatRecentLocationMessages`, plus "share with several
  chats at once". (M to L)
- **Vanish mode** (Messenger): your messages in a private chat are deleted for
  both sides once read and you leave the chat, with Telegram's 1-day auto-delete
  as a backstop. Tidiness, not secrecy, since clients that keep deleted messages
  still keep them. (M)
- **Smart notifications and digests** (iOS, Android 16): per chat or folder,
  "Instant", "Priority only" or "Digest", with digests at set times. TDLib makes no
  notifications for chats muted on Telegram's side, so this only works for chats
  XGram quiets locally. (M)
- **Focus schedule** (Slack, Teams): weekly quiet hours, VIPs who break through,
  urgent messages that repeat every 2 minutes for 20 minutes, a Quick Settings
  tile, and "While paused: 37 messages in 9 chats", which opens Catch Up. (M)
- **Unified inbox across accounts** (Beeper): one chat list for all logged-in
  accounts, each row with an account badge. Telegram X assumes one account per
  screen, so this is big. (L)
- **Screener** (HEY): first messages from people who are not contacts go to a
  "Requests" row and open in ghost mode, with Accept, Block and delete, or Always
  screen out. (S to M)
- **Command palette and keyboard shortcuts** (Superhuman): Ctrl+K to jump to any
  chat, action or setting, and single keys for the chat list. Useful on the
  unfolded Fold with a keyboard. (M)
- **Smart actions in messages** (Samsung Now nudge, iOS data detectors): ML Kit
  Entity Extraction turns dates, addresses, flight and tracking numbers into
  one-tap actions such as Add to calendar or Open in Maps. (M)
- **Ask your chats** (Slack AI, Gmail with Gemini): search by meaning and ask
  questions with cited answers, from an on-device embeddings index. Heavy on
  storage and battery. (XL)
- **Search the text in photos, plus search operators**: on-device OCR of photos
  and screenshots into a local full-text index, and `from:`, `in:`, `has:`,
  `before:` operators mapped to TDLib search parameters. ML Kit's text
  recognition does not read Arabic script, so Urdu screenshots need another
  engine. (L)
- **Threads in any group** (Slack, Discord): a thread panel built from reply
  chains, with Follow for new replies. (L)

Ideas from the research that are already built or folded into other features:
"Remind me about this message", snoozing chats and "seen, no reply" nudges are
part of Later; the daily recap of chosen channels is phase 2 of Catch Up.
