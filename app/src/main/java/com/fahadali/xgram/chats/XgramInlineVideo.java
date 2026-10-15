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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.WatchDog;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDataSource;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Play videos in chat (Messages page): a video message plays in place instead of in the media viewer, muted while
 * on screen or after a tap, with a seek track, the time, a sound switch and a button to the full screen viewer.
 *
 * One player at a time, made the way Telegram X makes its own ({@link U#newExoPlayer}, streamed from TDLib with
 * {@link U#newMediaSource}) and released as soon as it stops. The picture is a TextureView inside the message's
 * {@link MessageViewGroup}, the container Telegram X uses for round videos, laid over the cell the media wrapper
 * last drew, so it scrolls, swipes and recycles with the message. Everything here runs on the UI thread.
 */
public final class XgramInlineVideo {
  private XgramInlineVideo () { }

  /** Part of the cell that must be on screen for autoplay. */
  private static final float AUTOPLAY_VISIBLE = .6f;
  /** Below this part on screen an autoplayed (muted) video stops. */
  private static final float AUTOPLAY_STOP_VISIBLE = .4f;
  /** Below this part on screen a video the user is watching stops. */
  private static final float STOP_VISIBLE = .15f;
  private static final long AUTOPLAY_DELAY_MS = 300;
  private static final long TICK_MS = 50;

  private static final Handler handler = new Handler(Looper.getMainLooper());

  private static @Nullable Session current;

  /** Where videos were left, by file id, so one that comes back on screen goes on from there. */
  private static final Map<Integer, Long> positions = new LinkedHashMap<Integer, Long>(16, .75f, true) {
    @Override
    protected boolean removeEldestEntry (Map.Entry<Integer, Long> eldest) {
      return size() > 32;
    }
  };
  /** Files the player couldn't play: they open in the media viewer as before. */
  private static final SparseBooleanArray failed = new SparseBooleanArray();

  // Settings

  public static boolean isEnabled () {
    return XgramSettings.instance().inlineVideo();
  }

  private static boolean startsAutomatically () {
    return XgramSettings.instance().inlineVideoStart() == XgramSettings.INLINE_VIDEO_START_AUTO;
  }

  /**
   * Asked once per media message, when it is made: the player needs the message's own view group. Videos, and photos
   * of albums (an album can mix photos and videos and is shown by its first message).
   */
  public static boolean needViewGroup (@Nullable TdApi.Message msg) {
    if (msg == null || msg.content == null || msg.chatId == 0 || ChatId.isSecret(msg.chatId) || !Config.VIDEO_PLAYER_AVAILABLE || !isEnabled()) {
      return false;
    }
    switch (msg.content.getConstructor()) {
      case TdApi.MessageVideo.CONSTRUCTOR:
        return !Td.isSecret(msg.content);
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return msg.mediaAlbumId != 0 && !Td.isSecret(msg.content);
    }
    return false;
  }

  // Hooks: MediaWrapper

  /** A tap on a video cell. True when it was handled here instead of opening the media viewer. */
  public static boolean onClick (MediaWrapper wrapper, View view) {
    if (!isEnabled() || !wrapper.isVideo()) {
      return false;
    }
    Session s = current;
    if (s != null && s.wrapper == wrapper) {
      s.onVideoTap();
      return true;
    }
    ViewParent parent = view.getParent();
    if (!(parent instanceof MessageViewGroup) || !canPlay(wrapper, (MessageViewGroup) parent)) {
      return false;
    }
    return start(wrapper, (MessageViewGroup) parent, true);
  }

  /** While a video cell is drawn. Keeps the player over it; true when the cell is playing here. */
  public static boolean onCellDraw (MediaWrapper wrapper, View view, int left, int top, int right, int bottom, float selectionFactor) {
    final Session s = current;
    if (s == null || s.wrapper != wrapper) {
      return false;
    }
    if (selectionFactor > 0f || (s.controller != null && s.controller.getSelectableFactor() > 0f)) {
      // Selecting messages: the checkboxes are drawn on the message. Not while drawing: views can't be removed now.
      handler.post(() -> {
        if (current == s) {
          stop();
        }
      });
      return false;
    }
    ViewParent parent = view.getParent();
    if (!(parent instanceof MessageViewGroup)) {
      return false; // drawn somewhere else too (a preview): the chat's view keeps the player
    }
    MessageViewGroup group = (MessageViewGroup) parent;
    if (s.group != group) {
      if (s.group == null || s.group.getMessage() != s.message) {
        // The message moved to another view: follow it on the next frame.
        handler.post(() -> {
          if (current == s && group.getMessage() == s.message) {
            s.attachTo(group);
            s.message.invalidate();
          }
        });
      }
      return false;
    }
    TGMessage message = wrapper.xgramSource();
    Path bubblePath = message instanceof TGMessageMedia ? ((TGMessageMedia) message).xgramBubbleClipPath() : null;
    s.layer.setCell(left, top, right, bottom, wrapper.xgramCellPath(), bubblePath);
    return true;
  }

  public static void onWrapperDestroyed (MediaWrapper wrapper) {
    if (!UI.inUiThread()) {
      handler.post(() -> onWrapperDestroyed(wrapper));
      return;
    }
    Session s = current;
    if (s != null && s.wrapper == wrapper) {
      stop();
    }
  }

  // Hooks: MessageViewGroup

  public static void onGroupTranslate (MessageViewGroup group, float dx) {
    Session s = current;
    if (s != null && s.group == group) {
      s.layer.setTranslationX(dx);
    }
  }

  public static void onGroupMessageChanged (MessageViewGroup group, TGMessage message) {
    Session s = current;
    if (s != null && s.group == group && message != s.message) {
      stop();
    }
  }

  public static void onGroupDetached (MessageViewGroup group) {
    Session s = current;
    if (s != null && s.group == group) {
      stop();
    }
  }

  // Hooks: chat

  /** The chat scrolled or its messages changed. */
  public static void onMessagesShown (MessagesController controller) {
    Session s = current;
    if (s != null && s.controller == controller) {
      if (s.visibleFraction() < (s.engaged ? STOP_VISIBLE : AUTOPLAY_STOP_VISIBLE)) {
        stop();
      }
    }
    if (isEnabled() && startsAutomatically()) {
      scheduleAutoplay(controller);
    }
  }

  public static void onChatFocusChanged (MessagesController controller, boolean isFocused) {
    Session s = current;
    if (!isFocused) {
      if (s != null && s.controller == controller) {
        stop();
      }
    } else if (isEnabled() && startsAutomatically()) {
      scheduleAutoplay(controller);
    }
  }

  // Hook: media viewer

  private static int pendingFileId;
  private static long pendingPositionMs, pendingUntil;

  /** Asked by the media viewer's player when it gets a video: where the chat's player left it, once. */
  public static long takeStartPosition (@Nullable TdApi.File file) {
    if (file == null || pendingFileId == 0 || file.id != pendingFileId) {
      return 0;
    }
    pendingFileId = 0;
    return SystemClock.uptimeMillis() <= pendingUntil ? pendingPositionMs : 0;
  }

  // Playback

  public static void stop () {
    Session s = current;
    if (s != null) {
      current = null;
      s.release();
    }
  }

  private static boolean start (MediaWrapper wrapper, MessageViewGroup group, boolean byUser) {
    stop();
    Session s = new Session(wrapper, group, byUser);
    current = s;
    try {
      s.start();
      return true;
    } catch (Throwable t) {
      android.util.Log.w("XGram", "In-chat video failed to start", t);
      if (current == s) {
        stop();
      }
      return false;
    }
  }

  private static boolean canPlay (MediaWrapper wrapper, MessageViewGroup group) {
    TGMessage source = wrapper.xgramSource();
    TdApi.Video video = wrapper.getVideo();
    if (!(source instanceof TGMessageMedia) || video == null || video.video == null || group.getMessage() != source || !source.needViewGroup()) {
      return false;
    }
    if (source.getChatId() == 0 || source.isSecretChat() || source.isHot() || source.isSponsoredMessage() || source.isDestroyed() || wrapper.xgramSpoilerVisible()) {
      return false;
    }
    MessagesController c = source.messagesController();
    if (c == null || c.isDestroyed() || c.isInForceTouchMode() || !c.isFocused() || c.getSelectableFactor() > 0f) {
      return false;
    }
    TdApi.File file = video.video;
    if (failed.get(file.id)) {
      return false;
    }
    boolean loaded = TD.isFileLoaded(file);
    if (file.remote.isUploadingActive || (!file.remote.isUploadingCompleted && !loaded)) {
      return false; // still being sent
    }
    return loaded || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE;
  }

  /** Telegram X's own rules for media that starts by itself: downloaded already, or allowed on this network. */
  private static boolean canAutoplay (MediaWrapper wrapper) {
    TGMessage source = wrapper.xgramSource();
    TdApi.Video video = wrapper.getVideo();
    if (source == null || video == null) {
      return false;
    }
    if (TD.isFileLoaded(video.video)) {
      return true;
    }
    Tdlib tdlib = source.tdlib();
    TdlibFilesManager files = tdlib.files();
    if (files.isDataSaverActive()) {
      return false;
    }
    if (tdlib.context().watchDog().getConnectionType() == WatchDog.CONNECTION_TYPE_WIFI) {
      return true;
    }
    return files.canAutomaticallyDownload(video.video, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, tdlib.chatType(source.getChatId()));
  }

  private static MessagesController autoplayController;
  private static final Runnable autoplayRunnable = () -> {
    MessagesController c = autoplayController;
    autoplayController = null;
    if (c != null) {
      autoplay(c);
    }
  };

  private static void scheduleAutoplay (MessagesController controller) {
    autoplayController = controller;
    handler.removeCallbacks(autoplayRunnable);
    handler.postDelayed(autoplayRunnable, AUTOPLAY_DELAY_MS);
  }

  /** Starts, muted, the video most on screen, unless one is playing that the user started or is still in view. */
  private static void autoplay (MessagesController controller) {
    if (!isEnabled() || !startsAutomatically() || !Settings.instance().needAutoplayGIFs() || !UI.isResumed()) {
      return;
    }
    if (controller.isDestroyed() || !controller.isFocused() || controller.isInForceTouchMode() || controller.context().getCurrentPopupWindow() != null) {
      return; // the media viewer or a menu is open
    }
    View recyclerView = controller.getMessagesView();
    if (!(recyclerView instanceof ViewGroup)) {
      return;
    }
    Session s = current;
    if (s != null && (s.engaged || (s.controller == controller && s.visibleFraction() >= AUTOPLAY_VISIBLE))) {
      return;
    }
    ViewGroup list = (ViewGroup) recyclerView;
    MediaWrapper best = null;
    MessageViewGroup bestGroup = null;
    float bestVisible = 0f;
    List<MediaWrapper> videos = new ArrayList<>();
    for (int i = 0; i < list.getChildCount(); i++) {
      View child = list.getChildAt(i);
      if (!(child instanceof MessageViewGroup)) {
        continue;
      }
      MessageViewGroup group = (MessageViewGroup) child;
      TGMessage message = group.getMessage();
      if (!(message instanceof TGMessageMedia)) {
        continue;
      }
      videos.clear();
      ((TGMessageMedia) message).xgramCollectVideos(videos);
      for (MediaWrapper wrapper : videos) {
        int cellTop = wrapper.getCellTop();
        if (cellTop < 0) {
          continue; // not drawn yet
        }
        float visible = visibleFraction(list, group, cellTop, cellTop + wrapper.getCellHeight());
        if (visible >= AUTOPLAY_VISIBLE && visible > bestVisible && canPlay(wrapper, group) && canAutoplay(wrapper)) {
          best = wrapper;
          bestGroup = group;
          bestVisible = visible;
        }
      }
    }
    if (best != null && (s == null || s.wrapper != best)) {
      start(best, bestGroup, false);
    }
  }

  private static float visibleFraction (View list, View group, int cellTop, int cellBottom) {
    if (group.getParent() != list || cellBottom <= cellTop) {
      return 0f;
    }
    float offset = group.getTop() + group.getTranslationY();
    float top = offset + cellTop, bottom = offset + cellBottom;
    float visibleTop = Math.max(top, 0), visibleBottom = Math.min(bottom, list.getHeight());
    return visibleBottom <= visibleTop ? 0f : (visibleBottom - visibleTop) / (bottom - top);
  }

  private static TGPlayerController.TrackChangeListener trackListener;

  /** Telegram X's own player (music, voice, round videos) starting: the video here goes silent. Never removed. */
  private static void ensureTrackListener () {
    if (trackListener == null) {
      trackListener = (tdlib, newTrack, fileId, state, progress, byUser) -> {
        if (newTrack != null && state == TGPlayerController.STATE_PLAYING) {
          handler.post(() -> {
            Session s = current;
            if (s != null && !s.muted) {
              s.setMuted(true);
            }
          });
        }
      };
      TdlibManager.instance().player().addTrackChangeListener(trackListener);
    }
  }

  // One playing video

  private static final class Session implements Player.Listener, CallManager.CurrentCallListener, BaseActivity.ActivityListener {
    final MediaWrapper wrapper;
    final TGMessage message;
    final MessagesController controller;
    final Tdlib tdlib;
    final TdApi.File file;
    final long knownDurationMs;
    final BaseActivity context;
    final PlayerLayer layer;

    @Nullable MessageViewGroup group;
    @Nullable ExoPlayer player;
    float aspectRatio;
    /** Started or touched by the user: plays once, with sound, and isn't replaced by autoplay. */
    boolean engaged;
    boolean muted, inCall, ended, buffering, released, screenFlag;
    private int ticks;

    Session (MediaWrapper wrapper, MessageViewGroup group, boolean byUser) {
      TdApi.Video video = wrapper.getVideo();
      this.wrapper = wrapper;
      this.message = wrapper.xgramSource();
      this.controller = message.messagesController();
      this.tdlib = message.tdlib();
      this.file = video.video;
      this.knownDurationMs = Math.max(0, video.duration) * 1000L;
      this.aspectRatio = video.width > 0 && video.height > 0 ? (float) video.width / (float) video.height : 0f;
      this.context = UI.getContext(group.getContext());
      this.engaged = byUser;
      this.muted = !byUser;
      this.group = group;
      this.layer = new PlayerLayer(group.getContext(), this);
    }

    void start () {
      MessageViewGroup group = this.group;
      this.group = null;
      attachTo(group);
      inCall = TdlibManager.instance().calls().hasActiveCall();

      ExoPlayer player = U.newExoPlayer(context, Config.PREFER_RENDER_EXTENSIONS);
      this.player = player;
      player.addListener(this);
      player.setVideoTextureView(layer.textureView);
      player.setRepeatMode(engaged ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
      applyVolume();
      player.setMediaSource(U.newMediaSource(tdlib.id(), file, TdlibFilesManager.PRIORITY_STREAMING_VIDEO, TdlibDataSource.Flag.OPTIMIZE_CHUNKS, knownDurationMs));
      player.prepare();
      Long position = positions.get(file.id);
      if (position != null && position > 0) {
        player.seekTo(position);
      }
      player.setPlayWhenReady(true);

      TdlibManager.instance().calls().addCurrentCallListener(this);
      context.addActivityListener(this);
      if (!muted) {
        TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_OPEN_VIDEO);
      }
      ensureTrackListener(); // after the pause above: a new listener is told the current state right away
      updateScreenFlag();
      handler.postDelayed(tick, TICK_MS);
      message.invalidate(); // Telegram X's badge and play button make way for the player's
    }

    void attachTo (@NonNull MessageViewGroup group) {
      ViewParent parent = layer.getParent();
      if (parent == group) {
        return;
      }
      if (parent instanceof ViewGroup) {
        ((ViewGroup) parent).removeView(layer);
      }
      this.group = group;
      layer.cell.setEmpty();
      layer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      ViewGroup videoPlayerView = group.getVideoPlayerView();
      ViewSupport.setHigherElevation(layer, videoPlayerView, true);
      int index = group.indexOfChild(group.getOverlayView());
      if (index >= 0) {
        group.addView(layer, index); // above the picture, under the bubble time and the overlay
      } else {
        group.addView(layer);
      }
      layer.setTranslationX(videoPlayerView.getTranslationX());
      int width = group.getMeasuredWidth(), height = group.getMeasuredHeight();
      if (width > 0 && height > 0) {
        layer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        layer.layout(0, 0, width, height);
      }
    }

    void release () {
      if (released) {
        return;
      }
      released = true;
      handler.removeCallbacks(tick);
      ExoPlayer player = this.player;
      this.player = null;
      if (player != null) {
        long position = player.getCurrentPosition();
        long duration = duration(player);
        if (!ended && position > 1000 && (duration <= 0 || position < duration - 1000)) {
          positions.put(file.id, position);
        } else {
          positions.remove(file.id);
        }
        player.removeListener(this);
        player.clearVideoTextureView(layer.textureView);
        player.release();
      }
      TdlibManager.instance().calls().removeCurrentCallListener(this);
      context.removeActivityListener(this);
      if (screenFlag) {
        screenFlag = false;
        context.setScreenFlagEnabled(BaseActivity.SCREEN_FLAG_PLAYING_REGULAR_VIDEO, false);
      }
      ViewParent parent = layer.getParent();
      if (parent instanceof ViewGroup) {
        ((ViewGroup) parent).removeView(layer);
      }
      group = null;
      message.invalidate(); // Telegram X's badge and play button come back
    }

    private final Runnable tick = new Runnable() {
      @Override
      public void run () {
        if (released) {
          return;
        }
        if (++ticks % 20 == 0 && !isStillShown()) {
          stop();
          return;
        }
        if (isPlaying() || buffering || layer.dragging) {
          layer.invalidate();
        }
        handler.postDelayed(this, TICK_MS);
      }
    };

    /** The message is still there and still has this video (an album item can be deleted). */
    private boolean isStillShown () {
      if (message.isDestroyed() || !(message instanceof TGMessageMedia)) {
        return false;
      }
      List<MediaWrapper> videos = new ArrayList<>();
      ((TGMessageMedia) message).xgramCollectVideos(videos);
      return videos.contains(wrapper);
    }

    float visibleFraction () {
      MessageViewGroup group = this.group;
      View list = controller != null ? controller.getMessagesView() : null;
      if (group == null || list == null) {
        return 0f;
      }
      int cellTop = wrapper.getCellTop();
      if (cellTop < 0) {
        return 1f; // not drawn yet
      }
      return XgramInlineVideo.visibleFraction(list, group, cellTop, cellTop + wrapper.getCellHeight());
    }

    // State

    long duration () {
      return duration(player);
    }

    private long duration (@Nullable ExoPlayer player) {
      long duration = player != null ? player.getDuration() : C.TIME_UNSET;
      return duration != C.TIME_UNSET && duration > 0 ? duration : knownDurationMs;
    }

    long position () {
      return player != null ? Math.max(0, player.getCurrentPosition()) : 0;
    }

    boolean isPlaying () {
      return player != null && player.getPlayWhenReady() && !ended;
    }

    boolean isSilent () {
      return muted || inCall;
    }

    private void applyVolume () {
      if (player != null) {
        player.setVolume(isSilent() ? 0f : 1f);
      }
    }

    private void updateScreenFlag () {
      boolean need = !released && !muted && isPlaying();
      if (screenFlag != need) {
        screenFlag = need;
        context.setScreenFlagEnabled(BaseActivity.SCREEN_FLAG_PLAYING_REGULAR_VIDEO, need);
      }
    }

    private void engage () {
      if (!engaged) {
        engaged = true;
        if (player != null) {
          player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
      }
    }

    // Actions

    void setMuted (boolean muted) {
      if (released || this.muted == muted) {
        return;
      }
      this.muted = muted;
      if (!muted) {
        engage();
        TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_OPEN_VIDEO);
      }
      applyVolume();
      updateScreenFlag();
      layer.invalidate();
    }

    /** A tap on the picture: sound on or off, or play again when it's paused or over. */
    void onVideoTap () {
      if (released) {
        return;
      }
      if (!isPlaying()) {
        resume();
        setMuted(false);
      } else {
        setMuted(!muted);
      }
    }

    void resume () {
      if (released || player == null) {
        return;
      }
      engage();
      if (ended) {
        ended = false;
        player.seekTo(0);
      }
      player.setPlayWhenReady(true);
      updateScreenFlag();
      layer.invalidate();
    }

    void pause () {
      if (released || player == null) {
        return;
      }
      engage();
      player.setPlayWhenReady(false);
      updateScreenFlag();
      layer.invalidate();
    }

    void togglePlay () {
      if (isPlaying()) {
        pause();
      } else {
        resume();
      }
    }

    void seekTo (float fraction) {
      if (released || player == null) {
        return;
      }
      long duration = duration();
      if (duration <= 0) {
        return;
      }
      engage();
      ended = false;
      player.seekTo((long) (duration * Math.max(0f, Math.min(1f, fraction))));
      updateScreenFlag();
      layer.invalidate();
    }

    /** Opens Telegram X's media viewer at the same place; the player here stops. */
    void expand () {
      if (released) {
        return;
      }
      long position = ended ? 0 : position();
      long messageId = wrapper.getSourceMessageId();
      int fileId = file.id;
      stop();
      if (message instanceof TGMessageMedia && !message.isDestroyed()) {
        pendingFileId = position > 0 ? fileId : 0;
        pendingPositionMs = position;
        pendingUntil = SystemClock.uptimeMillis() + 10_000;
        MediaViewController.openFromMessage((TGMessageMedia) message, messageId);
      }
    }

    // Player.Listener

    @Override
    public void onPlaybackStateChanged (int playbackState) {
      buffering = playbackState == Player.STATE_BUFFERING;
      if (playbackState == Player.STATE_ENDED && player != null && player.getRepeatMode() == Player.REPEAT_MODE_OFF) {
        ended = true;
        player.setPlayWhenReady(false);
        updateScreenFlag();
      }
      layer.invalidate();
    }

    @Override
    public void onRenderedFirstFrame () {
      layer.textureView.setAlpha(1f);
      layer.invalidate();
    }

    @Override
    public void onVideoSizeChanged (@NonNull VideoSize videoSize) {
      if (videoSize.width <= 0 || videoSize.height <= 0) {
        return;
      }
      float width = videoSize.width * (videoSize.pixelWidthHeightRatio > 0f ? videoSize.pixelWidthHeightRatio : 1f);
      float height = videoSize.height;
      if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
        float temp = width;
        width = height;
        height = temp;
      }
      float ratio = width / height;
      if (Math.abs(ratio - aspectRatio) > .001f) {
        aspectRatio = ratio;
        layer.layoutTexture();
      }
    }

    @Override
    public void onPlayerError (@NonNull PlaybackException error) {
      android.util.Log.w("XGram", "In-chat video can't play", error);
      failed.put(file.id, true);
      final boolean openViewer = engaged;
      final long messageId = wrapper.getSourceMessageId();
      handler.post(() -> {
        if (current == this) {
          stop();
        }
        if (openViewer && message instanceof TGMessageMedia && !message.isDestroyed()) {
          MediaViewController.openFromMessage((TGMessageMedia) message, messageId);
        }
      });
    }

    // CallManager.CurrentCallListener

    @Override
    public void onCurrentCallChanged (Tdlib tdlib, @Nullable TdApi.Call call) {
      handler.post(() -> {
        if (!released) {
          inCall = call != null;
          applyVolume();
          layer.invalidate();
        }
      });
    }

    // BaseActivity.ActivityListener

    @Override
    public void onActivityPause () {
      stopLater();
    }

    @Override
    public void onActivityResume () { }

    @Override
    public void onActivityDestroy () {
      stopLater();
    }

    @Override
    public void onActivityPermissionResult (int code, boolean granted) { }

    private void stopLater () {
      // Not while the activity walks its listeners: stopping removes this one.
      handler.post(() -> {
        if (current == this) {
          stop();
        }
      });
    }
  }

  // The picture and the controls, over the media cell

  @SuppressLint("ViewConstructor")
  private static final class PlayerLayer extends ViewGroup {
    private static final int TARGET_NONE = 0, TARGET_PLAY = 1, TARGET_SOUND = 2, TARGET_EXPAND = 3, TARGET_TRACK = 4;

    final Session session;
    final TextureView textureView;
    /** The media cell, in the message view's coordinates (this view covers the whole message). */
    final Rect cell = new Rect();
    private final Rect textureRect = new Rect();
    private @Nullable Path cellPath, bubblePath;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF pillRect = new RectF(), playRect = new RectF(), soundRect = new RectF(), expandRect = new RectF();
    private final Drawable playIcon, pauseIcon, soundOnIcon, soundOffIcon, expandIcon, replayIcon;

    private int touchTarget;
    private float touchX, touchY;
    boolean dragging;
    private float dragFraction;

    PlayerLayer (Context context, Session session) {
      super(context);
      this.session = session;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setOutlineProvider(null); // no shadow from the elevation
      }
      textureView = new TextureView(context);
      textureView.setAlpha(0f); // the thumbnail stays visible until the first frame
      addView(textureView);

      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeCap(Paint.Cap.ROUND);
      stroke.setColor(0xffffffff);
      stroke.setStrokeWidth(Screen.dp(2.5f));
      iconPaint.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
      text.setColor(0xffffffff);
      text.setTypeface(Fonts.getRobotoMedium());
      text.setTextSize(Screen.dp(12f));

      playIcon = Drawables.get(R.drawable.baseline_play_arrow_36_white);
      pauseIcon = Drawables.get(R.drawable.baseline_pause_36_white);
      soundOnIcon = Drawables.get(R.drawable.baseline_volume_up_24_white);
      soundOffIcon = Drawables.get(R.drawable.baseline_volume_off_24_white);
      expandIcon = Drawables.get(R.drawable.baseline_zoom_out_map_24);
      replayIcon = Drawables.get(R.drawable.baseline_replay_24);
    }

    // Layout: the picture fills the cell like the thumbnail (center crop)

    void setCell (int left, int top, int right, int bottom, @Nullable Path cellPath, @Nullable Path bubblePath) {
      boolean changed = this.cellPath != cellPath || this.bubblePath != bubblePath;
      this.cellPath = cellPath;
      this.bubblePath = bubblePath;
      if (cell.left != left || cell.top != top || cell.right != right || cell.bottom != bottom) {
        cell.set(left, top, right, bottom);
        layoutTexture();
      } else if (changed) {
        invalidate();
      }
    }

    private void computeTextureRect () {
      int width = cell.width(), height = cell.height();
      if (width <= 0 || height <= 0) {
        textureRect.setEmpty();
        return;
      }
      float ratio = session.aspectRatio > 0f ? session.aspectRatio : (float) width / (float) height;
      int textureWidth, textureHeight;
      if ((float) width / (float) height > ratio) {
        textureWidth = width;
        textureHeight = Math.round(width / ratio);
      } else {
        textureHeight = height;
        textureWidth = Math.round(height * ratio);
      }
      int left = cell.centerX() - textureWidth / 2;
      int top = cell.centerY() - textureHeight / 2;
      textureRect.set(left, top, left + textureWidth, top + textureHeight);
    }

    private void measureTexture () {
      computeTextureRect();
      textureView.measure(MeasureSpec.makeMeasureSpec(textureRect.width(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureRect.height(), MeasureSpec.EXACTLY));
    }

    /** Outside a layout pass: the cell moved or the video's size is known. */
    void layoutTexture () {
      measureTexture();
      textureView.layout(textureRect.left, textureRect.top, textureRect.right, textureRect.bottom);
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      measureTexture();
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      textureView.layout(textureRect.left, textureRect.top, textureRect.right, textureRect.bottom);
    }

    // Drawing

    private static void clip (Canvas c, @Nullable Path path) {
      if (path != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        try {
          c.clipPath(path);
        } catch (Throwable ignored) { }
      }
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (cell.isEmpty()) {
        return;
      }
      int saveCount = c.save();
      c.clipRect(cell);
      clip(c, bubblePath);
      clip(c, cellPath);
      super.dispatchDraw(c);
      drawControls(c);
      c.restoreToCount(saveCount);
    }

    private void drawIcon (Canvas c, @Nullable Drawable d, float cx, float cy, float size) {
      if (d == null || d.getMinimumWidth() <= 0) {
        return;
      }
      float scale = size / d.getMinimumWidth();
      int saveCount = c.save();
      c.translate(cx, cy);
      c.scale(scale, scale);
      Drawables.draw(c, d, -d.getMinimumWidth() / 2f, -d.getMinimumHeight() / 2f, iconPaint);
      c.restoreToCount(saveCount);
    }

    private void drawControls (Canvas c) {
      final Session s = session;
      final int left = cell.left, top = cell.top, right = cell.right, bottom = cell.bottom;
      final long duration = s.duration();
      final long position = dragging && duration > 0 ? (long) (duration * dragFraction) : s.position();
      final float progress = duration > 0 ? Math.max(0f, Math.min(1f, (float) position / (float) duration)) : 0f;
      final boolean playing = s.isPlaying();

      final int pad = Screen.dp(8f);
      final int pillHeight = Screen.dp(26f);
      final int iconSize = Screen.dp(18f);
      final float expandRadius = Screen.dp(14f);

      // Top left: play or pause, the time, sound on or off
      String time = duration > 0 ? Strings.buildDuration(position / 1000) + " / " + Strings.buildDuration(duration / 1000) : Strings.buildDuration(position / 1000);
      float timeWidth = text.measureText(time);
      float playPart = Screen.dp(6f) + iconSize + Screen.dp(4f);
      float soundPart = Screen.dp(4f) + iconSize + Screen.dp(6f);
      float room = cell.width() - pad * 3 - expandRadius * 2;
      boolean showPlay = playPart + soundPart <= room;
      boolean showTime = showPlay && playPart + timeWidth + soundPart <= room;
      float pillWidth = (showPlay ? playPart : Screen.dp(2f)) + (showTime ? timeWidth : 0) + soundPart;
      pillRect.set(left + pad, top + pad, left + pad + pillWidth, top + pad + pillHeight);
      fill.setColor(0x66000000);
      c.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, fill);
      if (showPlay) {
        playRect.set(pillRect.left, pillRect.top, pillRect.right - soundPart, pillRect.bottom);
        drawIcon(c, playing ? pauseIcon : playIcon, pillRect.left + Screen.dp(6f) + iconSize / 2f, pillRect.centerY(), iconSize);
      } else {
        playRect.setEmpty();
      }
      if (showTime) {
        c.drawText(time, pillRect.left + playPart, pillRect.centerY() + Screen.dp(4.25f), text);
      }
      soundRect.set(pillRect.right - soundPart, pillRect.top, pillRect.right, pillRect.bottom);
      drawIcon(c, s.isSilent() ? soundOffIcon : soundOnIcon, soundRect.left + Screen.dp(4f) + iconSize / 2f, pillRect.centerY(), iconSize);

      // Top right: the full screen viewer
      float expandX = right - pad - expandRadius, expandY = pillRect.centerY();
      if (expandX - expandRadius >= pillRect.right + Screen.dp(4f)) {
        expandRect.set(expandX - expandRadius, expandY - expandRadius, expandX + expandRadius, expandY + expandRadius);
        c.drawCircle(expandX, expandY, expandRadius, fill);
        drawIcon(c, expandIcon, expandX, expandY, iconSize);
      } else {
        expandRect.setEmpty();
      }

      // Middle: paused, over, or loading
      float cx = cell.exactCenterX(), cy = cell.exactCenterY();
      if (!playing && !dragging) {
        float radius = Screen.dp(24f);
        c.drawCircle(cx, cy, radius, fill);
        drawIcon(c, s.ended ? replayIcon : playIcon, cx, cy, s.ended ? Screen.dp(24f) : Screen.dp(36f));
      } else if (playing && s.buffering) {
        float radius = Screen.dp(11f);
        c.drawCircle(cx, cy, radius + Screen.dp(9f), fill);
        float angle = (SystemClock.uptimeMillis() % 1000L) / 1000f * 360f;
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(rect, angle, 100f, false, stroke);
      }

      // Bottom: the seek track
      float trackHeight = dragging ? Screen.dp(4f) : Screen.dp(3f);
      float trackLeft = left + Screen.dp(10f), trackRight = right - Screen.dp(10f);
      float trackY = bottom - Screen.dp(10f);
      if (trackRight > trackLeft) {
        float x = trackLeft + (trackRight - trackLeft) * progress;
        rect.set(trackLeft - Screen.dp(1f), trackY - trackHeight / 2f - Screen.dp(1f), trackRight + Screen.dp(1f), trackY + trackHeight / 2f + Screen.dp(1f));
        fill.setColor(0x33000000);
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fill);
        rect.set(trackLeft, trackY - trackHeight / 2f, trackRight, trackY + trackHeight / 2f);
        fill.setColor(0x66ffffff);
        c.drawRoundRect(rect, trackHeight / 2f, trackHeight / 2f, fill);
        fill.setColor(0xffffffff);
        if (x > trackLeft) {
          rect.right = x;
          c.drawRoundRect(rect, trackHeight / 2f, trackHeight / 2f, fill);
        }
        if (dragging || s.engaged) {
          c.drawCircle(x, trackY, dragging ? Screen.dp(7f) : Screen.dp(5f), fill);
        }
      }
    }

    // Touch: only the controls; a tap anywhere else goes to the message (sound on or off, long press menu)

    private int findTarget (float x, float y) {
      if (cell.isEmpty() || !cell.contains((int) x, (int) y)) {
        return TARGET_NONE;
      }
      float slop = Screen.dp(6f);
      if (!expandRect.isEmpty() && x >= expandRect.left - slop && x <= expandRect.right + slop && y >= expandRect.top - slop && y <= expandRect.bottom + slop) {
        return TARGET_EXPAND;
      }
      if (soundRect.contains(x, y)) {
        return TARGET_SOUND;
      }
      if (!playRect.isEmpty() && playRect.contains(x, y)) {
        return TARGET_PLAY;
      }
      if (y >= cell.bottom - Screen.dp(28f) && cell.width() > Screen.dp(20f)) {
        return TARGET_TRACK;
      }
      return TARGET_NONE;
    }

    private float trackFraction (float x) {
      float trackLeft = cell.left + Screen.dp(10f), trackRight = cell.right - Screen.dp(10f);
      return trackRight > trackLeft ? Math.max(0f, Math.min(1f, (x - trackLeft) / (trackRight - trackLeft))) : 0f;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      final float x = e.getX(), y = e.getY();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          touchTarget = session.released ? TARGET_NONE : findTarget(x, y);
          if (touchTarget == TARGET_NONE) {
            return false;
          }
          touchX = x;
          touchY = y;
          ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true); // no scrolling or swipe to reply from the controls
          }
          if (touchTarget == TARGET_TRACK) {
            dragging = true;
            dragFraction = trackFraction(x);
            invalidate();
          }
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (touchTarget == TARGET_TRACK) {
            dragFraction = trackFraction(x);
            invalidate();
          } else if (touchTarget != TARGET_NONE && Math.max(Math.abs(x - touchX), Math.abs(y - touchY)) > Screen.getTouchSlop()) {
            touchTarget = TARGET_NONE;
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          int target = touchTarget;
          touchTarget = TARGET_NONE;
          if (target == TARGET_TRACK) {
            dragging = false;
            session.seekTo(trackFraction(x));
          } else if (target != TARGET_NONE) {
            playSoundEffect(SoundEffectConstants.CLICK);
            switch (target) {
              case TARGET_PLAY:
                session.togglePlay();
                break;
              case TARGET_SOUND:
                session.setMuted(!session.muted);
                break;
              case TARGET_EXPAND:
                handler.post(session::expand); // removes this view: not while it handles the touch
                break;
            }
          }
          invalidate();
          return true;
        }
        case MotionEvent.ACTION_CANCEL: {
          touchTarget = TARGET_NONE;
          dragging = false;
          invalidate();
          return true;
        }
      }
      return touchTarget != TARGET_NONE;
    }
  }
}
