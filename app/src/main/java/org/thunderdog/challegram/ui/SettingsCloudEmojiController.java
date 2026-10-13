/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 20/10/2019
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;

import com.fahadali.xgram.appearance.XgramSystemEmoji;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;

import me.vkryl.core.lambda.RunnableData;

public class SettingsCloudEmojiController extends SettingsCloudController<Settings.EmojiPack> {
  public SettingsCloudEmojiController (Context context, Tdlib tdlib) {
    super(context, tdlib, Settings.TUTORIAL_EMOJI_PACKS, R.string.EmojiInfo, R.string.EmojiCurrent, R.string.EmojiBuiltIn, R.string.EmojiLoaded, R.string.EmojiUpdate, R.string.EmojiInstalling);
  }

  @Override
  public int getId () {
    return R.id.controller_emojiSets;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.EmojiSets);
  }

  @Override
  protected Settings.EmojiPack getCurrentSetting () {
    return XgramSystemEmoji.isEnabled() ? XgramSystemEmoji.pack() : Settings.instance().getEmojiPack(); // XGram: System emoji
  }

  @Override
  protected void getSettings (RunnableData<List<Settings.EmojiPack>> callback) {
    tdlib.getEmojiPacks(packs -> callback.runWithData(XgramSystemEmoji.addTo(packs))); // XGram: System emoji next to the sets
  }

  @Override
  protected void applySetting (Settings.EmojiPack setting) {
    // XGram: System emoji; picking a set turns it off
    if (XgramSystemEmoji.isSystemPack(setting)) {
      XgramSystemEmoji.setEnabled(true);
    } else {
      XgramSystemEmoji.applyPack(setting);
    }
    if (getStickersAndEmojiController() != null) {
      getStickersAndEmojiController().updateSelectedEmoji();
    }
  }

  @Override
  protected int getSettingStatus (Settings.EmojiPack setting) {
    return XgramSystemEmoji.isSystemPack(setting) ? R.string.XgramEmojiSystemHint : 0; // XGram
  }

  @Override
  protected void drawSettingPreview (Canvas c, Settings.EmojiPack setting, ImageReceiver receiver) {
    if (XgramSystemEmoji.isSystemPack(setting)) { // XGram: the phone's emoji, drawn rather than downloaded
      XgramSystemEmoji.drawPreview(c, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
    }
  }
}
