# XGram exclusive features

XGram is a Telegram X fork (app id `com.fahadali.xgram`). Besides the NagramX and
AyuGram features it carries, it has a small set of features of its own that no
other Telegram client offered when they were designed (October 2026). This page
says what each one does for the user, and what is planned next.

All of them work on this device only. Other people never need XGram: they see
ordinary Telegram messages, read receipts and scheduled messages, nothing more.

## What is built

### Activity inbox

One screen lists every @mention of you, every reply to your messages and every
reaction to your messages, from all your groups, newest first, in Today /
Yesterday / This week / Earlier sections. Chips filter it to Mentions, Replies,
Reactions or Unread, or to chosen chats. A tap opens the chat at that message and
highlights it (in the right pane on the unfolded Fold); a swipe clears a row, and
"Mark all as read" reads everything in one go. Items stay in the list even after
another device has read them, and XGram quietly searches for the mentions and
reactions it missed while it was closed, paced so Telegram never has to slow it
down. With ghost mode on, opening a row never sends a read receipt. Open it from
the bell in the chat list header, the side menu, the navigation rail, an optional
toolbar tab, or the classic bottom bar in place of Calls. Settings: Settings >
XGram > Chat list > Activity.

### Undo Send

For a few seconds after you tap Send, the message waits on your phone instead of
going to Telegram. A bar above the message box counts down with Undo and Send
now; Undo puts the text (or the files and caption) back into the message box.
Because nothing has reached Telegram yet, the other person gets no notification
and no copy, which "Delete for everyone" can never promise. Waiting messages go
out at once when you leave the chat, the app goes to the background or you send
something else, and after a crash they come back as the chat's draft. Choose Off,
3, 5, 10 or 20 seconds under Settings > XGram > Messages > Message box (off by
default); single chats can be excepted from the chat's menu, and voice and video
messages can wait too if you want. Waiting messages also show in the chat itself
as faded bubbles with a draining ring, under your newest message; tap one to undo
it or send it now, and when its time is up it turns solid in place. With
"Only the newest waits", sending a new message sends the one before it at once,
so only one countdown is ever running.

### Check In

Tell a chat "I'm heading home, back by 22:40". From the chat's menu (private
chats, groups and topics) you pick a timer or a time, with your location and
battery level or without, and optionally "End when I arrive at a place". XGram
then puts an alert into that chat as a Telegram scheduled message and keeps its
location and battery up to date while you are out. If you don't tap "I'm safe" in
time, Telegram's own server sends the alert, so it still arrives when your phone
is dead, switched off or stolen. You can add time or cancel from the notification,
and XGram asks "Are you OK?" before the deadline. It is best effort, not an
emergency service. Settings (default timer, alert delay, location, battery,
arrival radius): Settings > XGram > Messages.

### Later

Snooze a chat until tonight, tomorrow morning or a time you pick, or until
something happens: until they reply, until you are mentioned, or until a new post.
The chat leaves your lists and comes back on time with a bell, moved up under your
pinned chats. On any message, "Remind me..." brings it back at the time you choose,
with an optional note; on your own messages, "If no reply in 1 day / 3 days / 1
week" reminds you to follow up when nobody answered. The Later page (side menu,
after Bookmarks) shows Due, Snoozed chats, Reminders, Follow-ups and Done.
Reminders use exact alarms, survive reboots and are included in XGram's backup.
Settings: Settings > XGram > Chat list > Later.

### Topic icons from all your emoji packs

When you create or edit a forum topic, the icon picker lists Telegram's default
topic icons, then your recently used custom emoji, then every custom emoji pack
you have installed, in your own order, with a tab strip to jump between packs.
Telegram X itself only ever offered the default icons. Packs load as you scroll,
so even thousands of emoji scroll smoothly. A search bar under the topic name
finds icons by word or by emoji (type "cat" or a cat face) across the default
icons, all emoji and the matching packs. Custom emoji icons need Telegram
Premium; without it the packs show with a lock and explain why.

### Where the code lives

| Feature | Package or class |
| --- | --- |
| Activity inbox | `com.fahadali.xgram.activity` (`XgramActivity` is the entry point) |
| Undo Send | `com.fahadali.xgram.chats.XgramUndoSend*` |
| Check In | `com.fahadali.xgram.checkin` (`XgramCheckIn`) |
| Later | `com.fahadali.xgram.later` (`XgramLater`, `XgramLaterStore`, `XgramLaterPicker`) |
| Topic icons | `com.fahadali.xgram.topics.ForumTopicEditController` |

Every feature works with Expressive design (Material 3 Expressive everywhere) and
without it (the classic Telegram X look), keeps its settings in both settings
pages and in the settings search, and is erased from the device on logout.

## Roadmap: what is not built yet

1. **Catch Up** is next. Your unread chats become a deck of cards, each with a
   short recap, and you swipe through them: right to mark read, left to keep,
   up to snooze into Later. The recap works without AI on every phone (the
   messages that need you, questions, the most reacted messages), and can use
   Telegram's own AI, your own API key, or Gemini Nano on the phone. Full design:
   [catch-up.md](catch-up.md).
2. **The runners-up**, after Catch Up. Full notes: [runners-up.md](runners-up.md).
   - Keyword and VIP alerts, even in muted chats.
   - Chat Lock with a hidden Locked folder and a secret code.
   - Free on-device voice message transcripts.
   - A Drafts and Scheduled hub.
   - Their local time, plus a guard against sending at 3 AM.

`runners-up.md` also lists, in one line each, the other ideas from the research
that were not ranked, so nothing from it is lost.

## Where this came from

In October 2026 four researchers each proposed about ten features, taken from
WhatsApp, iMessage, Slack, Discord, Gmail, LINE and others, that no Telegram client
had. A judge merged the duplicates and picked a top 5 (Activity inbox, Catch Up,
Check In, Later, Undo Send) and five runners-up. Four of the top 5 are built; Catch
Up is the one left. The research notes themselves lived in temporary files, so
these pages are now the record.
