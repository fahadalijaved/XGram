/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fahadali.xgram.update

import androidx.annotation.DrawableRes
import org.thunderdog.challegram.R

/**
 * The release notes as the update screen ([XgramUpdateScreen]) shows them. The bot's notes (tools/xgram/telegram,
 * posts/release_notes.example.html) are sections, a header line ("✨ New", "🛠️ Improved", "🐞 Fixed", "⚠️ Known
 * issues", "🧪 For testers: …") followed by "• " lines, then a closing line ("Based on Telegram X …"). Notes that came
 * in the APK's caption start with the caption's own lines (heading, file name, size, commit, SHA-256, source): those
 * are dropped. A text without "• " lines is kept whole as [body].
 */
class XgramUpdateNotes private constructor(
  /** Lines before the first section. */
  val intro: List<String>,
  val sections: List<Section>,
  /** The lines after the last section: "Based on Telegram X 0.29.1.1818. Take arm64 …". */
  val footer: String?,
  /** The whole text, when it has no "• " lines. */
  val body: String?
) {
  /** What a section is, from its title or emoji: the screen gives each its own icon, shape and colors. */
  enum class Kind { NEW, IMPROVED, FIXED, KNOWN_ISSUES, TESTERS, OTHER }

  /** "🧪 For testers: members of …": [emoji] "🧪", [title] "For testers", [description] the rest. */
  class Section(val emoji: String?, val title: String, val description: String?, val notes: List<Note>, val paragraphs: List<String>) {
    val kind: Kind = kindOf(emoji, title)

    /** "✨ New": the screen's top features come from it. */
    val isNew: Boolean
      get() = kind == Kind.NEW
  }

  /**
   * One "• " line. "Popup menu (Message menu → Popup menu, off by default): a long press opens …" has the [name]
   * "Popup menu", [where] "Message menu → Popup menu · off by default" and the [detail] "A long press opens …" (its
   * first letter capitalized). A line without a short "Name:" before its text ("Chats slide open more smoothly", or
   * one with brackets but no colon) has no name: it is all [body].
   */
  class Note(val text: String) {
    val name: String?
    val where: String?
    val detail: String?

    /** What the line says: the [detail] after a name, else the whole line. */
    val body: String
      get() = detail ?: capitalized(text)

    init {
      val colon = colonOutsideBrackets(text)
      val head = if (colon > 0) text.substring(0, colon).trim() else ""
      val rest = if (colon > 0) text.substring(colon + 1).trim() else ""
      var noteName: String? = null
      var noteWhere: String? = null
      if (head.isNotEmpty() && rest.isNotEmpty()) {
        val open = head.indexOf('(')
        val close = if (open >= 0) closingBracket(head, open) else -1
        val bracketed = open >= 0 && close > open
        val candidate = if (bracketed) (head.substring(0, open) + head.substring(close + 1)).replace(SPACES, " ").trim() else head
        // A sentence with a colon in it is not a name
        if (candidate.isNotEmpty() && candidate.length <= NAME_MAX) {
          noteName = candidate
          noteWhere = if (bracketed) {
            head.substring(open + 1, close).split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
          } else {
            null
          }
        }
      }
      name = noteName
      where = noteWhere
      detail = if (noteName != null) capitalized(rest) else null
    }
  }

  /** The section of the top features: New, else the first one. */
  val topSection: Section?
    get() = sections.firstOrNull { it.isNew } ?: sections.firstOrNull()

  /** The top features: the first lines of [topSection]. */
  val topNotes: List<Note>
    get() = topSection?.notes?.take(TOP_COUNT) ?: emptyList()

  /** Every "• " line of every section: "See all changes (14)". */
  val count: Int
    get() = sections.sumOf { it.notes.size }

  /** Whether "See all changes" shows more than the top features: other lines, headers' text, the intro or the footer. */
  val hasMore: Boolean
    get() = count > topNotes.size || intro.isNotEmpty() || footer != null ||
      sections.any { it.paragraphs.isNotEmpty() || it.description != null }

  companion object {
    const val TOP_COUNT = 3

    /** The notes of [text] (Found.changelog); empty for null. */
    @JvmStatic
    fun parse(text: String?): XgramUpdateNotes {
      val lines = (text ?: "").replace("\r\n", "\n").split('\n').map { it.trim() }
      val intro = ArrayList<String>()
      val sections = ArrayList<SectionBuilder>()
      val kept = ArrayList<String>() // the text without the caption's lines, for [body]
      var pending = ArrayList<String>() // lines after a list: the section's own, or the footer when no section follows
      var current: SectionBuilder? = null
      var started = false // a header or a "• " line was seen: the caption's lines come before
      var continues = false // the line before was a "• " line (or its continuation), without an empty line between
      for ((index, line) in lines.withIndex()) {
        if (line.isEmpty()) {
          continues = false
          if (kept.isNotEmpty() && kept.last().isNotEmpty()) {
            kept.add("")
          }
          continue
        }
        if (!started && isCaptionLine(line)) {
          continue
        }
        kept.add(line)
        val bullet = bulletText(line)
        if (bullet != null) {
          started = true
          var section = current
          if (section == null) {
            // "• " lines without a header: one section without a title
            intro.addAll(pending)
            pending = ArrayList()
            section = SectionBuilder(null, "", null)
            sections.add(section)
            current = section
          }
          section.paragraphs.addAll(pending)
          pending = ArrayList()
          section.notes.add(bullet)
          continues = true
          continue
        }
        if (isHeader(line, nextLine(lines, index))) {
          started = true
          val previous = current
          if (previous == null) {
            intro.addAll(pending)
          } else {
            previous.paragraphs.addAll(pending)
          }
          pending = ArrayList()
          val next = header(line)
          sections.add(next)
          current = next
          continues = false
          continue
        }
        val last = current
        if (continues && last != null && last.notes.isNotEmpty()) {
          // A long line broken in two: it belongs to the line above
          last.notes[last.notes.size - 1] = last.notes[last.notes.size - 1] + " " + line
          continue
        }
        pending.add(line)
      }
      if (sections.isEmpty()) {
        while (kept.isNotEmpty() && kept[kept.size - 1].isEmpty()) {
          kept.removeAt(kept.size - 1)
        }
        val body = kept.joinToString("\n").trim()
        return XgramUpdateNotes(emptyList(), emptyList(), null, body.ifEmpty { null })
      }
      return XgramUpdateNotes(intro.toList(), sections.map { it.build() }, pending.joinToString("\n").ifEmpty { null }, null)
    }

    /**
     * An icon for a line from its words: for a name (and where it is) the first keyword of the table that fits, so
     * "Message menu" is a menu, not a chat; else the keyword its text mentions first, so "Swipe … on a message to
     * reply, quote, translate" is a swipe. [fallback] when nothing fits.
     */
    @DrawableRes
    @JvmStatic
    fun iconFor(note: Note, @DrawableRes fallback: Int = R.drawable.baseline_auto_awesome_24): Int {
      val name = note.name
      if (name != null) {
        val head = (name + " " + (note.where ?: "")).lowercase()
        ICONS.firstOrNull { it.first.containsMatchIn(head) }?.let { return it.second }
      }
      return firstMentioned(note.body.lowercase()) ?: fallback
    }
  }
}

