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
import com.fahadali.xgram.XgramSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib
import kotlin.coroutines.resume

/** One item as the organizer sees it: a short line about it (what an AI reads; never the files themselves). */
class XDriveDigest(val message: TdApi.Message, val kind: XDriveKind, val text: String, val sourceChatId: Long, val sourceTitle: String?)

/** A folder the organizer proposes: a new one, or one the user has ([existingId]). Its items can be left out one by one. */
class XDriveProposedFolder(name: String, icon: String?, val emoji: String?, val existingId: String?) {
  var name by mutableStateOf(name)
  var icon by mutableStateOf(icon)
  var enabled by mutableStateOf(true)
  val items = mutableStateListOf<TdApi.Message>()
  val left = mutableStateListOf<Long>()

  val count: Int get() = items.size - left.size
}

/** How the organizer sorts: Gemini Nano on the phone, a cloud AI with the user's key, or rules (keywords, sites, file types). */
enum class XDriveEngine { NANO, CLOUD, RULES }

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
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano status", t)
      Status.UNSUPPORTED
    }
  }

  /** Follows AICore's download of the model ([progress]: bytes so far, bytes in all or 0), until it ends or fails. */
  suspend fun download(progress: (Long, Long) -> Unit) {
    val m = model() ?: return
    var total = 0L
    try {
      m.download().collect { status ->
        when (status) {
          is DownloadStatus.DownloadStarted -> {
            total = status.bytesToDownload
            progress(0L, total)
          }
          is DownloadStatus.DownloadProgress -> progress(status.totalBytesDownloaded, total)
          is DownloadStatus.DownloadFailed -> Log.w("XDrive: Gemini Nano download failed", status.e)
          else -> Unit
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano download", t)
    }
  }

  /** Loads the model into memory, so the first answer doesn't wait for it. */
  suspend fun warmup() {
    val m = model() ?: return
    try {
      m.warmup()
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano warmup", t)
    }
  }

  /** One answer, or null (failed, or no answer within [timeoutMs]). */
  suspend fun generate(prompt: String, maxTokens: Int, timeoutMs: Long): String? {
    val m = model() ?: return null
    val started = System.currentTimeMillis()
    return try {
      val request = generateContentRequest(TextPart(prompt)) {
        temperature = 0.2f
        topK = 10
        maxOutputTokens = maxTokens
      }
      val text = withTimeoutOrNull(timeoutMs) { m.generateContent(request).candidates.firstOrNull()?.text }
      Log.i("XDrive: Gemini Nano answered in %d ms (%s)", System.currentTimeMillis() - started, if (text == null) "no answer" else "${text.length} chars")
      text
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Log.w("XDrive: Gemini Nano failed after %d ms", t, System.currentTimeMillis() - started)
      null
    }
  }
}

/** Topics the rules know: the folder each makes, with its icon (XDriveIcons). */
enum class XDriveTopic(val title: Int, val icon: String) {
  ACCOUNTS(R.string.XgramXDriveTopicAccounts, "key"),
  RECEIPTS(R.string.XgramXDriveTopicReceipts, "receipt"),
  TRAVEL(R.string.XgramXDriveTopicTravel, "flight"),
  CODE(R.string.XgramXDriveTopicCode, "code"),
  TODO(R.string.XgramXDriveTopicTodo, "todo"),
  CONTACTS(R.string.XgramXDriveTopicContacts, "contact"),
  PLACES(R.string.XgramXDriveTopicPlaces, "place"),
  WATCH(R.string.XgramXDriveTopicWatch, "movie"),
  SOCIAL(R.string.XgramXDriveTopicSocial, "chat"),
  SHOPPING(R.string.XgramXDriveTopicShopping, "shopping"),
  READING(R.string.XgramXDriveTopicReading, "article"),
  DOCUMENTS(R.string.XgramXDriveTopicDocuments, "document"),
  APPS(R.string.XgramXDriveTopicApps, "apps"),
  ARCHIVES(R.string.XgramXDriveTopicArchives, "archive")
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
 * in. Nothing is sent anywhere: Nano runs on the phone. It lives as long as XDrive's screen, so closing the sheet
 * doesn't stop it. Compose state; main thread.
 */
class XDriveOrganizer(private val tdlib: Tdlib) {
  enum class Phase { READY, PREPARING, READING, CONSENT, SORTING, REVIEW, DONE }
  enum class NanoStep { NONE, DOWNLOADING, STARTING, NAMING, SORTING }

  /** An AI that sorts: how much it reads, in what batches, and one answer at a time (null: none came, see [lastError]). */
  private abstract class Ai(val name: String, val limit: Int, val sample: Int, val batch: Int, val parallel: Int, val maxFolders: Int, val timeoutMs: Long) {
    var lastError: String? = null
    abstract suspend fun ask(prompt: String, maxTokens: Int): String?
    /** What it is shown of an item (the cloud gets numbers, addresses and codes masked). */
    open fun line(digest: XDriveDigest, max: Int): String = digest.text.take(max)
  }

  private class NanoAi : Ai(Lang.getString(R.string.XgramXDriveEngineNanoName), 360, 30, 15, 1, 8, 45_000) {
    override suspend fun ask(prompt: String, maxTokens: Int): String? = XDriveNano.generate(prompt, maxTokens, timeoutMs)
  }

  private class CloudAi(val config: XDriveCloudConfig) : Ai(config.name, 2000, 160, 80, 2, 12, 170_000) {
    override suspend fun ask(prompt: String, maxTokens: Int): String? = try {
      withContext(Dispatchers.IO) { XDriveCloud.generate(config, prompt) }
    } catch (e: XDriveCloudException) {
      lastError = e.message
      Log.w("XDrive: %s", e.message)
      null
    }

    override fun line(digest: XDriveDigest, max: Int): String = XDriveCloud.redact(digest.text).take(max)
  }

  companion object {
    const val LIMIT = 5000
    /** A channel with at least this many items in XDrive gets a folder of its own. */
    private const val CHANNEL_MIN = 6
    /** Answers that may fail in a row before the rules take over. */
    private const val AI_FAILURES = 3
  }

  var phase by mutableStateOf(Phase.READY)
  var engine by mutableStateOf(XDriveEngine.RULES)
  var nanoStatus by mutableStateOf<XDriveNano.Status?>(null)
  var nanoStep by mutableStateOf(NanoStep.NONE)
  var downloaded by mutableLongStateOf(0L)
  var downloadTotal by mutableLongStateOf(0L)
  var readCount by mutableIntStateOf(0)
  var sortDone by mutableIntStateOf(0)
  var sortTotal by mutableIntStateOf(0)
  var unsortedLeft by mutableIntStateOf(0)
  var movedCount by mutableIntStateOf(0)
  /** Why the AI didn't do all the sorting (it wasn't ready, stopped answering), shown in the review. */
  var note by mutableStateOf<String?>(null)
  /** The AI sorting now ("Gemini Nano", "Claude"), for the progress text. */
  var aiName by mutableStateOf("")
  /** The cloud AI set up (null: none), read again whenever the sheet changes it. */
  var cloud by mutableStateOf(XDriveCloudConfig.load())
  /** Before anything goes to the cloud: how many items, and a few lines exactly as they would be sent. */
  var consentCount by mutableIntStateOf(0)
  val consentSample = mutableStateListOf<String>()
  private var consent: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
  val folders = mutableStateListOf<XDriveProposedFolder>()
  /** The user tapped "Use quick rules now": Nano's download or sorting is left, the rules finish. */
  private var skipNano = false
  /** The newest message this run read: once applied, XDrive counts as organized up to it. */
  private var newestRead = 0L

  val running: Boolean get() = phase == Phase.PREPARING || phase == Phase.READING || phase == Phase.CONSENT || phase == Phase.SORTING

  private val index = XDriveIndex.of(tdlib)

  suspend fun checkNano() {
    val status = XDriveNano.status()
    nanoStatus = status
    if (phase == Phase.READY && engine == XDriveEngine.RULES) {
      engine = when {
        cloud != null && XgramSettings.instance().xdriveAiProvider().isNotEmpty() && status == XDriveNano.Status.UNSUPPORTED -> XDriveEngine.CLOUD
        status != XDriveNano.Status.UNSUPPORTED -> XDriveEngine.NANO
        else -> XDriveEngine.RULES
      }
    }
  }

  fun cloudChanged() {
    cloud = XDriveCloudConfig.load()
    if (cloud == null && engine == XDriveEngine.CLOUD) engine = XDriveEngine.RULES
  }

  fun useRulesNow() {
    skipNano = true
    consent?.complete(false)
  }

  /** The user's answer to sending the items to the cloud. */
  fun answerConsent(send: Boolean) {
    consent?.complete(send)
  }

  fun reset() {
    phase = Phase.READY
    folders.clear()
    note = null
  }

  /** Reads, sorts and leaves the proposal in [folders] (phase REVIEW). */
  suspend fun run() {
    folders.clear()
    note = null
    skipNano = false
    readCount = 0
    newestRead = 0L
    sortDone = 0
    sortTotal = 0
    var ai: Ai? = null
    if (engine == XDriveEngine.NANO) {
      phase = Phase.PREPARING
      if (prepareNano()) ai = NanoAi()
    } else if (engine == XDriveEngine.CLOUD) {
      ai = cloud?.let { CloudAi(it) }
    }
    aiName = ai?.name ?: ""
    nanoStep = NanoStep.NONE
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
    fun folderFor(name: String, icon: String?): XDriveProposedFolder {
      val key = name.trim().lowercase()
      proposals[key]?.let { return it }
      val existing = index.findFolderByName(name)
      val folder = XDriveProposedFolder(existing?.name ?: name.trim(), existing?.icon ?: icon, existing?.emoji, existing?.id)
      proposals[key] = folder
      return folder
    }
    for ((topic, items) in byTopic) {
      folderFor(Lang.getString(topic.title), topic.icon).items.addAll(items)
    }
    var rest: List<XDriveDigest> = open
    if (ai != null && !skipNano) {
      rest = sortWithAi(ai, open) { name, items -> folderFor(name, XDriveIcons.keyForName(name)).items.addAll(items) }
    }
    nanoStep = NanoStep.NONE
    // What came from one channel, many times: a folder named after it
    val byChannel = rest.filter { it.sourceChatId != 0L && !it.sourceTitle.isNullOrEmpty() }.groupBy { it.sourceChatId }
    val placed = HashSet<Long>()
    for ((_, items) in byChannel) {
      if (items.size >= CHANNEL_MIN) {
        folderFor(items[0].sourceTitle!!, "chat").items.addAll(items.map { it.message })
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

  /**
   * Gets Gemini Nano ready: follows AICore's download while there is one (the user can switch to the rules meanwhile),
   * then loads the model. False: sort with the rules ([note] says why when it is not the user's choice).
   */
  private suspend fun prepareNano(): Boolean {
    var status = XDriveNano.status()
    nanoStatus = status
    if (status == XDriveNano.Status.DOWNLOADABLE || status == XDriveNano.Status.DOWNLOADING) {
      nanoStep = NanoStep.DOWNLOADING
      downloaded = 0
      downloadTotal = 0
      coroutineScope {
        val follower = launch {
          XDriveNano.download { done, total ->
            downloaded = done
            if (total > 0) downloadTotal = total
          }
        }
        while (status != XDriveNano.Status.AVAILABLE && status != XDriveNano.Status.UNSUPPORTED && !skipNano) {
          delay(1500)
          status = XDriveNano.status()
        }
        follower.cancel()
      }
      nanoStatus = status
    }
    if (skipNano) {
      engine = XDriveEngine.RULES
      return false
    }
    if (status != XDriveNano.Status.AVAILABLE) {
      note = Lang.getString(R.string.XgramXDriveNanoUnavailable)
      return false
    }
    nanoStep = NanoStep.STARTING
    withTimeoutOrNull(60_000) { XDriveNano.warmup() }
    return !skipNano
  }

  /** Puts the chosen items into their folders (new ones made); phase DONE. */
  fun apply() {
    var moved = 0
    for (folder in folders) {
      if (!folder.enabled) continue
      val left = folder.left.toHashSet()
      val ids = folder.items.filter { !left.contains(it.id) }.map { it.id }.toLongArray()
      if (ids.isEmpty()) continue
      var id = folder.existingId?.takeIf { index.findFolder(it) != null }
      if (id == null) {
        val existing = index.findFolderByName(folder.name)
        id = existing?.id ?: index.createFolder(folder.name, folder.icon).id
      }
      index.setFolder(ids, id)
      moved += ids.size
    }
    movedCount = moved
    index.setOrganizedUpTo(newestRead)
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
        if (message.id > newestRead) newestRead = message.id
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

  /** The items the AI can read: words describe them (a photo without a caption says nothing). */
  private fun readable(open: List<XDriveDigest>): List<XDriveDigest> =
    open.filter { it.text.length > 14 && !(it.kind == XDriveKind.PHOTO && it.text == "photo") && !(it.kind == XDriveKind.VIDEO && it.text == "video") }

  /**
   * Folder names by meaning from a sample, then each item into one (or none), in batches; returns what is left
   * unplaced. A cloud AI asks the user first, showing what would be sent.
   */
  private suspend fun sortWithAi(ai: Ai, open: List<XDriveDigest>, add: (String, List<TdApi.Message>) -> Unit): List<XDriveDigest> {
    val batch = readable(open).take(ai.limit)
    if (batch.size < 4) return open
    if (ai is CloudAi) {
      consentCount = batch.size
      consentSample.clear()
      consentSample.addAll(batch.take(4).map { ai.line(it, 120) })
      val answer = kotlinx.coroutines.CompletableDeferred<Boolean>()
      consent = answer
      phase = Phase.CONSENT
      val send = answer.await()
      consent = null
      phase = Phase.SORTING
      if (!send) return open
    }
    nanoStep = NanoStep.NAMING
    val existing = index.folders().map { it.name }
    val sample = batch.shuffled(java.util.Random(batch.size.toLong())).take(ai.sample)
    val namesPrompt = buildString {
      append("You help someone organize their saved notes, links and files into folders.\n")
      append("Here are some of their saved items:\n")
      sample.forEachIndexed { i, d -> append(i + 1).append(". ").append(ai.line(d, 100)).append('\n') }
      if (existing.isNotEmpty()) append("They already have these folders: ").append(existing.joinToString(", ")).append(".\n")
      append("Suggest between 3 and ").append(ai.maxFolders).append(" folders that group these items by topic. Each folder name must be 1 to 3 words. ")
      append("Write only the folder names, one per line.\n")
    }
    val answer = ai.ask(namesPrompt, 160)
    val names = if (answer != null) parseFolders(answer, ai.maxFolders) else emptyList()
    if (names.isEmpty()) {
      if (!skipNano) note = failedNote(ai)
      return open
    }
    nanoStep = NanoStep.SORTING
    sortTotal = batch.size
    sortDone = 0
    val placed = HashMap<Long, Int>()
    var failures = 0
    val chunks = batch.chunked(ai.batch)
    for (group in chunks.chunked(ai.parallel)) {
      if (skipNano) break
      val replies = coroutineScope {
        group.map { chunk ->
          async {
            val prompt = buildString {
              append("Folders:\n")
              names.forEachIndexed { i, name -> append(i + 1).append(". ").append(name).append('\n') }
              append("0. None of these\n\nItems:\n")
              chunk.forEachIndexed { i, d -> append(i + 1).append(". ").append(ai.line(d, 120)).append('\n') }
              append("\nFor each item, write its number, an equals sign and the number of the folder it belongs in, one per line, like 1=2. ")
              append("Use 0 when no folder fits well. Write nothing else.\n")
            }
            ai.ask(prompt, 10 * chunk.size + 20)
          }
        }.awaitAll()
      }
      group.forEachIndexed { g, chunk ->
        val reply = replies[g]
        if (reply == null) {
          failures++
        } else {
          failures = 0
          for (match in Regex("(\\d+)\\s*[=:→>-]+\\s*(\\d+)").findAll(reply)) {
            val item = match.groupValues[1].toIntOrNull() ?: continue
            val folder = match.groupValues[2].toIntOrNull() ?: continue
            if (item in 1..chunk.size && folder in 1..names.size) placed[chunk[item - 1].message.id] = folder - 1
          }
        }
        sortDone += chunk.size
      }
      if (failures >= AI_FAILURES) {
        note = failedNote(ai)
        break
      }
    }
    names.forEachIndexed { i, name ->
      val items = batch.filter { placed[it.message.id] == i }.map { it.message }
      if (items.isNotEmpty()) add(name, items)
    }
    return open.filter { !placed.containsKey(it.message.id) }
  }

  private fun failedNote(ai: Ai): String {
    val error = ai.lastError
    return if (error != null) Lang.getString(R.string.XgramXDriveAiFailedWhy, ai.name, error) else Lang.getString(R.string.XgramXDriveAiFailed, ai.name)
  }

  /** Folder name lines (numbers, bullets, emoji and quotes cleaned off). */
  private fun parseFolders(text: String, max: Int): List<String> {
    val result = ArrayList<String>()
    val seen = HashSet<String>()
    for (raw in text.lines()) {
      var line = raw.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").replace("*", "").trim()
      // Anything before the first letter (an emoji, a bullet)
      var i = 0
      while (i < line.length && !Character.isLetterOrDigit(line.codePointAt(i))) i += Character.charCount(line.codePointAt(i))
      line = line.substring(i).trim().trim('"', '\'', ':', '.', ' ')
      if (line.isEmpty() || line.length > 32 || line.split(' ').size > 4) continue
      if (seen.add(line.lowercase())) result.add(line)
      if (result.size == max) break
    }
    return result
  }
}
