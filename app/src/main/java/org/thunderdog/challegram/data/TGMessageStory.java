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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;

/**
 * XGram: a story shared into a chat (TdApi.MessageStory), like official Telegram: the story's picture in a portrait
 * frame with who posted it ("mentioned you in a story" for a mention). A tap opens the story's photo or video in the
 * media viewer (Telegram X has no story player). A story that has expired says so.
 */
public class TGMessageStory extends TGMessage {
  private static final float PREVIEW_WIDTH = 180f, RADIUS = 12f;

  private final TdApi.MessageStory storyRef;
  private @Nullable TdApi.Story story;
  private boolean storyMissing;
  private @Nullable Text titleText;
  private int previewWidth, previewHeight, contentWidth, contentHeight;
  private int lastStartX, lastStartY;

  public TGMessageStory (MessagesManager manager, TdApi.Message msg, TdApi.MessageStory storyRef) {
    super(manager, msg);
    this.storyRef = storyRef;
    tdlib.send(new TdApi.GetStory(storyRef.storyPosterChatId, storyRef.storyId, false), (story, error) -> runOnUiThreadOptional(() -> {
      if (story != null) {
        this.story = story;
      } else {
        this.storyMissing = true;
      }
      rebuildAndUpdateContent();
      invalidateContentReceiver();
    }));
  }

  private @Nullable TdApi.StoryVideo storyVideo () {
    return story != null && story.content != null && story.content.getConstructor() == TdApi.StoryContentVideo.CONSTRUCTOR ? ((TdApi.StoryContentVideo) story.content).video : null;
  }

  private @Nullable TdApi.Photo storyPhoto () {
    return story != null && story.content != null && story.content.getConstructor() == TdApi.StoryContentPhoto.CONSTRUCTOR ? ((TdApi.StoryContentPhoto) story.content).photo : null;
  }

  @Override
  protected void buildContent (int maxWidth) {
    previewWidth = Math.min(maxWidth, Screen.dp(PREVIEW_WIDTH));
    previewHeight = storyMissing ? 0 : previewWidth * 16 / 9;
    final String poster = tdlib.chatTitle(storyRef.storyPosterChatId);
    final String title = storyMissing ? Lang.getString(R.string.XgramStoryExpired) :
      storyRef.viaMention ? Lang.getString(R.string.XgramStoryMention, poster) : Lang.getString(R.string.XgramStoryFrom, poster);
    titleText = new Text.Builder(title, Math.max(previewWidth, Math.min(maxWidth, Screen.dp(240f))), Paints.robotoStyleProvider(14f), getTextColorSet()).allBold(!storyMissing).build();
    contentWidth = Math.max(previewWidth, titleText.getWidth());
    contentHeight = previewHeight + (previewHeight > 0 ? Screen.dp(8f) : 0) + titleText.getHeight();
  }

  @Override
  protected int getContentWidth () {
    return contentWidth;
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    final TdApi.StoryVideo video = storyVideo();
    final TdApi.Photo photo = storyPhoto();
    final TdApi.Minithumbnail mini = video != null ? video.minithumbnail : photo != null ? photo.minithumbnail : null;
    if (mini != null) {
      receiver.requestFile(new ImageFileLocal(mini), null);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    final TdApi.StoryVideo video = storyVideo();
    final TdApi.Photo photo = storyPhoto();
    ImageFile file = null;
    if (video != null && video.thumbnail != null && video.thumbnail.format != null && video.thumbnail.format.getConstructor() == TdApi.ThumbnailFormatJpeg.CONSTRUCTOR) {
      file = new ImageFile(tdlib, video.thumbnail.file);
    } else if (photo != null && photo.sizes != null && photo.sizes.length > 0) {
      TdApi.PhotoSize best = photo.sizes[0];
      for (TdApi.PhotoSize size : photo.sizes) {
        if (size.width <= Screen.dp(PREVIEW_WIDTH) * 2 && size.width > best.width) {
          best = size;
        }
      }
      file = new ImageFile(tdlib, best.photo);
    }
    if (file != null) {
      file.setScaleType(ImageFile.CENTER_CROP);
    }
    receiver.requestFile(file);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    lastStartX = startX;
    lastStartY = startY;
    if (previewHeight > 0) {
      final int radius = Screen.dp(RADIUS);
      preview.setRadius(radius);
      receiver.setRadius(radius);
      preview.setBounds(startX, startY, startX + previewWidth, startY + previewHeight);
      receiver.setBounds(startX, startY, startX + previewWidth, startY + previewHeight);
      if (receiver.needPlaceholder()) {
        if (preview.needPlaceholder()) {
          preview.drawPlaceholder(c);
        }
        preview.draw(c);
      }
      receiver.draw(c);
      if (storyVideo() != null) {
        final float cx = startX + previewWidth / 2f, cy = startY + previewHeight / 2f;
        c.drawCircle(cx, cy, Screen.dp(24f), Paints.fillingPaint(0x66000000));
        final android.graphics.Path play = new android.graphics.Path();
        play.moveTo(cx - Screen.dp(7f), cy - Screen.dp(10f));
        play.lineTo(cx + Screen.dp(11f), cy);
        play.lineTo(cx - Screen.dp(7f), cy + Screen.dp(10f));
        play.close();
        c.drawPath(play, Paints.fillingPaint(0xffffffff));
      }
    }
    if (titleText != null) {
      titleText.draw(c, startX, startY + previewHeight + (previewHeight > 0 ? Screen.dp(8f) : 0));
    }
  }

  // Opening

  private float touchX, touchY;
  private boolean touchPreview;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        touchX = e.getX();
        touchY = e.getY();
        touchPreview = story != null && previewHeight > 0 && e.getX() >= lastStartX && e.getX() < lastStartX + previewWidth && e.getY() >= lastStartY && e.getY() < lastStartY + previewHeight;
        if (touchPreview) {
          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE: {
        final int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        if (Math.abs(e.getX() - touchX) > slop || Math.abs(e.getY() - touchY) > slop) {
          touchPreview = false;
        }
        if (touchPreview) {
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP:
        if (touchPreview) {
          touchPreview = false;
          openStory();
          return true;
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        touchPreview = false;
        break;
    }
    return super.onTouchEvent(view, e);
  }

  private void openStory () {
    if (story == null) {
      return;
    }
    final TdApi.FormattedText caption = story.caption != null ? story.caption : new TdApi.FormattedText("", new TdApi.TextEntity[0]);
    MediaItem item = null;
    final TdApi.Photo photo = storyPhoto();
    final TdApi.StoryVideo video = storyVideo();
    if (photo != null) {
      item = new MediaItem(context(), tdlib, photo, caption);
    } else if (video != null && video.video != null) {
      TdApi.Video asVideo = new TdApi.Video((int) Math.round(video.duration), video.width, video.height, "story.mp4", "video/mp4", video.hasStickers, true, video.minithumbnail, video.thumbnail, video.video);
      item = new MediaItem(context(), tdlib, asVideo, new TdApi.AlternativeVideo[0], null, caption, false);
    }
    if (item != null) {
      MediaViewController.openFromMedia(controller(), item, null, false);
    }
  }
}
