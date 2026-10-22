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
package com.fahadali.xgram.xdrive

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fahadali.xgram.history.XgramMedia
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib
import kotlin.coroutines.resume

/** One item as the organizer sees it: a short line about it (what an AI reads; never the files themselves). */
class XDriveDigest(val message: TdApi.Message, val kind: XDriveKind, val text: String, val sourceChatId: Long, val sourceTitle: String?)

/** A folder the organizer proposes: a new one, or one the user has ([existingId]). Its items can be left out one by one. */
class XDriveProposedFolder(name: String, emoji: String?, val existingId: String?) {
  var name by mutableStateOf(name)
  var emoji by mutableStateOf(emoji)
  var enabled by mutableStateOf(true)
  val items = mutableStateListOf<TdApi.Message>()
  val left = mutableStateListOf<Long>()

  val count: Int get() = items.size - left.size
}

/** How the organizer sorts: Gemini Nano on the phone, or rules (keywords, sites, file types). */
enum class XDriveEngine { NANO, RULES }

/** Gemini Nano through ML Kit's Prompt API (AICore): Android 14+ phones that have it. Main thread. */
object XDriveNano {
  enum class Status { UNSUPPORTED, DOWNLOADABLE, DOWNLOADING, AVAILABLE }

  private var model: GenerativeModel? = null

  private fun model(): GenerativeModel? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return model ?: try {
      Generation.getClient().also { model = it }
    } catch (t: Throwable) {
      Log.w("XDrive: no Gemini Nano client", t)
      null
    }
  }

  suspend fun status(): Status {
    val m = model() ?: return Status.UNSUPPORTED
    return try {
      when (m.checkStatus()) {
        FeatureStatus.AVAILABLE -> Status.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> Status.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> Status.DOWNLOADING
        else -> Status.UNSUPPORTED
      }
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano status", t)
      Status.UNSUPPORTED
    }
  }

  /** Downloads the model (AICore keeps it for every app), [progress] from 0 to 1 while it does; true once it is there. */
  suspend fun download(progress: (Float) -> Unit): Boolean {
    val m = model() ?: return false
    var total = 0L
    var ok = false
    try {
      m.download().collect { status ->
        when (status) {
          is DownloadStatus.DownloadStarted -> total = status.bytesToDownload
          is DownloadStatus.DownloadProgress -> if (total > 0) progress((status.totalBytesDownloaded.toFloat() / total).coerceIn(0f, 1f))
          is DownloadStatus.DownloadCompleted -> ok = true
          is DownloadStatus.DownloadFailed -> Log.w("XDrive: Gemini Nano download failed", status.e)
          else -> Unit
        }
      }
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano download", t)
    }
    return ok || status() == Status.AVAILABLE
  }

  suspend fun generate(prompt: String, maxTokens: Int): String? {
    val m = model() ?: return null
    return try {
      val request = generateContentRequest(TextPart(prompt)) {
        temperature = 0.2f
        topK = 10
        maxOutputTokens = maxTokens
      }
      m.generateContent(request).candidates.firstOrNull()?.text
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano failed", t)
      null
    }
  }
}

/** Topics the rules know: the folder each makes, with its emoji. */
enum class XDriveTopic(val title: Int, val emoji: String) {
  ACCOUNTS(R.string.XgramXDriveTopicAccounts, "🔑"),
  RECEIPTS(R.string.XgramXDriveTopicReceipts, "🧾"),
  TRAVEL(R.string.XgramXDriveTopicTravel, "✈️"),
  CODE(R.string.XgramXDriveTopicCode, "💻"),
  TODO(R.string.XgramXDriveTopicTodo, "✅"),
  CONTACTS(R.string.XgramXDriveTopicContacts, "📇"),
  PLACES(R.string.XgramXDriveTopicPlaces, "📍"),
  WATCH(R.string.XgramXDriveTopicWatch, "🎬"),
  SOCIAL(R.string.XgramXDriveTopicSocial, "💬"),
  SHOPPING(R.string.XgramXDriveTopicShopping, "🛍️"),
  READING(R.string.XgramXDriveTopicReading, "📰"),
  DOCUMENTS(R.string.XgramXDriveTopicDocuments, "📄"),
  APPS(R.string.XgramXDriveTopicApps, "📦"),
  ARCHIVES(R.string.XgramXDriveTopicArchives, "🗜️")
}

