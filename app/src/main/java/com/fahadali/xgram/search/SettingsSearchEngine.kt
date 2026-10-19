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
package com.fahadali.xgram.search

import android.util.Log
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramSearchHit
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramR5Lists
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController

/**
 * The settings search: one index of every Telegram X settings page and row ([TgxSearchIndex]) and every XGram row
 * ([XgramSearchIndex]), built when a search page opens (titles in the app language, no TDLib requests), and the
 * ranking, which runs on every keystroke over the pre-normalized texts only.
 */
class SearchIndex internal constructor(
  val origin: Int,
  val entries: List<SearchEntry>,
  private val byKey: HashMap<String, SearchEntry>,
  /** The pages shown before anything is typed. */
  val shortcuts: List<SearchEntry>,
  internal val recentKeys: Set<String>
) {
  fun find(key: String): SearchEntry? = byKey[key]

  /** The results opened last that are still in the index, newest first. */
  fun recents(): List<SearchEntry> {
    val keys = XgramSettings.instance().searchRecents()
    val list = ArrayList<SearchEntry>(keys.size)
    for (key in keys) {
      val e = byKey[key]
      if (e != null) {
        list.add(e)
      }
    }
    return list
  }
}

object SettingsSearchEngine {
  private const val TAG = "XGram"
  private const val MAX_HITS = 60

  // Scores: the best rule that matches (baseScore), then the bonuses.
  private const val SCORE_EQUALS = 1000
  private const val SCORE_STARTS = 850
  private const val SCORE_WORD_PREFIXES = 700
  private const val SCORE_IN_ORDER = 30
  private const val SCORE_CONTAINS = 450
  private const val SCORE_TITLE_AND_KEYWORDS = 320
  private const val SCORE_KEYWORDS = 260
  private const val SCORE_PATH = 120
  private const val BONUS_PAGE = 60
  private const val BONUS_ORIGIN = 40
  private const val BONUS_RECENT = 25
  private const val BONUS_TOGGLE = 10

  private const val FSI = '\u2068'
  private const val PDI = '\u2069'
  private const val DROP = '\u0000'

  /** The pages the main Settings search shows before anything is typed, in Settings' order (TgxSearchIndex keys). */
  private val TGX_SHORTCUTS = arrayOf(
    "t:settings/notifications", "t:settings/data", "t:settings/privacy", "t:settings/stickers", "t:settings/themes",
    "t:settings/interface", "t:settings/folders", "t:settings/language", "t:settings/devices", "t:settings/xgram"
  )

  /**
   * Builds the index for a search page of [from]'s account. Main thread: titles come from the language pack, the XGram
   * rows from XGram's own page lists ([xgramHits]: the classic XGram page's own index; null reads them here).
   */
  @JvmStatic
  @JvmOverloads
  fun build(from: ViewController<*>, origin: Int, xgramHits: List<XgramSearchHit>? = null): SearchIndex {
    val tdlib = from.tdlib()
    val env = SearchEnv(tdlib, XgramExpressive.isEnabled())
    val builder = Builder(origin, Lang.rtl())
    val hits = xgramHits ?: try {
      XgramComposeBridge.searchIndex(from.context(), tdlib)
    } catch (t: Throwable) {
      Log.e(TAG, "settings search: cannot read XGram's rows", t)
      emptyList()
    }
    if (origin == SettingsSearch.ORIGIN_XGRAM) {
      builder.addXgram(hits, env)
      addTgx(builder, env)
    } else {
      addTgx(builder, env)
      builder.addXgram(hits, env)
    }
    val shortcuts = ArrayList<SearchEntry>()
    if (origin == SettingsSearch.ORIGIN_XGRAM) {
      for (hit in XgramComposeBridge.searchShortcuts()) {
        shortcuts.add(builder.xgramEntry("x:page/" + hit.section, hit, hit.title, hit.path, isPage = true, kw = XgramSearchIndex.NONE, order = -1))
      }
    } else {
      for (key in TGX_SHORTCUTS) {
        builder.byKey[key]?.let { shortcuts.add(it) }
      }
    }
    val recent = HashSet(XgramSettings.instance().searchRecents())
    return SearchIndex(origin, builder.entries, builder.byKey, shortcuts, recent)
  }

  /** Telegram X's part; without it (a registry that fails to build) XGram's rows are still found. */
  private fun addTgx(builder: Builder, env: SearchEnv) {
    try {
      builder.addTgx(env)
    } catch (t: Throwable) {
      Log.e(TAG, "settings search: cannot read Telegram X's rows", t)
    }
  }

