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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.fahadali.xgram.history.XgramMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.MessageListener
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.tool.Strings
import org.thunderdog.challegram.tool.UI
import kotlin.coroutines.resume

/** What an item of XDrive is, by its message's content. */
enum class XDriveKind { PHOTO, VIDEO, GIF, FILE, MUSIC, VOICE, LINK, NOTE, OTHER }

/** XDrive's views: everything, or one kind (each its own server query when it has a filter). */
enum class XDriveView(val title: Int, val icon: Int, val grid: Boolean) {
  ALL(R.string.XgramXDriveAll, R.drawable.baseline_cloud_24, false),
  PHOTOS(R.string.XgramXDrivePhotosOnly, R.drawable.baseline_image_24, true),
  VIDEOS(R.string.XgramXDriveVideos, R.drawable.baseline_videocam_24, true),
  FILES(R.string.XgramXDriveFiles, R.drawable.baseline_insert_drive_file_24, false),
  LINKS(R.string.XgramXDriveLinks, R.drawable.baseline_link_24, false),
  NOTES(R.string.XgramXDriveNotes, R.drawable.baseline_format_text_24, false),
  MUSIC(R.string.XgramXDriveMusic, R.drawable.baseline_music_note_24, false),
  VOICE(R.string.XgramXDriveVoice, R.drawable.baseline_mic_24, false),
  GIFS(R.string.XgramXDriveGifs, R.drawable.deproko_baseline_gif_24, true);

  /** The server's filter for this view; null: read the history (ALL, NOTES) and keep what fits. */
  fun filter(): TdApi.SearchMessagesFilter? = when (this) {
    PHOTOS -> TdApi.SearchMessagesFilterPhoto()
    VIDEOS -> TdApi.SearchMessagesFilterVideo()
    FILES -> TdApi.SearchMessagesFilterDocument()
    LINKS -> TdApi.SearchMessagesFilterUrl()
    MUSIC -> TdApi.SearchMessagesFilterAudio()
    VOICE -> TdApi.SearchMessagesFilterVoiceNote()
    GIFS -> TdApi.SearchMessagesFilterAnimation()
    ALL, NOTES -> null
  }

  fun accepts(kind: XDriveKind): Boolean = when (this) {
    ALL -> true
    PHOTOS -> kind == XDriveKind.PHOTO
    VIDEOS -> kind == XDriveKind.VIDEO
    FILES -> kind == XDriveKind.FILE
    LINKS -> kind == XDriveKind.LINK
    NOTES -> kind == XDriveKind.NOTE
    MUSIC -> kind == XDriveKind.MUSIC
    VOICE -> kind == XDriveKind.VOICE
    GIFS -> kind == XDriveKind.GIF
  }
}

/** Where a list comes from: all of XDrive, one folder, or what is in no folder. */
sealed class XDriveScope {
  object Everything : XDriveScope()
  object Unsorted : XDriveScope()
  data class InFolder(val folderId: String) : XDriveScope()
}

object XDriveItems {
  fun kindOf(message: TdApi.Message): XDriveKind = when (val c = message.content) {
    is TdApi.MessagePhoto -> XDriveKind.PHOTO
    is TdApi.MessageVideo, is TdApi.MessageVideoNote -> XDriveKind.VIDEO
    is TdApi.MessageAnimation -> XDriveKind.GIF
    is TdApi.MessageDocument -> when {
      c.document.mimeType.startsWith("image/") -> XDriveKind.PHOTO
      c.document.mimeType.startsWith("video/") -> XDriveKind.VIDEO
      else -> XDriveKind.FILE
    }
    is TdApi.MessageAudio -> XDriveKind.MUSIC
    is TdApi.MessageVoiceNote -> XDriveKind.VOICE
    is TdApi.MessageText -> if (urlOf(message) != null) XDriveKind.LINK else XDriveKind.NOTE
    else -> XDriveKind.OTHER
  }

  /** The first link of a text (an entity or the link preview's). */
  fun urlOf(message: TdApi.Message): String? {
    val text = (message.content as? TdApi.MessageText) ?: return null
    text.linkPreview?.url?.takeIf { it.isNotEmpty() }?.let { return it }
    for (entity in text.text.entities ?: emptyArray()) {
      when (val type = entity.type) {
        is TdApi.TextEntityTypeTextUrl -> return type.url
        is TdApi.TextEntityTypeUrl -> return text.text.text.substring(entity.offset, (entity.offset + entity.length).coerceAtMost(text.text.text.length))
      }
    }
    return null
  }