/** The rules: keywords (English and Arabic), sites and file types. Photos and videos without words stay unsorted. */
object XDriveRules {
  private val ACCOUNTS = Regex("(?i)(\\bpass(word|code|wd)?\\s*[:=]|\\bpwd\\b|\\blog\\s?in\\s*[:=]|\\buser\\s?name\\s*[:=]|\\bpin(\\s?code)?\\s*[:=]|\\brecovery codes?\\b|\\bbackup codes?\\b|\\bseed phrase\\b|\\bapi[ _-]?key\\b|\\b2fa\\b|\\biban\\b|كلمة (المرور|السر)|اسم المستخدم)")
  private val RECEIPTS = Regex("(?i)(\\binvoice\\b|\\breceipt\\b|\\bbill\\b|\\bpayment\\b|\\bpaid\\b|\\btransaction\\b|\\btxn\\b|\\border (id|no|number|#)|\\brefund\\b|\\bsubtotal\\b|\\btax invoice\\b|\\bstatement\\b|فاتورة|إيصال|ايصال|دفع)")
  private val TRAVEL = Regex("(?i)(\\bflight\\b|\\bboarding pass\\b|\\bpnr\\b|\\bbooking (ref|reference|confirmation|no)|\\be-?ticket\\b|\\bitinerary\\b|\\bhotel\\b|\\bvisa\\b|\\bpassport\\b|\\bairport\\b|\\bcheck-?in\\b|تذكرة|حجز|رحلة|فندق|تأشيرة)")
  private val TODO = Regex("(?im)(^\\s*([-*•]\\s*\\[[ xX]?\\]|☐|☑|✅|✔)|\\bto-?do\\b|\\bshopping list\\b|\\bremind(er)?\\b)")
  private val CODE_TEXT = Regex("(```|^\\s*(import |def |fun |function |class |public |private |#include|const |let |var |SELECT |sudo |git |npm |pip ))", RegexOption.MULTILINE)
  private val PHONE = Regex("\\+?\\d[\\d ()-]{7,}\\d")

