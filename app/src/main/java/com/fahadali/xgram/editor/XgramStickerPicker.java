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
package com.fahadali.xgram.editor;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.EmojiLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

/**
 * The photo editor's picker for picture layers: Telegram X's emoji, sticker and GIF keyboard (EmojiLayout, its recent
 * and favourite stickers, sets and saved GIFs) in a panel rising from the bottom over the dimmed photo, as tall as the
 * keyboard. A tap on an emoji, a sticker, a custom emoji or a GIF closes it and puts the picture on the photo (a sticker
 * or a GIF once its file is downloaded; its first frame when it moves); a tap above the panel closes it. Made once per
 * viewer and kept (its pages and scroll), destroyed with it. Its look is the keyboard's: Material 3 Expressive gives it
 * surfaceContainerLow with 28dp top corners and the Emoji / Stickers / GIFs button group. While it shows, a sticker set
 * opened from it (a trending sticker, View pack in a sticker's preview) puts its stickers on the photo too
 * ({@link #pickFromStickerSet}), never in the chat behind the viewer, and TalkBack reads only the picker.
 */
public final class XgramStickerPicker extends FrameLayoutFix implements EmojiLayout.Listener, FactorAnimator.Target {
  public interface Callback {
    /** {@code layer} was picked; a sticker's or a GIF's comes once its file is downloaded, maybe after the picker closed. */
    void onStickerPicked (XgramStickerPicker picker, XgramStickerLayer layer);
  }

  private static final int SCRIM_COLOR = 0x66000000;
  private static final float MIN_PANEL_DP = 280f, MAX_PANEL_SHARE = .7f;

  private final Tdlib tdlib;
  private final Callback callback;
  private final EmojiLayout emojiLayout;
  private final ColorDrawable scrim;
  private final BoolAnimator shownAnimator;
  private boolean showing, destroyed;
  private int bottomInset;
  private final XgramOverlayUnderlay underlay = new XgramOverlayUnderlay();

  /** The picker on screen: StickerSetWrap gives it the stickers tapped in a set opened from it. */
  private static @Nullable XgramStickerPicker showingPicker;

