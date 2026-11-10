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
package com.fahadali.xgram.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Background
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.filegen.VideoGenerationInfo
import org.thunderdog.challegram.mediaview.crop.CropState
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Settings
import java.io.File
import java.io.FileOutputStream

/**
 * XGram: a video as your profile photo, like official Telegram. Pick a video, choose which 10 seconds (a slider with
 * the frame there as a square preview); XGram's video converter (the one sending videos uses) makes the square, muted,
 * 800px clip Telegram asks for (MPEG4, at most 10 s and 2 MB), and its first frame is the still photo.
 */
object XgramVideoAvatar {
  private const val TAG = "XgramVideoAvatar"
  private const val REQUEST = 10091
  private const val MAX_US = 10_000_000L
  private const val SIZE = 800
  private const val BITRATE = 1_200_000L // 10 s stay well under 2 MB

  private class Source(val path: String, val width: Int, val height: Int, val durationUs: Long)

  /** Opens the flow from the profile photo menu: the system's video picker first. */
  @JvmStatic
  fun start(c: ViewController<*>) {
    val activity = c.context()
    val tdlib = c.tdlib()
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Intent(MediaStore.ACTION_PICK_IMAGES).setType("video/*")
    } else {
      Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*")
    }
    activity.putActivityResultHandler(REQUEST) { _, resultCode, data ->
      val uri = data?.data
      if (resultCode != Activity.RESULT_OK || uri == null) {
        return@putActivityResultHandler
      }
      UI.showToast(R.string.XgramVideoAvatarPreparing, Toast.LENGTH_SHORT)
      Background.instance().post {
        val source = runCatching { copyAndRead(activity, uri) }.onFailure { Log.w(TAG, "read", it) }.getOrNull()
        UI.post {
          if (source == null || source.width <= 0 || source.height <= 0 || source.durationUs <= 0) {
            UI.showToast(R.string.XgramVideoAvatarUnreadable, Toast.LENGTH_LONG)
          } else {
            showTrimmer(activity, tdlib, source)
          }
        }
      }
    }
    try {
      activity.startActivityForResult(intent, REQUEST)
    } catch (t: Throwable) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT)
    }
  }

  /** The picked video as a file of our own (the converter reads a path), with its shown size and length. */
  private fun copyAndRead(context: Context, uri: Uri): Source {
    val dir = File(context.cacheDir, "xgram_video_avatar").apply { mkdirs() }
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() } // older picks
    val file = File(dir, "source_${System.currentTimeMillis()}.mp4")
    context.contentResolver.openInputStream(uri).use { input ->
      requireNotNull(input)
      FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(file.absolutePath)
      val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
      val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
      val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
      val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      val turned = rotation == 90 || rotation == 270
      return Source(file.absolutePath, if (turned) h else w, if (turned) w else h, durationMs * 1000L)
    } finally {
      retriever.release()
    }
  }

  /** The centre square of the shown frame, as the converter's crop (fractions of the frame). */
  private fun squareCrop(source: Source): CropState {
    return if (source.width > source.height) {
      val side = source.height.toDouble() / source.width
      val left = (1.0 - side) / 2.0
      CropState(left, 0.0, left + side, 1.0, 0, 0f, 0)
    } else {
      val side = source.width.toDouble() / source.height
      val top = (1.0 - side) / 2.0
      CropState(0.0, top, 1.0, top + side, 0, 0f, 0)
    }
  }

  private fun save(tdlib: Tdlib, source: Source, startUs: Long) {
    val endUs = minOf(source.durationUs, startUs + MAX_US)
    val crop = squareCrop(source).takeIf { source.width != source.height }
    val limit = Settings.VideoLimit(Settings.VideoSize(SIZE), 30, BITRATE)
    val conversion = VideoGenerationInfo.makeConversion(
      0, true, limit, 0,
      if (startUs > 0) startUs else -1L, if (endUs < source.durationUs || startUs > 0) endUs else -1L,
      false, crop, File(source.path).lastModified()
    )
    val file = TdApi.InputFileGenerated(source.path, conversion, 0)
    UI.showToast(R.string.XgramVideoAvatarUploading, Toast.LENGTH_SHORT)
    tdlib.send(TdApi.SetProfilePhoto(TdApi.InputChatPhotoAnimation(file, 0.0), false)) { _, error ->
      UI.post {
        if (error != null) {
          Log.w(TAG, "SetProfilePhoto: ${error.code} ${error.message}")
          UI.showToast(R.string.XgramVideoAvatarFailed, Toast.LENGTH_LONG)
        } else {
          UI.showToast(R.string.XgramEmojiAvatarSet, Toast.LENGTH_SHORT)
        }
      }
    }
  }

  private fun showTrimmer(context: Context, tdlib: Tdlib, source: Source) {
    XgramR5ChatPickers.show(context) { dismiss ->
      TrimmerContent(source, dismiss) { startUs -> save(tdlib, source, startUs) }
    }
  }

  private fun clock(us: Long): String {
    val seconds = us / 1_000_000L
    return "%d:%02d".format(seconds / 60, seconds % 60)
  }

  @Composable
  private fun TrimmerContent(source: Source, dismiss: () -> Unit, onSave: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val maxStart = maxOf(0L, source.durationUs - MAX_US)
    var position by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    val startUs = (maxStart * position).toLong()
    LaunchedEffect(startUs / 250_000L) {
      // A frame a quarter second apart at most: the slider stays smooth
      val us = startUs
      frame = withContext(Dispatchers.IO) {
        runCatching {
          val retriever = MediaMetadataRetriever()
          try {
            retriever.setDataSource(source.path)
            retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          } finally {
            retriever.release()
          }
        }.getOrNull()
      } ?: frame
    }
    Surface(
      modifier = Modifier
        .widthIn(max = 420.dp)
        .padding(horizontal = 24.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
      shape = MaterialTheme.shapes.extraLarge,
      color = cs.surfaceContainerHigh
    ) {
      Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Lang.getString(R.string.XgramVideoAvatarTitle), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(160.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
          val bitmap = frame
          if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(160.dp))
          } else {
            CircularProgressIndicator()
          }
        }
        Spacer(Modifier.height(16.dp))
        Text(
          Lang.getString(R.string.XgramVideoAvatarRange, clock(startUs), clock(minOf(source.durationUs, startUs + MAX_US))),
          style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        if (maxStart > 0) {
          Slider(value = position, onValueChange = { position = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
          Text(Lang.getString(R.string.XgramVideoAvatarSlide), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = dismiss) {
            Text(Lang.getString(R.string.Cancel))
          }
          TextButton(onClick = {
            dismiss()
            onSave(startUs)
          }) {
            Text(Lang.getString(R.string.XgramEmojiAvatarSave))
          }
        }
      }
    }
  }
}
