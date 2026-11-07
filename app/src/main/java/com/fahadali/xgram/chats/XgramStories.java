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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XGram: stories, like official Telegram where Telegram X has none: a ring around the photo of a chat with active
 * stories (coloured while there is one you haven't seen), and its stories in the media viewer, one per page with the
 * caption (photos and videos), marked as seen (openStory/closeStory).
 */
public final class XgramStories {
  private XgramStories () { }

  public static boolean hasStories (Tdlib tdlib, long chatId) {
    if (!com.fahadali.xgram.XgramSettings.instance().showStories()) {
      return false; // Show stories off: no ring, the photo opens the chat, no profile item
    }
    final TdApi.ChatActiveStories stories = tdlib.chatActiveStories(chatId);
    return stories != null && stories.stories != null && stories.stories.length > 0;
  }

  public static boolean hasUnseen (Tdlib tdlib, long chatId) {
    final TdApi.ChatActiveStories stories = tdlib.chatActiveStories(chatId);
    if (stories == null || stories.stories == null) {
      return false;
    }
    for (TdApi.StoryInfo info : stories.stories) {
      if (info.storyId > stories.maxReadStoryId) {
        return true;
      }
    }
    return false;
  }

  /** Opens the chat's active stories in the media viewer, starting at the first one not seen yet. */
  public static void open (ViewController<?> c, long chatId) {
    final Tdlib tdlib = c.tdlib();
    final TdApi.ChatActiveStories known = tdlib.chatActiveStories(chatId);
    if (known != null && known.stories != null && known.stories.length > 0) {
      load(c, chatId, known);
      return;
    }
    tdlib.send(new TdApi.GetChatActiveStories(chatId), (stories, error) -> {
      if (stories != null && stories.stories != null && stories.stories.length > 0) {
        load(c, chatId, stories);
      } else {
        UI.showToast(R.string.XgramStoriesNone, android.widget.Toast.LENGTH_SHORT);
      }
    });
  }

  private static void load (ViewController<?> c, long chatId, TdApi.ChatActiveStories active) {
    final Tdlib tdlib = c.tdlib();
    final int count = active.stories.length;
    final TdApi.Story[] stories = new TdApi.Story[count];
    final AtomicInteger left = new AtomicInteger(count);
    for (int i = 0; i < count; i++) {
      final int index = i;
      tdlib.send(new TdApi.GetStory(chatId, active.stories[i].storyId, false), (story, error) -> {
        stories[index] = story;
        if (left.decrementAndGet() == 0) {
          c.runOnUiThreadOptional(() -> show(c, chatId, active, stories));
        }
      });
    }
  }

  private static @Nullable MediaItem toItem (ViewController<?> c, TdApi.Story story) {
    if (story == null || story.content == null) {
      return null;
    }
    final TdApi.FormattedText caption = story.caption != null ? story.caption : new TdApi.FormattedText("", new TdApi.TextEntity[0]);
    switch (story.content.getConstructor()) {
      case TdApi.StoryContentPhoto.CONSTRUCTOR:
        return new MediaItem(c.context(), c.tdlib(), ((TdApi.StoryContentPhoto) story.content).photo, caption);
      case TdApi.StoryContentVideo.CONSTRUCTOR: {
        final TdApi.StoryVideo video = ((TdApi.StoryContentVideo) story.content).video;
        if (video == null || video.video == null) {
          return null;
        }
        final TdApi.Video asVideo = new TdApi.Video((int) Math.round(video.duration), video.width, video.height, "story.mp4", "video/mp4", video.hasStickers, true, video.minithumbnail, video.thumbnail, video.video);
        return new MediaItem(c.context(), c.tdlib(), asVideo, new TdApi.AlternativeVideo[0], null, caption, false);
      }
      default:
        return null;
    }
  }

  private static void show (ViewController<?> c, long chatId, TdApi.ChatActiveStories active, TdApi.Story[] stories) {
    final ArrayList<MediaItem> items = new ArrayList<>();
    int startIndex = 0;
    for (int i = 0; i < stories.length; i++) {
      final MediaItem item = toItem(c, stories[i]);
      if (item == null) {
        continue;
      }
      if (startIndex == 0 && stories[i].id > active.maxReadStoryId && !items.isEmpty()) {
        startIndex = items.size();
      }
      items.add(item);
    }
    if (items.isEmpty()) {
      UI.showToast(R.string.XgramStoriesNone, android.widget.Toast.LENGTH_SHORT);
      return;
    }
    final MediaStack stack = new MediaStack(c.context(), c.tdlib());
    stack.set(startIndex, items);
    MediaViewController.openWithStack(c, stack, c.tdlib().chatTitle(chatId), null, false);
    // Seen, as official Telegram counts a view
    for (TdApi.Story story : stories) {
      if (story != null) {
        c.tdlib().send(new TdApi.OpenStory(chatId, story.id), (ok, error) -> { });
        c.tdlib().send(new TdApi.CloseStory(chatId, story.id), (ok, error) -> { });
      }
    }
  }
}