  public XgramStickerPicker (Context context, @NonNull ViewController<?> parent, @NonNull Callback callback) {
    super(context);
    this.tdlib = parent.tdlib();
    this.callback = callback;
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    this.scrim = new ColorDrawable(SCRIM_COLOR);
    setBackground(scrim);
    setOnClickListener(v -> hide(true)); // a tap above the panel
    setContentDescription(Lang.getString(R.string.Cancel));
    ViewCompat.setAccessibilityPaneTitle(this, Lang.getString(R.string.XgramEditorAddSticker)); // TalkBack says it opened

    emojiLayout = new EmojiLayout(context);
    emojiLayout.initWithMediasEnabled(parent, true, this, parent, false);
    emojiLayout.setNoInlineSearch(); // no search field to open over the photo
    emojiLayout.setClickable(true); // its empty spots don't close it
    // after init, which gives it the keyboard's own params
    emojiLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(MIN_PANEL_DP), Gravity.BOTTOM));
    addView(emojiLayout);

    shownAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);
    setVisibility(View.INVISIBLE);
  }

  // Showing

  public boolean isShowing () {
    return showing;
  }

  /** Rises from the bottom (the caller adds it to the viewer first). */
  public void show () {
    if (destroyed || showing) {
      return;
    }
    showing = true;
    showingPicker = this;
    setVisibility(View.VISIBLE);
    underlay.hide(this);
    XgramAppMotion.use(shownAnimator, XgramExpressive.SPATIAL_DEFAULT, true);
    shownAnimator.setValue(true, true);
    applyShownFactor();
  }

  /** Goes down, then leaves its parent. */
  public void hide (boolean animated) {
    if (!showing) {
      return;
    }
    showing = false;
    letGo();
    XgramAppMotion.use(shownAnimator, XgramExpressive.EFFECTS_FAST, true);
    shownAnimator.setValue(false, animated && getParent() != null);
    applyShownFactor();
    if (shownAnimator.getFloatValue() == 0f) {
      removeSelf();
    }
  }

  private void letGo () {
    if (showingPicker == this) {
      showingPicker = null;
    }
    underlay.restore();
  }

  private void removeSelf () {
    setVisibility(View.INVISIBLE);
    ViewParent parent = getParent();
    if (parent instanceof ViewGroup) {
      ((ViewGroup) parent).removeView(this);
    }
  }

  /** The viewer is gone: the keyboard's pages let go of their data. */
  public void destroy () {
    if (!destroyed) {
      destroyed = true;
      showing = false;
      letGo();
      removeSelf();
      emojiLayout.destroy();
    }
  }

  /** What is under it at the bottom (the navigation bar): the panel grows by it and its pages scroll above it. */
  public void setBottomInset (int bottomInset) {
    if (this.bottomInset != bottomInset) {
      this.bottomInset = bottomInset;
      emojiLayout.setExtraBottomInset(bottomInset, bottomInset);
      requestLayout();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int height = MeasureSpec.getSize(heightMeasureSpec);
    // as tall as the keyboard, within bounds
    int panelHeight = Math.max(Keyboard.getSize(), Screen.dp(MIN_PANEL_DP)) + bottomInset;
    if (height > 0) {
      panelHeight = Math.min(panelHeight, Math.round(height * MAX_PANEL_SHARE));
    }
    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) emojiLayout.getLayoutParams();
    if (params.height != panelHeight) {
      params.height = panelHeight;
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    applyShownFactor();
  }

  private void applyShownFactor () {
    final float factor = MathUtils.clamp(shownAnimator.getFloatValue());
    scrim.setAlpha((int) (255f * factor));
    final int panelHeight = emojiLayout.getMeasuredHeight() > 0 ? emojiLayout.getMeasuredHeight() : emojiLayout.getLayoutParams().height;
    emojiLayout.setTranslationY(panelHeight * (1f - factor));
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    applyShownFactor();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 0f && !showing) {
      removeSelf();
    }
  }

  // Picking

  @Override
  public void onEnterEmoji (String emoji) {
    if (showing && !StringUtils.isEmpty(emoji)) {
      callback.onStickerPicked(this, XgramStickerLayer.newEmoji(emoji));
      hide(true);
    }
  }

  @Override
  public void onEnterCustomEmoji (TGStickerObj sticker) {
    pickSticker(sticker);
  }

  @Override
  public boolean onSendSticker (@Nullable View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
    return pickSticker(sticker);
  }

  @Override
  public boolean onSendGIF (@Nullable View view, TdApi.Animation animation) {
    if (!showing || animation == null || animation.animation == null) {
      return false;
    }
    pickFile(animation.animation, XgramStickerLayer.FORMAT_VIDEO, aspect(animation.width, animation.height));
    hide(true);
    return true;
  }

  @Override
  public boolean isEmojiInputEmpty () {
    return true; // no backspace button
  }

  private boolean pickSticker (@Nullable TGStickerObj obj) {
    final TdApi.Sticker sticker = obj != null ? obj.getSticker() : null;
    if (!showing || sticker == null || sticker.sticker == null) {
      return false;
    }
    pickFile(sticker.sticker, XgramStickerLayer.formatOf(sticker.format), aspect(sticker.width, sticker.height));
    hide(true);
    return true;
  }

  /**
   * Whether a picker is on screen: a sticker set opened from it (StickerSetWrap) belongs to it then, so it sends
   * nothing to the chat behind the viewer.
   */
  public static boolean isPicking () {
    return showingPicker != null;
  }

  /** A sticker or a custom emoji tapped in a sticker set opened from the picker: true if it goes on the photo. */
  public static boolean pickFromStickerSet (@Nullable TGStickerObj sticker) {
    final XgramStickerPicker picker = showingPicker;
    return picker != null && picker.pickSticker(sticker);
  }

  private static float aspect (int width, int height) {
    return width > 0 && height > 0 ? (float) width / (float) height : 1f;
  }

  private void pickFile (@NonNull TdApi.File file, int format, float aspect) {
    if (TD.isFileLoaded(file)) {
      callback.onStickerPicked(this, XgramStickerLayer.newFile(file.local.path, format, aspect));
      return;
    }
    tdlib.client().send(new TdApi.DownloadFile(file.id, TdlibFilesManager.PRIORITY_USER_REQUEST_DOWNLOAD, 0, 0, true), result -> UI.post(() -> {
      if (result.getConstructor() == TdApi.File.CONSTRUCTOR && TD.isFileLoaded((TdApi.File) result)) {
        callback.onStickerPicked(this, XgramStickerLayer.newFile(((TdApi.File) result).local.path, format, aspect));
      } else {
        UI.showToast(R.string.XgramEditorStickerFailed, Toast.LENGTH_SHORT);
      }
    }));
  }

  // Emoji tones (a long press on an emoji): EmojiToneHelper places its popup through the viewer

  /**
   * Places {@code viewToDisplay} (the tones) in {@code contentView} above {@code anchorView} (the emoji), as
   * EmojiToneHelper.defaultDisplay does for the caption's keyboard, from where both are on screen.
   */
  public static int[] displayTone (View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset, ViewGroup contentView) {
    final int[] anchor = new int[2], parent = new int[2];
    anchorView.getLocationOnScreen(anchor);
    contentView.getLocationOnScreen(parent);
    final int x = anchor[0] - parent[0], y = anchor[1] - parent[1];
    final int desiredLeft = x + horizontalOffset;
    final int left = Math.max(horizontalMargin, Math.min(contentView.getMeasuredWidth() - Math.max(0, viewWidth) - horizontalMargin, desiredLeft));
    final int top = y - viewHeight + verticalOffset;
    FrameLayout.LayoutParams params = FrameLayoutFix.newParams(viewWidth, viewHeight, Gravity.LEFT | Gravity.TOP);
    params.leftMargin = left;
    params.topMargin = top;
    viewToDisplay.setLayoutParams(params);
    contentView.addView(viewToDisplay);
    return new int[] {left - x, top - y};
  }
}
