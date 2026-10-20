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
 * File created on 17/08/2015 at 19:33
 */
package org.thunderdog.challegram.navigation;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.TGLegacyAudioManager;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.ui.PlaybackController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.CounterPlaybackSpeedDrawableSet;
import org.thunderdog.challegram.util.RateLimiter;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;

public class HeaderFilling extends Drawable implements TGLegacyAudioManager.PlayListener, FactorAnimator.Target, CallManager.CurrentCallListener, TdlibCache.CallStateChangeListener, Runnable, TGPlayerController.TrackChangeListener, TGPlayerController.TrackListener, ClickHelper.Delegate, Destroyable, TGLegacyManager.EmojiLoadListener {
  private HeaderView headerView; // Header that holds the filling
  private final @Nullable NavigationController navigationController;

  // Filling

  private int color;

  private int width, height;
  private float fillingBottom;
  private float shadowTop;

  // private Paint bgPaint;

  private boolean isOutlineBig;
  private boolean outlineBigForced;

  // Player

  private static final int SECTION_AUDIO = 1 << 1;
  private static final int SECTION_CALL = 1 << 2;

  private Tdlib playingMessageTdlib;
  private TdApi.Message playingMessage;

  private float fillFactor;

  private float hideFactor;
  private float showFactor;
  private boolean showOngoingBar;
  private int ongoingSections;

  private Drawable hangIcon, micIcon, forwardIcon;
  private final BoolAnimator speedIsActive;
  private final @Nullable Counter speedCounter;

  private int lastBarType;
  private int textLeft;
  private int playerTop, playerBottom;

  private boolean needOffsets;

  public void setNeedOffsets () {
    needOffsets = true;
  }

  private int getTopOffset () {
    return needOffsets ? headerView.getEffectiveTopOffset() : 0;
  }

  private final ClickHelper helper;