  /** The entries that match [query], best first; at most 60. */
  @JvmStatic
  fun search(index: SearchIndex, query: String): List<SearchHit> {
    val q = Query.of(query) ?: return emptyList()
    val scored = ArrayList<Scored>()
    for (e in index.entries) {
      val base = baseScore(e, q)
      if (base <= 0) {
        continue
      }
      var score = base
      if (e.isPage) score += BONUS_PAGE
      if (e.isXgram == (index.origin == SettingsSearch.ORIGIN_XGRAM)) score += BONUS_ORIGIN
      if (index.recentKeys.contains(e.key)) score += BONUS_RECENT
      if (e.toggle != null) score += BONUS_TOGGLE
      score += 20 - minOf(20, e.title.length / 2)
      scored.add(Scored(e, score))
    }
    scored.sortWith(Comparator { a, b -> if (a.score != b.score) b.score.compareTo(a.score) else a.entry.order.compareTo(b.entry.order) })
    val count = minOf(scored.size, MAX_HITS)
    val result = ArrayList<SearchHit>(count)
    for (i in 0 until count) {
      val s = scored[i]
      result.add(SearchHit(s.entry, s.score, rangesOf(s.entry, q)))
    }
    return result
  }

  private class Scored(val entry: SearchEntry, val score: Int)

  // Text

  /**
   * The text as the search compares it: lower case, accents and other combining marks dropped, Arabic letter forms
   * folded (alef forms, teh marbuta, alef maksura; tatweel dropped), punctuation and '&' as spaces, single spaces.
   * [NormText.map] gives, per character, its index in [s], so a match is highlighted at the right place.
   */
  @JvmStatic
  fun normalize(s: CharSequence): NormText {
    val out = StringBuilder(s.length)
    val map = IntArray(s.length)
    var lastSpace = true
    for (i in 0 until s.length) {
      val c = fold(s[i])
      if (c == DROP) {
        continue
      }
      if (c == ' ') {
        if (!lastSpace) {
          map[out.length] = i
          out.append(' ')
          lastSpace = true
        }
        continue
      }
      map[out.length] = i
      out.append(c)
      lastSpace = false
    }
    var length = out.length
    if (length > 0 && out[length - 1] == ' ') {
      length--
      out.setLength(length)
    }
    return NormText(out.toString(), map.copyOf(length))
  }

  private fun fold(ch: Char): Char {
    if (ch.code < 0x80) {
      return when (ch) {
        in 'A'..'Z' -> (ch.code + 32).toChar()
        in 'a'..'z', in '0'..'9', '@', '#', '+' -> ch
        else -> ' '
      }
    }
    when (ch) {
      '\u0623', '\u0625', '\u0622', '\u0671' -> return '\u0627' // alef forms
      '\u0629' -> return '\u0647' // teh marbuta
      '\u0649' -> return '\u064A' // alef maksura
      '\u0640', '\u200E', '\u200F', FSI, PDI, '\u2066', '\u2067', '\u200B', '\u200C', '\u200D', '\u00AD' -> return DROP
      '\u00A0' -> return ' '
    }
    val type = Character.getType(ch)
    if (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()) {
      return DROP
    }
    if (!Character.isLetterOrDigit(ch)) {
      return ' '
    }
    val lower = Character.toLowerCase(ch)
    val decomposed = java.text.Normalizer.normalize(lower.toString(), java.text.Normalizer.Form.NFD)
    if (decomposed.length == 1) {
      return decomposed[0]
    }
    val base = decomposed[0]
    if (Character.getType(base) == Character.NON_SPACING_MARK.toInt()) {
      return lower
    }
    // The base letter only when the rest are marks (é: e + acute); a Hangul syllable gives jamo letters, kept whole
    for (k in 1 until decomposed.length) {
      val t = Character.getType(decomposed[k])
      if (t != Character.NON_SPACING_MARK.toInt() && t != Character.COMBINING_SPACING_MARK.toInt() && t != Character.ENCLOSING_MARK.toInt()) {
        return lower
      }
    }
    return base
  }

  /** The words of a phrase list ("dark|night mode|amoled"), normalized; English and app-language strings. */
  private fun words(phrases: String, into: MutableCollection<String>) {
    val text = normalize(phrases.replace('|', ' ')).text
    var start = 0
    while (start < text.length) {
      var end = text.indexOf(' ', start)
      if (end < 0) end = text.length
      if (end > start) {
        into.add(text.substring(start, end))
      }
      start = end + 1
    }
  }

  private class Query(val joined: String, val tokens: Array<String>) {
    companion object {
      fun of(query: String): Query? {
        val text = SettingsSearchEngine.normalize(query).text
        if (text.isEmpty()) {
          return null
        }
        val list = ArrayList<String>(4)
        var start = 0
        while (start < text.length) {
          var end = text.indexOf(' ', start)
          if (end < 0) end = text.length
          if (end > start) list.add(text.substring(start, end))
          start = end + 1
        }
        return Query(text, list.toTypedArray())
      }
    }
  }

