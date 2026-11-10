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

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.sticker.StickerSmallView
import org.thunderdog.challegram.component.sticker.TGStickerObj
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.ui.EmojiStatusSelectorEmojiPage

/**
 * XGram: an emoji as your profile photo, like official Telegram: pick a custom emoji (the emoji status picker, in a
 * pick-only mode), then a background gradient with the emoji on it as a preview; Telegram makes the photo from them.
 */
object XgramEmojiAvatar {
  private const val TAG = "XgramEmojiAvatar"

  /** Background gradients, top to bottom, close to the official apps' avatar constructor. */
  private val GRADIENTS = listOf(
    0xFF5A7FFF to 0xFF2CA0F2,
    0xFF8E6BF4 to 0xFF5A7FFF,
    0xFFE47ACB to 0xFF8E6BF4,
    0xFFFF6D88 to 0xFFE85A9E,
    0xFFFF9945 to 0xFFFF6B59,
    0xFFFFC44A to 0xFFFF9230,
    0xFF6FD25A to 0xFF35B07D,
    0xFF3FD3C4 to 0xFF2A9FD6,
    0xFF8D9AA8 to 0xFF5E6A78,
    0xFF2E3A4A to 0xFF151B24
  )

  /** Opens the flow from the profile photo menu. */
  @JvmStatic
  fun start(c: ViewController<*>) {
    val tdlib = c.tdlib()
    val picker = EmojiStatusSelectorEmojiPage.Wrapper(c.context(), tdlib, c, object : EmojiStatusSelectorEmojiPage.AnimationsEmojiStatusSetDelegate {
      override fun onAnimationStart() = Unit
      override fun onAnimationEnd() = Unit
      override fun getDestX(): Int = 0
      override fun getDestY(): Int = 0
    })
    picker.xgramPickOnly { sticker ->
      if (sticker != null && sticker.customEmojiId != 0L) {
        UI.post { showComposer(c.context(), tdlib, sticker) { fill -> save(tdlib, sticker.customEmojiId, fill) } }
      }
    }
    picker.show()
  }

  private fun save(tdlib: Tdlib, customEmojiId: Long, fill: TdApi.BackgroundFill) {
    val photo = TdApi.InputChatPhotoSticker(TdApi.ChatPhotoSticker(TdApi.ChatPhotoStickerTypeCustomEmoji(customEmojiId), fill))
    tdlib.send(TdApi.SetProfilePhoto(photo, false)) { _, error ->
      UI.post {
        if (error != null) {
          Log.w(TAG, "SetProfilePhoto: ${error.code} ${error.message}")
          UI.showToast(Lang.getString(if (error.message == "PREMIUM_ACCOUNT_REQUIRED") R.string.XgramEmojiAvatarPremium else R.string.XgramEmojiAvatarFailed), Toast.LENGTH_LONG)
        } else {
          UI.showToast(R.string.XgramEmojiAvatarSet, Toast.LENGTH_SHORT)
        }
      }
    }
  }

  private fun showComposer(context: Context, tdlib: Tdlib, sticker: TGStickerObj, onSave: (TdApi.BackgroundFill) -> Unit) {
    XgramR5ChatPickers.show(context) { dismiss ->
      ComposerContent(tdlib, sticker, dismiss) { top, bottom ->
        onSave(TdApi.BackgroundFillGradient(top.toInt() and 0xFFFFFF, bottom.toInt() and 0xFFFFFF, 0))
      }
    }
  }

  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  private fun ComposerContent(tdlib: Tdlib, sticker: TGStickerObj, dismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var picked by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme
    Surface(
      modifier = Modifier
        .widthIn(max = 420.dp)
        .padding(horizontal = 24.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
      shape = MaterialTheme.shapes.extraLarge,
      color = cs.surfaceContainerHigh
    ) {
      Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Lang.getString(R.string.XgramEmojiAvatarTitle), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Spacer(Modifier.height(20.dp))
        val (top, bottom) = GRADIENTS[picked]
        Box(
          Modifier.size(144.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(top), Color(bottom)))),
          contentAlignment = Alignment.Center
        ) {
          // Telegram X's own sticker view: plays the custom emoji as it does everywhere
          AndroidView(
            factory = { ctx ->
              // Touches pass by: no sticker preview or menu on a long press in the dialog
              object : StickerSmallView(ctx, 0) {
                override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
              }.apply {
                init(tdlib)
                setSticker(sticker)
                attach()
              }
            },
            onRelease = { view ->
              view.detach()
              view.performDestroy()
            },
            modifier = Modifier.size(96.dp)
          )
        }
        Spacer(Modifier.height(20.dp))
        FlowRow(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          GRADIENTS.forEachIndexed { index, (a, b) ->
            val selected = index == picked
            Box(
              Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(a), Color(b))))
                .then(if (selected) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                .selectable(selected = selected, role = Role.RadioButton) { picked = index }
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = dismiss) {
            Text(Lang.getString(R.string.Cancel))
          }
          TextButton(onClick = {
            dismiss()
            onSave(top, bottom)
          }) {
            Text(Lang.getString(R.string.XgramEmojiAvatarSave))
          }
        }
      }
    }
  }
}
