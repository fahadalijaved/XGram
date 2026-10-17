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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramLoadingIndicator;
import com.fahadali.xgram.appearance.XgramWavyLinear;
import com.fahadali.xgram.layout.XgramTwoPane;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.WatchDog;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDataSource;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
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
import tgx.td.MessageId;
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

  /** {@link #onSettingChanged()} once the switch has handled its own tap. */
  public static void postSettingChanged () {
    handler.post(XgramInlineVideo::onSettingChanged);
  }

  /**
   * Play videos in chat was switched: the video playing stops and the open chats whose media would now be made
   * differently load again where they are. Whether a video message gets the player's view group is fixed when the
   * message is made (TGMessage's constructor asks {@link TGMessageMedia#needViewGroup()}), so the messages are made
   * again rather than told. Both panes' screens are covered.
   */
  public static void onSettingChanged () {
    if (!UI.inUiThread()) {
      handler.post(XgramInlineVideo::onSettingChanged);
      return;
    }
    stop();
    BaseActivity context = UI.getUiContext();
    if (context == null) {
      return;
    }
    NavigationController navigation = context.navigation();
    if (navigation != null) {
      rebuildChats(navigation.getStack());
    }
    XgramTwoPane twoPane = context.xgramTwoPane();
    ViewController<?> leftMain = twoPane != null ? twoPane.leftMain() : null;
    NavigationController leftNavigation = leftMain != null ? leftMain.navigationController() : null;
    if (leftNavigation != null && leftNavigation != navigation) {
      rebuildChats(leftNavigation.getStack());
    }
  }

  private static void rebuildChats (@Nullable NavigationStack stack) {
    if (stack == null) {
      return;
    }
    for (int i = 0; i < stack.size(); i++) {
      ViewController<?> c = stack.get(i);
      if (c instanceof MessagesController) {
        rebuildChat((MessagesController) c);
      }
    }
  }

  private static void rebuildChat (MessagesController c) {
    if (c.isDestroyed() || c.getChat() == null || c.isInForceTouchMode()) {
      return; // a prewarmed chat has no chat yet: its messages are made after this
    }
    MessagesManager manager = c.getManager();
    if (manager == null || manager.isEventLog() || !needsRebuild(manager.getAdapter())) {
      return; // the recent actions log keeps its own loading: it picks the switch up when opened again
    }
    // Back at the first message on screen, at the offset the chat saved for it (MessagesManager.saveScrollPosition).
    TGMessage anchor = null;
    RecyclerView list = c.getMessagesView();
    RecyclerView.LayoutManager layoutManager = list != null ? list.getLayoutManager() : null;
    if (layoutManager instanceof LinearLayoutManager && !manager.isAtVeryBottom()) {
      int position = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
      if (position != RecyclerView.NO_POSITION) {
        anchor = manager.getAdapter().getMessage(position);
      }
    }
    if (anchor != null && anchor.getChatId() == c.getChatId() && anchor.getId() > 0 && !anchor.isSponsoredMessage() && !anchor.isSending()) {
      long messageId = anchor.getBiggestId();
      manager.resetByMessage(new MessageId(anchor.getChatId(), messageId, anchor.getOtherMessageIds(messageId)), MessagesManager.HIGHLIGHT_MODE_POSITION_RESTORE);
    } else {
      c.reloadData(); // at the bottom, or nothing to keep
    }
  }

  /** Some message on hand would get a different view now. */
  private static boolean needsRebuild (@Nullable MessagesAdapter adapter) {
    if (adapter == null) {
      return false;
    }
    for (int i = 0; i < adapter.getMessageCount(); i++) {
      TGMessage message = adapter.getMessage(i);
      if (message instanceof TGMessageMedia && !message.isDestroyed() && message.needViewGroup() != needViewGroup(message.getMessage())) {
        return true;
      }
    }
    return false;
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
      if (wrapper.isVideo()) {
        logRejected(wrapper, "off");
      }
      return false;
    }
    Session s = current;
    if (s != null && s.wrapper == wrapper) {
      s.onVideoTap();
      return true;
    }
    ViewParent parent = view.getParent();
    if (!(parent instanceof MessageViewGroup)) {
      logRejected(wrapper, "not-view-group"); // a message made while the switch was off: it opens in the media viewer
      return false;
    }
    String reason = rejectReason(wrapper, (MessageViewGroup) parent);
    if (reason != null) {
      logRejected(wrapper, reason);
      return false;
    }
    return start(wrapper, (MessageViewGroup) parent, true);
  }

  /** Taps only (never autoplay or drawing): why a video opened in the media viewer, for logcat -s XGram. */
  private static void logRejected (MediaWrapper wrapper, String reason) {
    android.util.Log.i("XGram", "In-chat video: " + reason + " msg=" + wrapper.getSourceMessageId());
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
    return rejectReason(wrapper, group) == null;
  }

  /** Why the video can't play in the message (it opens in the media viewer then), or null when it can. */
  @Nullable
  private static String rejectReason (MediaWrapper wrapper, MessageViewGroup group) {
    TGMessage source = wrapper.xgramSource();
    TdApi.Video video = wrapper.getVideo();
    if (!(source instanceof TGMessageMedia) || video == null || video.video == null) {
      return "no-video";
    }
    if (group.getMessage() != source || !source.needViewGroup()) {
      return "not-view-group";
    }
    if (source.getChatId() == 0 || source.isDestroyed()) {
      return "no-chat";
    }
    if (source.isSecretChat()) {
      return "secret";
    }
    if (source.isHot()) {
      return "hot";
    }
    if (source.isSponsoredMessage()) {
      return "sponsored";
    }
    if (wrapper.xgramSpoilerVisible()) {
      return "spoiler";
    }
    MessagesController c = source.messagesController();
    if (c == null || c.isDestroyed()) {
      return "no-chat";
    }
    if (c.isInForceTouchMode()) {
      return "force-touch";
    }
    if (!c.isFocused()) {
      return "not-focused";
    }
    if (c.getSelectableFactor() > 0f) {
      return "selecting";
    }
    TdApi.File file = video.video;
    if (failed.get(file.id)) {
      return "failed-before";
    }
    boolean loaded = TD.isFileLoaded(file);
    if (file.remote.isUploadingActive || (!file.remote.isUploadingCompleted && !loaded)) {
      return "uploading"; // still being sent
    }
    return loaded || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE ? null : "no-cloud";
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

    // Material 3 Expressive controls (drawControlsM3). Made on the first frame, once per player.
    private static final float M3_SCRIM_DP = 48f, M3_TRACK_INSET_DP = 12f;
    /** The track's touch band, its gap to the bubble time chip, and the shortest track kept beside the chip. */
    private static final float M3_TRACK_BAND_DP = 32f, M3_TRACK_CHIP_GAP_DP = 8f, M3_TRACK_MIN_DP = 48f;
    private static TextPaint m3Text;
    private static float m3TextBaseline;
    /** Paused disk: 0 a circle, 1 a 16dp rounded square at .85 (playing). */
    private XgramExpressive.Spring m3Centre;
    /** Paused disk opacity. */
    private XgramExpressive.Spring m3Fade;
    /** Seek track wave: 1 while playing and not dragging. */
    private XgramExpressive.Spring m3Wave;
    private Path m3PlayPausePath;
    private float m3PlayPauseDrawFactor = -1f;
    private Paint m3Tint, m3Scrim;
    private int m3TintColor, m3ScrimHeight;
    private long m3TimePosition = -1, m3TimeDuration = -1;
    private String m3Time = "";
    private float m3TimeWidth;
    /**
     * The seek track (layoutTrack), shared by drawing and touch, in both designs, and the bubble time chip it makes
     * way for ({@link #hasTimeChip}: one lies over the cell's bottom, so a tap on it goes to the message).
     */
    private float seekLeft, seekRight, seekY;
    private final RectF timeChip = new RectF();
    private boolean hasTimeChip;
    /** The classic controls' seek track: this far from the cell's sides and bottom. */
    private static final float CLASSIC_TRACK_INSET_DP = 10f;

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
      if (XgramExpressive.isEnabled()) {
        drawControlsM3(c);
        return;
      }
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

      // Bottom: the seek track, clear of the bubble time chip
      float trackHeight = dragging ? Screen.dp(4f) : Screen.dp(3f);
      layoutTrack();
      final float trackLeft = seekLeft, trackRight = seekRight;
      final float trackY = seekY;
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

    // Material 3 Expressive controls: a full-corner chip on a 50 % black scrim (play or pause, the time in
    // labelMedium, sound), a 32dp expand button, a primaryContainer disk while paused that morphs into a rounded
    // square and fades as playback resumes, the contained loading indicator while buffering, and the M3 linear seek
    // track (wavy while playing, a handle bar once touched) over a bottom gradient. Nothing is allocated per frame:
    // the time string is made again only when its second changes and the gradient when the cell's height does.

    private static TextPaint m3TextPaint () {
      if (m3Text == null) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffffffff);
        p.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
        p.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          p.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M));
          p.setFontFeatureSettings("tnum"); // the time doesn't change width while it counts
        }
        Paint.FontMetrics metrics = p.getFontMetrics();
        m3TextBaseline = -(metrics.ascent + metrics.descent) / 2f;
        m3Text = p;
      }
      return m3Text;
    }

    private void onM3Spring (XgramExpressive.Spring spring, float value) {
      invalidate();
    }

    /** Springs made at the current state on the first frame (nothing animates in), retargeted after that. */
    private void updateM3State (boolean showCentre, boolean waving) {
      final float centre = showCentre ? 0f : 1f, fade = showCentre ? 1f : 0f, wave = waving ? 1f : 0f;
      if (m3Centre == null) {
        m3Centre = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, centre, this::onM3Spring);
        m3Fade = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, fade, this::onM3Spring);
        m3Wave = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, wave, this::onM3Spring);
        m3PlayPausePath = new Path();
        m3Tint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        m3Scrim = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        m3TextPaint();
        return;
      }
      if (m3Centre.getTarget() != centre) {
        m3Centre.animateTo(centre);
      }
      if (m3Fade.getTarget() != fade) {
        m3Fade.animateTo(fade);
      }
      if (m3Wave.getTarget() != wave) {
        m3Wave.animateTo(wave);
      }
    }

    private void updateM3Time (long position, long duration) {
      final long positionSeconds = position / 1000;
      final long durationSeconds = duration > 0 ? duration / 1000 : -1;
      if (positionSeconds != m3TimePosition || durationSeconds != m3TimeDuration) {
        m3TimePosition = positionSeconds;
        m3TimeDuration = durationSeconds;
        m3Time = durationSeconds >= 0 ? Strings.buildDuration(positionSeconds) + " / " + Strings.buildDuration(durationSeconds) : Strings.buildDuration(positionSeconds);
        m3TimeWidth = m3TextPaint().measureText(m3Time);
      }
    }

    /** {@link #drawIcon} in {@code color} (its alpha too). The drawable is shared: its alpha is put back after. */
    private void drawTintedIcon (Canvas c, @Nullable Drawable d, float cx, float cy, float size, int color) {
      if (d == null || d.getMinimumWidth() <= 0) {
        return;
      }
      final int opaque = color | 0xff000000;
      if (m3TintColor != opaque || m3Tint.getColorFilter() == null) {
        m3TintColor = opaque;
        m3Tint.setColorFilter(new PorterDuffColorFilter(opaque, PorterDuff.Mode.SRC_IN));
      }
      final int alpha = color >>> 24;
      m3Tint.setAlpha(alpha);
      float scale = size / d.getMinimumWidth();
      int saveCount = c.save();
      c.translate(cx, cy);
      c.scale(scale, scale);
      Drawables.draw(c, d, -d.getMinimumWidth() / 2f, -d.getMinimumHeight() / 2f, m3Tint);
      c.restoreToCount(saveCount);
      if (alpha != 0xff) {
        d.setAlpha(0xff);
      }
    }

    /**
     * The seek track: the cell's width less 12dp at each end, 12dp over the bottom (10dp in the classic controls).
     * When the bubble time chip lies over the cell's bottom (a video without a caption), the track ends 8dp before
     * the chip on the chip's center line, the way a player puts the slider and a trailing label on one row; with less
     * than 48dp left for it, it rises above the chip instead. Either way the chip never sits on the track. The chip is
     * Telegram X's own with Expressive design off (MediaWrapper#xgramTimeChipRect).
     */
    private void layoutTrack () {
      final int inset = Screen.dp(XgramExpressive.isEnabled() ? M3_TRACK_INSET_DP : CLASSIC_TRACK_INSET_DP);
      float trackLeft = cell.left + inset, trackRight = cell.right - inset;
      float trackY = cell.bottom - inset;
      final RectF chip = timeChip;
      hasTimeChip = session.wrapper.xgramTimeChipRect(chip) &&
        chip.bottom > cell.bottom - Screen.dp(M3_TRACK_BAND_DP) && chip.top < cell.bottom &&
        chip.right > cell.left && chip.left < cell.right;
      if (hasTimeChip) {
        final boolean chipAtEnd = chip.centerX() >= cell.exactCenterX();
        final float start = chipAtEnd ? trackLeft : Math.max(trackLeft, chip.right + Screen.dp(M3_TRACK_CHIP_GAP_DP));
        final float end = chipAtEnd ? Math.min(trackRight, chip.left - Screen.dp(M3_TRACK_CHIP_GAP_DP)) : trackRight;
        if (end - start >= Screen.dp(M3_TRACK_MIN_DP)) {
          trackLeft = start;
          trackRight = end;
          trackY = chip.centerY();
        } else {
          trackY = chip.top - inset;
        }
      }
      seekLeft = trackLeft;
      seekRight = trackRight;
      seekY = trackY;
    }

    private void drawControlsM3 (Canvas c) {
      final Session s = session;
      final int left = cell.left, top = cell.top, right = cell.right, bottom = cell.bottom;
      final long duration = s.duration();
      final long position = dragging && duration > 0 ? (long) (duration * dragFraction) : s.position();
      final float progress = duration > 0 ? Math.max(0f, Math.min(1f, (float) position / (float) duration)) : 0f;
      final boolean playing = s.isPlaying();
      updateM3State(!playing && !dragging, playing && !dragging);
      boolean animating = false;
      layoutTrack();

      // Bottom: a gradient for the seek track to stand out on (taller when the track rises above the time chip)
      final int scrimHeight = Math.min(Math.max(Screen.dp(M3_SCRIM_DP), (int) (bottom - seekY) + Screen.dp(24f)), cell.height());
      if (scrimHeight > 0) {
        if (m3ScrimHeight != scrimHeight) {
          m3ScrimHeight = scrimHeight;
          m3Scrim.setShader(new LinearGradient(0f, 0f, 0f, scrimHeight, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        }
        int saveCount = c.save();
        c.translate(0f, bottom - scrimHeight);
        c.drawRect(left, 0f, right, scrimHeight, m3Scrim);
        c.restoreToCount(saveCount);
      }

      final int pad = Screen.dp(8f);
      final int chipHeight = Screen.dp(32f);
      final int iconSize = Screen.dp(20f);
      final int inner = Screen.dp(8f), spacing = Screen.dp(6f);
      final float expandRadius = Screen.dp(16f);

      // Top start: play or pause, the time, sound on or off
      updateM3Time(position, duration);
      final float playPart = inner + iconSize + spacing;
      final float soundPart = spacing + iconSize + inner;
      final float room = cell.width() - pad * 3 - expandRadius * 2;
      final boolean showPlay = playPart + soundPart <= room;
      final boolean showTime = showPlay && playPart + m3TimeWidth + soundPart <= room;
      final float chipWidth = showPlay ? playPart + (showTime ? m3TimeWidth : 0f) + soundPart : chipHeight;
      pillRect.set(left + pad, top + pad, left + pad + chipWidth, top + pad + chipHeight);
      fill.setColor(0x80000000);
      c.drawRoundRect(pillRect, chipHeight / 2f, chipHeight / 2f, fill);
      final Drawable soundIcon = s.isSilent() ? soundOffIcon : soundOnIcon;
      if (showPlay) {
        playRect.set(pillRect.left, pillRect.top, pillRect.right - soundPart, pillRect.bottom);
        drawIcon(c, playing ? pauseIcon : playIcon, pillRect.left + inner + iconSize / 2f, pillRect.centerY(), iconSize);
        soundRect.set(pillRect.right - soundPart, pillRect.top, pillRect.right, pillRect.bottom);
        drawIcon(c, soundIcon, soundRect.left + spacing + iconSize / 2f, pillRect.centerY(), iconSize);
      } else {
        playRect.setEmpty();
        soundRect.set(pillRect);
        drawIcon(c, soundIcon, pillRect.centerX(), pillRect.centerY(), iconSize);
      }
      if (showTime) {
        c.drawText(m3Time, pillRect.left + playPart, pillRect.centerY() + m3TextBaseline, m3TextPaint());
      }

      // Top end: the full screen viewer
      final float expandX = right - pad - expandRadius, expandY = pillRect.centerY();
      if (expandX - expandRadius >= pillRect.right + Screen.dp(4f)) {
        expandRect.set(expandX - expandRadius, expandY - expandRadius, expandX + expandRadius, expandY + expandRadius);
        c.drawCircle(expandX, expandY, expandRadius, fill);
        drawIcon(c, expandIcon, expandX, expandY, iconSize);
      } else {
        expandRect.setEmpty();
      }

      // Middle: paused or over (the disk morphs away as it plays), or loading. In a short cell (an album tile) the disk
      // keeps clear of the top controls: it moves down into the room under them and shrinks to it (to 32dp; with less
      // room it is left out, the top chip still plays and pauses)
      final float half = Screen.dp(28f);
      final float topLimit = top + pad + chipHeight + Screen.dp(4f);
      final float diskHalf = Math.min(half, (bottom - Screen.dp(8f) - topLimit) / 2f);
      final float cx = cell.exactCenterX(), cy = Math.max(cell.exactCenterY(), topLimit + Math.max(0f, diskHalf));
      final float fade = Math.max(0f, Math.min(1f, m3Fade.getValue()));
      if (fade > 0f && diskHalf >= Screen.dp(16f)) {
        final float morph = Math.max(-.2f, Math.min(1.2f, m3Centre.getValue()));
        final float corner = Math.min(half, half + (Screen.dp(XgramExpressive.CORNER_L) - half) * morph);
        final float scale = (1f - .15f * Math.max(0f, morph)) * (diskHalf / half);
        int saveCount = c.save();
        c.scale(scale, scale, cx, cy);
        rect.set(cx - half, cy - half, cx + half, cy + half);
        fill.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), .92f * fade));
        c.drawRoundRect(rect, corner, corner, fill);
        final int content = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), fade);
        if (s.ended) {
          drawTintedIcon(c, replayIcon, cx, cy, Screen.dp(24f), content);
        } else {
          final float glyph = Math.max(0f, Math.min(1f, morph)); // play, turning into pause as it goes
          DrawAlgorithms.drawPlayPause(c, (int) cx, (int) cy, Screen.dp(13f), m3PlayPausePath, m3PlayPauseDrawFactor, m3PlayPauseDrawFactor = glyph, 1f, content);
        }
        c.restoreToCount(saveCount);
      }
      if (playing && s.buffering) {
        fill.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), .92f));
        c.drawCircle(cx, cy, Screen.dp(24f), fill);
        XgramLoadingIndicator.draw(c, cx, cy, Screen.dp(38f), XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), SystemClock.uptimeMillis());
        animating = true;
      }

      // Bottom: the seek track, clear of the bubble time chip
      final float trackLeft = seekLeft, trackRight = seekRight;
      if (trackRight > trackLeft) {
        final float trackY = seekY;
        final float amplitude = Screen.dp(3f) * Math.max(0f, Math.min(1f, m3Wave.getValue()));
        final boolean handle = dragging || s.engaged;
        XgramWavyLinear.draw(c, trackLeft, trackRight, trackY, progress, 0xffffffff, 0x4dffffff,
          Screen.dp(4f), amplitude, Screen.dp(40f), Screen.dp(4f), true,
          handle ? Screen.dp(4f) : 0f, handle ? Screen.dp(16f) : 0f);
        if (playing && XgramWavyLinear.isWaveMoving(progress, amplitude)) {
          animating = true;
        }
      }

      if (animating) {
        postInvalidateOnAnimation(); // every frame while the wave or the indicator moves: the 50ms tick judders them
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
      // The track's own 32dp band, in both designs, so a tap on the bubble time chip beside it doesn't seek: the
      // chip itself always goes to the message (Tap time for details)
      layoutTrack();
      if (hasTimeChip && timeChip.contains(x, y)) {
        return TARGET_NONE;
      }
      final float band = Screen.dp(M3_TRACK_BAND_DP) / 2f;
      if (seekRight > seekLeft && Math.abs(y - seekY) <= band && x >= seekLeft - slop * 2f && x <= seekRight + slop * 2f) {
        return TARGET_TRACK;
      }
      return TARGET_NONE;
    }

    private float trackFraction (float x) {
      final float trackLeft = seekLeft, trackRight = seekRight;
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