  // Ranking

  private fun wordPrefix(norm: NormText, token: String): Int {
    val starts = norm.wordStarts
    for (k in starts.indices) {
      if (norm.text.startsWith(token, starts[k])) {
        return k
      }
    }
    return -1
  }

  private fun anyStartsWith(words: Array<String>, token: String): Boolean {
    for (w in words) {
      if (w.startsWith(token)) {
        return true
      }
    }
    return false
  }

  /**
   * The best of: the title is the query (1000), starts with it (850), every word of the query starts a word of the
   * title (700, +30 in order), is in the title (450), starts a word of the title or of the keywords (320), of the
   * keywords only (260), or of the path too (120, three characters or more). One character: title words only.
   */
  private fun baseScore(e: SearchEntry, q: Query): Int {
    val text = e.norm.text
    if (text.isEmpty()) {
      return 0
    }
    if (text == q.joined) {
      return SCORE_EQUALS
    }
    if (text.startsWith(q.joined)) {
      return SCORE_STARTS
    }
    val single = q.joined.length == 1
    val pathAllowed = q.joined.length >= 3
    var allPrefix = true
    var inOrder = true
    var lastWord = -1
    var allContained = true
    var allTitleOrAlt = true
    var anyTitle = false
    var allAlt = true
    var allAnywhere = true
    for (token in q.tokens) {
      val word = wordPrefix(e.norm, token)
      val prefix = word >= 0
      if (prefix) {
        anyTitle = true
        if (word < lastWord) inOrder = false
        lastWord = word
      } else {
        allPrefix = false
        if (single) {
          return 0
        }
      }
      if (!prefix && (token.length < 2 || !text.contains(token))) {
        allContained = false
      }
      val alt = !single && anyStartsWith(e.altWords, token)
      if (!alt) {
        allAlt = false
        if (!prefix) {
          allTitleOrAlt = false
          if (!pathAllowed || !anyStartsWith(e.pathWords, token)) {
            allAnywhere = false
          }
        }
      }
    }
    return when {
      allPrefix -> SCORE_WORD_PREFIXES + if (inOrder) SCORE_IN_ORDER else 0
      allContained -> SCORE_CONTAINS
      allTitleOrAlt && anyTitle -> SCORE_TITLE_AND_KEYWORDS
      allAlt -> SCORE_KEYWORDS
      allAnywhere && pathAllowed -> SCORE_PATH
      else -> 0
    }
  }

  /** The parts of the title that matched: the whole query, or each word where it starts a word (or else is). */
  private fun rangesOf(e: SearchEntry, q: Query): IntArray {
    val norm = e.norm
    val text = norm.text
    val map = norm.map
    if (text.isEmpty()) {
      return IntArray(0)
    }
    val out = IntArray(q.tokens.size * 2)
    var n = 0
    if (text.startsWith(q.joined)) {
      return intArrayOf(map[0], map[q.joined.length - 1] + 1)
    }
    for (token in q.tokens) {
      var at = -1
      for (start in norm.wordStarts) {
        if (text.startsWith(token, start)) {
          at = start
          break
        }
      }
      if (at < 0 && token.length >= 2) {
        at = text.indexOf(token)
      }
      if (at >= 0) {
        out[n++] = map[at]
        out[n++] = map[at + token.length - 1] + 1
      }
    }
    return if (n == out.size) out else out.copyOf(n)
  }

  // Building

  private class Builder(val origin: Int, val rtl: Boolean) {
    val entries = ArrayList<SearchEntry>(640)
    val byKey = HashMap<String, SearchEntry>(800)
    private val pageTitles = HashMap<TgxPage, String>()
    private val keywordCache = HashMap<Int, Array<String>>()
    private var order = 0

    fun label(res: Int): String = XgramR5Lists.label(res)

    fun pageTitle(page: TgxPage): String = pageTitles.getOrPut(page) {
      if (page.sentence != 0) XgramR5Lists.sentence(page.title, page.sentence) else label(page.title)
    }

    private fun keywords(res: Int): Array<String> = keywordCache.getOrPut(res) {
      val set = LinkedHashSet<String>()
      SettingsSearchEngine.words(Lang.getString(res), set)
      val builtin = Lang.getBuiltinString(res)
      if (builtin != null) {
        SettingsSearchEngine.words(builtin, set)
      }
      set.toTypedArray()
    }

    private fun isolate(s: String): String = if (rtl) "${SettingsSearchEngine.FSI}$s${SettingsSearchEngine.PDI}" else s