private val SPACES = Regex("\\s+")

/** A name longer than this ("Name: text") is a sentence with a colon in it. */
private const val NAME_MAX = 48

private val CAPTION_LABELS = arrayOf("Size:", "Commit:", "SHA-256:", "Source:")

/**
 * Keywords of a line and their icon, the specific ones first: for a name the first one that fits wins, for a text the
 * one it mentions first (see [XgramUpdateNotes.iconFor]).
 */
private val ICONS: List<Pair<Regex, Int>> = listOf(
  Regex("\\b(popup|pop-up|menus?)\\b") to R.drawable.baseline_more_vert_24,
  Regex("\\bbots?\\b") to R.drawable.deproko_baseline_bots_24,
  Regex("\\b(camera|lens|selfie)") to R.drawable.baseline_camera_alt_24,
  Regex("\\b(xdrive|drive|cloud|upload)") to R.drawable.baseline_cloud_24,
  Regex("\\b(lock|passcode|auto-lock|fingerprint)") to R.drawable.baseline_lock_24,
  Regex("\\b(login|log in|qr|password|two-step)") to R.drawable.baseline_vpn_key_24,
  Regex("\\b(avatar|profile|account)") to R.drawable.baseline_account_circle_24,
  Regex("\\battach") to R.drawable.deproko_baseline_attach_26,
  Regex("\\bsearch") to R.drawable.baseline_search_24,
  Regex("\\b(bookmark|saved\\b)") to R.drawable.baseline_bookmark_24,
  Regex("\\bquot") to R.drawable.baseline_format_quote_close_24,
  Regex("\\btranslat") to R.drawable.baseline_translate_24,
  Regex("\\b(swipe|gesture|long press|long-press)") to R.drawable.baseline_gesture_24,
  Regex("\\b(drag|reorder|rearrang)") to R.drawable.baseline_drag_handle_24,
  Regex("\\bstickers?\\b") to R.drawable.deproko_baseline_stickers_24,
  Regex("\\b(emoji|emoticon|reaction)") to R.drawable.baseline_emoticon_outline_24,
  Regex("\\bgifs?\\b") to R.drawable.deproko_baseline_gif_24,
  Regex("\\b(record|voice|mic|audio)") to R.drawable.baseline_mic_24,
  Regex("\\b(music|songs?\\b|player|playlist)") to R.drawable.baseline_music_note_24,
  Regex("\\b(download|boost)") to R.drawable.baseline_file_download_24,
  Regex("\\b(notification|notify)") to R.drawable.baseline_notifications_24,
  Regex("\\b(theme|colou?rs?\\b|icon pack|app icon|palette|wallpaper|accent)") to R.drawable.baseline_palette_24,
  Regex("\\b(font|text size|formatting)") to R.drawable.baseline_format_text_24,
  Regex("\\bcalls?\\b") to R.drawable.baseline_call_24,
  Regex("\\bvideo") to R.drawable.baseline_videocam_24,
  Regex("\\b(photo|image|picture|media|gallery)") to R.drawable.baseline_image_24,
  Regex("\\b(files?|documents?)\\b") to R.drawable.baseline_insert_drive_file_24,
  Regex("\\bfolder") to R.drawable.baseline_folder_24,
  Regex("\\barchiv") to R.drawable.baseline_archive_24,
  Regex("\\b(pin|pinned)\\b") to R.drawable.deproko_baseline_pin_24,
  Regex("\\b(ghost|read receipts?|unread|incognito)") to R.drawable.baseline_ghost_24,
  Regex("\\b(privacy|security|secret)") to R.drawable.baseline_security_24,
  Regex("\\b(delete|deleted|deleting)\\b") to R.drawable.baseline_delete_24,
  Regex("\\b(links?|urls?)\\b") to R.drawable.baseline_link_24,
  Regex("\\b(32-bit|64-bit|apks?\\b|arm64|arm32|armeabi)") to R.drawable.baseline_android_24,
  Regex("\\b(keyboard|typing|message box)") to R.drawable.baseline_keyboard_24,
  Regex("\\b(reply|replies)\\b") to R.drawable.baseline_reply_24,
  Regex("\\bforward") to R.drawable.baseline_forward_24,
  Regex("\\b(copy|copies)\\b") to R.drawable.baseline_content_copy_24,
  Regex("\\b(language|languages)\\b") to R.drawable.baseline_language_24,
  Regex("\\b(locations?|maps?)\\b") to R.drawable.baseline_location_on_24,
  Regex("\\bpolls?\\b") to R.drawable.baseline_poll_24,
  Regex("\\b(storage|cache)\\b") to R.drawable.baseline_storage_24,
  Regex("\\b(sessions?|devices?)\\b") to R.drawable.baseline_devices_other_24,
  Regex("\\b(schedul|remind)") to R.drawable.baseline_schedule_24,
  Regex("\\b(timer|auto-delete|self-destruct)") to R.drawable.baseline_auto_delete_24,
  Regex("\\bcontacts?\\b") to R.drawable.baseline_contact_phone_24,
  Regex("\\b(groups?|channels?|forums?|topics?)\\b") to R.drawable.baseline_forum_24,
  Regex("\\b(settings?|options?)\\b") to R.drawable.baseline_settings_24,
  Regex("\\b(chats?|messages?)\\b") to R.drawable.baseline_chat_bubble_24,
  Regex("\\b(fix|crash|bug)") to R.drawable.baseline_bug_report_24,
  Regex("\\b(update|install)") to R.drawable.baseline_system_update_24
)