  private val WATCH_SITES = listOf("youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "twitch.tv", "dailymotion.com", "netflix.com", "imdb.com")
  private val SOCIAL_SITES = listOf("instagram.com", "twitter.com", "x.com", "facebook.com", "fb.watch", "threads.net", "reddit.com", "linkedin.com", "pinterest.com", "snapchat.com", "t.me", "telegram.me", "bsky.app")
  private val SHOPPING_SITES = listOf("amazon.", "aliexpress.", "ebay.", "noon.com", "daraz.", "temu.com", "shein.com", "etsy.com", "walmart.com", "flipkart.com", "alibaba.com", "namshi.com", "ikea.com")
  private val READING_SITES = listOf("medium.com", "wikipedia.org", "substack.com", "bbc.", "cnn.com", "nytimes.com", "theguardian.com", "aljazeera.", "arabnews.com", "dawn.com", "theverge.com", "wired.com")
  private val CODE_SITES = listOf("github.com", "gitlab.com", "stackoverflow.com", "gist.github.com", "pastebin.com", "developer.android.com", "kotlinlang.org", "npmjs.com", "pypi.org")
  private val TRAVEL_SITES = listOf("booking.com", "airbnb.", "expedia.", "skyscanner.", "agoda.", "tripadvisor.", "saudia.com", "emirates.com", "qatarairways.com", "flynas.com", "piaonline.com")
  private val MAP_SITES = listOf("maps.google.", "google.com/maps", "goo.gl/maps", "maps.app.goo.gl", "maps.apple.com", "waze.com")

  private fun hostOf(url: String): String = try {
    (android.net.Uri.parse(if (url.contains("://")) url else "https://$url").host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
  } catch (t: Throwable) {
    ""
  }

  private fun siteIn(host: String, url: String, sites: List<String>): Boolean = sites.any { site ->
    if (site.contains('/')) url.lowercase().contains(site) else if (site.endsWith(".")) host.startsWith(site) || host.contains(".$site") else host == site || host.endsWith(".$site")
  }

  /** The topic of an item, or null: none is clear. */
  fun topicOf(message: TdApi.Message, kind: XDriveKind): XDriveTopic? {
    val content = message.content
    when (content) {
      is TdApi.MessageContact -> return XDriveTopic.CONTACTS
      is TdApi.MessageLocation, is TdApi.MessageVenue -> return XDriveTopic.PLACES
      else -> Unit
    }
    val text = XgramMedia.formattedTextOf(content)?.text ?: ""
    val fileName = (content as? TdApi.MessageDocument)?.document?.fileName?.lowercase() ?: ""
    val words = if (fileName.isNotEmpty()) "$fileName\n$text" else text
    if (words.isNotBlank()) {
      if (ACCOUNTS.containsMatchIn(words)) return XDriveTopic.ACCOUNTS
      if (RECEIPTS.containsMatchIn(words)) return XDriveTopic.RECEIPTS
      if (TRAVEL.containsMatchIn(words)) return XDriveTopic.TRAVEL
    }
    if (kind == XDriveKind.NOTE) {
      val entities = (content as? TdApi.MessageText)?.text?.entities ?: emptyArray()
      val codeLength = entities.filter { it.type is TdApi.TextEntityTypePre || it.type is TdApi.TextEntityTypePreCode || it.type is TdApi.TextEntityTypeCode }.sumOf { it.length }
      if ((text.isNotEmpty() && codeLength * 2 >= text.length) || CODE_TEXT.containsMatchIn(text)) return XDriveTopic.CODE
      if (TODO.containsMatchIn(text)) return XDriveTopic.TODO
      val phone = PHONE.find(text)
      if (phone != null && text.length < 80 && phone.value.count { it.isDigit() } >= 8) return XDriveTopic.CONTACTS
      return null
    }
    if (kind == XDriveKind.LINK) {
      val url = XDriveItems.urlOf(message) ?: return null
      val host = hostOf(url)
      return when {
        siteIn(host, url, MAP_SITES) -> XDriveTopic.PLACES
        siteIn(host, url, CODE_SITES) -> XDriveTopic.CODE
        siteIn(host, url, TRAVEL_SITES) -> XDriveTopic.TRAVEL
        siteIn(host, url, WATCH_SITES) -> XDriveTopic.WATCH
        siteIn(host, url, SOCIAL_SITES) -> XDriveTopic.SOCIAL
        siteIn(host, url, SHOPPING_SITES) -> XDriveTopic.SHOPPING
        siteIn(host, url, READING_SITES) -> XDriveTopic.READING
        (content as? TdApi.MessageText)?.linkPreview?.type is TdApi.LinkPreviewTypeArticle -> XDriveTopic.READING
        else -> null
      }
    }
    if (kind == XDriveKind.FILE && content is TdApi.MessageDocument) {
      val mime = content.document.mimeType.lowercase()
      return when {
        fileName.endsWith(".apk") || fileName.endsWith(".apks") || fileName.endsWith(".xapk") || mime == "application/vnd.android.package-archive" -> XDriveTopic.APPS
        Regex("\\.(zip|rar|7z|tar|gz|tgz|bz2|xz)$").containsMatchIn(fileName) -> XDriveTopic.ARCHIVES
        mime.startsWith("text/") || mime == "application/pdf" || mime.contains("officedocument") || mime.contains("msword") || mime.contains("ms-excel") ||
          mime.contains("ms-powerpoint") || mime.contains("opendocument") || mime == "application/epub+zip" || mime == "application/rtf" ||
          Regex("\\.(pdf|docx?|xlsx?|pptx?|txt|csv|rtf|odt|ods|epub|md)$").containsMatchIn(fileName) -> XDriveTopic.DOCUMENTS
        else -> null
      }
    }
    return null
  }
}

/**
 * Organize with AI: reads XDrive's items that are in no folder (newest first, up to [LIMIT]), proposes folders (the
 * rules' topics; with Gemini Nano also its own, by meaning) and, once the user has looked through them, puts the items
 * in. Nothing is sent anywhere: Nano runs on the phone. Compose state; main thread.
 */
class XDriveOrganizer(private val tdlib: Tdlib) {
  enum class Phase { READY, READING, SORTING, REVIEW, DONE }

  companion object {
    const val LIMIT = 5000
    /** Gemini Nano reads at most this many items (a few seconds per batch); the rest go by the rules. */
    private const val NANO_LIMIT = 360
    private const val NANO_BATCH = 15
    /** A channel with at least this many items in XDrive gets a folder of its own. */
    private const val CHANNEL_MIN = 6
  }

  var phase by mutableStateOf(Phase.READY)
  var engine by mutableStateOf(XDriveEngine.RULES)
  var nanoStatus by mutableStateOf<XDriveNano.Status?>(null)
  var downloadProgress by mutableStateOf<Float?>(null)
  var readCount by mutableIntStateOf(0)
  var sortDone by mutableIntStateOf(0)
  var sortTotal by mutableIntStateOf(0)
  var unsortedLeft by mutableIntStateOf(0)
  var movedCount by mutableIntStateOf(0)
  var madeCount by mutableIntStateOf(0)
  var startedAt by mutableLongStateOf(0L)
  val folders = mutableStateListOf<XDriveProposedFolder>()

  private val index = XDriveIndex.of(tdlib)

  suspend fun checkNano() {
    val status = XDriveNano.status()
    nanoStatus = status
    if (status == XDriveNano.Status.AVAILABLE || status == XDriveNano.Status.DOWNLOADABLE || status == XDriveNano.Status.DOWNLOADING) {
      engine = XDriveEngine.NANO
    }
  }

  /** Reads, sorts and leaves the proposal in [folders] (phase REVIEW). */
  suspend fun run() {
    startedAt = System.currentTimeMillis()
    folders.clear()
    readCount = 0
    sortDone = 0
    sortTotal = 0
    if (engine == XDriveEngine.NANO && nanoStatus != XDriveNano.Status.AVAILABLE) {
      downloadProgress = 0f
      val ok = XDriveNano.download { downloadProgress = it }
      downloadProgress = null
      nanoStatus = XDriveNano.status()
      if (!ok) engine = XDriveEngine.RULES
    }
    phase = Phase.READING
    suspendCancellableCoroutine<Unit> { cont -> index.load { if (cont.isActive) cont.resume(Unit) } }
    val messages = readUnsorted()
    phase = Phase.SORTING
    val digests = withContext(Dispatchers.Default) { messages.map { digestOf(it) } }
    val byTopic = LinkedHashMap<XDriveTopic, MutableList<TdApi.Message>>()
    val open = ArrayList<XDriveDigest>()
    withContext(Dispatchers.Default) {
      for (digest in digests) {
        val topic = XDriveRules.topicOf(digest.message, digest.kind)
        if (topic != null) byTopic.getOrPut(topic) { ArrayList() }.add(digest.message) else open.add(digest)
      }
    }
    val proposals = LinkedHashMap<String, XDriveProposedFolder>()
    fun folderFor(name: String, emoji: String?): XDriveProposedFolder {
      val key = name.trim().lowercase()
      proposals[key]?.let { return it }
      val existing = index.findFolderByName(name)
      val folder = XDriveProposedFolder(existing?.name ?: name.trim(), existing?.emoji ?: emoji, existing?.id)
      proposals[key] = folder
      return folder
    }
    for ((topic, items) in byTopic) {
      folderFor(Lang.getString(topic.title), topic.emoji).items.addAll(items)
    }
    var rest: List<XDriveDigest> = open
    if (engine == XDriveEngine.NANO) {
      rest = sortWithNano(open) { name, emoji, items -> folderFor(name, emoji).items.addAll(items) }
    }
    // What came from one channel, many times: a folder named after it
    val byChannel = rest.filter { it.sourceChatId != 0L && !it.sourceTitle.isNullOrEmpty() }.groupBy { it.sourceChatId }
    val placed = HashSet<Long>()
    for ((_, items) in byChannel) {
      if (items.size >= CHANNEL_MIN) {
        folderFor(items[0].sourceTitle!!, "📢").items.addAll(items.map { it.message })
        items.forEach { placed.add(it.message.id) }
      }
    }
    unsortedLeft = rest.count { !placed.contains(it.message.id) }
    val list = proposals.values.filter { it.items.isNotEmpty() }.sortedByDescending { it.items.size }
    for (folder in list) {
      // Newest first inside each folder
      val sorted = folder.items.sortedByDescending { it.id }
      folder.items.clear()
      folder.items.addAll(sorted)
    }
    folders.addAll(list)
    phase = Phase.REVIEW
  }

  /** Puts the chosen items into their folders (new ones made); phase DONE. */
  fun apply() {
    var moved = 0
    var made = 0
    for (folder in folders) {
      if (!folder.enabled) continue
      val left = folder.left.toHashSet()
      val ids = folder.items.filter { !left.contains(it.id) }.map { it.id }.toLongArray()
      if (ids.isEmpty()) continue
      var id = folder.existingId?.takeIf { index.findFolder(it) != null }
      if (id == null) {
        val existing = index.findFolderByName(folder.name)
        id = existing?.id ?: index.createFolder(folder.name, folder.emoji).id.also { made++ }
      }
      index.setFolder(ids, id)
      moved += ids.size
    }
    movedCount = moved
    madeCount = made
    phase = Phase.DONE
  }

  // Reading

  private suspend fun readUnsorted(): List<TdApi.Message> {
    val chatId = tdlib.selfChatId()
    val result = ArrayList<TdApi.Message>()
    val seen = HashSet<Long>()
    var fromId = 0L
    var empty = 0
    while (result.size < LIMIT && empty < 2) {
      val page = suspendCancellableCoroutine<Array<TdApi.Message>> { cont ->
        tdlib.send(TdApi.GetChatHistory(chatId, fromId, 0, 100, false), Tdlib.ResultHandler<TdApi.Messages> { messages, _ ->
          if (cont.isActive) cont.resume(messages?.messages?.filterNotNull()?.toTypedArray() ?: emptyArray())
        })
      }
      var fresh = 0
      for (message in page) {
        if (!seen.add(message.id)) continue
        fresh++
        if (fromId == 0L || message.id < fromId) fromId = message.id
        if (keep(message)) result.add(message)
      }
      empty = if (fresh == 0) empty + 1 else 0
      readCount = seen.size
    }
    return result
  }

  private fun keep(message: TdApi.Message): Boolean {
    if (message.sendingState != null) return false
    if (index.isIndexMessage(message.id) || XDriveIndex.isIndexDocument(message)) return false
    if (index.folderOf(message.id) != null) return false
    val kind = XDriveItems.kindOf(message)
    if (kind == XDriveKind.OTHER) {
      val c = message.content
      return c is TdApi.MessageLocation || c is TdApi.MessageVenue || c is TdApi.MessageContact
    }
    return true
  }

  private fun digestOf(message: TdApi.Message): XDriveDigest {
    val kind = XDriveItems.kindOf(message)
    val content = message.content
    val text = XgramMedia.formattedTextOf(content)?.text?.trim()?.replace(Regex("\\s+"), " ") ?: ""
    val line = when (kind) {
      XDriveKind.NOTE -> "note: " + text.take(150)
      XDriveKind.LINK -> {
        val url = XDriveItems.urlOf(message) ?: ""
        val preview = (content as? TdApi.MessageText)?.linkPreview
        val host = try { android.net.Uri.parse(if (url.contains("://")) url else "https://$url").host?.removePrefix("www.") ?: "" } catch (t: Throwable) { "" }
        "link $host" + (preview?.title?.takeIf { it.isNotEmpty() }?.let { ": " + it.take(80) } ?: "") + text.replace(url, "").trim().takeIf { it.isNotEmpty() }?.let { " — " + it.take(60) }.orEmpty()
      }
      XDriveKind.FILE -> {
        val document = (content as? TdApi.MessageDocument)?.document
        "file " + (document?.fileName ?: "") + (if (text.isNotEmpty()) " — " + text.take(80) else "")
      }
      XDriveKind.MUSIC -> "song " + XDriveItems.title(message).take(80)
      XDriveKind.VOICE -> "voice message" + (if (text.isNotEmpty()) ": " + text.take(80) else "")
      XDriveKind.PHOTO -> "photo" + (if (text.isNotEmpty()) ": " + text.take(120) else "")
      XDriveKind.VIDEO -> "video" + (if (text.isNotEmpty()) ": " + text.take(120) else "")
      XDriveKind.GIF -> "gif" + (if (text.isNotEmpty()) ": " + text.take(80) else "")
      XDriveKind.OTHER -> when (content) {
        is TdApi.MessageLocation, is TdApi.MessageVenue -> "location"
        is TdApi.MessageContact -> "contact"
        else -> "item"
      }
    }
    var sourceChatId = 0L
    when (val origin = message.forwardInfo?.origin) {
      is TdApi.MessageOriginChannel -> sourceChatId = origin.chatId
      is TdApi.MessageOriginChat -> sourceChatId = origin.senderChatId
      else -> Unit
    }
    val sourceTitle = if (sourceChatId != 0L) tdlib.chatTitle(sourceChatId)?.takeIf { it.isNotEmpty() } else null
    return XDriveDigest(message, kind, if (sourceTitle != null) "$line (from $sourceTitle)" else line, sourceChatId, sourceTitle)
  }

  // Gemini Nano

  /** Folder names by meaning from a sample, then each item into one (or none); returns what is left unplaced. */
  private suspend fun sortWithNano(open: List<XDriveDigest>, add: (String, String?, List<TdApi.Message>) -> Unit): List<XDriveDigest> {
    // Only what words describe: a photo without a caption says nothing
    val readable = open.filter { it.text.length > 14 && !(it.kind == XDriveKind.PHOTO && it.text == "photo") && !(it.kind == XDriveKind.VIDEO && it.text == "video") }
    val batch = readable.take(NANO_LIMIT)
    if (batch.size < 4) return open
    sortTotal = batch.size
    val existing = index.folders().map { it.name }
    val sample = batch.shuffled(java.util.Random(batch.size.toLong())).take(40)
    val namesPrompt = buildString {
      append("You help someone organize their saved notes, links and files into folders.\n")
      append("Here are some of their saved items:\n")
      sample.forEachIndexed { i, d -> append(i + 1).append(". ").append(d.text.take(110)).append('\n') }
      if (existing.isNotEmpty()) append("They already have these folders: ").append(existing.joinToString(", ")).append(".\n")
      append("Suggest between 3 and 8 folders that group these items by topic. Each folder name must be 1 to 3 words. ")
      append("Write one folder per line, starting with one emoji, like:\n📚 Study notes\n🍳 Recipes\n")
    }
    val names = parseFolders(XDriveNano.generate(namesPrompt, 160) ?: return open)
    if (names.isEmpty()) return open
    val placed = HashMap<Long, Int>()
    for (chunk in batch.chunked(NANO_BATCH)) {
      val prompt = buildString {
        append("Folders:\n")
        names.forEachIndexed { i, f -> append(i + 1).append(". ").append(f.second).append('\n') }
        append("0. None of these\n\nItems:\n")
        chunk.forEachIndexed { i, d -> append(i + 1).append(". ").append(d.text.take(130)).append('\n') }
        append("\nFor each item, write its number, an equals sign and the number of the folder it belongs in, one per line, like 1=2. ")
        append("Use 0 when no folder fits well.\n")
      }
      val answer = XDriveNano.generate(prompt, 12 * chunk.size + 20)
      if (answer != null) {
        for (match in Regex("(\\d+)\\s*[=:→>-]+\\s*(\\d+)").findAll(answer)) {
          val item = match.groupValues[1].toIntOrNull() ?: continue
          val folder = match.groupValues[2].toIntOrNull() ?: continue
          if (item in 1..chunk.size && folder in 1..names.size) placed[chunk[item - 1].message.id] = folder - 1
        }
      }
      sortDone += chunk.size
    }
    names.forEachIndexed { i, (emoji, name) ->
      val items = batch.filter { placed[it.message.id] == i }.map { it.message }
      if (items.isNotEmpty()) add(name, emoji, items)
    }
    return open.filter { !placed.containsKey(it.message.id) }
  }

  /** "📚 Study notes" lines (numbers, bullets and quotes cleaned off) into (emoji, name). */
  private fun parseFolders(text: String): List<Pair<String?, String>> {
    val result = ArrayList<Pair<String?, String>>()
    val seen = HashSet<String>()
    for (raw in text.lines()) {
      var line = raw.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").replace("*", "").trim()
      if (line.isEmpty()) continue
      var emoji: String? = null
      val first = line.codePointAt(0)
      if (!Character.isLetterOrDigit(first)) {
        val end = emojiEnd(line)
        emoji = line.substring(0, end).trim().takeIf { it.isNotEmpty() }
        line = line.substring(end).trim()
      }
      line = line.trim('"', '\'', ':', '.', ' ')
      if (line.isEmpty() || line.length > 32 || line.split(' ').size > 4) continue
      if (seen.add(line.lowercase())) result.add(Pair(emoji, line))
      if (result.size == 8) break
    }
    return result
  }

  /** Where the leading emoji (with its joiners and variation selectors) ends. */
  private fun emojiEnd(s: String): Int {
    var i = 0
    while (i < s.length) {
      val cp = s.codePointAt(i)
      if (Character.isLetterOrDigit(cp) || cp == ' '.code) break
      i += Character.charCount(cp)
    }
    return i
  }
}
