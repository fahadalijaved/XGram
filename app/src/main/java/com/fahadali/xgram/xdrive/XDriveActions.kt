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

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.U
import org.thunderdog.challegram.core.Background
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.data.TD
import org.thunderdog.challegram.mediaview.MediaViewController
import org.thunderdog.challegram.mediaview.data.MediaItem
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibUi
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.ui.ShareController
import java.io.File

/** What XDrive's items and folders do (opening, sharing, deleting, folders, new notes and uploads). UI thread. */
object XDriveActions {
  private const val REQUEST_FILES = 10081
  private const val REQUEST_MEDIA = 10082

  private fun toast(text: String) = UI.showToast(text, Toast.LENGTH_SHORT)

  /** Opens an item as itself: photos and videos in the viewer, music and voice in the player, links, files in an app. */
  fun open(c: ViewController<*>, message: TdApi.Message) {
    val tdlib = c.tdlib()
    when (val content = message.content) {
      is TdApi.MessagePhoto, is TdApi.MessageVideo, is TdApi.MessageAnimation, is TdApi.MessageVideoNote -> {
        val item = MediaItem.valueOf(c.context(), tdlib, message)
        if (item != null) {
          MediaViewController.openFromMedia(c, item, TdApi.SearchMessagesFilterPhotoAndVideo(), true)
        } else {
          showInChat(c, message.id)
        }
      }
      is TdApi.MessageAudio, is TdApi.MessageVoiceNote -> tdlib.context().player().playPauseMessage(tdlib, message, null)
      is TdApi.MessageText -> {
        val url = XDriveItems.urlOf(message)
        if (url != null) {
          tdlib.ui().openUrl(c, url, null)
        } else {
          showInChat(c, message.id)
        }
      }
      is TdApi.MessageDocument -> openDocument(c, message, content.document)
      else -> showInChat(c, message.id)
    }
  }