/** The icon of the keyword [text] mentions first (two at the same place: the table's order), or null. */
private fun firstMentioned(text: String): Int? {
  var icon: Int? = null
  var at = Int.MAX_VALUE
  for ((regex, drawable) in ICONS) {
    val match = regex.find(text) ?: continue
    if (match.range.first < at) {
      at = match.range.first
      icon = drawable
    }
  }
  return icon
}

/** "select part of a message" → "Select part of a message". */
private fun capitalized(text: String): String =
  text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

/** A section's kind from its title ("Known issues") or, in another language, its emoji ("⚠️"). */
private fun kindOf(emoji: String?, title: String): XgramUpdateNotes.Kind {
  val name = title.lowercase()
  val symbol = emoji ?: ""
  return when {
    name == "new" || name.startsWith("new ") || symbol.contains('✨') -> XgramUpdateNotes.Kind.NEW
    name.startsWith("improv") || name.startsWith("better") || name.startsWith("changed") || symbol.contains("🛠") || symbol.contains("🔧") -> XgramUpdateNotes.Kind.IMPROVED
    name.startsWith("fix") || symbol.contains("🐞") || symbol.contains("🐛") -> XgramUpdateNotes.Kind.FIXED
    name.startsWith("known") || symbol.contains('⚠') -> XgramUpdateNotes.Kind.KNOWN_ISSUES
    name.contains("tester") || symbol.contains("🧪") -> XgramUpdateNotes.Kind.TESTERS
    else -> XgramUpdateNotes.Kind.OTHER
  }
}

