# Catch Up

**Status: not built.** It is the next XGram exclusive feature. This page is the
full design from the October 2026 research, written so that a developer can build
it without the original notes. Class names refer to the XGram code at the time of
writing; TDLib calls were checked against the bundled `TdApi.java`.

## 1. Pitch

Your unread chats become a deck of cards, each with a short recap of what you
missed. Swipe right to mark a chat read, left to keep it unread, up to snooze it
into Later. Thirty unread chats take two minutes, with one hand.

## 2. Background

- **Slack mobile "Catch up"** (January 2024) shows unread channels as a stack of
  cards: swipe one way to mark read, the other to keep unread, hold to save for
  later. Muted channels are left out.
- **WhatsApp Message Summaries** (June 2025): tap the unread counter in a busy
  chat and get a private bulleted summary. Nobody in the chat can see you asked.
- **Slack AI recaps**: summaries of unread messages and a daily recap of chosen
  channels, with links to the source messages.
- **Telegram** (AI Summaries, January 2026) only summarizes one long channel post
  or Instant View page at a time (`summarizeMessage`). Nothing in Telegram recaps
  200 unread group messages. XGram already offers that single-message Summarize
  through `XgramAiTools`.
- **Other forks**: iMe sells cloud AI digests; Cherrygram has Gemini with your
  own key. No Telegram client has a swipe deck of unread chats or an on-device
  recap.

Catch Up does not replace `XgramPullToNext` (pull at the end of a channel to go to
the next unread one). That stays as it is.

## 3. User experience

Everything below must be drawn in Material 3 Expressive when Expressive design is
on (any Telegram X look left over is a bug), and in the classic Telegram X look
when it is off.

### 3.1 Ways in

1. **Chat list header**: a "Catch up" button, shown only while the list on screen
   has at least one chat that would be in the deck. Content description "Catch
   up, 12 chats". It can be turned off in settings. The header already holds the
   ghost button, the Activity bell and search, so when there is no room the
   button moves into the header's menu.
2. **Activity screen**: a "Catch up" item in its app bar menu.
3. **Inside a chat**: a "Catch me up" chip on the unread divider
   (`XgramM3UnreadDivider` with Expressive design, Telegram X's own divider
   without it) when the chat has about 20 or more unread messages
   (setting). It opens a single card for this chat, recap only, as a bottom sheet.
   The divider is drawn on a canvas, so the chip needs its own hit area in the
   message view.
4. **Chat long press** (`XgramChatLongPress`): "Catch up" for one chat, same
   single-card sheet.
5. **What's New** card for the release that ships it.

### 3.2 The deck

- **Which chats**: the chat list on screen (Main, Archive or a folder), chats
  with `unreadCount > 0` or `isMarkedAsUnread`.
- **Left out by default**: muted chats (setting "Include muted chats"), chats
  snoozed in Later, chats hidden by XGram's filters, and chats the user excluded
  from Catch Up. Secret chats are included, but only with the no-AI Highlights
  recap (section 5).
- **Order**: the chat list's order, except that chats with an unread mention of
  you or reply to you come first (setting "Mentions and replies first", on).
- **Size**: at most 100 cards per run. A progress line reads "4 of 23".
- **Forum groups**: one card per group. The card lists the topics with unread
  messages and their counts; the recap covers the three busiest topics.
- **Changes while the deck is open**: a chat read on another device is marked
  "Read elsewhere" and skipped when it is reached. A new message in a card that
  is not shown yet simply refreshes it. A new message in the card on screen
  shows a "2 new" chip that refreshes the card when tapped.

### 3.3 The card

- **Header**: the chat's avatar (48dp, avatar shape setting), title, the folder
  or topic line, an unread count chip, and the time of the newest message.
- **Highlights strip**: small chips such as "2 mentions", "1 reply to you",
  "3 questions", "4 links", "2 files", "1 poll". Each chip filters the body to
  those messages.
