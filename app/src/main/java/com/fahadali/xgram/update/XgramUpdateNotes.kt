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
  /** "🧪 For testers: members of …": [emoji] "🧪", [title] "For testers", [description] the rest. */
  class Section(val emoji: String?, val title: String, val description: String?, val notes: List<Note>, val paragraphs: List<String>) {
    /** "✨ New": the screen's top features come from it. */
    val isNew: Boolean
      get() = title.equals("New", ignoreCase = true) || title.startsWith("New ", ignoreCase = true) || emoji?.contains('✨') == true
  }

  /**
   * One "• " line: "Popup menu (Message menu → Popup menu, off by default): a long press opens …" is the [title]
   * "Popup menu", [where] "Message menu → Popup menu · off by default" and the [detail] "a long press opens …".
   */
  class Note(val text: String) {
    val title: String
    val where: String?
    val detail: String?

    init {
      val colon = colonOutsideBrackets(text)
      val head = if (colon >= 0) text.substring(0, colon) else text
      detail = if (colon >= 0) text.substring(colon + 1).trim().ifEmpty { null } else null
      val open = head.indexOf('(')
      val close = if (open >= 0) closingBracket(head, open) else -1
      if (open >= 0 && close > open) {
        where = head.substring(open + 1, close).split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        title = (head.substring(0, open) + head.substring(close + 1)).replace(SPACES, " ").trim().ifEmpty { text }
      } else {
        where = null
        title = head.trim().ifEmpty { text }
      }
    }
  }

  /** The section of the top features: New, else the first one. */
  val topSection: Section?
    get() = sections.firstOrNull { it.isNew } ?: sections.firstOrNull()

  /** The top features: the first lines of [topSection]. */
  val topNotes: List<Note>
    get() = topSection?.notes?.take(TOP_COUNT) ?: emptyList()

  /** The lines of [section] that "See all changes" shows: all but the top features. */
  fun moreNotes(section: Section): List<Note> = if (section === topSection) section.notes.drop(TOP_COUNT) else section.notes

  /** Whether "See all changes" has anything to show. */
  val hasMore: Boolean
    get() = intro.isNotEmpty() || footer != null || sections.any { moreNotes(it).isNotEmpty() || it.paragraphs.isNotEmpty() }

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

    /** An icon for a top feature, from the words of its title (then the rest of it): a sparkle when nothing fits. */
    @DrawableRes
    @JvmStatic
    fun iconFor(note: Note): Int {
      val name = (note.title + " " + (note.where ?: "")).lowercase()
      ICONS.firstOrNull { it.first.containsMatchIn(name) }?.let { return it.second }
      val all = note.text.lowercase()
      ICONS.firstOrNull { it.first.containsMatchIn(all) }?.let { return it.second }
      return R.drawable.baseline_auto_awesome_24
    }
  }
}

private val SPACES = Regex("\\s+")

private val CAPTION_LABELS = arrayOf("Size:", "Commit:", "SHA-256:", "Source:")

/** Keywords of a top feature and their icon, the first match wins (so "Message menu" is a menu, not a chat). */
private val ICONS: List<Pair<Regex, Int>> = listOf(
  Regex("\\b(popup|pop-up|menu)") to R.drawable.baseline_more_vert_24,
  Regex("\\bbots?\\b") to R.drawable.deproko_baseline_bots_24,
  Regex("\\b(camera|lens)") to R.drawable.baseline_camera_alt_24,
  Regex("\\b(xdrive|drive|cloud)") to R.drawable.baseline_cloud_24,
  Regex("\\b(lock|passcode|auto-lock)") to R.drawable.baseline_lock_24,
  Regex("\\b(avatar|profile photo)") to R.drawable.baseline_account_circle_24,
  Regex("\\battach") to R.drawable.deproko_baseline_attach_26,
  Regex("\\bsearch") to R.drawable.baseline_search_24,
  Regex("\\bbookmark") to R.drawable.baseline_bookmark_24,
  Regex("\\bnotification") to R.drawable.baseline_notifications_24,
  Regex("\\b(theme|colou?r|icon|palette|wallpaper)") to R.drawable.baseline_palette_24,
  Regex("\\btranslat") to R.drawable.baseline_translate_24,
  Regex("\\bcalls?\\b") to R.drawable.baseline_call_24,
  Regex("\\bvideo") to R.drawable.baseline_videocam_24,
  Regex("\\b(voice|mic)") to R.drawable.baseline_mic_24,
  Regex("\\b(photo|image|picture|media)") to R.drawable.baseline_image_24,
  Regex("\\bfolder") to R.drawable.baseline_folder_24,
  Regex("\\b(emoji|sticker|reaction)") to R.drawable.baseline_emoticon_outline_24,
  Regex("\\b(schedul|remind)") to R.drawable.baseline_schedule_24,
  Regex("\\b(privacy|security|ghost)") to R.drawable.baseline_security_24,
  Regex("\\b(group|channel|forum|topic)") to R.drawable.baseline_forum_24,
  Regex("\\b(chat|message)") to R.drawable.baseline_chat_bubble_24,
  Regex("\\b(fix|crash|bug)") to R.drawable.baseline_bug_report_24,
  Regex("\\b(update|install)") to R.drawable.baseline_system_update_24
)

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