  /** The item's headline: a file's name, a link's title, a text's first line, or what it is. */
  fun title(message: TdApi.Message): String {
    when (val c = message.content) {
      is TdApi.MessageDocument -> if (c.document.fileName.isNotEmpty()) return c.document.fileName
      is TdApi.MessageAudio -> {
        val a = c.audio
        if (a.title.isNotEmpty()) return if (a.performer.isNotEmpty()) "${a.performer} – ${a.title}" else a.title
        if (a.fileName.isNotEmpty()) return a.fileName
      }
      is TdApi.MessageText -> {
        c.linkPreview?.let { p -> if (p.title.isNotEmpty()) return p.title }
      }
      else -> Unit
    }
    val text = XgramMedia.formattedTextOf(message.content)?.text?.trim()
    if (!text.isNullOrEmpty()) {
      val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: text
      return if (line.length > 140) line.substring(0, 140) + "…" else line
    }
    return when (kindOf(message)) {
      XDriveKind.PHOTO -> Lang.getString(R.string.Photo)
      XDriveKind.VIDEO -> Lang.getString(R.string.Video)
      XDriveKind.GIF -> Lang.getString(R.string.Gif)
      XDriveKind.VOICE -> Lang.getString(R.string.XgramXDriveVoiceOne)
      XDriveKind.MUSIC -> Lang.getString(R.string.Audio)
      else -> Lang.getString(R.string.File)
    }
  }

  /** The supporting line: size, the link's site, a note's next line, and the date. */
  fun subtitle(message: TdApi.Message): String {
    val date = com.fahadali.xgram.appearance.XgramR6B5Pages.shortDate(message.date.toLong())
    val detail: String? = when (val c = message.content) {
      is TdApi.MessageDocument -> Strings.buildSize(c.document.document.size)
      is TdApi.MessageAudio -> Strings.buildSize(c.audio.audio.size)
      is TdApi.MessageVideo -> Strings.buildSize(c.video.video.size)
      is TdApi.MessageVoiceNote -> Strings.buildDuration(c.voiceNote.duration.toLong())
      is TdApi.MessageText -> urlOf(message)?.let { hostOf(it) } ?: c.text.text.trim().lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.trim()?.take(100)
      else -> null
    }
    return if (detail.isNullOrEmpty()) date else if (date.isEmpty()) detail else "$detail · $date"
  }

  private fun hostOf(url: String): String = try {
    android.net.Uri.parse(if (url.contains("://")) url else "https://$url").host?.removePrefix("www.") ?: url
  } catch (t: Throwable) {
    url
  }

  /** Text, caption and file name, for a search inside a folder. */
  fun matches(message: TdApi.Message, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    if (title(message).lowercase().contains(q)) return true
    val text = XgramMedia.formattedTextOf(message.content)?.text
    return text != null && text.lowercase().contains(q)
  }

  /** The thumbnail file (decodable) and the tiny inline preview of an item, either may be missing. */
  fun thumbOf(message: TdApi.Message): Pair<TdApi.File?, TdApi.Minithumbnail?> {
    fun usable(t: TdApi.Thumbnail?): TdApi.File? = t?.takeIf {
      it.format is TdApi.ThumbnailFormatJpeg || it.format is TdApi.ThumbnailFormatPng || it.format is TdApi.ThumbnailFormatWebp
    }?.file
    return when (val c = message.content) {
      is TdApi.MessagePhoto -> {
        val sizes = c.photo.sizes
        val pick = sizes.filter { it.width >= 200 }.minByOrNull { it.width } ?: sizes.maxByOrNull { it.width }
        Pair(pick?.photo, c.photo.minithumbnail)
      }
      is TdApi.MessageVideo -> Pair(usable(c.video.thumbnail), c.video.minithumbnail)
      is TdApi.MessageVideoNote -> Pair(usable(c.videoNote.thumbnail), c.videoNote.minithumbnail)
      is TdApi.MessageAnimation -> Pair(usable(c.animation.thumbnail), c.animation.minithumbnail)
      is TdApi.MessageDocument -> Pair(usable(c.document.thumbnail), c.document.minithumbnail)
      is TdApi.MessageAudio -> Pair(usable(c.audio.albumCoverThumbnail), c.audio.albumCoverMinithumbnail)
      is TdApi.MessageText -> {
        val photo = (c.linkPreview?.type as? TdApi.LinkPreviewTypePhoto)?.photo
        if (photo != null) {
          val pick = photo.sizes.filter { it.width >= 100 }.minByOrNull { it.width } ?: photo.sizes.maxByOrNull { it.width }
          Pair(pick?.photo, photo.minithumbnail)
        } else {
          Pair(null, null)
        }
      }
      else -> Pair(null, null)
    }
  }