  public HeaderFilling (HeaderView parent, @Nullable NavigationController navigationController) {
    this.xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    this.headerView = parent;
    this.navigationController = navigationController;

    if (headerView != null) {
      helper = new ClickHelper(this);
      helper.setNoSound(true);
    } else {
      helper = null;
    }

    this.fillFactor = 1f;

    this.micIcon = Drawables.get(parent.getResources(), R.drawable.baseline_mic_24);
    this.hangIcon = Drawables.get(parent.getResources(), R.drawable.baseline_call_end_24);
    this.forwardIcon = Drawables.get(parent.getResources(), R.drawable.baseline_fast_forward_24);
    this.speedIsActive = new BoolAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

    this.textLeft = Screen.dp(72f);

    if (xgram) {
      xgramPhoneIcon = Drawables.get(parent.getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_phone_24));
      // A video call's chip shows the camera: its own instance, swapped (never crossfaded) with the phone
      xgramVideoIcon = Drawables.get(parent.getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_videocam_24));
      xgramMusicIcon = Drawables.get(parent.getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_music_note_24));
      xgramClose = new com.fahadali.xgram.appearance.XgramM3Chat.DrawnIconButton(parent, R.drawable.baseline_close_24);
    }

    if (navigationController != null) {
      this.speedCounter = new Counter.Builder()
        .noBackground()
        .allBold(true)
        .drawable(Drawables.get(R.drawable.baseline_playback_speed_x_5), 0, Gravity.LEFT)
        .setCustomTextPartBuilder(new CounterPlaybackSpeedDrawableSet())
        .textSize(13f)
        .colorSet(this::getSpeedCounterColor)
        .callback((a, b) -> invalidate())
        .build();

      TdlibManager.instance().player().addTrackChangeListener(this);
      initCallListeners();
      updateSpeedCounter(TdlibManager.instance().player().getSpeed(), false);
    } else {
      this.speedCounter = null;
    }
    TGLegacyManager.instance().addEmojiListener(this);
  }

  private void updateSpeedCounter (int speed, boolean animated) {
    if (xgram && (xgramSpeedText == null || xgramSpeed != speed)) {
      xgramSpeed = speed;
      xgramSpeedText = PlaybackSpeedLayout.getSpeedText(speed) + "×";
      xgramSpeedTextWidth = com.fahadali.xgram.appearance.XgramR5Calls.measurePaint(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M).measureText(xgramSpeedText);
      invalidateOngoingBar();
    }
    if (speedCounter != null) {
      speedIsActive.setValue(speed != TGPlayerController.PLAY_SPEED_NORMAL, animated);
      speedCounter.setCount(speed, false, PlaybackSpeedLayout.getSpeedText(speed), animated);
    }
  }

  private int getSpeedCounterColor () {
    return ColorUtils.fromToArgb(Theme.getColor(ColorId.icon), Theme.getColor(ColorId.iconActive), speedIsActive.getFloatValue());
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidateOngoingBar();
  }

  @Override
  public void performDestroy () {
    TGLegacyManager.instance().removeEmojiListener(this);
    UI.removePendingRunnable(xgramPulseNext); // XGram
  }

  // Component stuff

  public int getExtraHeight () { // Called to get the extra height of the header
    return ShadowView.simpleBottomShadowHeight();
  }

  public void forceBigOutline (boolean isBig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (outlineBigForced != isBig) {
        outlineBigForced = isBig;
        headerView.invalidateOutline();
      }
    }
  }

  public int getOutlineBottom () {
    return outlineBigForced ? Size.getHeaderPortraitSize() + Size.getMaximumHeaderSizeDifference() : isOutlineBig ? Math.min((int) fillingBottom, Size.getHeaderPortraitSize() + Size.getMaximumHeaderSizeDifference()) : Size.getHeaderPortraitSize();
  }

  public int getBottom () {
    return height + (int) (playerSize() * showFactor);
  }

  public int getPlayerOffset () {
    return (int) (playerSize() * showFactor);
  }

  /** The ongoing bar's height: Telegram X's 36dp bar, or the Expressive call chip and mini player's 64dp. */
  private int playerSize () {
    return xgram ? Screen.dp(XGRAM_BAR) : HeaderView.getPlayerSize();
  }

  /**
   * XGram: the height of the ongoing call / audio band under the app bar for code outside the header (the round video
   * PiP margin): {@link HeaderView#getPlayerSize()}, or 64dp with Expressive design.
   */
  public static int playerBandSize () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? Screen.dp(XGRAM_BAR) : HeaderView.getPlayerSize();
  }

  public void setCollapsed (boolean collapsed) {
    if (collapsed) {
      if (showFactor != 0f) {
        hideFactor = 1f;
      }
      fillFactor = 0f;
    } else {
      hideFactor = 0f;
      fillFactor = 1f;
    }
    invalidate();
  }

  public void collapseFilling (float px) {
    boolean updated = false;
    if (showFactor != 0f || true) {
      if (px > playerBottom) {
        if (fillFactor != 1f || hideFactor != 0f) {
          fillFactor = 1f;
          hideFactor = 0f;
          updated = true;
        }
      } else {
        float x = px - playerTop;
        hideFactor = x <= 0 ? 1f : x >= playerSize() ? 0f : 1f - (x / (float) playerSize());

        if (px > fillingBottom) {
          fillFactor = 1f;
        } else {
          fillFactor = px / fillingBottom;
        }
        updated = true;
      }
    } else {
      if (px > fillingBottom) {
        if (fillFactor != 1f) {
          fillFactor = 1f;
          updated = true;
        }
      } else {
        fillFactor = px / fillingBottom;
        updated = true;
      }
    }
    if (updated) {
      invalidate();
    }
  }

  public void layout (int width, int height, float factor) { // Called during the layout
    this.width = width;
    trimText(lastTextPadding);
    layout(height, factor);
  }

  public void layout (int height, float factor) { // Called during the navigation animation
    height += getTopOffset();

    this.height = height;

    if (showFactor != 0f) {
      layoutOngoingBar();
    } else {
      fillingBottom = shadowTop = playerBottom = height;
      playerTop = playerBottom - (xgram ? playerSize() : Size.getHeaderPlayerSize());
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (isOutlineBig) {
        if (factor <= .5f) {
          isOutlineBig = false;
          headerView.invalidateOutline();
        } else if (factor == 1f) {
          headerView.invalidateOutline();
        }
      } else {
        if (factor > .5f) {
          isOutlineBig = true;
          headerView.invalidateOutline();
        } else if (factor == 0f) {
          headerView.invalidateOutline();
        }
      }
    }
  }

  public void layout () {
    layoutOngoingBar();
    if (navigationController != null) {
      navigationController.applyPlayerOffset(showFactor, (float) playerSize() * showFactor);
    }
  }

  private float playerAllowance = 1f;

  public void setPlayerAllowance (float allowance) {
    if (this.playerAllowance != allowance) {
      this.playerAllowance = allowance;
      layout();
      invalidate();
      UI.getContext(headerView.getContext()).getRoundVideoController().checkLayout();
    }
  }

  private void layoutOngoingBar () {
    playerBottom = height + (int) ((float) playerSize() * (showFactor - MathUtils.clamp((float) (height - HeaderView.getSize(true)) / (float) Size.getHeaderSizeDifference(false))) * playerAllowance);
    playerTop = playerBottom - playerSize();

    fillingBottom = height;
    shadowTop = Math.max(fillingBottom, playerBottom);
  }

  private float dropShadowAlpha = 1f;

  public void setShadowAlpha (float alpha) {
    if (this.dropShadowAlpha != alpha) {
      this.dropShadowAlpha = alpha;
      invalidate();
    }
  }

  public void setColor (int color) {
    this.color = color;
  }

  public int getColor () {
    return color;
  }

  private float radiusFactor;
  private int radiusColor;

  public void setRadiusFactor (float factor, int radiusColor) {
    this.radiusFactor = factor;
    this.radiusColor = radiusColor;
  }

  public void resetRadius () {
    this.radiusFactor = 0f;
    this.radiusColor = 0;
  }

  // Restoring the lost rect

  private boolean restoreRect;
  private float restorePixels;
  private int restoreColor;

  public void setRestorePixels (boolean restore, float pixels, int color) {
    this.restoreRect = restore;
    if (restore) {
      this.restorePixels = pixels;
      this.restoreColor = color;
      invalidate();
    }
  }

  public void restorePixels (float pixels) {
    if (this.restoreRect && this.restorePixels != pixels) {
      this.restorePixels = pixels;
      invalidate();
    }
  }

  // Drawing

  private boolean hasVisibleOngoingBar () {
    return showFactor != 0f && hideFactor != 1f && dropShadowAlpha != 0f;
  }

  @Override
  public void draw (@NonNull Canvas c) {
    if (restoreRect && restorePixels > 0) {
      if (Lang.rtl()) {
        c.drawRect(0, 0, restorePixels, fillingBottom, Paints.fillingPaint(restoreColor));
      } else {
        c.drawRect(width - restorePixels, 0, width, fillingBottom, Paints.fillingPaint(restoreColor));
      }
    }
    if (hasVisibleOngoingBar()) {
      drawOngoingBar(c);
    }
    if (fillFactor == 1f && hideFactor == 0f) {
      c.drawRect(0f, 0f, width, fillingBottom, Paints.fillingPaint(color));
      if (radiusFactor != 0f && radiusColor != 0) {
        c.save();
        c.clipRect(0, 0, width, fillingBottom);
        float radius = (float) Math.sqrt(width * width + fillingBottom * fillingBottom) * .5f;
        float startX = Lang.rtl() ? Screen.dp(49f) / 2 - Screen.dp(3f) - Screen.dp(5f) : width - Screen.dp(49f) / 2 + Screen.dp(3f) + Screen.dp(5f);
        float endX = width / 2;
        float x = startX + (endX - startX) * radiusFactor;
        float y = headerView.getEffectiveTopOffset() + HeaderView.getSize(false) / 2 + Screen.dp(2f);
        c.drawCircle(x, y, radius * radiusFactor, Paints.fillingPaint(ColorUtils.alphaColor(.35f + .65f * radiusFactor, radiusColor)));
        // c.drawCircle(startX, y, Screen.dp(2f), Paints.fillingPaint(0xaaff0000));
        c.restore();
      }
      if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) { // XGram: Material 3 Expressive app bars have no shadow
        ShadowView.drawDropShadow(c, 0, width, (int) shadowTop, dropShadowAlpha);
      }
    } else {
      if (fillFactor == 1f) {
        c.drawRect(0f, 0f, width, fillingBottom, Paints.fillingPaint(color));
      } else {
        c.drawRect(0f, 0f, width, fillingBottom * fillFactor, Paints.fillingPaint(color));
      }
    }
    if (navigationController != null) {
      ViewController<?> current = navigationController.getCurrentStackItem();
      if (current != null) {
        current.drawTransform(c, width, (int) fillingBottom);
      }
    }
    /*if (needStatusBar) {
      final int offset = getTopOffset();
      if (navigationController == null && offset > 0) {
        c.drawRect(0, 0, width, offset, Paints.fillingPaint(Theme.getColor(ColorId.statusBar)));
      }
    }*/
  }

  private void invalidate () {
    // invalidateSelf();
    headerView.invalidate();
  }

  private void invalidateOngoingBar () {
    // invalidateSelf();
    headerView.invalidate(0, playerTop, width, playerBottom);
  }

  private boolean isCurrentSection (int section) {
    return (ongoingSections & section) != 0 || (!showOngoingBar && lastBarType == section);
  }

  private boolean canBuildSection (int section) {
    int flags = this.ongoingSections & ~section;
    return section > flags;
  }

  private void drawOngoingBar (Canvas c) {
    int hide = (int) ((float) playerSize() * hideFactor);
    int playerTop = this.playerTop - hide;
    int playerBottom = this.playerBottom - hide;
    float rectWidth;

    if (restoreRect && restorePixels > 0) {
      rectWidth = width - restorePixels;
    } else {
      rectWidth = width;
    }

    if (xgram) { // XGram M3E: the call chip and the mini player card
      if (isCurrentSection(SECTION_CALL)) {
        xgramDrawCall(c, playerTop, rectWidth, playerBottom);
      } else if (isCurrentSection(SECTION_AUDIO)) {
        xgramDrawAudio(c, playerTop, rectWidth, playerBottom);
      }
      return;
    }

    if (isCurrentSection(SECTION_CALL)) {
      drawOngoingCall(c, playerTop, rectWidth, playerBottom);
    } else if (isCurrentSection(SECTION_AUDIO)) {
      drawOngoingAudio(c, playerTop, rectWidth, playerBottom);
    }
  }

  // Audio (new)

  private Tdlib currentTrackTdlib;
  private TdApi.Message currentTrack;

  @Override
  public void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) {
    boolean changedTrack = false;
    if (currentTrackTdlib != tdlib || currentTrack != newTrack) {
      if (currentTrack != null) {
        TdlibManager.instance().player().removeTrackListener(currentTrackTdlib, currentTrack, this);
      }
      currentTrack = newTrack;
      currentTrackTdlib = tdlib;
      changedTrack = true;
    }
    boolean isPlaying = state == TGPlayerController.STATE_PLAYING;
    TdApi.Message newMessage = newTrack != null && state != TGPlayerController.STATE_NONE ? newTrack : null;
    boolean updatePlay;
    if (this.playingMessageTdlib != tdlib || this.playingMessage != newMessage) {
      this.playingMessageTdlib = tdlib;
      this.playingMessage = newMessage;
      boolean show = newMessage != null;
      setShowOngoingSection(SECTION_AUDIO, show, true, false);
      updatePlay = show;
    } else {
      updatePlay = newTrack != null && TGPlayerController.compareTracks(newTrack, playingMessage);
    }
    if (updatePlay) {
      setIsPlaying(isPlaying, hasVisibleOngoingBar());
      if (changedTrack) {
        TdlibManager.instance().player().addTrackListener(tdlib, newTrack, this);
      }
    }
  }

  @Override
  public void onPlaybackSpeedChanged (int newSpeed) {
    UI.execute(() -> updateSpeedCounter(newSpeed, true));
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) { }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (currentTrack != null && !Td.isVideoNote(currentTrack.content)) {
      setSeekFactor(progress);
    }
  }

  // Audio

  public boolean playPause () {
    if (playingMessage != null) {
      TdlibManager.instance().player().playPauseMessage(playingMessageTdlib, playingMessage, null);
      return true;
    }
    return false;
  }

  public void openPlayer () {
    if (navigationController == null || playingMessageTdlib == null || playingMessage == null) {
      return;
    }
    if (!Td.isAudio(playingMessage.content)) {
      if (playingMessage.chatId == 0 || playingMessage.id == 0) {
        return;
      }

      final BaseActivity context = UI.getContext(navigationController.getContext());

      if (TD.isScheduled(playingMessage)) {
        playingMessageTdlib.ui().openScheduledMessage(new TdlibContext(context, playingMessageTdlib), playingMessage);
      } else {
        playingMessageTdlib.ui().openMessage(new TdlibContext(context, playingMessageTdlib), playingMessage, null);
      }

      return;
    }
    PlaybackController c = new PlaybackController(navigationController.getContext(), playingMessageTdlib);
    if (c.prepare() != -1) {
      navigationController.navigateTo(c);
    }
  }

  @Override
  public void onPlayPause (int fileId, boolean isPlaying, boolean isUpdate) {
    setIsPlaying(isPlaying, true);
  }

  @Override
  public boolean needPlayProgress (int fileId) {
    return true;
  }

  @Override
  public void onPlayProgress (int fileId, float progress, boolean isUpdate) {
    setSeekFactor(progress);
  }

  private float seekFactor;

  public void setSeekFactor (float seekFactor) { // 0f -> 1f length of the player seek
    if (this.seekFactor != seekFactor) {
      final int oldWidth = (int) ((float) width * this.seekFactor);
      this.seekFactor = seekFactor;
      final int newWidth = (int) ((float) width * seekFactor);
      if (oldWidth != newWidth) {
        invalidateOngoingBar();
      }
    }
  }

  private boolean isAudioPlaying;

  private static String buildPrivateTitle (Tdlib tdlib, TdApi.Message message) {
    String title;
    if (tdlib.isSelfSender(message)) {
      title = Lang.getString(R.string.FromYou);
    } else {
      title = tdlib.messageAuthor(message);
      if (StringUtils.isEmpty(title)) {
        title = Lang.getString(Td.isVideoNote(message.content) ? R.string.AttachRound : R.string.AttachAudio);
      }
    }
    return title;
  }

  private void buildAudio () {
    if (playPausePath == null) {
      playPausePath = new Path();
    }

    this.seekFactor = 0f;
    String title, subtitle;
    String fileName = null;
    boolean isVoice = false;

    if (playingMessage.content.getConstructor() != TdApi.MessageAudio.CONSTRUCTOR) {
      title = buildPrivateTitle(playingMessageTdlib, playingMessage);
      subtitle = TD.isScheduled(playingMessage) ? Lang.getString(R.string.ScheduledMessage) : playingMessage.date != 0 ? Lang.getRelativeTimestamp(playingMessage.date, TimeUnit.SECONDS, true, 5) : null; // TODO update
      isVoice = playingMessage.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR;
    } else {
      TdApi.Audio audio = ((TdApi.MessageAudio) playingMessage.content).audio;
      title = TD.getTitle(audio);
      subtitle = TD.getSubtitle(audio);
      fileName = audio.fileName;
    }

    if (xgram) { // XGram M3E: title over the supporting line, not one "title — subtitle" line
      xgramAudioVoice = playingMessage.content.getConstructor() != TdApi.MessageAudio.CONSTRUCTOR;
      String xgramTitle = title != null ? title.trim() : null;
      String xgramSubtitle = subtitle != null ? subtitle.trim() : null;
      if (StringUtils.isEmpty(xgramTitle) && fileName != null) {
        xgramTitle = fileName.trim();
      }
      xgramSetAudioText(xgramTitle, xgramSubtitle);
    }

    if (title != null) {
      title = title.trim();
    }
    if (subtitle != null) {
      subtitle = subtitle.trim();
    }
    if (title != null && subtitle != null && title.length() > 0 && subtitle.length() > 0) {
      subtitle = isVoice && !TD.isScheduled(playingMessage) ? " " + subtitle : " — " + subtitle;
    }

    if (StringUtils.isEmpty(subtitle) && StringUtils.isEmpty(title) && fileName != null) {
      subtitle = fileName.trim();
    }

    setText(StringUtils.isEmpty(title) ? null : title, StringUtils.isEmpty(subtitle) ? null : subtitle, Screen.dp(67 + (SIZE + GAP) * 3 + OFFSET));
  }

  private void drawOngoingAudio (Canvas c, int playerTop, float rectWidth, int playerBottom) {
    if (restoreRect && restorePixels > 0) {
      c.drawRect(rectWidth, playerTop, width, playerBottom, Paints.fillingPaint(Theme.fillingColor()));
    }

    final int playerFillingColor = ColorUtils.alphaColor(dropShadowAlpha, Theme.fillingColor());

    c.drawRect(0, playerTop, rectWidth, playerBottom, Paints.fillingPaint(playerFillingColor));
    if (seekFactor != 0f) {
      c.drawRect(0, playerBottom - (Screen.dp(1f) + 1), (int) (width * seekFactor), playerBottom, Paints.fillingPaint(ColorUtils.alphaColor(dropShadowAlpha, Theme.getColor(ColorId.headerBarCallActive))));
    }

    if (speedCounter != null) {
      speedCounter.draw(c, getRightButtonCenter(width, 2), (playerBottom + playerTop) / 2f, Gravity.CENTER, 1f);
    }

    TdApi.File file = TD.getFile(playingMessage);
    DrawAlgorithms.drawPlayPause(c, getRightButtonCenter(width, 0), playerBottom - Size.getHeaderPlayerSize() / 2, Screen.dp(12f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = playPauseFactor, file != null ? TD.getFileProgress(file) : 1f, ColorUtils.alphaColor(dropShadowAlpha, Theme.iconColor()));

    Drawables.draw(c, forwardIcon, getRightButtonLeft(width, 1), playerBottom - Size.getHeaderPlayerSize() / 2 - forwardIcon.getMinimumHeight() / 2, dropShadowAlpha == 1f ? Paints.getIconGrayPorterDuffPaint() : Paints.getPorterDuffPaint(ColorUtils.alphaColor(dropShadowAlpha, Theme.iconColor())));
    drawCloseIcon(c, playerTop, width, playerBottom, false);

    drawOngoingText(c, playerTop, rectWidth, playerBottom, Screen.dp(67f), null, 1f, 1f);
  }

  private void drawCloseIcon (Canvas c, int playerTop, float rectWidth, int playerBottom, boolean alignRight) {
    float cx = alignRight ? rectWidth - Screen.dp(50f) / 2 : Screen.dp(28f);
    DrawAlgorithms.drawMark(c, cx, playerTop + (playerBottom - playerTop) / 2, 1f, Screen.dp(9f), Paints.getProgressPaint(ColorUtils.alphaColor(dropShadowAlpha, Theme.iconColor()),  Screen.dp(2f)));
  }

  // Ongoing bar text

  private String title, subtitle;
  private Text trimmedTitle, trimmedSubtitle;

  private void setText (String title, String text, int textPadding) {
    boolean changed = lastTextPadding != 0 && lastTextPadding != textPadding;
    if (!StringUtils.equalsOrBothEmpty(this.title, title)) {
      this.title = title;
      changed = true;
    }
    if (!StringUtils.equalsOrBothEmpty(this.subtitle, text)) {
      this.subtitle = text;
      changed = true;
    }
    if (changed) {
      trimText(textPadding);
    }
  }

  private int lastTextPadding;

  private void trimText (int textPadding) {
    this.lastTextPadding = textPadding;
    trimTitle(textPadding);
    trimSubtitle(textPadding);
  }

  private void trimTitle (int padding) {
    if (width != 0) {
      final int availWidth = width - padding;
      this.trimmedTitle = !StringUtils.isEmpty(title) && availWidth > 0 ? new Text.Builder(title, availWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.NORMAL).singleLine().allBold().build() : null;
    }
  }

  private int getTitleWidth () {
    return (trimmedTitle != null ? trimmedTitle.getWidth() : 0);
  }

  private void trimSubtitle (int textPadding) {
    if (width != 0) {
      final int availWidth = width - textPadding - getTitleWidth();
      this.trimmedSubtitle = !StringUtils.isEmpty(subtitle) && availWidth > Screen.dp(16) ? new Text.Builder(subtitle, availWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.LIGHT).singleLine().build() : null;
    }
  }

  private void drawOngoingText (Canvas c, int playerTop, float rectWidth, int playerBottom, int textLeft, TextColorSet theme, float titleAlpha, float textAlpha) {
    int textTop = Screen.dp(9f);
    if (trimmedTitle != null) {
      trimmedTitle.draw(c, textLeft, playerTop + textTop, theme, titleAlpha * dropShadowAlpha);
    }
    if (trimmedSubtitle != null) {
      trimmedSubtitle.draw(c, textLeft + getTitleWidth(), playerTop + textTop, theme, textAlpha * dropShadowAlpha);
    }
  }

  // Audio play state

  private static final int ANIMATOR_PLAY_ID = 1;
  private FactorAnimator playAnimator;
  private Path playPausePath;
  private float playPauseFactor, playPauseDrawFactor = -1;

  public void setIsPlaying (boolean isPlaying, final boolean animated) {
    if (this.isAudioPlaying != isPlaying) {
      this.isAudioPlaying = isPlaying;
      final float toFactor = isPlaying ? 1f : 0f;
      if (xgram) {
        xgramAnimate(ANIMATOR_PLAY_ID, toFactor, animated);
      } else if (animated) {
        if (playAnimator == null) {
          playAnimator = new FactorAnimator(ANIMATOR_PLAY_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160l, this.playPauseFactor);
        }
        playAnimator.animateTo(toFactor);
      } else {
        if (playAnimator != null) {
          playAnimator.forceFactor(toFactor);
        }
        setPlayPauseFactor(toFactor);
      }
    }
  }

  private void setPlayPauseFactor (float playPauseFactor) {
    if (this.playPauseFactor != playPauseFactor) {
      this.playPauseFactor = playPauseFactor;
      invalidateOngoingBar();
    }
  }

  public void setSeekAnimated (float seekTo, int duration) {
    setSeekFactor(seekTo);
  }

  // Calls

  private Tdlib callTdlib;
  private TdApi.Call call;

  private int getCallId () {
    return call != null ? call.id : 0;
  }

  private void initCallListeners () {
    TdlibManager.instance().calls().addCurrentCallListener(this);
    setCall(TdlibManager.instance().calls().getCurrentCallTdlib(), TdlibManager.instance().calls().getCurrentCall(), true);
  }

  @Override
  public void onCurrentCallChanged (final Tdlib tdlib, @Nullable final TdApi.Call call) {
    UI.post(() -> {
      setCall(tdlib, call, false);
      invalidate(); // XGram: the chip's glyph (video was turned on)
    });
  }

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (getCallId() == call.id) {
      updateCall(call);
    }
  }

  @Override
  public void onCallStateChanged (final int callId, final int newState) {
    if (getCallId() == callId && newState == CallState.ESTABLISHED) {
      buildCall();
      invalidateOngoingBar();
    }
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) { }

  @Override
  public void onCallSettingsChanged (final int callId, final CallSettings settings) {
    if (getCallId() == callId) {
      setCallMuted(settings.isMicMuted(), isOngoingBarVisible());
    }
  }

  private TdApi.CallState previousCallState;

  private void updateCall (TdApi.Call call) {
    this.previousCallState = this.call != null ? this.call.state : null;
    this.call = call;
    buildCall();
    invalidateOngoingBar();
  }

  private void buildCall () {
    if (call == null) {
      return;
    }
    TdApi.User user = callTdlib.cache().user(call.userId);
    String callState;
    if (previousCallState != null && call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      callState = TD.getCallState2(call, previousCallState, TdlibManager.instance().calls().getCallDuration(callTdlib, call.id), true);
    } else {
      callState = TD.getCallState(call, TdlibManager.instance().calls().getCallDuration(callTdlib, call.id), true);
    }
    setText(TD.getUserName(user), "  " + callState, textLeft * 2);
    if (xgram) {
      xgramSetCallText(TD.getUserName(user), callState);
    }
    if (!TD.isFinished(call)) {
      CallSettings settings = callTdlib.cache().getCallSettings(call.id);
      setCallMuted(settings != null && settings.isMicMuted(), isOngoingBarVisible());
    }
    setCallActive(!TD.isFinished(call), true, call != null ? call.state : null);
    setCallIncoming(!TD.isFinished(call) && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR, isOngoingBarVisible());
    setCallFlashing(TD.getCallNeedsFlashing(call));
    updateCallLooping();
  }

  private void setCall (@Nullable Tdlib tdlib, @Nullable TdApi.Call call, final boolean force) {
    if (this.call != null) {
      this.callTdlib.cache().unsubscribeFromCallUpdates(this.call.id, this);
    }
    final Tdlib oldTdlib = this.callTdlib;
    final TdApi.Call oldCall = this.call;
    if (call != null) {
      this.call = call;
      this.callTdlib = tdlib;
      tdlib.cache().subscribeToCallUpdates(call.id, this);
    }
    if (call != null || oldCall == null || force) {
      this.call = call;
      setShowOngoingSection(SECTION_CALL, call != null, false, force);
    } else {
      UI.post(() -> {
        if (HeaderFilling.this.callTdlib != null && HeaderFilling.this.callTdlib.id() == oldTdlib.id() &&
          HeaderFilling.this.call != null && oldCall.id == HeaderFilling.this.call.id) {
          HeaderFilling.this.call = null;
          HeaderFilling.this.callTdlib = null;
          setShowOngoingSection(SECTION_CALL, false, false, false);
        }
      }, isOngoingBarVisible() ? 2000 : 0);
    }
  }

  private void updateCallLooping () {
    setCallIsLooping(call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
  }

  private boolean isCallLooping;

  private void setCallIsLooping (boolean isLooping) {
    if (this.isCallLooping != isLooping) {
      this.isCallLooping = isLooping;
      if (isLooping) {
        UI.post(this);
      } else {
        UI.removePendingRunnable(this);
      }
    }
  }

  @Override
  public void run () {
    if (call != null) {
      buildCall();
      invalidateOngoingBar();
      if (isCallLooping) {
        UI.post(this, getTimeTillNextUpdate());
      }
    }
  }

  private long getTimeTillNextUpdate () {
    return TdlibManager.instance().calls().getTimeTillNextCallDurationUpdate(callTdlib, call.id);
  }

  private boolean isCallActive;
  private static final int ANIMATOR_CALL_ACTIVE_ID = 4;
  private FactorAnimator callActiveAnimator;
  private float callActiveFactor;

  private void setCallActive (boolean isActive, boolean animated, TdApi.CallState state) {
    if (this.isCallActive != isActive) {
      this.isCallActive = isActive;
      final float toFactor = isActive ? 1f : 0f;
      if (xgram) {
        xgramAnimate(ANIMATOR_CALL_ACTIVE_ID, toFactor, animated);
      } else if (animated) {
        if (callActiveAnimator == null) {
          callActiveAnimator = new FactorAnimator(ANIMATOR_CALL_ACTIVE_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.callActiveFactor);
        }
        callActiveAnimator.animateTo(toFactor);
      } else {
        if (callActiveAnimator != null) {
          callActiveAnimator.forceFactor(toFactor);
        }
        setCallActiveFactor(callActiveFactor);
      }
    }
  }

  private void setCallActiveFactor (float factor) {
    if (this.callActiveFactor != factor) {
      this.callActiveFactor = factor;
      invalidateOngoingBar();
    }
  }

  private boolean isCallPendingIncoming;
  private static final int ANIMATOR_CALL_INCOMING_ID = 5;
  private FactorAnimator callIncomingAnimator;
  private float callIncomingFactor;

  private void setCallIncoming (boolean isIncoming, boolean animated) {
    if (this.isCallPendingIncoming != isIncoming) {
      this.isCallPendingIncoming = isIncoming;
      final float toFactor = isIncoming ? 1f : 0f;
      if (xgram) {
        xgramAnimate(ANIMATOR_CALL_INCOMING_ID, toFactor, animated);
      } else if (animated) {
        if (callIncomingAnimator == null) {
          callIncomingAnimator = new FactorAnimator(ANIMATOR_CALL_INCOMING_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.callIncomingFactor);
        }
        callIncomingAnimator.animateTo(toFactor);
      } else {
        if (callIncomingAnimator != null) {
          callIncomingAnimator.forceFactor(toFactor);
        }
        setCallIncomingFactor(toFactor);
      }
    }
  }

  private void setCallIncomingFactor (float factor) {
    if (this.callIncomingFactor != factor) {
      this.callIncomingFactor = factor;
      invalidateOngoingBar();
    }
  }

  private boolean isCallFlashing;
  private static final int ANIMATOR_CALL_FLASH_ID = 6;
  private FactorAnimator callFlashAnimator;
  private float callFlashFactor;

  private void setCallFlashing (boolean isCallFlashing) {
    if (this.isCallFlashing != isCallFlashing) {
      this.isCallFlashing = isCallFlashing;
      if (xgram) {
        xgramSetPulsing(isCallFlashing);
        return;
      }
      if (isCallFlashing) {
        if (callFlashAnimator == null) {
          callFlashAnimator = new FactorAnimator(ANIMATOR_CALL_FLASH_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CallController.CALL_FLASH_DURATION);
          callFlashAnimator.setStartDelay(CallController.CALL_FLASH_DELAY);
        }
        if (!callFlashAnimator.isAnimating()) {
          callFlashAnimator.forceFactor(0f);
          callFlashAnimator.animateTo(1f);
        }
      } else {
        flashCallBar.cancelIfScheduled();
        if (callFlashAnimator != null && callFlashAnimator.getFactor() == 0f) {
          callFlashAnimator.forceFactor(0f);
        }
      }
    }
  }

  private void setCallFlashFactor (float flashFactor) {
    if (this.callFlashFactor != flashFactor) {
      this.callFlashFactor = flashFactor;
      invalidateOngoingBar();
    }
  }

  private static final int ANIMATOR_CALL_MUTE_ID = 2;

  private boolean isCallMuted;
  private FactorAnimator callMuteAnimator;
  private float callMuteFactor;

  private void setCallMuted (boolean isCallMuted, boolean animated) {
    if (this.isCallMuted != isCallMuted) {
      this.isCallMuted = isCallMuted;
      final float toFactor = isCallMuted ? 1f : 0f;
      if (xgram) {
        xgramAnimate(ANIMATOR_CALL_MUTE_ID, toFactor, animated);
      } else if (animated) {
        if (callMuteAnimator == null) {
          callMuteAnimator = new FactorAnimator(ANIMATOR_CALL_MUTE_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.callMuteFactor);
        }
        callMuteAnimator.animateTo(toFactor);
      } else {
        if (callMuteAnimator != null) {
          callMuteAnimator.forceFactor(toFactor);
        }
        setCallMuteFactor(toFactor);
      }
    }
  }

  private void setCallMuteFactor (float factor) {
    if (this.callMuteFactor != factor) {
      this.callMuteFactor = factor;
      invalidateOngoingBar();
    }
  }

  private void toggleCallMute () {
    if (call != null) {
      CallSettings settings = callTdlib.cache().getCallSettings(call.id);
      if (settings == null) {
        settings = new CallSettings(callTdlib, call.id);
      }
      setCallMuted(!settings.isMicMuted(), true);
      settings.setMicMuted(isCallMuted);
    }
  }

  private void openCall () {
    TdlibManager.instance().calls().openCurrentCall();
  }

  private void drawOngoingCall (Canvas c, int playerTop, float rectWidth, int playerBottom) {
    final int backgroundColor = ColorUtils.fromToArgb(Theme.getColor(ColorId.headerBarCallMuted), ColorUtils.fromToArgb(Theme.getColor(ColorId.headerBarCallActive), Theme.getColor(ColorId.headerBarCallIncoming), callIncomingFactor), (1f - callMuteFactor) * callActiveFactor);
    if (restoreRect && restorePixels > 0) {
      c.drawRect(rectWidth, playerTop, width, playerBottom, Paints.fillingPaint(backgroundColor));
    }
    final int playerFillingColor = ColorUtils.alphaColor(dropShadowAlpha, backgroundColor);

    c.drawRect(0, playerTop, rectWidth, playerBottom, Paints.fillingPaint(playerFillingColor));

    Paint iconPaint = Paints.whitePorterDuffPaint();

    // Mute button
    iconPaint.setAlpha((int) (255f * dropShadowAlpha * callActiveFactor * (1f - callIncomingFactor)));
    int cx = width - Screen.dp(12f) - micIcon.getMinimumWidth();
    int cy = playerTop + Screen.dp(6f);
    Drawables.draw(c, micIcon, cx, cy, iconPaint);
    DrawAlgorithms.drawCross(c, cx + micIcon.getMinimumWidth() / 2, cy + micIcon.getMinimumHeight() / 2, callMuteFactor, ColorUtils.color(iconPaint.getAlpha(), 0xffffff), playerFillingColor);

    // Call button
    iconPaint.setAlpha((int) (255f * dropShadowAlpha));
    cx = Screen.dp(18f);
    cy = playerTop + Screen.dp(6f);

    float callRotationFactor = callIncomingFactor * callActiveFactor;
    if (callRotationFactor != 0f) {
      c.save();
      c.rotate(225f * callRotationFactor, cx + hangIcon.getMinimumWidth() / 2, cy + hangIcon.getMinimumHeight() / 2);
    }
    Drawables.draw(c, hangIcon, cx, cy, iconPaint);
    if (callActiveFactor != 1f) {
      DrawAlgorithms.drawCross(c, cx + hangIcon.getMinimumWidth() / 2, cy + hangIcon.getMinimumHeight() / 2 - Screen.dp(2f), 1f - callActiveFactor, ColorUtils.color((int) (255f * dropShadowAlpha), 0xffffff), backgroundColor);
    }
    if (callRotationFactor != 0f) {
      c.restore();
    }
    iconPaint.setAlpha(0xff);

    float textAlpha = ((float) 0xe0 / (float) 0xff) * (callFlashFactor <= .5f ? 1f - (callFlashFactor / .5f) : (callFlashFactor - .5f) / .5f);
    drawOngoingText(c, playerTop, rectWidth, playerBottom, textLeft, TextColorSets.WHITE, 1f, textAlpha);
  }

  // Show/hide ongoing bar

  private boolean isOngoingBarVisible () {
    return showFactor > 0f && shadowTop > fillingBottom && hideFactor < 1f;
  }

  private static final int ANIMATOR_SHOW_ID = 0;
  private FactorAnimator barShowAnimator;

  private void setShowOngoingSection (int section, boolean isShowing, boolean needRebuild, boolean force) {
    if (BitwiseUtils.hasFlag(ongoingSections, section) != isShowing) {
      this.ongoingSections = BitwiseUtils.setFlag(ongoingSections, section, isShowing);
      if (isShowing) {
        if (canBuildSection(section)) {
          lastBarType = section;
          buildSection(section);
        }
      }/* else if ((ongoingSections & SECTION_LOCATION) != 0) {
        buildLocation();
      }*/ else if ((ongoingSections & SECTION_CALL) != 0) {
        buildCall();
      } else if ((ongoingSections & SECTION_AUDIO) != 0) {
        buildAudio();
      }
      if (isShowing || ongoingSections != 0) {
        invalidateOngoingBar();
      }
      showHideAnimated(ongoingSections != 0, force);
    } else {
      if (needRebuild) {
        buildSection(section);
      }
      invalidateOngoingBar();
    }
  }

  private void buildSection (int section) {
    switch (section) {
      /*case SECTION_LOCATION:
        buildLocation();
        break;*/
      case SECTION_CALL:
        buildCall();
        break;
      case SECTION_AUDIO:
        buildAudio();
        break;
    }
  }

  private void showHideAnimated (boolean show, boolean force) {
    if (this.showOngoingBar != show) {
      this.showOngoingBar = show;

      if (force) {
        setShowFactor(show ? 1f : 0f);
        return;
      }

      ViewController<?> c = navigationController != null ? navigationController.getCurrentStackItem() : null;
      boolean allowAnimation = c != null && !c.usePopupMode();
      final float toFactor = show ? 1f : 0f;
      if (xgram) {
        xgramAnimate(ANIMATOR_SHOW_ID, toFactor, allowAnimation); // a spring, snapped with Reduce motion
      } else if (allowAnimation) {
        if (barShowAnimator == null) {
          barShowAnimator = new FactorAnimator(ANIMATOR_SHOW_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l, this.showFactor);
        }
        barShowAnimator.animateTo(toFactor);
      } else {
        if (barShowAnimator != null) {
          barShowAnimator.forceFactor(toFactor);
        }
        setShowFactor(toFactor);
      }
    } else if (showOngoingBar) {
      invalidate();
    }
  }

  private void setShowFactor (float playerFactor) {
    if (this.showFactor != playerFactor) {
      this.showFactor = playerFactor;
      layout();
      invalidate();

      UI.getContext(headerView.getContext()).getRoundVideoController().checkLayout();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_SHOW_ID: {
        setShowFactor(factor);
        break;
      }
      case ANIMATOR_PLAY_ID: {
        setPlayPauseFactor(factor);
        break;
      }
      case ANIMATOR_CALL_MUTE_ID: {
        setCallMuteFactor(factor);
        break;
      }
      case ANIMATOR_CALL_ACTIVE_ID: {
        setCallActiveFactor(factor);
        break;
      }
      case ANIMATOR_CALL_INCOMING_ID: {
        setCallIncomingFactor(factor);
        break;
      }
      case ANIMATOR_CALL_FLASH_ID: {
        setCallFlashFactor(factor);
        break;
      }
    }
  }

  private final RateLimiter flashCallBar = new RateLimiter(() -> {
    if (isCallFlashing) {
      callFlashAnimator.animateTo(1f);
    }
  }, 100L, null);

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CALL_FLASH_ID: {
        if (finalFactor == 1f) {
          callFlashAnimator.forceFactor(0f);
          if (isCallFlashing) {
            flashCallBar.run();
          }
        }
        break;
      }
    }
  }

  // Touch processing

  private float touchDownX, touchDownY;
  private float playbackOffsetY;

  private void performSoundFeedback () {
    if (headerView != null) {
      headerView.playSoundEffect(SoundEffectConstants.CLICK);
    }
  }

  public boolean onTouchEvent (MotionEvent e) {
    // XGram M3E: the mini player's close button takes the gestures that start on it
    if (xgram && xgramClose != null && xgramCloseVisible && hasVisibleOngoingBar() && !isCurrentSection(SECTION_CALL) && isCurrentSection(SECTION_AUDIO) &&
      xgramClose.onTouchEvent(e, xgramStopPlayback)) {
      return true;
    }
    return helper != null && helper.onTouchEvent(headerView, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    touchDownX = x;
    touchDownY = y;
    return (navigationController == null || !navigationController.isAnimating()) && (showOngoingBar || showFactor != 0f) && dropShadowAlpha != 0f && hideFactor != 1f && !(y < playerTop) && !(y > playerBottom) && !(y <= fillingBottom);
  }

  @Override
  public boolean needLongPress (float x, float y) {
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      return false;
    }
    if (xgram) {
      return BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO) && xgramInSpeedChip(x);
    }
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      int rIndex = getRightButtonIndex(width, x);
      return rIndex == 2;
    }
    return false;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (needLongPress(x, y) && playbackSpeedLayout == null) {
      showPlaybackSpeedPicker(x, y);
      return true;
    }
    return false;
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    if (playbackSpeedLayout != null) {
      playbackSpeedLayout.processTouchEvent(MotionEvent.ACTION_MOVE, x, y, playbackOffsetY, true);
    }
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    if (playbackSpeedLayout != null) {
      playbackSpeedLayout.processTouchEvent(MotionEvent.ACTION_UP, x, y, playbackOffsetY, true);
    }
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    if (playbackSpeedLayout != null) {
      playbackSpeedLayout.processTouchEvent(MotionEvent.ACTION_CANCEL, x, y, playbackOffsetY, true);
    }
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (xgram) {
      xgramOnClickAt(x);
      return;
    }
    final boolean isAudio = BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO);
    int rIndex = getRightButtonIndex(width, x);

    int buttonSize = Screen.dp(52f);
    if (!isAudio && x <= width && x >= width - buttonSize - Screen.dp(24f)) {
      int index = x >= width - buttonSize ? 0 : (int) (width - x - buttonSize) / Screen.dp(24f) + 1;
      onRightClick(index);
    } else if (isAudio && x <= width && rIndex <= 2) {
      onRightClick(rIndex);
    } else if (x >= 0 && x <= buttonSize) {
      onLeftClick();
    } else {
      onBarClick();
    }
  }

  @Override
  public void onClickTouchMove (View view, float x, float y) {
    if (playbackSpeedLayout == null && !helper.inLongPress()){
      final float dx = x - touchDownX;
      final float dy = y - touchDownY;
      if (Math.hypot(dx, dy) > ViewConfiguration.get(view.getContext()).getScaledTouchSlop() * 1.77f && dy > 0 && Math.abs(dy) > Math.abs(dx) && needLongPress(touchDownX, touchDownY)) {
        if (onLongPressRequestedAt(view, touchDownX, touchDownY)) {
          helper.onLongPress(view, x, y);
        }
      }
    }
  }

  private boolean onRightClick (int index) {
    /*if (U.getFlag(ongoingSections, SECTION_LOCATION)) {
      stopAllLiveLocations();
    } else*/
    switch (index) {
      case 0: { // play/pause or mute/unute
        performSoundFeedback();
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
          if (isCallActive && !isCallPendingIncoming) {
            toggleCallMute();
          }
        } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
          if (!playPause()) {
            openPlayer();
          }
        }
        return true;
      }
      case 1: {
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
          return false;
        }
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
          TdlibManager.instance().player().playNextMessageInQueue();
          return true;
        }
        break;
      }
      case 2: {
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
          return false;
        }
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
          onPlaybackSpeedClick();
          return true;
        }
        break;
      }
    }
    return false;
  }

  private void onLeftClick () {
   /* if (U.getFlag(ongoingSections, SECTION_LOCATION)) {
      // Do nothing
    } else*/
   performSoundFeedback();
   if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      if (isCallActive) {
        if (call != null && headerView != null) {
          if (isCallPendingIncoming) {
            TdlibManager.instance().calls().acceptCall(headerView.getContext(), callTdlib, call.id);
          } else {
            TdlibManager.instance().calls().hangUp(callTdlib, call.id);
          }
        }
      }
    } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      TdlibManager.instance().player().stopPlayback(true);
    }
  }

  private void onBarClick () {
    /*if (U.getFlag(ongoingSections, SECTION_LOCATION)) {
      openLiveLocationList();
    } else*/
    performSoundFeedback();
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      openCall();
    } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      openPlayer();
    }
  }

  // XGram M3E (round 5): the ongoing call is a 48dp full-corner chip and the audio player a 56dp mini player card,
  // both in a 64dp band under the app bar (its color). Springs drive every state; colors are roles read at draw time.

  private final boolean xgram;
  private static final float XGRAM_BAR = 64f, XGRAM_CHIP = 48f, XGRAM_CARD = 56f, XGRAM_MAX_WIDTH = 640f, XGRAM_BUTTON = 40f;
  private final com.fahadali.xgram.appearance.XgramExpressive.Spring[] xgramSprings = new com.fahadali.xgram.appearance.XgramExpressive.Spring[7];
  private float xgramPlayShape, xgramMuteShape;
  private @Nullable Drawable xgramPhoneIcon, xgramMusicIcon, xgramVideoIcon;
  private @Nullable com.fahadali.xgram.appearance.XgramM3Chat.DrawnIconButton xgramClose;
  private boolean xgramCloseVisible;
  private final Runnable xgramStopPlayback = () -> TdlibManager.instance().player().stopPlayback(true);
  private final android.graphics.RectF xgramRect = new android.graphics.RectF();

  private int xgramSpeed;
  private @Nullable String xgramSpeedText;
  private float xgramSpeedTextWidth;

  private @Nullable String xgramCallName, xgramCallState, xgramCallNameDrawn;
  private float xgramCallNameWidth, xgramCallStateWidth;
  private int xgramCallLayoutWidth = -1;

  private @Nullable String xgramAudioTitle, xgramAudioSubtitle, xgramAudioTitleDrawn, xgramAudioSubtitleDrawn;
  private float xgramAudioTitleWidth, xgramAudioSubtitleWidth;
  private int xgramAudioLayoutWidth = -1;
  private boolean xgramAudioVoice;

  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPulse;
  private float xgramPulseValue;
  private final Runnable xgramPulseNext = this::xgramPulseNextStep;

  private void xgramAnimate (final int id, float to, boolean animated) {
    com.fahadali.xgram.appearance.XgramExpressive.Spring spring = xgramSprings[id];
    if (spring == null) {
      final int token;
      switch (id) {
        case ANIMATOR_SHOW_ID:
          token = com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT;
          break;
        case ANIMATOR_PLAY_ID:
        case ANIMATOR_CALL_MUTE_ID:
          token = com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST;
          break;
        default:
          token = com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT;
          break;
      }
      spring = new com.fahadali.xgram.appearance.XgramExpressive.Spring(token, xgramFactor(id), (s, value) -> xgramOnSpring(id, value));
      xgramSprings[id] = spring;
    } else if (!spring.isRunning() && spring.getValue() != xgramFactor(id) && id != ANIMATOR_PLAY_ID && id != ANIMATOR_CALL_MUTE_ID) {
      spring.setValue(xgramFactor(id)); // the factor was forced (a forced show or hide) since the spring last ran
    }
    if (animated) {
      spring.animateTo(to);
    } else {
      spring.setValue(to);
      xgramOnSpring(id, to);
    }
  }

  private float xgramFactor (int id) {
    switch (id) {
      case ANIMATOR_SHOW_ID: return showFactor;
      case ANIMATOR_PLAY_ID: return playPauseFactor;
      case ANIMATOR_CALL_MUTE_ID: return callMuteFactor;
      case ANIMATOR_CALL_ACTIVE_ID: return callActiveFactor;
      case ANIMATOR_CALL_INCOMING_ID: return callIncomingFactor;
    }
    return 0f;
  }

  private void xgramOnSpring (int id, float value) {
    final float clamped = MathUtils.clamp(value);
    switch (id) {
      case ANIMATOR_PLAY_ID:
        xgramPlayShape = value; // the button's shape may overshoot, the icon may not
        setPlayPauseFactor(clamped);
        invalidateOngoingBar();
        break;
      case ANIMATOR_CALL_MUTE_ID:
        xgramMuteShape = value;
        setCallMuteFactor(clamped);
        invalidateOngoingBar();
        break;
      default:
        onFactorChanged(id, clamped, clamped, null);
        break;
    }
  }

  /** The tone pulse of a ringing call: the chip's state layer breathes in and out (EFFECTS_DEFAULT, a pause at each end). */
  private void xgramSetPulsing (boolean pulsing) {
    UI.removePendingRunnable(xgramPulseNext);
    if (pulsing && !com.fahadali.xgram.appearance.XgramMotion.reduce()) {
      if (xgramPulse == null) {
        xgramPulse = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> {
          xgramPulseValue = MathUtils.clamp(value);
          invalidateOngoingBar();
        }).setEndListener(() -> {
          if (isCallFlashing) {
            UI.post(xgramPulseNext, 450L);
          }
        });
      }
      xgramPulse.animateTo(1f);
    } else if (xgramPulse != null) {
      xgramPulse.animateTo(0f);
    }
  }

  private void xgramPulseNextStep () {
    if (isCallFlashing && xgramPulse != null) {
      xgramPulse.animateTo(xgramPulse.getTarget() >= 1f ? 0f : 1f);
    }
  }

  private void xgramSetCallText (@Nullable String name, @Nullable String state) {
    if (!StringUtils.equalsOrBothEmpty(xgramCallName, name) || !StringUtils.equalsOrBothEmpty(xgramCallState, state)) {
      xgramCallName = name;
      xgramCallState = state;
      xgramCallLayoutWidth = -1;
    }
  }

  private void xgramSetAudioText (@Nullable String title, @Nullable String subtitle) {
    xgramAudioTitle = title;
    xgramAudioSubtitle = subtitle;
    xgramAudioLayoutWidth = -1;
  }

  /** Mirrors {@code x} in the current box (xgramRect) for RTL languages. */
  private float xgramMx (float x) {
    return Lang.rtl() ? xgramRect.left + xgramRect.right - x : x;
  }

  /** The chip (call) or card (audio) of the bar between {@code playerTop} and {@code playerBottom}, into xgramRect. */
  private void xgramBox (boolean audio, int playerTop, int playerBottom, float rectWidth) {
    final float inset = Screen.dp(audio ? 8f : 16f);
    float left = inset, right = rectWidth - inset;
    final float maxWidth = Screen.dp(XGRAM_MAX_WIDTH);
    if (right - left > maxWidth) {
      final float cx = (left + right) / 2f;
      left = cx - maxWidth / 2f;
      right = cx + maxWidth / 2f;
    }
    final float height = Screen.dp(audio ? XGRAM_CARD : XGRAM_CHIP);
    final float cy = (playerTop + playerBottom) / 2f;
    xgramRect.set(left, cy - height / 2f, right, cy + height / 2f);
  }

  // Audio card geometry (LTR, from the card's end): close 4dp in, play 4dp before it, the speed chip 8dp before play

  private float xgramCloseCenter () {
    return xgramRect.right - Screen.dp(4f + XGRAM_BUTTON / 2f);
  }

  private float xgramPlayCenter () {
    return xgramRect.right - Screen.dp(4f + XGRAM_BUTTON + 4f + XGRAM_BUTTON / 2f);
  }

  private float xgramSpeedChipRight () {
    return xgramPlayCenter() - Screen.dp(XGRAM_BUTTON / 2f + 8f);
  }

  private float xgramSpeedChipWidth () {
    return xgramSpeedTextWidth + Screen.dp(12f) * 2;
  }

  private boolean xgramInSpeedChip (float x) {
    xgramBox(true, playerTop, playerBottom, width);
    final float right = xgramSpeedChipRight() + Screen.dp(4f), left = xgramSpeedChipRight() - xgramSpeedChipWidth() - Screen.dp(4f);
    final float a = xgramMx(left), b = xgramMx(right);
    return x >= Math.min(a, b) && x <= Math.max(a, b);
  }

  private void xgramOnClickAt (float x) {
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      performSoundFeedback();
      xgramBox(false, playerTop, playerBottom, width);
      final float muteCx = xgramMx(xgramRect.right - Screen.dp(4f + XGRAM_BUTTON / 2f));
      if (Math.abs(x - muteCx) <= Screen.dp(24f) && isCallActive && !isCallPendingIncoming) {
        toggleCallMute();
      } else {
        openCall();
      }
    } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      xgramBox(true, playerTop, playerBottom, width);
      if (Math.abs(x - xgramMx(xgramPlayCenter())) <= Screen.dp(24f)) {
        performSoundFeedback();
        if (!playPause()) {
          openPlayer();
        }
      } else if (xgramInSpeedChip(x)) {
        onPlaybackSpeedClick();
      } else {
        performSoundFeedback();
        openPlayer();
      }
    }
  }

  private void xgramDrawBand (Canvas c, int playerTop, int playerBottom) {
    c.drawRect(0, playerTop, width, playerBottom, Paints.fillingPaint(ColorUtils.alphaColor(dropShadowAlpha, color)));
  }

  /** XGram: the chip's call is a video call, or used video (CallManager notifies the call listeners when it starts). */
  private boolean xgramCallIsVideo () {
    final TdApi.Call call = this.call;
    if (call == null || !com.fahadali.xgram.call.XgramCallVideo.isAvailable()) {
      return false;
    }
    if (call.isVideo) {
      return true;
    }
    final com.fahadali.xgram.call.XgramCallVideo video = TdlibManager.instance().calls().video(call.id);
    return video != null && video.wasVideoUsed();
  }

  private void xgramDrawCall (Canvas c, int playerTop, float rectWidth, int playerBottom) {
    xgramCloseVisible = false;
    xgramDrawBand(c, playerTop, playerBottom);
    xgramBox(false, playerTop, playerBottom, rectWidth);
    final float left = xgramRect.left, top = xgramRect.top, right = xgramRect.right, bottom = xgramRect.bottom;
    final float cy = (top + bottom) / 2f;
    final float alpha = dropShadowAlpha;

    // primaryContainer while active, tertiaryContainer while an incoming call rings, surfaceContainerHighest muted or ended
    final float live = (1f - callMuteFactor) * callActiveFactor;
    final int container = ColorUtils.fromToArgb(
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST),
      ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.TERTIARY_CONTAINER), callIncomingFactor),
      live);
    final int content = ColorUtils.fromToArgb(
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE),
      ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_TERTIARY_CONTAINER), callIncomingFactor),
      live);
    int fill = container;
    if (xgramPulseValue > 0f) {
      fill = androidx.core.graphics.ColorUtils.compositeColors(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(content, .12f * xgramPulseValue), container | 0xff000000);
    }
    com.fahadali.xgram.appearance.XgramR5Calls.drawRoundRect(c, left, top, right, bottom, (bottom - top) / 2f, com.fahadali.xgram.appearance.XgramExpressive.withAlpha(fill, alpha));
    final int contentColor = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(content, alpha);

    // 24dp phone icon 16dp in (the camera for a video call, or once video was used in it)
    com.fahadali.xgram.appearance.XgramR5Calls.drawIcon(c, xgramCallIsVideo() && xgramVideoIcon != null ? xgramVideoIcon : xgramPhoneIcon, xgramMx(left + Screen.dp(16f + 12f)), cy, contentColor);

    // Mute toggle (40dp, tonal; muted: primary, its corners morphing from full to 12dp)
    final float toggleAlpha = alpha * callActiveFactor * (1f - callIncomingFactor);
    final boolean hasToggle = toggleAlpha > 0f;
    final float toggleCx = xgramMx(right - Screen.dp(4f + XGRAM_BUTTON / 2f));
    if (hasToggle) {
      final int off = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(content, .12f * toggleAlpha);
      final int on = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY), toggleAlpha);
      com.fahadali.xgram.appearance.XgramR5Calls.drawToggleButton(c, toggleCx, cy, Screen.dp(XGRAM_BUTTON), xgramMuteShape, 0f, off, on, content);
      final int iconColor = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(ColorUtils.fromToArgb(content, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY), callMuteFactor), toggleAlpha);
      // The icon crossfades content to onPrimary through alpha (two cached filters, none per frame of the morph)
      if (callMuteFactor < 1f) {
        com.fahadali.xgram.appearance.XgramR5Calls.drawIcon(c, micIcon, toggleCx, cy, com.fahadali.xgram.appearance.XgramExpressive.withAlpha(content, toggleAlpha * (1f - callMuteFactor)));
      }
      if (callMuteFactor > 0f) {
        com.fahadali.xgram.appearance.XgramR5Calls.drawIcon(c, micIcon, toggleCx, cy, com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY), toggleAlpha * callMuteFactor));
      }
      if (callMuteFactor > 0f) {
        final int behind = ColorUtils.fromToArgb(androidx.core.graphics.ColorUtils.compositeColors(off, fill | 0xff000000), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY), callMuteFactor);
        DrawAlgorithms.drawCross(c, toggleCx, cy, callMuteFactor, iconColor, behind);
      }
    }

    // Name in labelLarge, then the duration or status in titleSmall with tabular figures
    final float textStart = left + Screen.dp(16f + 24f + 12f);
    final float textEnd = hasToggle ? right - Screen.dp(4f + XGRAM_BUTTON + 8f) : right - Screen.dp(16f);
    final int available = (int) Math.max(0f, textEnd - textStart);
    if (xgramCallLayoutWidth != available) {
      xgramCallLayoutWidth = available;
      final android.text.TextPaint statePaint = com.fahadali.xgram.appearance.XgramR5Calls.measureTabularTitlePaint();
      xgramCallStateWidth = !StringUtils.isEmpty(xgramCallState) ? statePaint.measureText(xgramCallState) : 0f;
      final float nameSpace = available - (xgramCallStateWidth > 0f ? xgramCallStateWidth + Screen.dp(8f) : 0f);
      final android.text.TextPaint namePaint = com.fahadali.xgram.appearance.XgramR5Calls.measurePaint(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L);
      xgramCallNameDrawn = com.fahadali.xgram.appearance.XgramR5Calls.ellipsize(xgramCallName, namePaint, nameSpace);
      xgramCallNameWidth = xgramCallNameDrawn != null ? namePaint.measureText(xgramCallNameDrawn) : 0f;
    }
    float x = textStart;
    if (xgramCallNameDrawn != null) {
      final android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramR5Calls.labelLargePaint(contentColor);
      c.drawText(xgramCallNameDrawn, Lang.rtl() ? xgramMx(x) - xgramCallNameWidth : x, com.fahadali.xgram.appearance.XgramR5Calls.baseline(paint, cy), paint);
      x += xgramCallNameWidth + Screen.dp(8f);
    }
    if (xgramCallState != null && xgramCallStateWidth > 0f && x + xgramCallStateWidth <= textEnd + 1f) {
      final android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramR5Calls.titleSmallTabularPaint(contentColor);
      c.drawText(xgramCallState, Lang.rtl() ? xgramMx(x) - xgramCallStateWidth : x, com.fahadali.xgram.appearance.XgramR5Calls.baseline(paint, cy), paint);
    }
  }

  private void xgramDrawAudio (Canvas c, int playerTop, float rectWidth, int playerBottom) {
    xgramDrawBand(c, playerTop, playerBottom);
    xgramBox(true, playerTop, playerBottom, rectWidth);
    final float left = xgramRect.left, top = xgramRect.top, right = xgramRect.right, bottom = xgramRect.bottom;
    final float alpha = dropShadowAlpha;
    final boolean rtl = Lang.rtl();

    // The card: surfaceContainerHigh, the chip step with Pure black (it floats over the black page)
    final int cardRole = com.fahadali.xgram.appearance.XgramAmoled.floatingRole(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH);
    com.fahadali.xgram.appearance.XgramR5Calls.drawRoundRect(c, left, top, right, bottom, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L),
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(cardRole), alpha));

    // 40dp CORNER_M tile: a mic (voice and video notes) or a music note on secondaryContainer; 2dp above centre for the wave
    final float tile = Screen.dp(XGRAM_BUTTON);
    final float contentCy = top + Screen.dp(XGRAM_CARD / 2f - 2f);
    final float tileLeft = left + Screen.dp(8f);
    final float tileL = rtl ? xgramMx(tileLeft + tile) : tileLeft;
    com.fahadali.xgram.appearance.XgramR5Calls.drawRoundRect(c, tileL, contentCy - tile / 2f, tileL + tile, contentCy + tile / 2f, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M),
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), alpha));
    com.fahadali.xgram.appearance.XgramR5Calls.drawIcon(c, xgramAudioVoice ? micIcon : xgramMusicIcon, tileL + tile / 2f, contentCy,
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER), alpha));

    // Play / pause: a 40dp primaryContainer circle that morphs to a 12dp rounded square while playing
    final float playCx = xgramMx(xgramPlayCenter());
    final float half = tile / 2f;
    final float corner = half + (Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M) - half) * Math.max(0f, Math.min(1.2f, xgramPlayShape));
    com.fahadali.xgram.appearance.XgramR5Calls.drawRoundRect(c, playCx - half, contentCy - half, playCx + half, contentCy + half, MathUtils.clamp(corner, 0f, half),
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER), alpha));
    final TdApi.File file = playingMessage != null ? TD.getFile(playingMessage) : null;
    if (playPausePath == null) {
      playPausePath = new Path();
    }
    DrawAlgorithms.drawPlayPause(c, (int) playCx, (int) contentCy, Screen.dp(9f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = playPauseFactor, file != null ? TD.getFileProgress(file) : 1f,
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), alpha));

    // Close: a standard icon button with a 48dp target
    if (xgramClose != null) {
      final float closeCx = xgramMx(xgramCloseCenter());
      xgramClose.setBounds((int) (closeCx - Screen.dp(24f)), (int) (contentCy - Screen.dp(24f)), (int) (closeCx + Screen.dp(24f)), (int) (contentCy + Screen.dp(24f)));
      xgramClose.draw(c, alpha);
      xgramCloseVisible = alpha > 0f && hideFactor < 1f;
    }

    // Speed: an outlined labelMedium chip, secondaryContainer when the speed is not 1x
    final float chipRight = xgramSpeedChipRight();
    final float chipLeft = chipRight - xgramSpeedChipWidth();
    final float chipHalf = Screen.dp(16f);
    final float cl = rtl ? xgramMx(chipRight) : chipLeft, cr = rtl ? xgramMx(chipLeft) : chipRight;
    final boolean fast = xgramSpeed != TGPlayerController.PLAY_SPEED_NORMAL;
    final float chipCorner = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_S);
    if (fast) {
      com.fahadali.xgram.appearance.XgramR5Calls.drawRoundRect(c, cl, contentCy - chipHalf, cr, contentCy + chipHalf, chipCorner,
        com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), alpha));
    } else {
      com.fahadali.xgram.appearance.XgramR5Calls.drawOutline(c, cl, contentCy - chipHalf, cr, contentCy + chipHalf, chipCorner,
        com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.OUTLINE_VARIANT), alpha));
    }
    if (xgramSpeedText != null) {
      final android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramR5Calls.labelMediumPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(
        com.fahadali.xgram.appearance.XgramExpressive.color(fast ? com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT), alpha));
      c.drawText(xgramSpeedText, (cl + cr) / 2f - xgramSpeedTextWidth / 2f, com.fahadali.xgram.appearance.XgramR5Calls.baseline(paint, contentCy), paint);
    }

    // Title (titleSmall) over the supporting line (bodySmall, onSurfaceVariant)
    final float textStart = tileLeft + tile + Screen.dp(12f);
    final float textEnd = chipLeft - Screen.dp(8f);
    final int available = (int) Math.max(0f, textEnd - textStart);
    if (xgramAudioLayoutWidth != available) {
      xgramAudioLayoutWidth = available;
      final android.text.TextPaint titlePaint = com.fahadali.xgram.appearance.XgramR5Calls.measurePaint(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_S);
      final android.text.TextPaint subtitlePaint = com.fahadali.xgram.appearance.XgramR5Calls.measurePaint(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_S);
      xgramAudioTitleDrawn = com.fahadali.xgram.appearance.XgramR5Calls.ellipsize(xgramAudioTitle, titlePaint, available);
      xgramAudioTitleWidth = xgramAudioTitleDrawn != null ? titlePaint.measureText(xgramAudioTitleDrawn) : 0f;
      xgramAudioSubtitleDrawn = com.fahadali.xgram.appearance.XgramR5Calls.ellipsize(xgramAudioSubtitle, subtitlePaint, available);
      xgramAudioSubtitleWidth = xgramAudioSubtitleDrawn != null ? subtitlePaint.measureText(xgramAudioSubtitleDrawn) : 0f;
    }
    final boolean twoLines = xgramAudioSubtitleDrawn != null;
    if (xgramAudioTitleDrawn != null) {
      final android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramR5Calls.titleSmallPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), alpha));
      final float lineCy = twoLines ? contentCy - Screen.dp(8f) : contentCy;
      c.drawText(xgramAudioTitleDrawn, rtl ? xgramMx(textStart) - xgramAudioTitleWidth : textStart, com.fahadali.xgram.appearance.XgramR5Calls.baseline(paint, lineCy), paint);
    }
    if (twoLines) {
      final android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramR5Calls.bodySmallPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT), alpha));
      final float lineCy = xgramAudioTitleDrawn != null ? contentCy + Screen.dp(10f) : contentCy;
      c.drawText(xgramAudioSubtitleDrawn, rtl ? xgramMx(textStart) - xgramAudioSubtitleWidth : textStart, com.fahadali.xgram.appearance.XgramR5Calls.baseline(paint, lineCy), paint);
    }

    // Progress in the card's bottom inset: wavy while playing, flat when paused (mirrored for RTL)
    final float amplitude = Screen.dpf(1.5f) * playPauseFactor;
    final float waveLeft = left + Screen.dp(16f), waveRight = right - Screen.dp(16f), waveCy = bottom - Screen.dp(4f);
    int saveCount = -1;
    if (rtl) {
      saveCount = c.save();
      c.scale(-1f, 1f, (waveLeft + waveRight) / 2f, waveCy);
    }
    com.fahadali.xgram.appearance.XgramWavyLinear.draw(c, waveLeft, waveRight, waveCy, seekFactor,
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY), alpha),
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), alpha),
      Screen.dpf(3f), amplitude, Screen.dpf(24f), Screen.dpf(3f), true);
    if (saveCount != -1) {
      c.restoreToCount(saveCount);
    }
    if (isAudioPlaying && alpha > 0f && com.fahadali.xgram.appearance.XgramWavyLinear.isWaveMoving(seekFactor, amplitude)) {
      headerView.postInvalidateOnAnimation(0, playerTop, width, playerBottom);
    }
  }

  // Drawable utils

  @Override
  public void setAlpha (int alpha) { }

  @Override
  public void setColorFilter (ColorFilter colorFilter) { }

  @Override
  @SuppressWarnings("deprecation")
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }



  private static final int SIZE = 24;
  private static final int OFFSET = 12;
  private static final int GAP = 8;

  private static int getRightButtonIndex (int width, float x) {
    return Math.max(0, ((int) (width - x - Screen.dp(OFFSET - GAP / 2f))) / Screen.dp(SIZE + GAP));
  }

  private static int getRightButtonLeft (int width, int index) {
    return width - Screen.dp(OFFSET + SIZE * (index + 1) + GAP * index);
  }

  private static int getRightButtonRight (int width, int index) {
    return getRightButtonLeft(width, index) + Screen.dp(SIZE);
  }

  private static int getRightButtonCenter (int width, int index) {
    return getRightButtonLeft(width, index) + Screen.dp(SIZE / 2f);
  }


  // Playback Speed

  private void onPlaybackSpeedClick () {
    final int speed = TdlibManager.instance().player().getSpeed();
    final boolean isVideo = TdlibManager.instance().player().isPlayingRoundVideo();

    if (speed < 100) {
      TdlibManager.instance().player().setPlaybackSpeed(100);
      showPlaybackTooltip(isVideo ? R.string.PlaybackSpeedHintVideo100 : R.string.PlaybackSpeedHintAudio100);
    } else if (speed < 150) {
      TdlibManager.instance().player().setPlaybackSpeed(150);
      showPlaybackTooltip(isVideo ? R.string.PlaybackSpeedHintVideo150 : R.string.PlaybackSpeedHintAudio150);
    } else if (speed < 200) {
      TdlibManager.instance().player().setPlaybackSpeed(200);
      showPlaybackTooltip(isVideo ? R.string.PlaybackSpeedHintVideo200 : R.string.PlaybackSpeedHintAudio200);
    } else {
      TdlibManager.instance().player().setPlaybackSpeed(100);
      showPlaybackTooltip(isVideo ? R.string.PlaybackSpeedHintVideo100 : R.string.PlaybackSpeedHintAudio100);
    }
    UI.hapticVibrate(headerView, false);
  }

  private void showPlaybackTooltip (int res) {
    String text = Lang.getString(res);
    if (Settings.instance().needTutorial(Settings.TUTORIAL_PLAYBACK_SPEED_HOLD)) {
      showPlaybackTooltip(Lang.getString(R.string.PlaybackSpeedHintTemplate, text, Lang.getString(R.string.PlaybackSpeedHintHold)));
    } else if (Settings.instance().needTutorial(Settings.TUTORIAL_PLAYBACK_SPEED_SWIPE)) {
      showPlaybackTooltip(Lang.getString(R.string.PlaybackSpeedHintTemplate, text, Lang.getString(R.string.PlaybackSpeedHintSwipe)));
    } else {
      showPlaybackTooltip(text);
    }
  }

  private void showPlaybackTooltip (CharSequence text) {
    ((BaseActivity) headerView.getContext()).tooltipManager()
      .builder(headerView)
      .locate((targetView, outRect) -> {
        final int width = targetView.getMeasuredWidth();
        if (xgram) { // XGram M3E: under the speed chip
          xgramBox(true, playerTop, playerBottom, width);
          final float chipRight = xgramSpeedChipRight();
          final float chipLeft = chipRight - xgramSpeedChipWidth();
          outRect.set((int) xgramMx(Lang.rtl() ? chipRight : chipLeft), 0, (int) xgramMx(Lang.rtl() ? chipLeft : chipRight), playerBottom - Screen.dp(12f));
          return;
        }
        outRect.set(getRightButtonLeft(width, 2), 0, getRightButtonRight(width, 2), playerBottom);
      })
      .maxWidth(300)
      .show(null, text).hideDelayed(3000, TimeUnit.MILLISECONDS);
  }

  private PlaybackSpeedLayout playbackSpeedLayout;
  private PopupLayout playbackSpeedPopup;

  private void showPlaybackSpeedPicker (float touchX, float touchY) {
    playbackOffsetY = Screen.dp(24) - (playerTop + playerBottom) / 2f + touchY;

    Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_PLAYBACK_SPEED_HOLD);

    playbackSpeedPopup = new PopupLayout(headerView.getContext());
    playbackSpeedLayout = new PlaybackSpeedLayout(headerView.getContext());
    playbackSpeedLayout.init(navigationController != null ? navigationController.getThemeListeners() : null, (speed, needClose) -> {
      TdlibManager.instance().player().setPlaybackSpeed(speed);
      if (needClose) {
        playbackSpeedPopup.hideWindow(true);
      }
    }, TdlibManager.instance().player().getSpeed());
    // XGram: the menu's edge and top in the header's coordinates; they become a translation from where the menu is laid
    // out (its gravity's edge of the window-wide popup) once it is, below: the header is not always at the window's
    // edge (two panes: the chat list's app bar ends at the divider; the navigation rail; side insets)
    final boolean alignRight; // the menu's right edge goes to edgeX, else its left edge
    final float edgeX, topY = (playerTop + playerBottom) / 2f - Screen.dp(24 + 8);
    if (xgram) { // XGram M3E: over the speed chip
      xgramBox(true, playerTop, playerBottom, width);
      // LTR: the menu's right edge 32dp past the chip centre; RTL (the chip mirrored): its left edge 32dp before the
      // mirrored centre
      final float center = xgramSpeedChipRight() - xgramSpeedChipWidth() / 2f;
      alignRight = !Lang.rtl();
      edgeX = Lang.rtl() ? xgramMx(center) - Screen.dp(24 + 8) : center + Screen.dp(24 + 8);
    } else {
      // The buttons stay on the right in RTL too (the menu's LEFT gravity put it past the window's left edge)
      alignRight = true;
      edgeX = getRightButtonCenter(width, 2) + Screen.dp(24 + 8);
    }
    playbackSpeedLayout.setTranslationY(topY);
    playbackSpeedLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (!(v.getParent() instanceof View) || headerView == null || headerView.getWindowToken() == null) {
        return;
      }
      final android.graphics.Rect header = com.fahadali.xgram.appearance.XgramPopupBounds.boundsOnScreen(headerView, new android.graphics.Rect());
      final int[] parent = new int[2];
      ((View) v.getParent()).getLocationOnScreen(parent);
      v.setTranslationX(header.left + edgeX - (parent[0] + (alignRight ? right : left)));
      v.setTranslationY(header.top + topY - (parent[1] + top));
      // The reveal (Expressive design off) and the scale grow from the aligned side, at the speed button: showMoreView
      // put the pivot on the other side for the right-aligned menu in RTL
      final int menuWidth = right - left;
      if (menuWidth > 0 && (v.getPivotX() >= menuWidth / 2f) != alignRight) {
        v.setPivotX(alignRight ? menuWidth - Screen.dp(17f) : Screen.dp(17f));
      }
    });
    playbackSpeedLayout.processTouchEvent(MotionEvent.ACTION_DOWN, touchX, touchY, playbackOffsetY, true);

    playbackSpeedPopup.init(true);
    playbackSpeedPopup.setNeedRootInsets();
    playbackSpeedPopup.setOverlayStatusBar(true);
    playbackSpeedPopup.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismissPrepare (PopupLayout popup) {
        if (playbackSpeedLayout == popup.getContentChild()) {
          playbackSpeedLayout = null;
          if (navigationController != null) {
            navigationController.unlockSwipeNavigation();
          }
        }
      }

      @Override
      public void onPopupDismiss (PopupLayout popup) {

      }
    });

    playbackSpeedPopup.showMoreView(playbackSpeedLayout);
    if (navigationController != null) {
      navigationController.lockSwipeNavigation();
    }
  }
}
