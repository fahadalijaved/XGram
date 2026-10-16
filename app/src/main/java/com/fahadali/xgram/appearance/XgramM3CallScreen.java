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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.graphics.shapes.Morph;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.TextView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E: the voice call screen (CallController with Expressive design on), after Google Phone's Material 3
 * Expressive in-call screen.
 * <ul>
 * <li>Top bar: a "Telegram call" labelMedium chip (surfaceContainerHigh) with the signal bars once connected, and the
 * encryption key emoji in a tonal secondaryContainer chip that opens a key card (a floating surfaceContainerHigh card,
 * corner 28dp) with the big emoji and the check hint.</li>
 * <li>Hero: the avatar (128dp, 120dp on short windows) on a halo that morphs between a circle and a 12-sided cookie
 * and turns while the call rings or connects (static with Reduce motion), the name in headlineMedium and the status in
 * titleMedium onSurfaceVariant, in sentence case, with the loading indicator while it is in progress.</li>
 * <li>Controls: 72dp toggle icon buttons with labels (mute, speaker or Bluetooth, message), then the action area: the
 * 96 x 64dp end call pill, the incoming swipe pill, or close and call again after a failed call.</li>
 * <li>Background: surface with a soft gradient from a container tone in the contact's accent hue at the top.</li>
 * </ul>
 * Every state change springs (XgramExpressive.Spring, snapping with Reduce motion); colors are read from the M3 roles
 * at draw time, so light, dark and Pure black all apply, and a theme switch repaints through {@link #invalidate()}.
 * Tabletop posture keeps the hero above the fold and the controls below it. Main thread only.
 */
public final class XgramM3CallScreen extends FrameLayoutFix {
  public interface Callback {
    void onMuteClick ();
    void onSpeakerClick ();
    void onMessageClick ();
    /** The branding chip (debug builds: the call log). */
    void onBrandClick ();
    /** False when the call cannot be answered yet (the microphone permission is being asked). */
    boolean onAnswer ();
    void onDecline (boolean isHangUp);
    void onRedial ();
    void onDismiss ();
  }

  // Geometry (dp)

  private static final float TOP_GAP = 8f, TOP_BAR = 48f, SIDE = 16f, CHIP_H = 32f, KEY_CHIP_H = 36f;
  private static final float STAGE = 184f, AVATAR = 128f, STAGE_COMPACT = 160f, AVATAR_COMPACT = 120f;
  private static final float COMPACT_HEIGHT = 720f;
  private static final float NAME_GAP = 20f, STATUS_GAP = 6f, COLUMN_MAX = 560f, CARD_MAX = 400f;
  private static final float CONTROLS_MAX = 400f, SWIPE_MAX = 360f, ACTION_GAP = 28f, ACTION_AREA = 112f;
  private static final float CONTROLS_BOTTOM = 24f, CONTROLS_ENTER = 24f;

  private static final int MODE_NONE = 0, MODE_SWIPE = 1, MODE_END = 2, MODE_RETRY = 3, MODE_DECLINE = 4;

  private final Tdlib tdlib;
  private final Callback callback;

  // Views

  private final FrameLayoutFix topBar;
  private final LinearLayout brandChip;
  private final OutlineChip brandOutline;
  private final ImageView brandIcon;
  private final TextView brandText;
  private final BarsView barsView;
  private final EmojiTextView keyChip;
  private final Drawable keyLock;

  private final LinearLayout hero;
  private final Stage stage;
  private final AvatarView avatarView;
  private final EmojiTextView nameView;
  private final EmojiStatusHelper emojiStatus;
  private final TextView statusView;
  private final XgramLoadingIndicatorDrawable statusIndicator;
  private boolean statusIndicatorShown;

  private final View dismissCatcher;
  private final LinearLayout keyCard;
  private final TextView keyTitle, keyHint;
  private final EmojiTextView keyEmoji;

  private final LinearLayout controls;
  private final LinearLayout toggleRow;
  private final XgramM3CallButton muteButton, speakerButton, messageButton;
  private final FrameLayoutFix actionArea;
  private final XgramM3CallButton endButton;
  private final XgramM3CallSwipe swipe;
  private final LinearLayout retryRow;
  private final XgramM3CallButton closeButton, redialButton;

  // Motion

  private final Fade toggleFade, endFade, swipeFade, retryFade, barsFade;
  private final XgramExpressive.Spring keyShown, keyExpanded;

  // State

  private int topInset, bottomInset, foldTop = -1;
  private boolean compact;
  private float enterFactor;
  private int mode = MODE_NONE;
  private int callId, callState;
  private boolean callOutgoing;
  private @Nullable TdApi.User user;
  private @Nullable TdlibAccentColor accentColor;
  private boolean hasKey;
  private String statusText;

  // Colors

  private int colorStamp;
  private @ColorInt int heroTint, haloColor;
  private int tintSource;
  private boolean tintDark, tintValid;
  private final float[] hct = new float[3];

  private final Paint gradientPaint = new Paint();
  private @Nullable LinearGradient gradient;
  private int gradientTop, gradientBottom, gradientHeight;

  public XgramM3CallScreen (Context context, Tdlib tdlib, Callback callback) {
    super(context);
    this.tdlib = tdlib;
    this.callback = callback;
    setWillNotDraw(false);

    // Hero

    hero = new LinearLayout(context);
    hero.setOrientation(LinearLayout.VERTICAL);
    hero.setGravity(Gravity.CENTER_HORIZONTAL);
    hero.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(COLUMN_MAX), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

    stage = new Stage(context);
    stage.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(STAGE), Screen.dp(STAGE)));
    avatarView = new AvatarView(context);
    avatarView.setNeedFull(true);
    avatarView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(AVATAR), Screen.dp(AVATAR), Gravity.CENTER));
    stage.addView(avatarView);
    hero.addView(stage);

    nameView = new EmojiTextView(context) {
      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        drawEmojiStatus(this, canvas);
      }
    };
    nameView.setScrollDisabled(true);
    nameView.setSingleLine(true);
    nameView.setEllipsize(TextUtils.TruncateAt.END);
    nameView.setGravity(Gravity.CENTER_HORIZONTAL);
    XgramExpressive.applyText(nameView, XgramExpressive.TS_HEADLINE_M, false);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = Screen.dp(NAME_GAP);
    nameView.setLayoutParams(lp);
    hero.addView(nameView);
    emojiStatus = new EmojiStatusHelper(tdlib, nameView, null);

    statusView = new TextView(context);
    statusView.setScrollDisabled(true);
    statusView.setMaxLines(2);
    statusView.setEllipsize(TextUtils.TruncateAt.END);
    statusView.setGravity(Gravity.CENTER);
    XgramExpressive.applyText(statusView, XgramExpressive.TS_TITLE_M, false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      statusView.setFontFeatureSettings("tnum"); // the duration does not shift as it counts
    }
    statusView.setCompoundDrawablePadding(Screen.dp(8f));
    statusIndicator = new XgramLoadingIndicatorDrawable(20f, XgramExpressive.PRIMARY);
    // Wrap content, so the leading indicator sits right next to the centered text
    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = Screen.dp(STATUS_GAP);
    statusView.setLayoutParams(lp);
    hero.addView(statusView);
    addView(hero);

    // Tap outside the key card closes it

    dismissCatcher = new View(context);
    dismissCatcher.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    dismissCatcher.setOnClickListener(v -> setKeyExpanded(false));
    dismissCatcher.setClickable(false);
    dismissCatcher.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(dismissCatcher);

    // Key card

    keyCard = new LinearLayout(context);
    keyCard.setOrientation(LinearLayout.VERTICAL);
    keyCard.setGravity(Gravity.CENTER_HORIZONTAL);
    int pad = Screen.dp(24f);
    keyCard.setPadding(pad, pad, pad, pad);
    // A floating card over the page (the hero fades out under it): surfaceContainerHighest at elevation 3, so it
    // stands off the tinted gradient in light and dark; Pure black puts it on the chip step (XgramAmoled.floatingRole)
    XgramExpressive.MorphDrawable card = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_XL, Float.NaN);
    card.setFloatingColorRole(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
    keyCard.setBackground(card);
    keyCard.setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    keyCard.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(CARD_MAX), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    keyCard.setOnClickListener(v -> setKeyExpanded(false));
    keyCard.setAlpha(0f);
    keyCard.setVisibility(View.INVISIBLE);

    keyTitle = new TextView(context);
    keyTitle.setScrollDisabled(true);
    keyTitle.setGravity(Gravity.CENTER);
    XgramExpressive.applyText(keyTitle, XgramExpressive.TS_TITLE_M, false);
    keyTitle.setText(Lang.getString(R.string.XgramM3CallKey));
    keyCard.addView(keyTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    keyEmoji = new EmojiTextView(context);
    keyEmoji.setScrollDisabled(true);
    keyEmoji.setSingleLine(true);
    keyEmoji.setGravity(Gravity.CENTER);
    keyEmoji.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 32f);
    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = Screen.dp(16f);
    keyCard.addView(keyEmoji, lp);

    keyHint = new TextView(context);
    keyHint.setScrollDisabled(true);
    keyHint.setGravity(Gravity.CENTER);
    XgramExpressive.applyText(keyHint, XgramExpressive.TS_BODY_M, false);
    keyHint.setLineSpacing(Screen.dp(4f), 1f);
    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = Screen.dp(16f);
    keyCard.addView(keyHint, lp);
    addView(keyCard);

    // Top bar

    topBar = new FrameLayoutFix(context);
    topBar.setPadding(Screen.dp(SIDE), 0, Screen.dp(SIDE), 0);
    topBar.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(TOP_BAR), Gravity.TOP));
    topBar.setAlpha(0f);

    final boolean rtl = Lang.rtl();
    brandChip = new LinearLayout(context);
    brandChip.setOrientation(LinearLayout.HORIZONTAL);
    brandChip.setGravity(Gravity.CENTER_VERTICAL);
    brandChip.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    brandChip.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CHIP_H), (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
    // An outlined assist chip: a fill would vanish on the tinted top of the gradient in every scheme
    brandOutline = new OutlineChip();
    brandChip.setBackground(brandOutline);

    brandIcon = new ImageView(context);
    brandIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    brandIcon.setImageDrawable(Drawables.get(R.drawable.deproko_logo_telegram_18));
    brandChip.addView(brandIcon, new LinearLayout.LayoutParams(Screen.dp(16f), Screen.dp(16f)));

    brandText = new TextView(context);
    brandText.setScrollDisabled(true);
    brandText.setSingleLine(true);
    brandText.setEllipsize(TextUtils.TruncateAt.END);
    XgramExpressive.applyText(brandText, XgramExpressive.TS_LABEL_M, false);
    brandText.setText(Lang.getString(R.string.XgramM3CallBranding));
    LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    chipLp.setMarginStart(Screen.dp(8f));
    brandChip.addView(brandText, chipLp);

    barsView = new BarsView(context);
    chipLp = new LinearLayout.LayoutParams(Screen.dp(16f), Screen.dp(14f));
    chipLp.setMarginStart(Screen.dp(8f));
    barsView.setLayoutParams(chipLp);
    barsView.setVisibility(View.GONE);
    brandChip.addView(barsView);
    topBar.addView(brandChip);

    keyChip = new EmojiTextView(context);
    keyChip.setScrollDisabled(true);
    keyChip.setSingleLine(true);
    keyChip.setGravity(Gravity.CENTER);
    keyChip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f);
    keyChip.setPadding(Screen.dp(12f), 0, Screen.dp(14f), 0);
    keyChip.setCompoundDrawablePadding(Screen.dp(6f));
    keyLock = Drawables.get(R.drawable.baseline_lock_16);
    keyLock.setBounds(0, 0, keyLock.getMinimumWidth(), keyLock.getMinimumHeight());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      keyChip.setCompoundDrawablesRelative(keyLock, null, null, null);
    } else {
      keyChip.setCompoundDrawables(keyLock, null, null, null);
    }
    keyChip.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(KEY_CHIP_H), (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
    XgramExpressive.MorphDrawable keyMorph = XgramExpressive.attachPressMorph(keyChip, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    keyMorph.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
    keyChip.setContentDescription(Lang.getString(R.string.XgramM3CallKeyShow));
    keyChip.setOnClickListener(v -> setKeyExpanded(true));
    keyChip.setId(R.id.btn_emoji);
    keyChip.setAlpha(0f);
    keyChip.setVisibility(View.INVISIBLE);
    topBar.addView(keyChip);
    addView(topBar);

    // Controls

    controls = new LinearLayout(context);
    controls.setOrientation(LinearLayout.VERTICAL);
    controls.setGravity(Gravity.CENTER_HORIZONTAL);
    controls.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(CONTROLS_MAX), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
    controls.setAlpha(0f);

    toggleRow = new LinearLayout(context);
    toggleRow.setOrientation(LinearLayout.HORIZONTAL);
    toggleRow.setGravity(Gravity.CENTER_HORIZONTAL);
    muteButton = newToggle(context, R.drawable.baseline_mic_24, R.string.XgramM3CallMute, v -> callback.onMuteClick());
    muteButton.setNeedCross(true);
    muteButton.setId(R.id.btn_mute);
    speakerButton = newToggle(context, R.drawable.baseline_volume_up_24, R.string.XgramM3CallSpeaker, v -> callback.onSpeakerClick());
    speakerButton.setId(R.id.btn_speaker);
    messageButton = newToggle(context, R.drawable.baseline_chat_bubble_24, R.string.XgramM3CallMessage, v -> callback.onMessageClick());
    messageButton.setId(R.id.btn_openChat);
    toggleRow.addView(muteButton);
    toggleRow.addView(speakerButton);
    toggleRow.addView(messageButton);
    controls.addView(toggleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    actionArea = new FrameLayoutFix(context);
    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ACTION_AREA));
    lp.topMargin = Screen.dp(ACTION_GAP);
    actionArea.setLayoutParams(lp);

    endButton = new XgramM3CallButton(context, XgramM3CallButton.STYLE_END);
    endButton.setIcon(R.drawable.baseline_call_end_24);
    endButton.setDescription(Lang.getString(R.string.XgramM3CallEnd));
    endButton.setId(R.id.btn_acceptOrHangCall);
    endButton.setOnClickListener(v -> onEndClick());
    endButton.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    actionArea.addView(endButton);

    swipe = new XgramM3CallSwipe(context);
    swipe.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(SWIPE_MAX), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    swipe.setListener(new XgramM3CallSwipe.Listener() {
      @Override
      public boolean onSwipeAnswer () {
        return callback.onAnswer();
      }

      @Override
      public void onSwipeDecline () {
        callback.onDecline(false);
      }
    });
    actionArea.addView(swipe);

    retryRow = new LinearLayout(context);
    retryRow.setOrientation(LinearLayout.HORIZONTAL);
    retryRow.setGravity(Gravity.CENTER_HORIZONTAL);
    retryRow.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    closeButton = new XgramM3CallButton(context, XgramM3CallButton.STYLE_TONAL);
    closeButton.setIcon(R.drawable.baseline_close_24);
    closeButton.setLabel(Lang.getString(R.string.XgramM3CallClose));
    closeButton.setId(R.id.btn_closeCall);
    closeButton.setOnClickListener(v -> callback.onDismiss());
    redialButton = new XgramM3CallButton(context, XgramM3CallButton.STYLE_ACCEPT);
    redialButton.setIcon(R.drawable.baseline_phone_24);
    redialButton.setLabel(Lang.getString(R.string.XgramM3CallAgain));
    redialButton.setOnClickListener(v -> callback.onRedial());
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rowLp.leftMargin = rowLp.rightMargin = Screen.dp(8f);
    retryRow.addView(closeButton, rowLp);
    rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rowLp.leftMargin = rowLp.rightMargin = Screen.dp(8f);
    retryRow.addView(redialButton, rowLp);
    actionArea.addView(retryRow);

    controls.addView(actionArea);
    addView(controls);

    toggleFade = new Fade(toggleRow, .9f, false);
    endFade = new Fade(endButton, .7f, false);
    swipeFade = new Fade(swipe, .9f, false);
    retryFade = new Fade(retryRow, .8f, false);
    barsFade = new Fade(barsView, .6f, true);
    keyShown = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> updateKeyViews());
    keyShown.setEndListener(this::updateKeyViews);
    keyExpanded = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> updateKeyViews());
    keyExpanded.setEndListener(this::updateKeyViews);

    syncColors(true);
  }

  private XgramM3CallButton newToggle (Context context, @DrawableRes int icon, int label, View.OnClickListener listener) {
    XgramM3CallButton button = new XgramM3CallButton(context, XgramM3CallButton.STYLE_TOGGLE);
    button.setIcon(icon);
    button.setLabel(Lang.getString(label));
    button.setOnClickListener(listener);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = lp.rightMargin = Screen.dp(4f);
    button.setLayoutParams(lp);
    return button;
  }

  // Public API (CallController)

  /** The view the key tooltip points at. */
  public View getKeyAnchor () {
    return keyChip;
  }

  /** Whether the key chip and card already have the emoji. */
  public boolean hasKey () {
    return hasKey;
  }

  /** The debug log toggle (the branding chip) for builds that show it. */
  public void setBrandClickable (boolean clickable) {
    if (clickable) {
      brandChip.setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE_VARIANT, brandOutline,
        new XgramExpressiveC.PressMorphMask(10000f, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_FULL)));
      brandChip.setOnClickListener(v -> callback.onBrandClick());
    }
  }

  public void setUser (@Nullable TdApi.User user) {
    this.user = user;
    this.accentColor = user != null ? tdlib.cache().userAccentColor(user) : null;
    avatarView.setUser(tdlib, user, false);
    emojiStatus.updateEmoji(tdlib, user, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
      @Override
      public long mediaTextComplexColor () {
        return Theme.newComplexColor(true, ColorId.iconActive);
      }
    }, R.drawable.baseline_premium_star_28, 22);
    nameView.setText(TD.getUserName(user));
    int statusWidth = user != null && user.isPremium ? emojiStatus.getWidth(Screen.dp(7f)) : 0;
    nameView.setPadding(0, 0, statusWidth, 0);
    tintValid = false;
    syncColors(true);
  }

  public void setKeyHint (CharSequence hint) {
    keyHint.setText(hint);
  }

  /** The four emoji of the encryption key. */
  public void setKey (@Nullable String[] emojis) {
    if (emojis == null || emojis.length == 0) {
      return;
    }
    StringBuilder chipText = new StringBuilder(), cardText = new StringBuilder();
    for (String emoji : emojis) {
      if (chipText.length() > 0) {
        chipText.append(' ');
        cardText.append("  ");
      }
      chipText.append(emoji);
      cardText.append(emoji);
    }
    keyChip.setText(Emoji.instance().replaceEmoji(chipText.toString()));
    keyEmoji.setText(Emoji.instance().replaceEmoji(cardText.toString()));
    hasKey = true;
  }

  public void setKeyVisible (boolean visible, boolean animated) {
    visible = visible && hasKey;
    if (!visible) {
      setKeyExpanded(false);
    }
    if (visible) {
      keyChip.setVisibility(View.VISIBLE);
    }
    springTo(keyShown, visible ? 1f : 0f, animated);
    updateKeyViews();
  }

  public void setKeyExpanded (boolean expanded) {
    if (expanded && keyShown.getTarget() != 1f) {
      return;
    }
    if (expanded) {
      keyCard.setVisibility(View.VISIBLE);
    }
    springTo(keyExpanded, expanded ? 1f : 0f, isLaidOutCompat());
    updateKeyViews();
  }

  public boolean isKeyExpanded () {
    return keyExpanded.getTarget() == 1f;
  }

  private void updateKeyViews () {
    float shown = keyShown.getValue();
    float expandedRaw = keyExpanded.getValue();
    float expanded = MathUtils.clamp(expandedRaw);

    keyChip.setAlpha(MathUtils.clamp(shown) * (1f - expanded));
    float chipScale = .8f + .2f * shown;
    keyChip.setScaleX(chipScale);
    keyChip.setScaleY(chipScale);
    boolean chipVisible = keyShown.getTarget() == 1f || shown > 0f;
    keyChip.setVisibility(chipVisible ? View.VISIBLE : View.INVISIBLE);
    keyChip.setEnabled(keyShown.getTarget() == 1f && keyExpanded.getTarget() == 0f);

    keyCard.setAlpha(expanded);
    float cardScale = .9f + .1f * expandedRaw;
    keyCard.setScaleX(cardScale);
    keyCard.setScaleY(cardScale);
    keyCard.setTranslationY((1f - expanded) * -Screen.dp(12f));
    boolean cardVisible = keyExpanded.getTarget() == 1f || expanded > 0f;
    keyCard.setVisibility(cardVisible ? View.VISIBLE : View.INVISIBLE);

    hero.setAlpha(1f - expanded);
    dismissCatcher.setClickable(keyExpanded.getTarget() == 1f);
  }

  /** Status in sentence case; {@code inProgress} shows the loading indicator in front of it (not with Reduce motion). */
  public void setStatus (String text, boolean inProgress) {
    if (!TextUtils.equals(statusText, text)) {
      statusText = text;
      statusView.setText(text);
    }
    boolean showIndicator = inProgress && !XgramMotion.reduce();
    if (statusIndicatorShown != showIndicator) {
      statusIndicatorShown = showIndicator;
      if (showIndicator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          statusView.setCompoundDrawablesRelative(statusIndicator, null, null, null);
        } else {
          statusView.setCompoundDrawables(statusIndicator, null, null, null);
        }
        statusIndicator.start();
      } else {
        statusIndicator.stop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          statusView.setCompoundDrawablesRelative(null, null, null, null);
        } else {
          statusView.setCompoundDrawables(null, null, null, null);
        }
      }
    }
  }

  /** The halo pulse and the incoming pill's motion. */
  public void setRinging (boolean ringing, boolean animated) {
    stage.setRinging(ringing, animated);
    swipe.setActive(ringing);
  }

  public void setTogglesVisible (boolean visible, boolean animated) {
    toggleFade.set(visible, animated);
  }

  public void setMuted (boolean muted, boolean animated) {
    muteButton.setChecked(muted, animated);
  }

  /** Speaker (on when the audio leaves the earpiece) or Bluetooth while the call is routed to a headset. */
  public void setAudioRoute (boolean bluetooth, boolean on, boolean animated) {
    speakerButton.setIcon(bluetooth ? R.drawable.baseline_bluetooth_24 : R.drawable.baseline_volume_up_24);
    speakerButton.setLabel(Lang.getString(bluetooth ? R.string.XgramM3CallBluetooth : R.string.XgramM3CallSpeaker));
    speakerButton.setChecked(on, animated);
  }

  public void setCallBars (int count, boolean visible, boolean animated) {
    barsView.setCount(count);
    barsFade.set(visible, animated);
  }

  /** Popup open and close progress: the chips fade in and the controls rise into place. */
  public void setEnterFactor (float factor) {
    this.enterFactor = factor;
    topBar.setAlpha(factor);
    controls.setAlpha(factor);
    controls.setTranslationY((1f - factor) * Screen.dp(CONTROLS_ENTER));
  }

  public void setTopInset (int inset) {
    if (this.topInset != inset) {
      this.topInset = inset;
      requestLayout();
    }
  }

  public void setBottomInset (int inset) {
    if (this.bottomInset != inset) {
      this.bottomInset = inset;
      requestLayout();
    }
  }

  /** Tabletop posture: where the fold crosses this view (XgramWindow.tabletopFoldTop), or -1. */
  public void setFoldTop (int foldTop) {
    if (this.foldTop != foldTop) {
      this.foldTop = foldTop;
      requestLayout();
    }
  }

  /**
   * The action area for the call state, as Telegram X's CallControlsLayout: the swipe pill for an incoming call, the
   * end call pill while it rings, connects or runs, close and call again after it failed or dropped.
   */
  public void setCall (TdApi.Call call, boolean animated) {
    int state = call.state.getConstructor();
    int newMode;
    switch (state) {
      case TdApi.CallStatePending.CONSTRUCTOR:
        newMode = call.isOutgoing ? MODE_END : MODE_SWIPE;
        break;
      case TdApi.CallStateDiscarded.CONSTRUCTOR: {
        int reason = ((TdApi.CallStateDiscarded) call.state).reason.getConstructor();
        if (reason == TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR || reason == TdApi.CallDiscardReasonHungUp.CONSTRUCTOR || call.isOutgoing) {
          newMode = MODE_RETRY;
        } else {
          newMode = MODE_DECLINE;
        }
        break;
      }
      case TdApi.CallStateError.CONSTRUCTOR:
        newMode = MODE_RETRY;
        break;
      case TdApi.CallStateExchangingKeys.CONSTRUCTOR:
      case TdApi.CallStateReady.CONSTRUCTOR:
      case TdApi.CallStateHangingUp.CONSTRUCTOR:
      default:
        newMode = MODE_END;
        break;
    }
    if (newMode == MODE_SWIPE && (this.mode != MODE_SWIPE || this.callId != call.id)) {
      swipe.reset();
    }
    if (this.callId != 0 && this.callId != call.id) {
      hasKey = false; // a replaced call has its own key (CallController.updateEmoji sets it once it is ready)
    }
    this.callId = call.id;
    this.callState = state;
    this.callOutgoing = call.isOutgoing;
    if (this.mode != newMode) {
      this.mode = newMode;
      endButton.setDescription(Lang.getString(newMode == MODE_DECLINE ? R.string.XgramM3CallDecline : R.string.XgramM3CallEnd));
      endFade.set(newMode == MODE_END || newMode == MODE_DECLINE, animated);
      swipeFade.set(newMode == MODE_SWIPE, animated);
      retryFade.set(newMode == MODE_RETRY, animated);
    }
  }

  private void onEndClick () {
    if (mode == MODE_DECLINE) {
      callback.onDecline(false);
      return;
    }
    switch (callState) {
      case TdApi.CallStateDiscarded.CONSTRUCTOR:
      case TdApi.CallStateError.CONSTRUCTOR:
        callback.onRedial();
        break;
      case TdApi.CallStatePending.CONSTRUCTOR:
        if (callOutgoing) {
          callback.onDecline(false);
        } else {
          callback.onAnswer();
        }
        break;
      default:
        callback.onDecline(true);
        break;
    }
  }

  public void performDestroy () {
    statusIndicator.stop();
    stage.setRinging(false, false);
    swipe.setActive(false);
    avatarView.performDestroy();
    emojiStatus.performDestroy();
  }

  // Motion helpers

  private boolean isLaidOutCompat () {
    return getWidth() > 0 && getHeight() > 0;
  }

  private static void springTo (XgramExpressive.Spring spring, float target, boolean animated) {
    if (animated) {
      spring.animateTo(target);
    } else {
      spring.setValue(target);
    }
  }

  /** A shown / hidden flag that springs in (alpha clamped, scale overshooting) and leaves the view invisible when out. */
  private final class Fade implements XgramExpressive.Spring.Listener {
    private final View view;
    private final float minScale;
    private final XgramExpressive.Spring spring;
    private final boolean goneWhenHidden;

    /** Starts hidden; {@code goneWhenHidden} for a view inside a wrap content row (the bars in the branding chip). */
    Fade (View view, float minScale, boolean goneWhenHidden) {
      this.view = view;
      this.minScale = minScale;
      this.goneWhenHidden = goneWhenHidden;
      this.spring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, this);
      this.spring.setEndListener(this::settle);
      apply(spring.getValue());
      settle();
    }

    void set (boolean visible, boolean animated) {
      float target = visible ? 1f : 0f;
      if (spring.getTarget() == target && (animated || spring.getValue() == target)) {
        return;
      }
      if (visible) {
        view.setVisibility(View.VISIBLE);
      }
      setTouchable(view, visible);
      if (animated && isLaidOutCompat()) {
        spring.animateTo(target);
      } else {
        spring.setValue(target);
        apply(target);
        settle();
      }
    }

    @Override
    public void onSpringUpdate (XgramExpressive.Spring s, float value) {
      apply(value);
    }

    private void apply (float value) {
      view.setAlpha(MathUtils.clamp(value));
      float scale = minScale + (1f - minScale) * value;
      view.setScaleX(scale);
      view.setScaleY(scale);
    }

    private void settle () {
      if (spring.getTarget() == 0f && !spring.isRunning()) {
        view.setVisibility(goneWhenHidden ? View.GONE : View.INVISIBLE);
      }
    }
  }

  private static void setTouchable (View view, boolean touchable) {
    if (view instanceof XgramM3CallButton) {
      ((XgramM3CallButton) view).setTouchable(touchable);
    } else if (view instanceof XgramM3CallSwipe) {
      ((XgramM3CallSwipe) view).setTouchable(touchable);
    } else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        setTouchable(group.getChildAt(i), touchable);
      }
    }
  }

  // Colors

  /** The role colors the views keep (text colors, icon filters) follow the theme; checked whenever this view draws. */
  private void syncColors (boolean force) {
    final int onSurface = XgramExpressive.color(XgramExpressive.ON_SURFACE);
    final int variant = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    final int onSecondary = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
    final int surface = XgramExpressive.color(XgramExpressive.SURFACE);
    final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
    final int accent = accentColor != null ? accentColor.getPrimaryColor() : primary;
    int stamp = onSurface;
    stamp = stamp * 31 + variant;
    stamp = stamp * 31 + onSecondary;
    stamp = stamp * 31 + surface;
    stamp = stamp * 31 + primary;
    stamp = stamp * 31 + accent;
    stamp = stamp * 31 + (Theme.isDark() ? 1 : 0);
    stamp = stamp * 31 + (XgramAmoled.isActive() ? 1 : 0);
    if (!force && stamp == colorStamp) {
      return;
    }
    colorStamp = stamp;

    nameView.setTextColor(onSurface);
    statusView.setTextColor(variant);
    brandText.setTextColor(variant);
    brandIcon.setColorFilter(Paints.getColorFilter(variant));
    keyChip.setTextColor(onSecondary);
    keyLock.setColorFilter(Paints.getColorFilter(onSecondary));
    keyTitle.setTextColor(onSurface);
    keyEmoji.setTextColor(onSurface);
    keyHint.setTextColor(variant);

    updateTint(accent);
    stage.color = haloColor;

    stage.invalidate();
    barsView.invalidate();
    brandChip.invalidate();
    keyChip.invalidate();
    keyCard.invalidate();
    muteButton.invalidate();
    speakerButton.invalidate();
    messageButton.invalidate();
    endButton.invalidate();
    closeButton.invalidate();
    redialButton.invalidate();
    swipe.invalidate();
  }

  /** A container tone in the contact's accent hue: the top of the gradient, and the halo a step stronger. */
  private void updateTint (int source) {
    boolean dark = Theme.isDark();
    if (tintValid && tintSource == source && tintDark == dark) {
      return;
    }
    tintValid = true;
    tintSource = source;
    tintDark = dark;
    androidx.core.graphics.ColorUtils.colorToM3HCT(source | 0xff000000, hct);
    float hue = hct[0];
    float chroma = Math.max(16f, Math.min(hct[1], 48f));
    heroTint = androidx.core.graphics.ColorUtils.M3HCTToColor(hue, chroma, dark ? 30f : 90f) | 0xff000000;
    haloColor = androidx.core.graphics.ColorUtils.M3HCTToColor(hue, chroma, dark ? 36f : 82f) | 0xff000000;
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    syncColors(false);
    super.dispatchDraw(canvas);
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight();
    final int base = XgramExpressive.color(XgramExpressive.SURFACE);
    c.drawColor(base);
    if (heroTint == 0 || height <= 0) {
      return;
    }
    float strength = XgramAmoled.isActive() ? .34f : Theme.isDark() ? .5f : .45f;
    int top = ColorUtils.fromToArgb(base, heroTint, strength);
    int gradientH = (int) (height * .66f);
    if (gradient == null || gradientTop != top || gradientBottom != base || gradientHeight != gradientH) {
      // Only when the theme, the contact or the size changes
      gradientTop = top;
      gradientBottom = base;
      gradientHeight = gradientH;
      gradient = new LinearGradient(0, 0, 0, gradientH, top, base, Shader.TileMode.CLAMP);
      gradientPaint.setShader(gradient);
    }
    c.drawRect(0, 0, width, gradientH, gradientPaint);
  }

  // Layout

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int height = MeasureSpec.getSize(heightMeasureSpec);
    boolean compact = height > 0 && height < Screen.dp(COMPACT_HEIGHT);
    if (this.compact != compact) {
      this.compact = compact;
      ViewGroup.LayoutParams stageLp = stage.getLayoutParams();
      stageLp.width = stageLp.height = Screen.dp(compact ? STAGE_COMPACT : STAGE);
      ViewGroup.LayoutParams avatarLp = avatarView.getLayoutParams();
      avatarLp.width = avatarLp.height = Screen.dp(compact ? AVATAR_COMPACT : AVATAR);
    }
    if (width > 0) {
      hero.getLayoutParams().width = Math.max(0, Math.min(width - Screen.dp(48f), Screen.dp(COLUMN_MAX)));
      keyCard.getLayoutParams().width = Math.max(0, Math.min(width - Screen.dp(48f), Screen.dp(CARD_MAX)));
      int controlsWidth = Math.max(0, Math.min(width - Screen.dp(32f), Screen.dp(CONTROLS_MAX)));
      controls.getLayoutParams().width = controlsWidth;
      swipe.getLayoutParams().width = Math.min(controlsWidth, Screen.dp(SWIPE_MAX));
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private static void layoutAt (View v, int x, int y) {
    v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    final int width = right - left, height = bottom - top;

    int barTop = topInset + Screen.dp(TOP_GAP);
    layoutAt(topBar, 0, barTop);
    int barBottom = barTop + topBar.getMeasuredHeight();

    final int controlsH = controls.getMeasuredHeight();
    final int controlsX = (width - controls.getMeasuredWidth()) / 2;
    int controlsTop, heroBottom;
    int lower = height - bottomInset;
    if (foldTop > barBottom && foldTop + controlsH <= lower) {
      // Tabletop: the hero above the fold, the controls centered below it
      controlsTop = foldTop + Math.max(Screen.dp(16f), (lower - foldTop - controlsH) / 2);
      heroBottom = foldTop - Screen.dp(8f);
    } else {
      controlsTop = lower - Screen.dp(CONTROLS_BOTTOM) - controlsH;
      heroBottom = controlsTop - Screen.dp(16f);
    }
    layoutAt(controls, controlsX, controlsTop);

    final int heroH = hero.getMeasuredHeight();
    int space = heroBottom - barBottom - heroH;
    int heroTop = barBottom + Math.max(Screen.dp(8f), (int) (space * .45f));
    if (space < Screen.dp(8f)) {
      heroTop = barBottom + Math.min(Screen.dp(8f), Math.max(0, space));
    }
    layoutAt(hero, (width - hero.getMeasuredWidth()) / 2, heroTop);

    int cardTop = heroTop + (heroH - keyCard.getMeasuredHeight()) / 2;
    cardTop = Math.max(barBottom + Screen.dp(8f), cardTop);
    layoutAt(keyCard, (width - keyCard.getMeasuredWidth()) / 2, cardTop);
  }

  // Name with the emoji status after it

  private void drawEmojiStatus (EmojiTextView view, Canvas c) {
    if (user == null || !user.isPremium) {
      return;
    }
    Layout layout = view.getLayout();
    if (layout == null || layout.getLineCount() == 0) {
      return;
    }
    int end = view.getPaddingLeft() + (int) layout.getLineRight(0) + Screen.dp(7f);
    int x = Math.min(end, view.getWidth() - view.getPaddingRight() + Screen.dp(7f));
    int y = (view.getHeight() - Screen.dp(25f)) / 2;
    emojiStatus.draw(c, x, y);
  }

  // Outlined chip (M3 assist chip: 1dp outlineVariant, full corners, no fill)

  private static final class OutlineChip extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int alpha = 255;

    OutlineChip () {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dp(1f));
    }

    @Override
    public void draw (@androidx.annotation.NonNull Canvas c) {
      float half = paint.getStrokeWidth() / 2f;
      rect.set(getBounds());
      rect.inset(half, half);
      if (rect.isEmpty()) {
        return;
      }
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT), alpha / 255f));
      float radius = rect.height() / 2f;
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha (int alpha) {
      if (this.alpha != alpha) {
        this.alpha = alpha;
        invalidateSelf();
      }
    }

    @Override
    public void setColorFilter (@Nullable android.graphics.ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }

  // Signal bars

  private static final class BarsView extends View {
    private static final int MAX = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int count = -1;
    private long lastUpdate;
    private final Runnable delayedInvalidate = () -> {
      lastUpdate = SystemClock.elapsedRealtime();
      invalidate();
    };

    BarsView (Context context) {
      super(context);
    }

    /** At most one redraw a second, as Telegram X's CallStrengthView. */
    void setCount (int count) {
      boolean changed = Math.max(this.count, 0) != Math.max(count, 0);
      this.count = count;
      if (!changed) {
        return;
      }
      long now = SystemClock.elapsedRealtime();
      removeCallbacks(delayedInvalidate);
      if (lastUpdate == 0L || now - lastUpdate >= 1000L) {
        lastUpdate = now;
        invalidate();
      } else {
        postDelayed(delayedInvalidate, 1000L - (now - lastUpdate));
      }
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      removeCallbacks(delayedInvalidate);
    }

    @Override
    protected void onDraw (Canvas c) {
      final float bar = Screen.dp(3f), gap = Screen.dp(1.5f);
      final float total = bar * MAX + gap * (MAX - 1);
      final float bottom = getHeight() / 2f + Screen.dp(6f);
      final float step = Screen.dp(3f);
      float x = (getWidth() - total) / 2f;
      final int on = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      final int off = XgramExpressive.withAlpha(on, XgramExpressive.DISABLED_CONTENT);
      for (int i = 0; i < MAX; i++) {
        rect.set(x, bottom - step * (i + 1) - Screen.dp(1f), x + bar, bottom);
        paint.setColor(count > i ? on : off);
        c.drawRoundRect(rect, bar / 2f, bar / 2f, paint);
        x += bar + gap;
      }
    }
  }

  // Avatar stage: the halo that morphs between a circle and a cookie while the call rings

  private static final class Stage extends FrameLayoutFix {
    private static final int FRAMES = 24;
    private static final long BEAT_MS = 1000L, TURN_MS = 14000L;
    private static Path[] frames;

    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final XgramExpressive.Spring visibility;
    private boolean ringing;
    private long startTime;
    @ColorInt int color;

    Stage (Context context) {
      super(context);
      setWillNotDraw(false);
      paint.setStyle(Paint.Style.FILL);
      visibility = new XgramExpressive.Spring(XgramExpressive.SPATIAL_SLOW, 0f, (s, value) -> invalidate());
    }

    void setRinging (boolean ringing, boolean animated) {
      if (this.ringing == ringing) {
        return;
      }
      this.ringing = ringing;
      if (ringing) {
        startTime = 0L;
      }
      if (animated) {
        visibility.animateTo(ringing ? 1f : 0f);
      } else {
        visibility.setValue(ringing ? 1f : 0f);
      }
      invalidate();
    }

    /** Unit paths from the circle to the 12-sided cookie, built once. */
    private static Path[] frames () {
      if (frames == null) {
        Path[] out = new Path[FRAMES];
        try {
          Morph morph = new Morph(XgramShapes.CIRCLE, XgramShapes.COOKIE_12);
          for (int i = 0; i < FRAMES; i++) {
            out[i] = XgramShapes.toPath(morph, (float) i / (FRAMES - 1), new Path());
          }
        } catch (Throwable t) {
          android.util.Log.e("XGram", "Call halo shapes failed", t);
          for (int i = 0; i < FRAMES; i++) {
            Path p = new Path();
            p.addCircle(.5f, .5f, .5f, Path.Direction.CW);
            out[i] = p;
          }
        }
        frames = out;
      }
      return frames;
    }

    private void drawShape (Canvas c, Path unit, float cx, float cy, float size, float rotation, int color) {
      if (size <= 0f || (color >>> 24) == 0) {
        return;
      }
      path.set(unit);
      XgramShapes.fit(path, cx, cy, size, rotation, matrix);
      paint.setColor(color);
      c.drawPath(path, paint);
    }

    @Override
    protected void onDraw (Canvas c) {
      final float v = visibility.getValue();
      if (v <= .001f || (color >>> 24) == 0) {
        return;
      }
      final boolean reduce = XgramMotion.reduce();
      final long now = SystemClock.uptimeMillis();
      if (startTime == 0L) {
        startTime = now;
      }
      final long t = reduce ? 0L : now - startTime;
      float morph;
      if (reduce) {
        morph = 1f;
      } else {
        long beat = t / BEAT_MS;
        float local = (float) (t % BEAT_MS) / 1000f;
        float p = XgramExpressive.springProgress(XgramExpressive.SPATIAL_SLOW, local);
        morph = beat % 2 == 0 ? p : 1f - p;
      }
      final float rotation = reduce ? 0f : 360f * (float) (t % TURN_MS) / TURN_MS;
      final Path[] f = frames();
      final int index = Math.round(MathUtils.clamp(morph) * (FRAMES - 1));
      final float alpha = MathUtils.clamp(v);
      final float grow = .86f + .14f * v;
      final float box = Math.min(getWidth(), getHeight());
      final float cx = getWidth() / 2f, cy = getHeight() / 2f;
      drawShape(c, f[index], cx, cy, box * grow * (.94f + .06f * morph), -rotation * .5f, XgramExpressive.withAlpha(color, alpha * .45f));
      drawShape(c, f[FRAMES - 1 - index], cx, cy, box * grow * (.84f + .04f * morph), rotation, XgramExpressive.withAlpha(color, alpha));
      if (ringing && !reduce) {
        postInvalidateOnAnimation();
      }
    }
  }
}
