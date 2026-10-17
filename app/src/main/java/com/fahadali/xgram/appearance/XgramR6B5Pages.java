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
package com.fahadali.xgram.appearance;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.SettingsCloudController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.concurrent.TimeUnit;

/**
 * XGram M3E round 6, batch 5: the views XGram's own pages share in Expressive design: a chat's 40dp avatar for the
 * Compose rows (TgxChatAvatar), an emoji set's preview, the M3 profile-style hero of a community, and the wallpaper
 * colours preview of the Themes page. Colours are roles read when drawn, so light, dark and Pure black all apply to
 * views already built; nothing is allocated per frame. Main thread only.
 */
public final class XgramR6B5Pages {
  private XgramR6B5Pages () { }

  /** A short time or date for a row's trailing label (12:30, Mon, 12 Oct). */
  @NonNull
  public static String shortDate (long unixSeconds) {
    return unixSeconds > 0 ? Lang.timeOrDateShort(unixSeconds, TimeUnit.SECONDS) : "";
  }

  // Avatar

  /**
   * A chat's avatar through Telegram X's avatar receiver, centred in the view at the smaller of its sides (a 40dp
   * leading avatar, a 96dp hero). The receiver follows the window: attached while the view is, destroyed by
   * {@link #destroy}.
   */
  public static class AvatarView extends View {
    private final AvatarReceiver receiver;
    private @Nullable Object key;

    public AvatarView (Context context) {
      super(context);
      this.receiver = new AvatarReceiver(this);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public final AvatarReceiver getReceiver () {
      return receiver;
    }

    /** The avatar of a chat row's wrapper (its chat, user or placeholder); one already shown is not asked again. */
    public void setWrapper (@Nullable DoubleTextWrapper wrapper) {
      if (wrapper != null && key == wrapper) {
        return;
      }
      key = wrapper;
      if (wrapper != null) {
        wrapper.requestAvatar(receiver);
      } else {
        receiver.clear();
      }
      invalidate();
    }

    /** The avatar of {@code chatId}; the same chat is not asked again. */
    public void setChat (@Nullable Tdlib tdlib, long chatId) {
      if (tdlib == null || chatId == 0) {
        key = null;
        receiver.clear();
        invalidate();
        return;
      }
      if (key instanceof ChatKey && ((ChatKey) key).matches(tdlib, chatId)) {
        return;
      }
      key = new ChatKey(tdlib, chatId);
      receiver.requestChat(tdlib, chatId, AvatarReceiver.Options.NONE);
      invalidate();
    }

    /** The avatar of a message's sender: a user ({@code senderId} above 0) or a chat (below 0); the same one is not asked again. */
    public void setSender (@Nullable Tdlib tdlib, long senderId) {
      if (tdlib == null || senderId == 0) {
        key = null;
        receiver.clear();
        invalidate();
        return;
      }
      if (key instanceof SenderKey && ((SenderKey) key).matches(tdlib, senderId)) {
        return;
      }
      key = new SenderKey(tdlib, senderId);
      receiver.requestMessageSender(tdlib, senderId > 0 ? new TdApi.MessageSenderUser(senderId) : new TdApi.MessageSenderChat(senderId), AvatarReceiver.Options.NONE);
      invalidate();
    }

    /** Forgets what is shown, so the next request is made again (a photo that changed). */
    public void reset () {
      key = null;
    }

    public void destroy () {
      key = null;
      receiver.destroy();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      receiver.detach();
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final int width = getWidth() - getPaddingLeft() - getPaddingRight();
      final int height = getHeight() - getPaddingTop() - getPaddingBottom();
      final int size = Math.min(width, height);
      if (size <= 0) {
        return;
      }
      final int left = getPaddingLeft() + (width - size) / 2, top = getPaddingTop() + (height - size) / 2;
      receiver.setBounds(left, top, left + size, top + size);
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholder(c);
      }
      receiver.draw(c);
    }
  }

  private static final class ChatKey {
    final Tdlib tdlib;
    final long chatId;