  /** The item's main file (to download and open), null for texts. */
  fun fileOf(message: TdApi.Message): TdApi.File? = when (val c = message.content) {
    is TdApi.MessageDocument -> c.document.document
    is TdApi.MessagePhoto -> c.photo.sizes.maxByOrNull { it.width }?.photo
    is TdApi.MessageVideo -> c.video.video
    is TdApi.MessageAnimation -> c.animation.animation
    is TdApi.MessageAudio -> c.audio.audio
    is TdApi.MessageVoiceNote -> c.voiceNote.voice
    is TdApi.MessageVideoNote -> c.videoNote.video
    else -> null
  }
}

/** Decoded thumbnails, shared by every XDrive list (a few MB at most). */
object XDriveThumbs {
  private val cache = object : LruCache<Int, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
  }
  private val minis = LruCache<String, Bitmap>(200)

  fun cached(fileId: Int?): Bitmap? = if (fileId == null || fileId == 0) null else cache.get(fileId)

  fun mini(mini: TdApi.Minithumbnail?): Bitmap? {
    if (mini == null || mini.data == null || mini.data.isEmpty()) return null
    val key = "${mini.width}x${mini.height}:${mini.data.contentHashCode()}"
    minis.get(key)?.let { return it }
    return try {
      BitmapFactory.decodeByteArray(mini.data, 0, mini.data.size)?.also { minis.put(key, it) }
    } catch (t: Throwable) {
      null
    }
  }

  /** Downloads (if needed) and decodes a thumbnail file, at most [maxSize] pixels on its longer side. */
  suspend fun load(tdlib: Tdlib, file: TdApi.File, maxSize: Int): Bitmap? {
    cached(file.id)?.let { return it }
    val path = if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) file.local.path else download(tdlib, file.id, 1)
    if (path.isNullOrEmpty()) return null
    val bitmap = withContext(Dispatchers.IO) { decode(path, maxSize) } ?: return null
    cache.put(file.id, bitmap)
    return bitmap
  }

  suspend fun download(tdlib: Tdlib, fileId: Int, priority: Int): String? = suspendCancellableCoroutine { cont ->
    tdlib.send(TdApi.DownloadFile(fileId, priority, 0, 0, true), Tdlib.ResultHandler<TdApi.File> { result, _ ->
      if (cont.isActive) {
        cont.resume(if (result != null && result.local.isDownloadingCompleted) result.local.path else null)
      }
    })
  }

  private fun decode(path: String, maxSize: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) {
      sample *= 2
    }
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (t: Throwable) {
    null
  }
}

/**
 * One XDrive list (a view, a scope, a search): pages of messages from TDLib, newest first. The server filters by kind
 * and searches; a folder's items come from the index. Changes in Saved Messages reload it. UI thread.
 */
