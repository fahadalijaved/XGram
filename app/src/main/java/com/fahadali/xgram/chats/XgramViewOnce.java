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
package com.fahadali.xgram.chats;

import android.content.Context;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.HapticMenuHelper;

import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/**
 * View once and self-destruct timers for photos and videos, from the send button's long-press menu of the attach
 * sheet's gallery and of the photo editor: "Send as view once" and "Self-destruct timer…".
 *
 * Telegram takes a self-destruct type only in private chats (TdApi InputMessagePhoto / InputMessageVideo
 * selfDestructType), and not with bots, in Saved Messages or on a scheduled message, so other chats get no item:
 * groups and channels have no view once to fake. Each self-destructing item goes as its own message
 * (TD.getCombineMode), never in an album, and never as a file (ImageGalleryFile.canSendAsFile).
 */
public final class XgramViewOnce {
  private XgramViewOnce () { }

  /** True when {@code chat} takes view-once and timed photos and videos. */
  public static boolean canSend (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR && !tdlib.isBotChat(chat) && !tdlib.isSelfChat(chat.id);
  }

  /** True when every one of {@code files} is a local photo or video (a self-destruct type goes on those only). */
  public static boolean canApply (@Nullable List<? extends ImageFile> files) {
    if (files == null || files.isEmpty()) {
      return false;
    }
    for (ImageFile file : files) {
      if (!(file instanceof ImageGalleryFile)) {
        return false;
      }
    }
    return true;
  }

  /** The two menu items, for {@code count} items about to be sent. */
  public static void addMenuItems (List<HapticMenuHelper.MenuItem> items, int count, Runnable onViewOnce, Runnable onTimer) {
    final CharSequence title = count <= 1 ? Lang.getString(R.string.XgramViewOnceSend) : Lang.getString(R.string.XgramViewOnceSendX, String.valueOf(count));
    final HapticMenuHelper.MenuItem viewOnce = count <= 1 ?
      new HapticMenuHelper.MenuItem(R.id.btn_xgramSendViewOnce, title, R.drawable.baseline_hot_once_24) :
      new HapticMenuHelper.MenuItem(R.id.btn_xgramSendViewOnce, title, Lang.getString(R.string.XgramViewOnceSeparate), R.drawable.baseline_hot_once_24);
    items.add(viewOnce.setOnClickListener((view, parentView, item) -> {
      if (view.getId() == R.id.btn_xgramSendViewOnce) {
        onViewOnce.run();
      }
      return true;
    }));
    items.add(new HapticMenuHelper.MenuItem(R.id.btn_xgramSendSelfDestruct, Lang.getString(R.string.XgramViewOnceTimer), R.drawable.xgram_r6_b1_timer_24).setOnClickListener((view, parentView, item) -> {
      if (view.getId() == R.id.btn_xgramSendSelfDestruct) {
        onTimer.run();
      }
      return true;
    }));
  }

  /**
   * Takes "Schedule" and "Send when online" out of {@code items} when one of {@code files} has a self-destruct type (the
   * editor's stopwatch): Telegram refuses scheduled self-destructing media, so both only showed an error. The menu is
   * built on every long press, so they come back once the timers are cleared.
   */
  public static void dropScheduling (List<HapticMenuHelper.MenuItem> items, @Nullable List<? extends ImageFile> files) {
    if (files == null) {
      return;
    }
    boolean any = false;
    for (ImageFile file : files) {
      if (file.getSelfDestructType() != null) {
        any = true;
        break;
      }
    }
    if (!any) {
      return;
    }
    for (int i = items.size() - 1; i >= 0; i--) {
      final HapticMenuHelper.MenuItem item = items.get(i);
      if (item.id == R.id.btn_sendScheduled || item.id == R.id.btn_sendOnceOnline) {
        item.performDestroy(); // the "Send when online" item's user status listener
        items.remove(i);
      }
    }
  }

  public interface Sender {
    /** Sends the files, true when they went. */
    boolean send ();
  }

  /**
   * Puts {@code type} on every one of {@code files} and sends them. When nothing went (a restriction), each file gets
   * its old type back, so a later plain send is not a view-once one. After a send the types stay: the media thread reads
   * them later (MessagesController.sendPhotosAndVideosCompressed).
   */
  public static boolean sendWith (List<? extends ImageFile> files, @Nullable TdApi.MessageSelfDestructType type, Sender sender) {
    final TdApi.MessageSelfDestructType[] old = new TdApi.MessageSelfDestructType[files.size()];
    for (int i = 0; i < old.length; i++) {
      final ImageFile file = files.get(i);
      old[i] = file.getSelfDestructType();
      file.setSelfDestructType(type);
    }
    boolean sent = false;
    try {
      sent = sender.send();
    } finally {
      if (!sent) {
        for (int i = 0; i < old.length; i++) {
          files.get(i).setSelfDestructType(old[i]);
        }
      }
    }
    return sent;
  }

  /**
   * The self-destruct timer picker (Off, View once, 1–20 s, 25–55 s, 1 min), View once picked at first, then
   * {@code onPicked} with the chosen type. Off sends nothing: the menu item is a timer, not a plain send.
   */
  public static void pickTimer (Tdlib tdlib, Context context, boolean useDarkMode, @Nullable List<? extends ImageFile> files, RunnableData<TdApi.MessageSelfDestructType> onPicked) {
    boolean allVideos = files != null && !files.isEmpty();
    if (files != null) {
      for (ImageFile file : files) {
        if (!(file instanceof ImageGalleryFile) || !((ImageGalleryFile) file).isVideo()) {
          allVideos = false;
          break;
        }
      }
    }
    tdlib.ui().showTTLPicker(context, new TdApi.MessageSelfDestructTypeImmediately(), true, useDarkMode, true, allVideos ? R.string.MessageLifetimeVideo : R.string.MessageLifetimePhoto, result -> {
      if (result.isOff()) {
        return;
      }
      onPicked.runWithData(result.isImmediate() ? new TdApi.MessageSelfDestructTypeImmediately() : new TdApi.MessageSelfDestructTypeTimer(result.getTtlTime()));
    });
  }
}