- **Body**: either the **recap** (3 to 6 bullets; each bullet cites the messages
  it came from as [1], [2]; a tap on a citation opens the chat at that message)
  or the **messages** (the last 8 unread messages, sender and 1 to 2 lines each).
  A segmented button switches between Recap and Messages; the choice is
  remembered.
- **Footer**: which backend made the recap ("Highlights", "Telegram AI", "Your
  key: <provider>", "On this phone"), with an info button that explains where
  the text went. "Try another" re-runs the recap with the next backend.
- **Buttons** under the card, for people who don't swipe and for TalkBack: Keep
  unread, Later, Open, Mark read.
- **Stack**: the next two cards peek behind the top one (scaled to 0.95 and
  0.90).
- **Expressive on**: the card is a `surfaceContainerHigh` tile with extra large
  corners, M3 chips and a segmented button, filled-tonal buttons, and the M3E
  spring motion used elsewhere in XGram (`EFFECTS_*` / `SPATIAL_*`).
- **Expressive off**: a card on `ColorId.filling` with Telegram X fonts, icons and
  plain buttons; nothing M3.

### 3.4 Gestures

| Gesture | Effect |
| --- | --- |
| Swipe right | Mark the chat read (section 4.5). |
| Swipe left | Keep it unread. Nothing is sent to Telegram. |
| Swipe up | Snooze into Later: opens Later's snooze picker for this chat. |
| Tap | Open the chat at the first unread message. Back returns to the deck at the same card. |
| Long press | Menu: Open, Mark read, Later, Mute, Recap with another backend, Leave out of Catch Up. |

- The card follows the finger and flies off with a spring past the threshold,
  with a haptic tick at the threshold. With Reduce motion it fades instead.
- After every swipe an Undo snackbar (`XgramSnackbar`) shows for 5 seconds. Undo
  brings the card back. For a chat that was marked read on the server, Undo calls
  `toggleChatIsMarkedAsUnread(chatId, true)`, because TDLib cannot move the read
  pointer back; the chat then shows as unread everywhere, but the exact position
  of the last read message is lost. Read on this device only (ghost mode) is
  undone exactly.
- Every gesture is also a TalkBack custom action on the card.

### 3.5 Panes on the Fold

- **Folded, or one pane**: the deck is a full screen; a tap pushes the chat over
  it.
- **Unfolded, two panes** (`XgramTwoPane`): the deck sits in the left pane and the
  chat opens in the right pane, so you can peek and keep swiping.
- Folding or unfolding keeps the deck and its position, like the Activity screen.

### 3.6 End of the deck

"You're all caught up", with a short tally ("18 read, 6 kept unread, 3 snoozed")
and two buttons: Done, and "Go through the kept chats again".

### 3.7 Ghost mode

- Building the deck never reads anything: history is fetched with
  `getChatHistory`, which does not mark messages read, and `openChat` or
  `viewMessages` are never called while cards are loading.
- When ghost mode does not read messages in a chat
  (`XgramGhost.dontReadMessages(tdlib, chatId)`), a swipe right reads it on this
  device only through `XgramLocalRead`, and the card says "Read on this phone
  only". This is decided per chat at the moment of the swipe.

### 3.8 Settings

A "Catch Up" group in Settings > XGram > Chat list, in both settings pages, found
by the settings search (catch up, recap, summary, digest), and backed up (except
API keys):

- Button in the chat list header (on)
- Include muted chats (off)
- Mentions and replies first (on)
- "Catch me up" in chats from N unread messages (20; off, 10, 20, 50)
- Recap: Messages only / Highlights / Telegram AI / Your API key / On this phone
- Fall back to the next recap automatically (on)
- Your API key: provider (OpenAI-compatible or Gemini), base URL, model, key
- Recap language: the chat's language, or XGram's language
- A privacy note that says, for the chosen backend, who sees the chat text

## 4. Mechanics

### 4.1 Building the deck

1. Take the chats of the chat list on screen from the chats TDLib already has.
   Unread chats can be deep in the list, so when the list is not fully loaded,
   call `loadChats(chatList, 100)` in pages while the deck builds, until TDLib
   answers 404 (everything loaded) or 500 chats were seen.
2. Keep a chat when `chat.unreadCount > 0 || chat.isMarkedAsUnread`, and drop it
   when it is muted (`!tdlib.chatNotificationsEnabled(chat)`, unless muted chats
   are included), snoozed (`XgramLater.isChatHidden`), hidden by XGram's
   filters, or excluded from Catch Up (a per-account set in XGram prefs).
3. Sort as in section 3.2. A chat with `unreadMentionCount > 0` or an unread
   reply to you (Activity's store already knows these) goes first.
4. Listen to `updateChatReadInbox`, `updateNewMessage`, `updateChatLastMessage`
   and `updateChatIsMarkedAsUnread` while the deck is open, as described in
   section 3.2.

### 4.2 Loading a card's messages

- Load lazily: only the card on screen and the next two.
- Page backwards from the newest message:
  `getChatHistory(chatId, fromMessageId, offset = 0, limit = 100, onlyLocal)`,
  starting with `fromMessageId = 0` and then the oldest id received, until a
  message with `id <= chat.lastReadInboxMessageId` appears, the page is empty, or
  the cap is reached. Try `onlyLocal = true` first, then repeat the missing part
  with `onlyLocal = false`. TDLib may return fewer than `limit` messages; keep
  paging.
- Cap per card: 300 messages or about 60,000 characters of transcript. Beyond
  the cap the card says "and 1,240 earlier messages".
- Forum topics: `getForumTopics` gives each topic's unread count; load the three
  busiest with `getForumTopicHistory(chatId, forumTopicId, fromMessageId, offset,
  limit)` in the same way.
- Requests go one chat at a time, about 300 ms apart. On `FLOOD_WAIT` the deck
  stops loading, shows the card's plain message list where it has one, and
  retries after the wait, the same way Activity's paced search does.

### 4.3 The transcript

- One numbered line per message: `[n] Name: text`. The name is the sender's
  first name (the chat title for channel posts), "You" for your own messages.
- Text and captions as they are. Media without text goes through
  `XgramContentText.describe(tdlib, content)` ("Photo", "Voice message",
  "Poll: <question>"). A voice note adds its transcript when one already exists.
- Forwards add "(forwarded from X)". Replies add "(reply to [m])" when the
  target is in the transcript, otherwise "(reply to Name)".
- Joins, leaves and similar service messages collapse into one line ("3 people
  joined").
- Each message is cut at 500 characters. Right-to-left text is kept as is.
- A table maps `n` to the message id (and topic), so citations can open the
  message.

### 4.4 Highlights (no AI)

Picked from the transcript, at most 5 lines, each a quoted message with its
citation:

- **Mentions of you**: a `TextEntityTypeMentionName` with your user id, a
  `TextEntityTypeMention` with one of your usernames, or `containsUnreadMention`.
  Reuse the classifier in `com.fahadali.xgram.activity` rather than writing a
  second one.
- **Replies to you**: `replyTo` pointing at one of your messages.
- **Questions**: messages from others that end with "?" or the Arabic question
  mark "؟" (U+061F, also used in Urdu) and that you did not answer later in the
  transcript. At most 3.
- **Most reacted**: the 2 messages with the highest total of
  `interactionInfo.reactions`, if at least 3.
- **Pinned**: messages pinned during the unread stretch.
- **Counts** for the chip row: links (`TextEntityTypeUrl` / `TextEntityTypeTextUrl`),
  photos and videos, files, voice notes and polls.

### 4.5 Marking read

- Normal case: do what Telegram X's own "Mark as read" in the chat list does,
  `tdlib.markChatAsRead(chatId, new TdApi.MessageSourceChatList(), true, after)`,
  which sends `viewMessages(chatId, [lastMessage.id], source, forceRead = true)`
  and clears `isMarkedAsUnread`. Then `readAllChatMentions(chatId)` and
  `readAllChatReactions(chatId)` (for forums, `readAllForumTopicMentions` and
  `readAllForumTopicReactions` per topic) so the @ and heart badges go too.
- Ghost mode for this chat: read through `XgramLocalRead` only, as in 3.7.
- Keep all of this in one small class (like `XgramActivityRead` for Activity), so
  there is exactly one place in Catch Up that tells Telegram something was read.
- Undo: `toggleChatIsMarkedAsUnread(chatId, true)`, see 3.4.

### 4.6 Snooze and open

- Swipe up calls Later's picker for one chat
  (`XgramLaterPicker.showForChats(c, tdlib, new long[] {chatId}, chatList, after)`)
  and removes the card when a snooze was set.
- Tap opens the chat with Telegram X's usual open parameters, highlighting the
  first unread message (the message after `lastReadInboxMessageId`), with
  `keepStack()` so Back returns to the deck.

## 5. Recap backends

All backends sit behind one small interface, so the card does not care which one
answered:

```
interface RecapBackend {
  int availability();                    // ready, needs download, needs setup, unavailable
  void recap(Transcript t, String lang, RecapCallback cb);   // bullets with [n] citations
  void cancel();
}
```

The chain runs in this order, skipping a backend that is off or unavailable:
the one the user chose, then (if "fall back" is on) Highlights. Recaps are cached
per account by `(chatId, last message id, backend)`, in memory and in a small
LevelDB table, so swiping back and forth costs nothing.

Rules for every backend:

- **Secret chats never leave the phone**: only Highlights and On this phone.
- A cloud backend is used only after the user turned it on and read its privacy
  note.
- At most 6 bullets are shown. For the backends that cite (Telegram AI and the
  user's key), a bullet that cites no valid [n] is dropped; Gemini Nano is cited
  per chunk (5.4).

### 5.1 Highlights, no AI (default, every phone)

Section 4.4. Free, instant, offline and private. It is the fallback for every
other backend.

### 5.2 Telegram's own AI (no key needed)

- Telegram's AI is made for rewriting text, not for summarizing chats, so this
  route needs a short spike before it is relied on.
- **First try**: `composeRichMessageWithAi(message, translateToLanguageCode = "",
  styleName = "", customPrompt, addEmojis = false)`. The message is an
  `InputRichMessage` whose source is `RichMessageSourceBlocks` with one
  `InputPageBlockParagraph` per transcript line (the Markdown and HTML sources
  are for bots only). The custom prompt is something like "Summarize this chat
  as 3 to 6 short bullets. After each bullet, cite the line numbers it comes
  from as [n]. Answer in <language>." It may be at most
  `text_composition_style_prompt_length_max` characters, and the whole message
  at most `rich_message_text_length_max`. Flatten the returned `RichMessage`
  blocks to text.
- **Second try**: create a hidden style once with
  `createTextCompositionStyle(title, customEmojiId = 0, prompt, showCreator =
  false)` and call `composeTextWithAi(text, "", styleName, false)`, which is the
  call `XgramAiTools` already uses. Downsides: the style probably appears in the
  user's style list on every Telegram app (hide it from XGram's own AI editor
  list), and creating it can fail with `TONES_SAVED_TOO_MANY`.
- Long transcripts are split into chunks that fit the limit; each chunk is
  summarized, and the chunk summaries are summarized once more.
- Limits: a daily free amount, more with Premium. Handle `AICOMPOSE_FLOOD_PREMIUM`
  the way `XgramAiTools.showError` does (an explanation, then fall back to
  Highlights). Must not be used in secret chats (TDLib says so for
  `composeTextWithAi`).
- Privacy: the text goes to Telegram, which already stores these cloud chats, so
  this costs less privacy than a third-party service. Still opt-in.

### 5.3 The user's own API key

- Providers: any OpenAI-compatible endpoint (base URL, model, key) or the Gemini
  API. Share one small HTTP client (`chat/completions` for OpenAI-compatible
  servers) with the LLM translator planned as item 21 of the owner's backlog
  ("More translation providers + LLM translator"), rather than adding a second
  one.
- The key is stored encrypted with the Android Keystore, never backed up and
  never logged. A "Test" button sends a one-line request.
- Chunking as in 5.2, with the prompt as a system message and the transcript as
  the user message.
- Privacy note: "Chat text is sent to <provider> under your own account."

### 5.4 Gemini Nano on the phone

- **API**: ML Kit GenAI Summarization (Gemini Nano running in Android's AICore).
  The client is `Summarization.getClient(SummarizerOptions)` with input type
  CONVERSATION and output of up to three bullets. Call `checkFeatureStatus()`
  first: it answers unavailable, downloadable, downloading or available. Call
  `downloadFeature(callback)` when it is downloadable (the model is downloaded by
  the system, not by XGram, and the card shows the progress), then
  `runInference(request, streamingCallback)` and `close()` when done.
- **Build**: add the `com.google.mlkit:genai-summarization` dependency only to the
  `latest` flavor (minSdk 33; ML Kit GenAI needs at least 26), as
  `"latestImplementation"(...)`. Do not use the four-argument
  `flavorImplementation(legacy, lollipop, marshmallow, latest)` helper in
  `buildSrc/.../Extensions.kt`: its `latest` slot also feeds the `tens` (minSdk
  24) and `qrs` flavors. Keep the `RecapBackend` interface in `src/main`, put the
  class that touches ML Kit in `app/src/onlyLatest/java` (a folder that
  `findExtraFolders` already adds to the `latest` build only), and load it by
  name with `Class.forName` (with a keep rule in `app/proguard-rules.pro`); when
  the class is missing (every other flavor) the backend reports "unavailable".
  Check the ML Kit GenAI page for the current version; it was a beta in 2025.
- **Limits**: about 4,000 tokens of input per request (chunk as in 5.2), and at
  most three bullets per call. Summarization supports English, Japanese and
  Korean only. For other languages (detect with `LanguageDetector`), try the ML
  Kit GenAI Prompt API if it supports the language, or fall back to the next
  backend. Translating the transcript first with XGram's translator would send
  the text to a translation service, so that is not "on this phone" any more and
  is never done for secret chats. Inference runs only while the app is in the
  foreground, which is fine because recaps are made when the deck is open. The
  system can also refuse a request when it is busy or the app used too much of
  its quota; treat that as "unavailable for now".
- **Citations**: Nano's bullets do not cite messages. Split the transcript by
  time into at most 6 chunks, ask for one bullet per chunk, and cite each bullet
  with its chunk's first message.
- **Privacy**: nothing leaves the phone. Allowed for secret chats.
- **The owner's phone**: a Galaxy Z Fold, model SM-F976B, with a Snapdragon 8
  Elite Gen 5 and Android 17. An earlier research note mistook this model number
  for the 2020 Fold2 with a Snapdragon 865 and said Gemini Nano was not available;
  that was wrong. Google lists recent Galaxy flagships (S25, Z Fold7 and later)
  among the supported devices, so Gemini Nano is **likely available** on this
  phone, but it has to be checked:
  1. Settings > Apps (show system apps): look for "AICore", or run
     `adb shell pm list packages | findstr aicore` (a read-only check).
  2. In a debug build, log the result of `checkFeatureStatus()` under the tag
     `XgramCatchUp` once at start-up, then run one recap of a fixed English test
     transcript and log the time it took.
  3. If the status is "unavailable", check that AICore and Google Play services
     are up to date, then decide whether to wait for Samsung and Google or drop
     this backend for now. The deck must work fully without it.

## 6. Limits

- Telegram has no server call that summarizes a chat; `summarizeMessage` only
  handles one long post. Every recap is built by XGram from the history.
- Telegram's AI route is unproven for summaries, has a capped prompt and input
  length, and a daily quota without Premium.
- Gemini Nano depends on the phone, summarizes only English, Japanese and Korean,
  and takes about 4,000 tokens per request.
- TDLib cannot move the read pointer back: "keep unread" changes nothing, and
  Undo after a read marks the whole chat unread instead of restoring the exact
  position.
- Very long backlogs are recapped from the newest 300 messages only.
- Secret chats never go to a cloud backend.

## 7. Effort

L in total, shipped in two steps:

1. **Step 1, M**: the deck, the cards, the gestures and Undo, the Highlights
   recap, the panes, ghost mode, settings. This works on every phone and is
   already a complete feature.
2. **Step 2, M**: the backend interface, Telegram AI (after its spike), the
   user's key, Gemini Nano, chunking, the cache, the privacy notes and the
   in-chat "Catch me up" chip.

A possible **phase 2** (M): a daily recap. The user picks channels and groups
("Add to recap" in the chat long press) and a time; a WorkManager job builds a
recap page with one section per chat (bullets, the top posts by reactions and
views, links and files) and posts one notification ("Your recap: 7 chats") that
opens it, with Open, Mark read and Remove from recap. On-device backends need the
app in the foreground, so the job prepares the transcripts and the recap is made
when the page opens, unless a cloud backend is chosen.

## 8. Risks

- **Summary quality** on mixed Arabic, Urdu and English chats. Test with real
  chats before choosing the default backend.
- **Privacy**: cloud backends send chat text off the phone. They are opt-in per
  backend, each with a clear note, and never used for secret chats.
- **Requests**: loading history for many chats costs requests. Load only the
  visible cards and pace them; stop at `FLOOD_WAIT`.
- **Ghost mode**: the deck must never send a read receipt where ghost mode does
  not read messages. Audit every path that calls `viewMessages`.
- **Gesture conflicts**: horizontal swipes on the deck must not open the side menu
  or switch chat list pages, and vertical swipes must not scroll the page under
  it.
- **Geometry**: the card stack, the citation spans and the chips must be checked
  for clipping and overlap at every width, in both looks and with RTL, before the
  first test build.

## 9. Test plan

1. **Deck contents**: a folder with private chats, groups, a forum, a channel, a
   muted group, a snoozed chat and a secret chat. The deck holds the right chats,
   in the right order, with mentions first; muted chats appear only with the
   setting on.
2. **Swipe right** marks the chat read on the server: check on Telegram Desktop
   that the unread count, the @ badge and the heart badge are gone.
3. **Swipe left** changes nothing on the server.
4. **Undo** after a read: the chat shows as unread on this phone and on Desktop.
5. **Ghost mode on**: build the deck and swipe right; Desktop must still show the
   messages as unread for the sender (no read ticks), while this phone shows the
   chat as read.
6. **Swipe up** opens Later's picker; the chat disappears from the list and comes
   back at the chosen time.
7. **Tap** opens the chat at the first unread message; Back returns to the same
   card. Unfolded, the chat opens in the right pane and the deck stays.
8. **Fold and unfold** in the middle of the deck: same card, nothing reloads.
9. **Highlights**: a prepared group with a mention, a reply to you, a question in
   English and one in Urdu with "؟", a message with 5 reactions and a link. Each
   appears with the right citation, and a citation tap opens that message.
10. **Long backlog**: a group with 1,500 unread messages; the card loads within a
    few seconds, uses at most 300 messages, and says how many earlier messages
    it skipped.
11. **Telegram AI**: a free account until `AICOMPOSE_FLOOD_PREMIUM`; the card
    explains the limit and falls back to Highlights.
12. **Own key**: a wrong key, no network and a timeout each give a clear message
    and fall back. The key is not in the backup file.
13. **Gemini Nano** on the owner's Fold: the status is logged, the model downloads
    with visible progress, an English chat gets a recap, an Arabic chat falls back
    as configured.
14. **Secret chat**: only Highlights or On this phone are ever used, whatever the
    setting says.
15. **Read elsewhere**: read a chat on Desktop while the deck is open; its card is
    marked and skipped.
16. **Flood wait**: simulate one in a debug build; loading pauses and resumes
    after the wait, without lost cards.
17. **Expressive on and off**: every part (button, card, chips, snackbar, sheet,
    end screen) in the right look, light and dark, with RTL, with Reduce motion
    and with TalkBack (every gesture available as an action).
18. **Account switch and logout**: the deck closes on switch; logout erases the
    recap cache and the excluded chats of that account.