class XDriveFeed(
  private val tdlib: Tdlib,
  val view: XDriveView,
  val scope: XDriveScope,
  val query: String
) : MessageListener {
  val items = mutableStateListOf<TdApi.Message>()
  val loading = mutableStateOf(false)
  val reachedEnd = mutableStateOf(false)

  private val chatId = tdlib.selfChatId()
  private val index = XDriveIndex.of(tdlib)
  private val seen = HashSet<Long>()
  private var fromId = 0L
  private var generation = 0
  private var reloadPosted = false
  private var attached = false

  fun attach() {
    if (!attached && chatId != 0L) {
      attached = true
      tdlib.listeners().subscribeToMessageUpdates(chatId, this)
    }
    if (items.isEmpty() && !loading.value && !reachedEnd.value) {
      loadMore()
    }
  }

  fun detach() {
    if (attached) {
      attached = false
      tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this)
    }
    generation++
  }

  fun reload() {
    generation++
    items.clear()
    seen.clear()
    fromId = 0
    reachedEnd.value = false
    loading.value = false
    loadMore()
  }

  private fun keep(message: TdApi.Message): Boolean {
    if (message.sendingState is TdApi.MessageSendingStateFailed) return false
    if (index.isIndexMessage(message.id) || XDriveIndex.isIndexDocument(message)) return false
    val kind = XDriveItems.kindOf(message)
    if (kind == XDriveKind.OTHER) {
      // Only things worth keeping (no service messages): in Everything
      val c = message.content
      val keepable = c is TdApi.MessageSticker || c is TdApi.MessageLocation || c is TdApi.MessageVenue || c is TdApi.MessageContact || c is TdApi.MessagePoll
      if (view != XDriveView.ALL || !keepable) return false
    }
    if (!view.accepts(kind)) return false
    when (scope) {
      XDriveScope.Unsorted -> if (index.folderOf(message.id) != null) return false
      is XDriveScope.InFolder -> if (index.folderOf(message.id) != scope.folderId) return false
      XDriveScope.Everything -> Unit
    }
    return true
  }

  fun loadMore() {
    if (loading.value || reachedEnd.value || chatId == 0L) return
    loading.value = true
    val gen = generation
    when {
      scope is XDriveScope.InFolder -> loadFolder(gen, scope.folderId)
      view.filter() != null || query.isNotBlank() -> loadSearch(gen)
      else -> loadHistory(gen, 0)
    }
  }

  private fun done(gen: Int, added: List<TdApi.Message>, end: Boolean) {
    if (gen != generation) return
    for (message in added) {
      if (seen.add(message.id)) items.add(message)
    }
    loading.value = false
    reachedEnd.value = end
  }

  /** A server search: by kind, by words, or both; paged with nextFromMessageId. */
  private fun loadSearch(gen: Int) {
    val filter = view.filter()
    tdlib.send(TdApi.SearchChatMessages(chatId, null, query.trim(), null, fromId, 0, 50, filter), Tdlib.ResultHandler<TdApi.FoundChatMessages> { found, _ ->
      UI.post {
        val list = found?.messages?.filterNotNull()?.filter { keep(it) } ?: emptyList()
        val next = found?.nextFromMessageId ?: 0L
        fromId = next
        done(gen, list, found == null || next == 0L)
        // Notes and the unsorted view keep only some of a page: read on while too few came
        if (gen == generation && !reachedEnd.value && items.size < 30 && list.size < 10) loadMore()
      }
    })
  }

  /** The history, newest first, keeping what fits (everything, notes, unsorted). */
  private fun loadHistory(gen: Int, depth: Int) {
    tdlib.send(TdApi.GetChatHistory(chatId, fromId, 0, 100, false), Tdlib.ResultHandler<TdApi.Messages> { result, _ ->
      UI.post {
        if (gen != generation) return@post
        val messages = result?.messages?.filterNotNull() ?: emptyList()
        var oldest = fromId
        var fresh = 0
        val kept = ArrayList<TdApi.Message>()
        for (message in messages) {
          if (message.id == fromId || seen.contains(message.id)) continue
          fresh++
          if (oldest == 0L || message.id < oldest) oldest = message.id
          if (keep(message)) kept.add(message)
        }
        fromId = oldest
        val end = fresh == 0
        done(gen, kept, end)
        if (!end && kept.size < 15 && depth < 20) {
          loading.value = true
          loadHistory(gen, depth + 1)
        }
      }
    })
  }

  /** A folder: its messages by id from the index (deleted ones are dropped from it). */
  private fun loadFolder(gen: Int, folderId: String) {
    index.load {
      val ids = index.messagesIn(folderId)
      if (ids.isEmpty()) {
        done(gen, emptyList(), true)
        return@load
      }
      tdlib.send(TdApi.GetMessages(chatId, ids), Tdlib.ResultHandler<TdApi.Messages> { result, _ ->
        UI.post {
          val found = result?.messages ?: emptyArray()
          val missing = ArrayList<Long>()
          val list = ArrayList<TdApi.Message>()
          for (i in ids.indices) {
            val message = found.getOrNull(i)
            if (message == null) {
              missing.add(ids[i])
            } else if (keep(message) && XDriveItems.matches(message, query)) {
              list.add(message)
            }
          }
          if (missing.isNotEmpty() && result != null) index.forget(missing)
          done(gen, list, true)
        }
      })
    }
  }

  private fun scheduleReload() {
    if (reloadPosted) return
    reloadPosted = true
    UI.post({
      reloadPosted = false
      if (attached) reload()
    }, 600)
  }

  // Saved Messages changed (TDLib thread)

  override fun onNewMessage(message: TdApi.Message) {
    if (message.sendingState == null) UI.post { if (attached) scheduleReload() }
  }

  override fun onMessageSendSucceeded(message: TdApi.Message, oldMessageId: Long) {
    UI.post { if (attached) scheduleReload() }
  }

  override fun onMessagesDeleted(chatId: Long, messageIds: LongArray) {
    UI.post {
      if (!attached) return@post
      val ids = messageIds.toHashSet()
      items.removeAll { ids.contains(it.id) }
    }
  }

  override fun onMessageContentChanged(chatId: Long, messageId: Long, newContent: TdApi.MessageContent) {
    UI.post { if (attached && items.any { it.id == messageId }) scheduleReload() }
  }
}
