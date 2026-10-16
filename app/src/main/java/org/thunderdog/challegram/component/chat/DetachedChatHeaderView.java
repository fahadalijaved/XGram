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
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.SparseDrawableView;

import me.vkryl.android.ScrimUtil;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class DetachedChatHeaderView extends SparseDrawableView implements Destroyable {
  private final DoubleImageReceiver avatar = new DoubleImageReceiver(this, 0);

  private TdlibAccentColor accentColor;
  private Drawable avatarPlaceholderDrawable;

  private final Drawable topShadow;
  private final Drawable bottomShadow;

  private Text title;
  private Text subtitle;

  public static int getViewHeight () {
    if (xgram()) {
      return Screen.dp(XGRAM_TOP + XGRAM_AVATAR + XGRAM_AVATAR_GAP + XGRAM_TITLE_LINE + XGRAM_TEXT_GAP + XGRAM_SUBTITLE_LINE + XGRAM_BOTTOM);
    }
    return (int) (HeaderView.getBigSize(false) * 0.7f);
  }

  // XGram M3E: the join sheet's hero, not a photo banner under scrims: a centred 88dp round photo (the group or
  // channel glyph in onPrimaryContainer on a primaryContainer circle without one), the title in headlineSmall and
  // onSurface, the member count in bodyMedium and onSurfaceVariant, both centred under it.

  private static final float XGRAM_TOP = 24f, XGRAM_AVATAR = 88f, XGRAM_AVATAR_GAP = 16f, XGRAM_TITLE_LINE = 32f,
    XGRAM_TEXT_GAP = 4f, XGRAM_SUBTITLE_LINE = 20f, XGRAM_BOTTOM = 8f, XGRAM_SIDE = 24f;

  private static boolean xgram () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  }

  private void xgramLayout (int width) {
    final int size = Screen.dp(XGRAM_AVATAR);
    final int left = (width - size) / 2, top = Screen.dp(XGRAM_TOP);
    avatar.setBounds(left, top, left + size, top + size);
    avatar.setRadius(size / 2f);
    final int maxWidth = Math.max(0, width - Screen.dp(XGRAM_SIDE) * 2);
    if (title != null) {
      title.changeMaxWidth(maxWidth);
    }
    if (subtitle != null) {
      subtitle.changeMaxWidth(maxWidth);
    }
  }

  private void xgramDraw (Canvas canvas) {
    final int width = getMeasuredWidth();
    final float cx = width / 2f;
    final float cy = Screen.dp(XGRAM_TOP + XGRAM_AVATAR / 2f);
    final float radius = Screen.dp(XGRAM_AVATAR) / 2f;
    if (avatarPlaceholderDrawable != null) {
      canvas.drawCircle(cx, cy, radius, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER)));
      Drawables.draw(canvas, avatarPlaceholderDrawable, cx - avatarPlaceholderDrawable.getMinimumWidth() / 2f, cy - avatarPlaceholderDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER)));
    } else {
      if (avatar.needPlaceholder()) {
        canvas.drawCircle(cx, cy, radius, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST)));
      }
      avatar.draw(canvas);
    }
    int y = Screen.dp(XGRAM_TOP + XGRAM_AVATAR + XGRAM_AVATAR_GAP);
    if (title != null) {
      title.draw(canvas, Math.round(cx - title.getWidth() / 2f), y + (Screen.dp(XGRAM_TITLE_LINE) - title.getHeight()) / 2);
    }
    y += Screen.dp(XGRAM_TITLE_LINE + XGRAM_TEXT_GAP);
    if (subtitle != null) {
      subtitle.draw(canvas, Math.round(cx - subtitle.getWidth() / 2f), y + (Screen.dp(XGRAM_SUBTITLE_LINE) - subtitle.getHeight()) / 2);
    }
  }

  public DetachedChatHeaderView (@NonNull Context context) {
    super(context);
    setWillNotDraw(false);
    topShadow = ScrimUtil.makeCubicGradientScrimDrawable(0x77000000, 2, Gravity.TOP, false);
    bottomShadow = ScrimUtil.makeCubicGradientScrimDrawable(0x99000000, 2, Gravity.BOTTOM, false);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (xgram()) {
      xgramLayout(getMeasuredWidth());
      return;
    }
    avatar.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    title.changeMaxWidth(getMeasuredWidth() - Screen.dp(13f));
    subtitle.changeMaxWidth(getMeasuredWidth() - Screen.dp(13f));
    topShadow.setBounds(0, 0, getMeasuredWidth(), (int) ((float) HeaderView.getSize(false)) + Size.getHeaderPlayerSize());
    bottomShadow.setBounds(0, 0, getMeasuredWidth(), getBottomShadowSize());
  }

  private int getBottomShadowSize () {
    return (int) (((Screen.dp(28f) + Screen.dp(5f) + ((title.getHeight() + subtitle.getHeight()) * 1.3f) + Screen.dp(8f)) + Screen.dp(14f)) * (1f / .9f));
  }

  public void bindWith (Tdlib tdlib, TdApi.ChatInviteLinkInfo linkInfo) {
    boolean isChannel = TD.isChannel(linkInfo.type);

    if (xgram()) {
      // XGram M3E: headlineSmall in onSurface and bodyMedium in onSurfaceVariant, centred (see xgramDraw)
      final int maxWidth = Math.max(0, getMeasuredWidth() - Screen.dp(XGRAM_SIDE) * 2);
      title = new Text.Builder(linkInfo.title, maxWidth,
        com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S, false, com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S)),
        com.fahadali.xgram.appearance.XgramM3Profile.TEXT_ON_SURFACE)
        .singleLine()
        .clipTextArea()
        .build();
      subtitle = new Text.Builder(Lang.pluralMembers(linkInfo.memberCount, 0, isChannel).toString(), maxWidth,
        com.fahadali.xgram.appearance.XgramM3Profile.listSupportingProvider(),
        com.fahadali.xgram.appearance.XgramM3Profile.TEXT_ON_SURFACE_VARIANT).singleLine().build();
    } else {
      title = new Text.Builder(linkInfo.title, getMeasuredWidth(), Paints.robotoStyleProvider(18), TextColorSets.WHITE)
        .singleLine()
        .clipTextArea()
        .allBold()
        .build();
      subtitle = new Text.Builder(Lang.pluralMembers(linkInfo.memberCount, 0, isChannel).toString(), getMeasuredWidth(), Paints.robotoStyleProvider(14), TextColorSets.WHITE).singleLine().build();
    }
    accentColor = tdlib.chatAccentColor(linkInfo.chatId);
    final int photoSize = xgram() ? Screen.dp(XGRAM_AVATAR) : getHeight();

    if (linkInfo.photo != null) {
      ImageFileLocal file;

      if (linkInfo.photo.minithumbnail != null) {
        file = new ImageFileLocal(linkInfo.photo.minithumbnail);
        file.setSize(photoSize);
        file.setDecodeSquare(true);
        file.setScaleType(ImageFile.CENTER_CROP);
      } else {
        file = null;
      }

      ImageFile fileNetwork = new ImageFile(tdlib, linkInfo.photo.big);
      fileNetwork.setSize(photoSize);
      fileNetwork.setScaleType(ImageFile.CENTER_CROP);

      avatarPlaceholderDrawable = null;
      avatar.requestFile(file, fileNetwork);
    } else {
      avatarPlaceholderDrawable = getSparseDrawable(isChannel ? R.drawable.baseline_bullhorn_56 : R.drawable.baseline_group_56, ColorId.NONE);
      avatar.clear();
    }
  }

  public void attach() {
    avatar.attach();
  }

  public void detach() {
    avatar.detach();
  }

  @Override
  public void performDestroy () {
    avatar.destroy();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (xgram()) {
      xgramDraw(canvas);
      return;
    }
    if (avatarPlaceholderDrawable != null) {
      int cx = avatar.centerX();
      int cy = avatar.centerY();

      canvas.drawColor(accentColor.getPrimaryColor());
      float placeholderScale = (avatar.getWidth() / 2f / ((float) getMeasuredWidth() / 2f));
      canvas.save();
      canvas.scale(placeholderScale, placeholderScale, cx, cy);
      Drawables.draw(canvas, avatarPlaceholderDrawable, cx - avatarPlaceholderDrawable.getMinimumWidth() / 2f, cy - avatarPlaceholderDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(ColorUtils.alphaColor(.3f, Color.WHITE)));
      canvas.restore();
    } else {
      avatar.draw(canvas);

      topShadow.setAlpha((int) (255f * .8f));
      topShadow.draw(canvas);

      canvas.save();
      int shadowTop = getMeasuredHeight() - getBottomShadowSize();
      canvas.translate(0, Math.max(shadowTop, topShadow.getBounds().bottom - Screen.dp(28f)));
      bottomShadow.setAlpha((int) (255f * .8f));
      bottomShadow.draw(canvas);
      canvas.restore();
    }

    float baseTextLeft = Screen.dp(13f);
    float baseSubtitleTop = title.getHeight() + Screen.dp(4f);
    float baseTitleTop = getMeasuredHeight() - (title.getHeight() + subtitle.getHeight() + Screen.dp(13f));

    canvas.save();
    canvas.translate(baseTextLeft, baseTitleTop);
    title.draw(canvas, 0, 0, null, 1f);
    subtitle.draw(canvas, 0, (int) baseSubtitleTop, null, 1f);
    canvas.restore();
  }
}