  private fun openDocument(c: ViewController<*>, message: TdApi.Message, document: TdApi.Document) {
    val tdlib = c.tdlib()
    val mime = document.mimeType ?: ""
    val opened: (TdApi.File) -> Unit = { file ->
      if (mime.startsWith("image/") || mime.startsWith("video/")) {
        Background.instance().post {
          val item = MediaItem.valueOf(c.context(), tdlib, TdApi.Document(document.fileName, mime, document.minithumbnail, document.thumbnail, file), null)
          c.runOnUiThreadOptional {
            if (item != null) {
              item.setSourceMessageId(message.chatId, message.id)
              MediaViewController.openFromMedia(c, item, null, true)
            } else {
              U.openFile(c, document.fileName, File(file.local.path), mime, 0)
            }
          }
        }
      } else {
        U.openFile(c, document.fileName, File(file.local.path), mime, 0)
      }
    }
    val file = document.document
    if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
      opened(file)
      return
    }
    toast(Lang.getString(R.string.XgramXDriveDownloading))
    tdlib.send(TdApi.DownloadFile(file.id, 32, 0, 0, true), Tdlib.ResultHandler<TdApi.File> { result, error ->
      c.runOnUiThreadOptional {
        if (result != null && result.local.isDownloadingCompleted) {
          opened(result)
        } else if (error != null) {
          UI.showError(error)
        }
      }
    })
  }

  /** The message in Saved Messages itself (past XDrive's routing). */
  fun showInChat(c: ViewController<*>, messageId: Long) {
    val tdlib = c.tdlib()
    val chatId = tdlib.selfChatId()
    val params = TdlibUi.ChatOpenParameters().keepStack().xgramSkipDriveRouting()
    if (messageId != 0L) {
      params.highlightMessage(tgx.td.MessageId(chatId, messageId)).ensureHighlightAvailable()
    }
    tdlib.ui().openChat(c, chatId, params)
  }

  fun share(c: ViewController<*>, messages: List<TdApi.Message>) {
    if (messages.isEmpty()) return
    val share = ShareController(c.context(), c.tdlib())
    share.setArguments(ShareController.Args(messages.sortedBy { it.id }.toTypedArray()))
    share.show()
  }

  fun delete(host: XgramComposeHost, messages: List<TdApi.Message>, after: () -> Unit) {
    if (messages.isEmpty()) return
    val tdlib = host.tdlib
    val text = if (messages.size == 1) Lang.getString(R.string.XgramXDriveDeleteOne) else Lang.getString(R.string.XgramXDriveDeleteMany, Lang.plural(R.string.xMessages, messages.size.toLong()))
    host.confirm(ConfirmSpec(text = text, confirm = Lang.getString(R.string.Delete), destructive = true) {
      tdlib.send(TdApi.DeleteMessages(tdlib.selfChatId(), messages.map { it.id }.toLongArray(), true), Tdlib.ResultHandler<TdApi.Ok> { _, error ->
        if (error != null) UI.post { UI.showError(error) }
      })
      after()
    })
  }

  /** A folder for [messageIds] (one from the list, or a new one): [onChosen] gets its id. */
  fun chooseFolder(host: XgramComposeHost, messageIds: LongArray, onChosen: (String) -> Unit) {
    val index = XDriveIndex.of(host.tdlib)
    index.load {
      val folders = index.folders()
      val newKey = "\u0000new"
      val options = ArrayList<Pair<String, String>>()
      for (folder in folders) options.add(Pair(folder.name, folder.id))
      options.add(Pair(Lang.getString(R.string.XgramXDriveNewFolder) + "…", newKey))
      // Checked only when all of them are in that folder: a choice that is checked does nothing, and a mixed selection
      // still has to be able to go into the folder one of them is in
      val first = messageIds.firstOrNull()?.let { index.folderOf(it) }
      val current = if (first != null && messageIds.all { index.folderOf(it) == first }) first else ""
      host.choose(ChoiceSpec(title = Lang.getString(R.string.XgramXDriveChooseFolder), options = options, selected = current) { id ->
        if (id == newKey) {
          newFolder(host) { folder -> onChosen(folder.id) }
        } else if (index.findFolder(id) != null) {
          onChosen(id)
        }
      })
    }
  }

  fun newFolder(host: XgramComposeHost, after: ((XDriveIndex.Folder) -> Unit)? = null) {
    val index = XDriveIndex.of(host.tdlib)
    host.prompt(PromptSpec(title = Lang.getString(R.string.XgramXDriveNewFolder), hint = Lang.getString(R.string.XgramXDriveFolderName), confirm = Lang.getString(R.string.XgramXDriveCreate)) { text ->
      val name = text.trim()
      when {
        name.isEmpty() -> Unit
        index.findFolderByName(name) != null -> toast(Lang.getString(R.string.XgramXDriveFolderExists, name))
        else -> {
          val folder = index.createFolder(name)
          after?.invoke(folder)
        }
      }
    })
  }

  fun renameFolder(host: XgramComposeHost, folder: XDriveIndex.Folder) {
    val index = XDriveIndex.of(host.tdlib)
    host.prompt(PromptSpec(title = Lang.getString(R.string.XgramXDriveRename), hint = Lang.getString(R.string.XgramXDriveFolderName), initial = folder.name) { text ->
      val name = text.trim()
      val existing = if (name.isEmpty()) null else index.findFolderByName(name)
      when {
        name.isEmpty() -> Unit
        existing != null && existing.id != folder.id -> toast(Lang.getString(R.string.XgramXDriveFolderExists, name))
        else -> index.renameFolder(folder.id, name)
      }
    })
  }

  fun deleteFolder(host: XgramComposeHost, folder: XDriveIndex.Folder, after: () -> Unit) {
    host.confirm(ConfirmSpec(text = Lang.getString(R.string.XgramXDriveDeleteFolderConfirm, folder.name), confirm = Lang.getString(R.string.XgramXDriveDeleteFolder), destructive = true) {
      XDriveIndex.of(host.tdlib).deleteFolder(folder.id)
      after()
    })
  }

  /** A text note into XDrive, in [folderId] when one is shown. */
  fun newNote(host: XgramComposeHost, folderId: String?) {
    val tdlib = host.tdlib
    host.prompt(PromptSpec(title = Lang.getString(R.string.XgramXDriveNewNote), hint = Lang.getString(R.string.XgramXDriveNoteHint), confirm = Lang.getString(R.string.Save)) { text ->
      if (text.isNotBlank()) {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text.trim(), emptyArray()), null, false)
        send(tdlib, content, folderId)
      }
    })
  }

  private fun sendOptions() = TdApi.MessageSendOptions(null, true, false, false, false, 0, false, null, 0, 0, false)

  private fun send(tdlib: Tdlib, content: TdApi.InputMessageContent, folderId: String?) {
    tdlib.send(TdApi.SendMessage(tdlib.selfChatId(), null, null, sendOptions(), null, content), Tdlib.ResultHandler<TdApi.Message> { message, error ->
      UI.post {
        if (message == null) {
          if (error != null) UI.showError(error)
        } else if (folderId != null) {
          XDriveIndex.of(tdlib).setFolderWhenSent(longArrayOf(message.id), folderId)
        }
      }
    })
  }

  /** The system's picker for files ([media]: photos and videos); they go to XDrive as files (originals). */
  fun upload(host: XgramComposeHost, media: Boolean, folderId: String?) {
    val activity = host.activity
    val tdlib = host.tdlib
    val request = if (media) REQUEST_MEDIA else REQUEST_FILES
    val intent = if (media && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Intent(MediaStore.ACTION_PICK_IMAGES).putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100)
    } else {
      Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if (media) "*/*" else "*/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).also { if (media) it.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*")) }
    }
    activity.putActivityResultHandler(request) { _, resultCode, data ->
      if (resultCode != Activity.RESULT_OK || data == null) return@putActivityResultHandler
      val uris = LinkedHashSet<Uri>()
      data.data?.let { uris.add(it) }
      val clip: ClipData? = data.clipData
      if (clip != null) for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
      if (uris.isEmpty()) return@putActivityResultHandler
      Background.instance().post {
        val contents = uris.map { uri ->
          TdApi.InputMessageDocument(TdApi.InputDocument(TD.createInputFile(uri.toString()), null, true), null)
        }
        UI.post {
          for (content in contents) send(tdlib, content, folderId)
          toast(Lang.getString(R.string.XgramXDriveUploading, Lang.plural(R.string.xFiles, contents.size.toLong())))
        }
      }
    }
    try {
      activity.startActivityForResult(intent, request)
    } catch (t: Throwable) {
      toast(Lang.getString(R.string.NoFilePicker))
    }
  }
}