    /** "a > b > c", each part isolated in RTL so the crumbs read in the app's direction. */
    fun join(parts: List<String>): String {
      if (parts.isEmpty()) {
        return ""
      }
      var path = isolate(parts[0])
      for (i in 1 until parts.size) {
        path = Lang.getString(R.string.XgramSettingsSearchPath, path, isolate(parts[i]))
      }
      return path
    }

    fun altWordsOf(titleRes: Int, title: String, kw: IntArray, more: IntArray?): Array<String> {
      val set = LinkedHashSet<String>()
      if (titleRes != 0) {
        val builtin = Lang.getBuiltinString(titleRes)
        if (builtin != null && builtin != title) {
          SettingsSearchEngine.words(builtin, set)
        }
      }
      for (res in kw) {
        set.addAll(keywords(res))
      }
      if (more != null) {
        for (res in more) {
          set.addAll(keywords(res))
        }
      }
      return set.toTypedArray()
    }

    fun pathWordsOf(path: String): Array<String> {
      val list = ArrayList<String>(6)
      SettingsSearchEngine.words(path, list)
      return list.toTypedArray()
    }

    private fun add(e: SearchEntry) {
      if (byKey.containsKey(e.key)) {
        return // the first place a row is listed wins
      }
      entries.add(e)
      byKey[e.key] = e
    }

    private fun visible(row: TgxRow, env: SearchEnv): Boolean {
      try {
        if (!row.visible(env)) {
          return false
        }
        val target = row.target
        if (target != null && !target.visible(env)) {
          return false
        }
        var p: TgxPage? = row.page
        while (p != null) {
          if (!p.visible(env)) {
            return false
          }
          p = p.parent
        }
        return true
      } catch (t: Throwable) {
        Log.w(SettingsSearchEngine.TAG, "settings search: cannot check ${row.key}", t)
        return false
      }
    }

    fun addTgx(env: SearchEnv) {
      for (row in TgxSearchIndex.rows) {
        if (!visible(row, env)) {
          continue
        }
        try {
          val target = row.target
          val title = row.titleText?.invoke()
            ?: if (row.sentence != 0) XgramR5Lists.sentence(row.title, row.sentence) else label(row.title)
          // The path: the pages below Settings, then the group; Settings itself for its own rows.
          val parts = ArrayList<String>(4)
          var p: TgxPage? = row.page
          while (p != null && p.parent != null) {
            parts.add(pageTitle(p))
            p = p.parent
          }
          parts.reverse()
          if (row.group != 0) {
            parts.add(label(row.group))
          }
          if (parts.isEmpty()) {
            parts.add(label(R.string.Settings))
          }
          val path = join(parts)
          var icon = row.icon
          if (icon == 0 && target != null) icon = target.icon
          var q: TgxPage? = row.page
          while (icon == 0 && q != null) {
            icon = q.icon
            q = q.parent
          }
          val norm = SettingsSearchEngine.normalize(title)
          val alt = altWordsOf(row.title, title, row.kw, null)
          add(SearchEntry(row.key, title, path, icon, row.row == null, false, order++, SearchTarget.Tgx(row), norm, alt, pathWordsOf(path)))
        } catch (t: Throwable) {
          Log.w(SettingsSearchEngine.TAG, "settings search: cannot index ${row.key}", t)
        }
      }
    }

    fun addXgram(hits: List<XgramSearchHit>, env: SearchEnv) {
      for (hit in hits) {
        val key = XgramComposeBridge.hitKey(hit)
        add(xgramEntry(key, hit, hit.title, xgramPath(hit), isPage = false, kw = XgramSearchIndex.keywords(hit.id), order = order++))
      }
      if (env.expressive) {
        for (item in XgramSearchIndex.navBar()) {
          add(xgramEntry(item.key, item.hit, item.hit.title, xgramPath(item.hit), isPage = false, kw = item.kw, order = order++))
        }
      }
    }

    /**
     * XGram's own path; from Settings it starts with XGram (a row of XGram's main page already names it). Crumb by crumb,
     * as Telegram X's paths, so in RTL each reads in the app's direction.
     */
    private fun xgramPath(hit: XgramSearchHit): String {
      val parts = hit.pathParts.filter { it.isNotEmpty() }
      if (origin != SettingsSearch.ORIGIN_SETTINGS || (hit.section == XgramSettingsController.SECTION_MAIN && hit.subPage == 0)) {
        return join(parts)
      }
      return join(listOf(label(R.string.XgramSettings)) + parts)
    }

    fun xgramEntry(key: String, hit: XgramSearchHit, title: String, path: String, isPage: Boolean, kw: IntArray, order: Int): SearchEntry =
      SearchEntry(
        key, title, path, hit.icon, isPage, true, order, SearchTarget.Xgram(hit),
        SettingsSearchEngine.normalize(title), altWordsOf(0, title, kw, null), pathWordsOf(path)
      )
  }
}