    ChatKey (Tdlib tdlib, long chatId) {
      this.tdlib = tdlib;
      this.chatId = chatId;
    }

    boolean matches (Tdlib tdlib, long chatId) {
      return this.tdlib == tdlib && this.chatId == chatId;
    }
  }

  private static final class SenderKey {
    final Tdlib tdlib;
    final long senderId;

    SenderKey (Tdlib tdlib, long senderId) {
      this.tdlib = tdlib;
      this.senderId = senderId;
    }

    boolean matches (Tdlib tdlib, long senderId) {
      return this.tdlib == tdlib && this.senderId == senderId;
    }
  }

  // Emoji set preview

  /**
   * An emoji set's preview as a row's trailing content: the set's preview image, and what the page draws over it
   * (System emoji draws the phone's own). The image follows the window.
   */
  public static final class PackPreviewView extends View {
    private final ImageReceiver receiver;
    private @Nullable SettingsCloudController<?> page;
    private @Nullable Settings.CloudSetting setting;

    public PackPreviewView (Context context) {
      super(context);
      this.receiver = new ImageReceiver(this, 0);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setSetting (@Nullable SettingsCloudController<?> page, @Nullable Settings.CloudSetting setting) {
      if (this.page == page && this.setting == setting) {
        return;
      }
      this.page = page;
      this.setting = setting;
      receiver.requestFile(setting != null ? setting.getPreviewFile() : null);
      invalidate();
    }

    public void destroy () {
      page = null;
      setting = null;
      receiver.destroy();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      receiver.detach();
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final int size = Math.min(getWidth(), getHeight());
      if (size <= 0) {
        return;
      }
      final int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
      receiver.setBounds(left, top, left + size, top + size);
      receiver.draw(c);
      final SettingsCloudController<?> page = this.page;
      final Settings.CloudSetting setting = this.setting;
      if (page != null && setting != null) {
        page.xgramDrawPreview(c, setting, receiver);
      }
    }
  }

  // Community hero (M3 profile)

  /**
   * The first item of a community's settings: a 96dp avatar with a 40dp tonal camera badge at its bottom end (change
   * photo, only for those who may), the name in headlineSmall and the chat count in bodyMedium, centred, on the page.
   */
  @SuppressLint("ViewConstructor")
  public static final class CommunityHero extends LinearLayout {
    private static final float AVATAR = 96f, BADGE = 40f;

    private final AvatarView avatar;
    private final ImageView badge;
    private final TextView name, count;