private class SectionBuilder(val emoji: String?, val title: String, val description: String?) {
  val notes = ArrayList<String>()
  val paragraphs = ArrayList<String>()

  fun build() = XgramUpdateNotes.Section(emoji, title, description, notes.map { XgramUpdateNotes.Note(it) }, paragraphs.toList())
}

/** The text of a "• " line (or "- ", "* "), else null. */
private fun bulletText(line: String): String? {
  val first = line[0]
  if (first == '•' || first == '◦' || first == '▪' || first == '‣') {
    return line.substring(1).trim().ifEmpty { null }
  }
  if ((first == '-' || first == '*' || first == '–') && line.length > 1 && line[1] == ' ') {
    return line.substring(2).trim().ifEmpty { null }
  }
  return null
}

private fun nextLine(lines: List<String>, index: Int): String? {
  for (i in index + 1 until lines.size) {
    if (lines[i].isNotEmpty()) {
      return lines[i]
    }
  }
  return null
}

/** "🐞 Fixed" before a list: a line starting with a symbol (or a short one ending in ":") that a "• " line follows. */
private fun isHeader(line: String, next: String?): Boolean {
  if (next == null || bulletText(next) == null) {
    return false
  }
  return !Character.isLetterOrDigit(line.codePointAt(0)) || (line.endsWith(':') && line.length <= 48)
}

/** "🧪 For testers: members of …": the symbols before the first letter, the words up to ":" and the rest. */
private fun header(line: String): SectionBuilder {
  var start = 0
  while (start < line.length) {
    val codePoint = line.codePointAt(start)
    if (Character.isLetterOrDigit(codePoint)) {
      break
    }
    start += Character.charCount(codePoint)
  }
  val emoji = line.substring(0, start).trim().ifEmpty { null }
  val rest = line.substring(start)
  val colon = rest.indexOf(':')
  val title = (if (colon >= 0) rest.substring(0, colon) else rest).trim()
  val description = if (colon >= 0) rest.substring(colon + 1).trim().ifEmpty { null } else null
  return SectionBuilder(emoji, title.ifEmpty { line }, description)
}

/** The caption's own lines, when the notes came in the APK's caption (the bot's single-file post). */
private fun isCaptionLine(line: String): Boolean {
  if (CAPTION_LABELS.any { line.startsWith(it) }) {
    return true
  }
  if (line.endsWith(".apk", ignoreCase = true) && line.none { it.isWhitespace() }) {
    return true
  }
  if (line.endsWith(": what's new") || line.endsWith(": what’s new")) {
    return true
  }
  return line.contains("XGram ") && (line.contains(" · Stable") || line.contains(" · CI build"))
}

/** The first ":" outside brackets that isn't part of a link ("https://") or a time ("10:30"), or -1. */
private fun colonOutsideBrackets(text: String): Int {
  var depth = 0
  for (i in text.indices) {
    when (text[i]) {
      '(' -> depth++
      ')' -> if (depth > 0) depth--
      ':' -> {
        val next = text.getOrNull(i + 1)
        val previous = text.getOrNull(i - 1)
        val inLinkOrTime = next == '/' || (next != null && next.isDigit() && previous != null && previous.isDigit())
        if (depth == 0 && !inLinkOrTime) {
          return i
        }
      }
    }
  }
  return -1
}

private fun closingBracket(text: String, open: Int): Int {
  var depth = 0
  for (i in open until text.length) {
    when (text[i]) {
      '(' -> depth++
      ')' -> {
        depth--
        if (depth == 0) {
          return i
        }
      }
    }
  }
  return -1
}