    public CommunityHero (Context context, @NonNull View.OnClickListener onChangePhoto) {
      super(context);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_HORIZONTAL);
      setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(16f));
      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      FrameLayout frame = new FrameLayout(context);
      // The badge overhangs the avatar's corner by a few dp; its 48dp touch target needs the room
      final int frameSize = Screen.dp(AVATAR + 8f);
      avatar = new AvatarView(context);
      frame.addView(avatar, new FrameLayout.LayoutParams(Screen.dp(AVATAR), Screen.dp(AVATAR), Gravity.CENTER));
      badge = XgramR5Security.newTonalIconButton(context, R.drawable.baseline_camera_alt_24, BADGE, Lang.getString(R.string.XgramCommunityChangePhoto));
      badge.setOnClickListener(onChangePhoto);
      frame.addView(badge, new FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f), Gravity.BOTTOM | Gravity.END));
      addView(frame, new LinearLayout.LayoutParams(frameSize, frameSize));

      name = new XgramR5Lists.RoleText(context, XgramExpressive.TS_HEADLINE_S, XgramExpressive.ON_SURFACE);
      name.setGravity(Gravity.CENTER);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      nameParams.topMargin = Screen.dp(12f);
      addView(name, nameParams);

      count = new XgramR5Lists.RoleText(context, XgramExpressive.TS_BODY_M, XgramExpressive.ON_SURFACE_VARIANT);
      count.setGravity(Gravity.CENTER);
      count.setFontFeatureSettings("tnum");
      LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      countParams.topMargin = Screen.dp(4f);
      addView(count, countParams);
    }

    public AvatarView getAvatar () {
      return avatar;
    }

    public void bind (CharSequence title, CharSequence chats, boolean canChangePhoto) {
      name.setText(title);
      count.setText(chats);
      badge.setVisibility(canChangePhoto ? View.VISIBLE : View.GONE);
      setContentDescription(title + ", " + chats);
    }

    public void destroy () {
      avatar.destroy();
    }
  }

  // Wallpaper colours preview (Themes)

  /**
   * A small chat in the wallpaper colours Expressive design uses: the page in surface, a surfaceContainer app bar with
   * an avatar and a title line, an incoming bubble in surfaceContainerHigh, an outgoing one in primaryContainer and the
   * M3 FAB (a rounded square, 16dp corners at 56dp, scaled) in primary. Every colour is a role read when drawn, so the
   * preview follows light, dark and Pure black. Clipped to {@code clipCornerPx} (the card's corner minus its padding).
   */
  public static final class WallpaperPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final android.graphics.Path clip = new android.graphics.Path();
    private float clipCorner = -1f;
    private int clipWidth, clipHeight;
    private @Nullable XgramExpressive.MorphDrawable card;
    private float cardPadding;

    public WallpaperPreview (Context context) {
      super(context);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The card around the preview: the clip follows its (animated) corner minus {@code paddingPx}. */
    public void setCard (@Nullable XgramExpressive.MorphDrawable card, float paddingPx) {
      this.card = card;
      this.cardPadding = paddingPx;
      invalidate();
    }

    private void fill (Canvas c, int role, float l, float t, float r, float b, float radius) {
      paint.setColor(XgramExpressive.color(role));
      rect.set(l, t, r, b);
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final float w = getWidth(), h = getHeight();
      if (w <= 0f || h <= 0f) {
        return;
      }
      float corner = Screen.dpf(XgramExpressive.CORNER_S);
      if (card != null) {
        corner = Math.max(0f, card.cornerPx() - cardPadding);
      }
      if (corner != clipCorner || clipWidth != (int) w || clipHeight != (int) h) {
        // Rebuilt only while the card's corner springs or the size changes
        clipCorner = corner;
        clipWidth = (int) w;
        clipHeight = (int) h;
        clip.rewind();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW);
      }
      final int save = c.save();
      c.clipPath(clip);
      final float u = Screen.dpf(1f);
      fill(c, XgramExpressive.SURFACE, 0, 0, w, h, 0);
      final float header = h * .24f;
      fill(c, XgramExpressive.SURFACE_CONTAINER, 0, 0, w, header, 0);
      paint.setColor(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER));
      c.drawCircle(header / 2f + u * 4, header / 2f, header * .26f, paint);
      fill(c, XgramExpressive.ON_SURFACE, header + u * 6, header * .38f, w * .62f, header * .38f + u * 4, u * 2);
      final float bubble = h * .2f, radius = u * 9;
      final float y1 = header + u * 10;
      fill(c, XgramExpressive.SURFACE_CONTAINER_HIGH, u * 8, y1, w * .66f, y1 + bubble, radius);
      fill(c, XgramExpressive.ON_SURFACE_VARIANT, u * 16, y1 + bubble * .42f, w * .52f, y1 + bubble * .42f + u * 3, u * 1.5f);
      final float y2 = y1 + bubble + u * 8;
      fill(c, XgramExpressive.PRIMARY_CONTAINER, w * .38f, y2, w - u * 8, y2 + bubble, radius);
      fill(c, XgramExpressive.ON_PRIMARY_CONTAINER, w * .46f, y2 + bubble * .42f, w - u * 18, y2 + bubble * .42f + u * 3, u * 1.5f);
      final float fab = u * 24;
      fill(c, XgramExpressive.PRIMARY, w - fab - u * 8, h - fab - u * 8, w - u * 8, h - u * 8, fab * 16f / 56f);
      c.restoreToCount(save);
    }
  }
}
