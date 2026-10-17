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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.graphics.shapes.Morph;

import com.fahadali.xgram.call.XgramCallVideo;
import com.fahadali.xgram.call.XgramCallVideoLayer;
import com.fahadali.xgram.call.XgramVideoMediaAware;

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
 * XGram M3E: the call screen (CallController with Expressive design on), after Google Phone's Material 3 Expressive
 * in-call screen. Voice and video share one layout.
 * <ul>
 * <li>Top bar: an M3 small app bar row: a 48dp minimise icon button, a tonal 36dp "Telegram call" chip
 * (surfaceContainerHigh a step toward the halo's hue: the Telegram glyph in primary, the call's state, then the
 * encryption lock and "End-to-end encrypted" once connected, and the signal bars, its width springing as they come
 * and go, dropping the label, the lock and the bars in turn when the row gets too narrow) and the encryption key emoji
 * in a tonal secondaryContainer chip of the same height that opens a key card (a floating surfaceContainerHigh card,
 * corner 28dp) with the big emoji and the check hint. While your camera is on, an audio output icon button comes in
 * before the key chip (Flip takes the speaker's place in the row).</li>
 * <li>Hero: the avatar (128dp, 120dp on short windows) on a halo that morphs between a circle and a 12-sided cookie
 * and turns while the call rings or connects (static with Reduce motion), the name in headlineMedium and the status in
 * titleMedium onSurfaceVariant, in sentence case, with the loading indicator while it is in progress.</li>
 * <li>Controls: toggle icon buttons with labels (mute, speaker or Bluetooth / flip, video, message), 72, 64 or 56dp so
 * four fit on the 360dp cover, then the action area: the 96 x 64dp end call pill, the incoming swipe pill (with a
 * "Video" toggle above it for an incoming video call: answer with video or without), or close and call again after a
 * failed call.</li>
 * <li>Background: surface with a soft gradient from a container tone in the contact's accent hue at the top.</li>
 * <li>Video (XgramCallVideoLayer): your camera or the other side's video fills the stage behind everything; the hero
 * shrinks into a compact header under the top bar, the text turns white over scrims, the controls sink 48dp, and your
 * camera goes to a draggable tile (XgramM3CallVideoTile) once theirs shows. Over their video the chrome hides after a
 * few seconds (never with TalkBack); a tap on the stage brings it back.</li>
 * </ul>
 * Every state change springs (XgramExpressive.Spring, snapping with Reduce motion); colors are read from the M3 roles
 * at draw time, so light, dark and Pure black all apply, and a theme switch repaints through {@link #invalidate()}.
 * Tabletop posture keeps the hero (and the video) above the fold and the controls below it. Nothing is laid out per
 * frame. Main thread only.
 */
public final class XgramM3CallScreen extends FrameLayoutFix implements XgramCallVideo.Listener, XgramCallVideoLayer.Listener, XgramM3CallVideoTile.Callback {
  public interface Callback {
    void onMuteClick ();
    void onSpeakerClick ();
    void onMessageClick ();
    /** The branding chip (debug builds: the call log). */
    void onBrandClick ();
    /** The minimise icon button of the top bar: back to the chats, the call goes on. */
    void onMinimize ();
    /** False when the call cannot be answered yet (a permission is being asked). */
    boolean onAnswer ();
    void onDecline (boolean isHangUp);
    void onRedial ();
    void onDismiss ();
    /** The Video toggle. */
    void onVideoClick ();
    /** Flip (the front or the rear camera). */
    void onFlipClick ();
    /** The audio output icon button of the top bar (while Flip has the speaker's place). */
    void onAudioRouteClick ();
    /** The answer-with-video toggle of an incoming video call. */
    void onAnswerVideoChanged (boolean on);
    /** Video shows on the stage, or not (the status bar icons turn white over it). */
    void onVideoPresentationChanged (boolean video);
  }

  // Geometry (dp)

  private static final float TOP_GAP = 8f, TOP_BAR = 48f, SIDE = 16f, BAR_EDGE = 4f, KEY_CHIP_H = 36f, BAR_CHIP_GAP = 8f;
  private static final float STAGE = 184f, AVATAR = 128f, STAGE_COMPACT = 160f, AVATAR_COMPACT = 120f;
  private static final float COMPACT_HEIGHT = 720f;
  private static final float NAME_GAP = 20f, STATUS_GAP = 6f, COLUMN_MAX = 560f, CARD_MAX = 400f;
  private static final float CONTROLS_MAX = 400f, SWIPE_MAX = 360f, ACTION_GAP = 28f, ACTION_AREA = 112f;
  private static final float CONTROLS_BOTTOM = 24f, CONTROLS_ENTER = 24f;
  private static final float SLOT_MAX = 96f, SLOT_REGULAR = 88f, SLOT_COMPACT = 72f;
  private static final float ROUTE_GAP = 4f;

  // Video (dp, factors)

  private static final float HEADER_GAP = 4f, VIDEO_HUG = 48f, CHROME_SHIFT = 16f, SCRIM_REACH = 48f;
  private static final float TILE_EDGE = 16f, TILE_GAP = 12f, TILE_MIN = 96f, TILE_MAX = 144f, TILE_FRACTION = .28f;
  /** headlineMedium 28 to a visual titleLarge 22, titleMedium 16 to 14. */
  private static final float NAME_SHRINK = .2143f, STATUS_SHRINK = .125f;
  private static final long CHROME_TIMEOUT_MS = 4000L;
  private static final int WHITE = 0xffffffff, WHITE_80 = 0xccffffff;

  private static final int MODE_NONE = 0, MODE_SWIPE = 1, MODE_END = 2, MODE_RETRY = 3, MODE_DECLINE = 4;

  private final Tdlib tdlib;
  private final Callback callback;

  // Views

  private final TopBar topBar;
  private final ImageView backButton, routeButton;
  private final BrandChip brandChip;
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
  private final FrameLayoutFix toggleSlot, speakerSlot;
  private final LinearLayout toggleRow;
  private final XgramM3CallVideoToggle muteButton, speakerButton, flipButton, videoButton, messageButton, answerVideoButton;
  private final FrameLayoutFix actionArea;
  private final XgramM3CallButton endButton;
  private final XgramM3CallSwipe swipe;
  private final @Nullable XgramVideoMediaAware swipeMedia;
  private final LinearLayout retryRow;
  private final XgramM3CallButton closeButton, redialButton;

  // Video views (made with the first call that can do video)

  private @Nullable XgramCallVideoLayer layer;
  private @Nullable XgramM3CallVideoTile tile;

  // Motion

  private final Fade toggleFade, endFade, swipeFade, retryFade, speakerFade, flipFade, answerVideoFade, routeFade;
  private final XgramExpressive.Spring keyShown, keyExpanded;
  /** v: the hero's compaction; m: colors, background and scrims; hug: the controls sinking; chrome: auto-hide. */
  private final XgramExpressive.Spring videoSpatial, videoEffects, hug, chrome, chromeShift;

  // State

  private int topInset, bottomInset, foldTop = -1;
  private boolean compact;
  private float enterFactor;
  private int mode = MODE_NONE;
  private int callId, callState;
  private boolean callOutgoing, callIsVideo;
  private @Nullable TdApi.User user;
  private @Nullable TdlibAccentColor accentColor;
  private boolean hasKey;
  private String statusText;
  private boolean ringing;

  // Video state

  private @Nullable XgramCallVideo video;
  private boolean videoAvailable;
  private @Nullable Boolean peerSupportsVideo;
  private boolean answerWithVideo = true;
  private int presentation = XgramCallVideoLayer.PRESENT_VOICE;
  private boolean mediaDark;
  private boolean flipPending;
  private boolean routeBluetooth, routeOn;
  private boolean connected, debugShown, chromeHidden;
  private int tier = XgramM3CallVideoToggle.TIER_REGULAR, slotPx;

  // Geometry from the last layout (pixels)

  private boolean tabletop;
  private int barBottomPx, controlsTopPx, heroDy, headerBottom, stageTop, stageBottom;
  private int sideLeft, sideRight;
  private final int[] location = new int[2];
  private final Rect tileBounds = new Rect();

  // Stage taps
  private final int touchSlop;
  private boolean stageTap;
  private float stageDownX, stageDownY;

  private final Runnable hideChrome = () -> setChromeShown(false, true);

  // Colors

  private int colorStamp;
  private @ColorInt int heroTint, haloColor;
  private int tintSource;
  private boolean tintDark, tintValid;
  private final float[] hct = new float[3];

  private final Paint gradientPaint = new Paint();
  private final Paint stagePaint = new Paint();
  private @Nullable LinearGradient gradient;
  private int gradientTop, gradientBottom, gradientHeight;

  public XgramM3CallScreen (Context context, Tdlib tdlib, Callback callback) {
    super(context);
    this.tdlib = tdlib;
    this.callback = callback;
    this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    setWillNotDraw(false);
    stagePaint.setStyle(Paint.Style.FILL);

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

    // Top bar: an M3 small app bar row. The minimise icon button (48dp, 4dp from the edge, so its icon sits 16dp in),
    // the tonal brand chip after it at 56dp, and the key chip at the end; TopBar keeps the brand chip clear of it.

    final boolean rtl = Lang.rtl();
    topBar = new TopBar(context);
    topBar.setPadding(Screen.dp(rtl ? SIDE : BAR_EDGE), 0, Screen.dp(rtl ? BAR_EDGE : SIDE), 0);
    topBar.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(TOP_BAR), Gravity.TOP));
    topBar.setAlpha(0f);

    backButton = new ImageView(context);
    backButton.setScaleType(ImageView.ScaleType.CENTER);
    backButton.setImageResource(XgramIconPack.map(R.drawable.baseline_arrow_back_24));
    if (rtl) {
      backButton.setScaleX(-1f); // the arrow points to the start
    }
    backButton.setContentDescription(Lang.getString(R.string.XgramR6B2CallMinimize));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      backButton.setTooltipText(Lang.getString(R.string.XgramR6B2CallMinimize));
    }
    backButton.setOnClickListener(v -> callback.onMinimize());
    XgramAppMotion.iconButton(backButton, XgramExpressive.ON_SURFACE);
    backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(TOP_BAR), Screen.dp(TOP_BAR), (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
    topBar.addView(backButton);

    brandChip = new BrandChip(context);
    FrameLayout.LayoutParams brandLp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(TOP_BAR), (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
    if (rtl) {
      brandLp.rightMargin = Screen.dp(TOP_BAR + BAR_EDGE);
    } else {
      brandLp.leftMargin = Screen.dp(TOP_BAR + BAR_EDGE);
    }
    brandChip.setLayoutParams(brandLp);
    brandChip.setLabel(Lang.getString(R.string.XgramM3CallBranding), false);
    topBar.addView(brandChip);

    // The audio output, while Flip has the speaker's place in the row (TopBar places it before the key chip)
    routeButton = new ImageView(context);
    routeButton.setScaleType(ImageView.ScaleType.CENTER);
    routeButton.setId(R.id.btn_callRoute);
    routeButton.setOnClickListener(v -> callback.onAudioRouteClick());
    XgramAppMotion.iconButton(routeButton, XgramExpressive.ON_SURFACE);
    routeButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(TOP_BAR), Screen.dp(TOP_BAR), (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
    topBar.addView(routeButton);

    keyChip = new EmojiTextView(context);
    keyChip.setScrollDisabled(true);
    keyChip.setSingleLine(true);
    keyChip.setGravity(Gravity.CENTER);
    keyChip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f);
    keyChip.setPadding(Screen.dp(12f), 0, Screen.dp(14f), 0);
    keyChip.setCompoundDrawablePadding(Screen.dp(6f));
    keyLock = Drawables.get(XgramIconPack.map(R.drawable.baseline_lock_16));
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

    controls = new LinearLayout(context) {
      @Override
      public boolean dispatchTouchEvent (MotionEvent ev) {
        // Hidden over video: the touch goes to the stage (a tap there brings the chrome back)
        return (!chromeHidden || ev.getActionMasked() != MotionEvent.ACTION_DOWN) && super.dispatchTouchEvent(ev);
      }
    };
    controls.setOrientation(LinearLayout.VERTICAL);
    controls.setGravity(Gravity.CENTER_HORIZONTAL);
    controls.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(CONTROLS_MAX), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
    controls.setAlpha(0f);

    // The toggle row: [Mute] [Speaker | Flip] [Video] [Message]
    toggleRow = new LinearLayout(context);
    toggleRow.setOrientation(LinearLayout.HORIZONTAL);
    toggleRow.setGravity(Gravity.CENTER_HORIZONTAL);
    muteButton = newToggle(context, R.drawable.baseline_mic_24, R.string.XgramM3CallMute, v -> callback.onMuteClick());
    muteButton.setCrossMode(XgramM3CallVideoToggle.CROSS_WHEN_CHECKED);
    muteButton.setId(R.id.btn_mute);

    speakerSlot = new FrameLayoutFix(context);
    speakerSlot.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(SLOT_MAX), ViewGroup.LayoutParams.WRAP_CONTENT));
    speakerButton = newSlotToggle(context, R.drawable.baseline_volume_up_24, R.string.XgramM3CallSpeaker, v -> callback.onSpeakerClick());
    speakerButton.setId(R.id.btn_speaker);
    flipButton = newSlotToggle(context, R.drawable.baseline_camera_rear_24, R.string.XgramVideoFlip, v -> onFlipClick());
    flipButton.setId(R.id.btn_flipCamera);
    flipButton.setContentDescription(Lang.getString(R.string.XgramVideoFlipToRear));
    speakerSlot.addView(speakerButton);
    speakerSlot.addView(flipButton);

    videoButton = newToggle(context, R.drawable.baseline_videocam_24, R.string.Video, v -> callback.onVideoClick());
    videoButton.setCrossMode(XgramM3CallVideoToggle.CROSS_WHEN_UNCHECKED);
    videoButton.setId(R.id.btn_video);
    videoButton.setVisibility(View.GONE); // until setVideoAvailable

    messageButton = newToggle(context, R.drawable.baseline_chat_bubble_24, R.string.XgramM3CallMessage, v -> callback.onMessageClick());
    messageButton.setId(R.id.btn_openChat);
    toggleRow.addView(muteButton);
    toggleRow.addView(speakerSlot);
    toggleRow.addView(videoButton);
    toggleRow.addView(messageButton);

    // The single "Video" toggle of an incoming video call sits where the row will be
    answerVideoButton = new XgramM3CallVideoToggle(context);
    answerVideoButton.setIcon(R.drawable.baseline_videocam_24);
    answerVideoButton.setLabel(Lang.getString(R.string.Video));
    answerVideoButton.setCrossMode(XgramM3CallVideoToggle.CROSS_WHEN_UNCHECKED);
    answerVideoButton.setId(R.id.btn_xgramAnswerVideo);
    answerVideoButton.setChecked(true, false);
    answerVideoButton.setOnClickListener(v -> onAnswerVideoClick());
    answerVideoButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(SLOT_MAX), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

    toggleSlot = new FrameLayoutFix(context);
    toggleSlot.addView(toggleRow, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    toggleSlot.addView(answerVideoButton);
    controls.addView(toggleSlot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
    // The pill follows video (XgramVideoMediaAware): a white hint over it, the camera as the answer glyph
    swipeMedia = swipe;

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

    // Above the video tile for touches and drawing (ViewGroup sorts both by Z once a child has one); no background,
    // so no outline and no shadow
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      final float z = Screen.dp(4f);
      dismissCatcher.setTranslationZ(z);
      topBar.setTranslationZ(z);
      controls.setTranslationZ(z);
    }

    toggleFade = new Fade(toggleRow, .9f, false);
    endFade = new Fade(endButton, .7f, false);
    swipeFade = new Fade(swipe, .9f, false);
    retryFade = new Fade(retryRow, .8f, false);
    speakerFade = new Fade(speakerButton, .9f, false);
    flipFade = new Fade(flipButton, .9f, false);
    answerVideoFade = new Fade(answerVideoButton, .9f, false);
    routeFade = new Fade(routeButton, .8f, false);
    speakerFade.set(true, false);
    setStateDescription(answerVideoButton, R.string.XgramVideoAnswerWith, true);
    keyShown = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> updateKeyViews());
    keyShown.setEndListener(this::updateKeyViews);
    keyExpanded = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> updateKeyViews());
    keyExpanded.setEndListener(this::updateKeyViews);

    videoSpatial = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyVideoFactors());
    videoEffects = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> applyVideoFactors());
    hug = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyControlsTransform());
    chrome = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 1f, (s, value) -> applyChrome());
    chromeShift = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 1f, (s, value) -> applyChrome());

    syncColors(true);
  }

  private XgramM3CallVideoToggle newToggle (Context context, @DrawableRes int icon, int label, View.OnClickListener listener) {
    XgramM3CallVideoToggle button = new XgramM3CallVideoToggle(context);
    button.setIcon(icon);
    button.setLabel(Lang.getString(label));
    button.setOnClickListener(listener);
    button.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(SLOT_MAX), ViewGroup.LayoutParams.WRAP_CONTENT));
    return button;
  }

  /** A toggle filling the Speaker / Flip slot. */
  private XgramM3CallVideoToggle newSlotToggle (Context context, @DrawableRes int icon, int label, View.OnClickListener listener) {
    XgramM3CallVideoToggle button = new XgramM3CallVideoToggle(context);
    button.setIcon(icon);
    button.setLabel(Lang.getString(label));
    button.setOnClickListener(listener);
    button.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
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
      brandChip.setOnClickListener(v -> callback.onBrandClick()); // the chip draws its own pressed state layer
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
    // The status follows the name: on its left in RTL (as UserView draws it)
    final boolean rtl = org.thunderdog.challegram.core.Lang.rtl();
    nameView.setPadding(rtl ? statusWidth : 0, 0, rtl ? 0 : statusWidth, 0);
    final String name = user != null ? TD.getUserName(user) : null;
    if (layer != null) {
      layer.setPeerName(name);
    }
    if (tile != null) {
      tile.setPeerName(name);
    }
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
    final boolean changed = keyShown.getTarget() != (visible ? 1f : 0f);
    springTo(keyShown, visible ? 1f : 0f, animated);
    updateKeyViews();
    if (changed) {
      topBar.requestLayout(); // the brand chip makes room for the key chip (or takes it back), once per change
      if (visible) {
        setChromeShown(true, animated);
      }
    }
  }

  public void setKeyExpanded (boolean expanded) {
    if (expanded && keyShown.getTarget() != 1f) {
      return;
    }
    if (expanded) {
      keyCard.setVisibility(View.VISIBLE);
      setChromeShown(true, isLaidOutCompat());
    }
    springTo(keyExpanded, expanded ? 1f : 0f, isLaidOutCompat());
    updateKeyViews();
    scheduleChromeHide();
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

    applyHero();
    if (tile != null) {
      tile.setDim(expanded); // the tile fades under the card and takes no taps
    }
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

  /** The halo pulse (not over video: the halo comes back with the voice hero) and the incoming pill's motion. */
  public void setRinging (boolean ringing, boolean animated) {
    this.ringing = ringing;
    stage.setRinging(ringing && presentation == XgramCallVideoLayer.PRESENT_VOICE, animated);
    swipe.setActive(ringing);
  }

  public void setTogglesVisible (boolean visible, boolean animated) {
    toggleFade.set(visible, animated);
    updateRoute(animated);
  }

  public void setMuted (boolean muted, boolean animated) {
    muteButton.setChecked(muted, animated);
  }

  /** Speaker (on when the audio leaves the earpiece) or Bluetooth while the call is routed to a headset. */
  public void setAudioRoute (boolean bluetooth, boolean on, boolean animated) {
    speakerButton.setIcon(bluetooth ? R.drawable.baseline_bluetooth_24 : R.drawable.baseline_volume_up_24);
    speakerButton.setLabel(Lang.getString(bluetooth ? R.string.XgramM3CallBluetooth : R.string.XgramM3CallSpeaker));
    speakerButton.setChecked(on, animated);
    // The audio output icon button of the top bar (video): Bluetooth, the speaker, or the phone's earpiece
    this.routeBluetooth = bluetooth;
    this.routeOn = on;
    final int icon = bluetooth ? R.drawable.baseline_bluetooth_24 : on ? R.drawable.baseline_volume_up_24 : R.drawable.baseline_phone_in_talk_24;
    routeButton.setImageResource(XgramIconPack.map(icon));
    final String output = Lang.getString(R.string.XgramVideoAudioOutput, Lang.getString(bluetooth ? R.string.XgramM3CallBluetooth : on ? R.string.XgramM3CallSpeaker : R.string.XgramVideoRoutePhone));
    routeButton.setContentDescription(output);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      routeButton.setTooltipText(output);
    }
  }

  public void setCallBars (int count, boolean visible, boolean animated) {
    brandChip.setBars(count, visible, animated && isLaidOutCompat());
  }

  /** Popup open and close progress: the chips fade in and the controls rise into place. */
  public void setEnterFactor (float factor) {
    this.enterFactor = factor;
    applyChrome();
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
    this.callIsVideo = call.isVideo;
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
      // Each new incoming video call starts answering with video
      setAnswerWithVideo(true, false, false);
    }
    if (this.callId != 0 && this.callId != call.id) {
      hasKey = false; // a replaced call has its own key (CallController.updateEmoji sets it once it is ready)
    }
    final boolean stateChanged = this.callState != state || this.callId != call.id;
    this.callId = call.id;
    this.callState = state;
    this.callOutgoing = call.isOutgoing;
    // The brand chip tells the call's encryption state once it is connected (on the cover screen the key chip then
    // takes the room and the chip keeps the glyph and the bars)
    updateBrandLabel(animated);
    brandChip.setEncrypted(state == TdApi.CallStateReady.CONSTRUCTOR, animated && isLaidOutCompat());
    if (this.mode != newMode) {
      this.mode = newMode;
      endButton.setDescription(Lang.getString(newMode == MODE_DECLINE ? R.string.XgramM3CallDecline : R.string.XgramM3CallEnd));
      endFade.set(newMode == MODE_END || newMode == MODE_DECLINE, animated);
      swipeFade.set(newMode == MODE_SWIPE, animated);
      retryFade.set(newMode == MODE_RETRY, animated);
      updateHug(animated);
      updateObstruction();
    }
    answerVideoFade.set(newMode == MODE_SWIPE && call.isVideo && videoAvailable, animated);
    updateVideoControls(animated);
    if (stateChanged) {
      setChromeShown(true, animated); // any change of the call brings the chrome back
    }
  }

  private void updateBrandLabel (boolean animated) {
    final int label;
    if (callState == TdApi.CallStateReady.CONSTRUCTOR) {
      label = R.string.XgramR6B2CallEncrypted;
    } else if (videoAvailable && (callIsVideo || (video != null && video.wasVideoUsed()))) {
      label = R.string.XgramVideoCallBranding;
    } else {
      label = R.string.XgramM3CallBranding;
    }
    brandChip.setLabel(Lang.getString(label), animated && isLaidOutCompat());
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

  // Video (CallController)

  /** Whether this device can do video: the row gets its fourth toggle (it never re-lays out mid-call). */
  public void setVideoAvailable (boolean available) {
    if (this.videoAvailable != available) {
      this.videoAvailable = available;
      videoButton.setVisibility(available ? View.VISIBLE : View.GONE);
      requestLayout();
    }
  }

  /** The call's video (null: none, the screen goes back to voice at once, as for a replaced call). */
  public void setVideo (@Nullable XgramCallVideo video) {
    if (this.video == video) {
      updateVideoControls(isLaidOutCompat());
      return;
    }
    if (this.video != null) {
      this.video.removeListener(this);
    }
    this.video = video;
    flipPending = false;
    if (video != null) {
      ensureVideoViews();
      video.addListener(this);
    }
    if (layer != null) {
      layer.setVideo(video, video != null && isLaidOutCompat());
    }
    updateVideoControls(false);
    updateBrandLabel(false);
  }

  private void ensureVideoViews () {
    final String name = user != null ? TD.getUserName(user) : null;
    if (layer == null) {
      layer = new XgramCallVideoLayer(getContext(), XgramCallVideoLayer.STYLE_EXPRESSIVE, this);
      layer.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      layer.setPeerName(name);
      addView(layer, 0);
    }
    if (tile == null) {
      tile = new XgramM3CallVideoTile(getContext(), true, this);
      tile.setPeerName(name);
      addView(tile, indexOfChild(hero) + 1);
      layer.setTile(tile);
    }
  }

  /** Whether the other side's app can do video (null: not known yet): the Video toggle looks disabled when it cannot. */
  public void setPeerSupportsVideo (@Nullable Boolean supports) {
    this.peerSupportsVideo = supports;
    updateVideoControls(isLaidOutCompat());
  }

  /** The incoming video call is answered with video (the toggle above the swipe pill). */
  public boolean isAnswerWithVideo () {
    return answerWithVideo;
  }

  /** Connected (ready, the duration counting): the chrome may hide over video. */
  public void setConnected (boolean connected) {
    if (this.connected != connected) {
      this.connected = connected;
      scheduleChromeHide();
    }
  }

  /** The call log overlay of debug builds is open (the chrome stays). */
  public void setDebugShown (boolean shown) {
    if (this.debugShown != shown) {
      this.debugShown = shown;
      scheduleChromeHide();
    }
  }

  private void onAnswerVideoClick () {
    setAnswerWithVideo(!answerWithVideo, true, true);
  }

  private void setAnswerWithVideo (boolean on, boolean animated, boolean notify) {
    final boolean changed = answerWithVideo != on;
    answerWithVideo = on;
    answerVideoButton.setChecked(on, animated);
    setStateDescription(answerVideoButton, R.string.XgramVideoAnswerWith, on);
    if (swipeMedia != null) {
      swipeMedia.setVideoAnswer(on && callIsVideo && videoAvailable);
    }
    if (notify && changed) {
      callback.onAnswerVideoChanged(on);
    }
  }

  private void onFlipClick () {
    final XgramCallVideo v = video;
    if (v != null && v.isCapturing()) {
      flipPending = true;
      flipButton.spinTo(v.isFront() ? R.drawable.baseline_camera_front_24 : R.drawable.baseline_camera_rear_24);
    }
    callback.onFlipClick();
  }

  private static @DrawableRes int flipIcon (boolean front) {
    // The camera it switches to (CameraControlButton's convention)
    return front ? R.drawable.baseline_camera_rear_24 : R.drawable.baseline_camera_front_24;
  }

  private static void setStateDescription (View view, int label, boolean on) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      view.setContentDescription(Lang.getString(label));
      view.setStateDescription(Lang.getString(on ? R.string.XgramVideoOn : R.string.XgramVideoOff));
    } else {
      view.setContentDescription(Lang.getString(R.string.XgramVideoStateDesc, Lang.getString(label), Lang.getString(on ? R.string.XgramVideoOn : R.string.XgramVideoOff)));
    }
  }

  private boolean isCameraOn () {
    return video != null && !video.isReleased() && video.isCameraOn();
  }

  /** The Video toggle, the Speaker / Flip slot and the audio output button. */
  private void updateVideoControls (boolean animated) {
    final boolean cameraOn = isCameraOn();
    videoButton.setChecked(cameraOn, animated);
    setStateDescription(videoButton, R.string.Video, cameraOn);
    videoButton.setDisabledLook(Boolean.FALSE.equals(peerSupportsVideo) && !callIsVideo);
    speakerFade.set(!cameraOn, animated);
    flipFade.set(cameraOn, animated);
    syncSlotTouchables();
    if (video != null && !flipPending) {
      flipButton.setIcon(flipIcon(video.isFront()));
    }
    if (video != null) {
      flipButton.setContentDescription(Lang.getString(video.isFront() ? R.string.XgramVideoFlipToRear : R.string.XgramVideoFlipToFront));
    }
    updateRoute(animated);
  }

  private void updateRoute (boolean animated) {
    final boolean on = isCameraOn() && toggleFade.isShown();
    if (routeFade.isShown() != on) {
      routeFade.set(on, animated);
      topBar.requestLayout(); // the brand chip makes room, once per change
    }
  }

  /** The Speaker and Flip buttons take taps only while their own fade and the row are in. */
  private void syncSlotTouchables () {
    final boolean row = toggleFade.isShown();
    speakerButton.setTouchable(row && speakerFade.isShown());
    flipButton.setTouchable(row && flipFade.isShown());
  }

  // XgramCallVideo.Listener

  @Override
  public void onCallVideoChanged (XgramCallVideo v) {
    if (v != video) {
      return;
    }
    if (v.isReleased()) {
      setVideo(null);
      return;
    }
    updateVideoControls(isLaidOutCompat());
    updateBrandLabel(isLaidOutCompat());
    if (layer != null) {
      layer.update(isLaidOutCompat());
    }
  }

  @Override
  public void onCameraSwitched (XgramCallVideo v, boolean front, boolean ok) {
    if (v != video) {
      return;
    }
    flipPending = false;
    flipButton.spinTo(flipIcon(front)); // done: the final icon; failed: back to the old one
    flipButton.setContentDescription(Lang.getString(front ? R.string.XgramVideoFlipToRear : R.string.XgramVideoFlipToFront));
    if (ok) {
      flipButton.announceForAccessibility(Lang.getString(front ? R.string.XgramVideoFrontCamera : R.string.XgramVideoRearCamera));
    }
  }

  // XgramCallVideoLayer.Listener

  @Override
  public void onPresentationChanged (int presentation, boolean animated) {
    this.presentation = presentation;
    final boolean video = presentation != XgramCallVideoLayer.PRESENT_VOICE;
    animated = animated && isLaidOutCompat();
    springTo(videoSpatial, video ? 1f : 0f, animated);
    springTo(videoEffects, video ? 1f : 0f, animated);
    if (!animated) {
      applyVideoFactors();
    }
    stage.setRinging(ringing && !video, animated);
    updateHug(animated);
    setKeepScreenOn(video);
    if (presentation != XgramCallVideoLayer.PRESENT_REMOTE) {
      setChromeShown(true, animated);
    }
    scheduleChromeHide();
    updateObstruction();
    callback.onVideoPresentationChanged(video);
  }

  // XgramM3CallVideoTile.Callback

  @Override
  public void onTileTap () {
    if (layer != null) {
      layer.toggleSwap();
    }
    scheduleChromeHide();
  }

  @Override
  public void onTileDragging (boolean dragging) {
    scheduleChromeHide();
  }

  // Video presentation: the hero, the colors and the background

  private boolean isVideoShown () {
    return presentation != XgramCallVideoLayer.PRESENT_VOICE;
  }

  /** v (SPATIAL_DEFAULT): the hero shrinks under the top bar; m (EFFECTS_DEFAULT): white text over black and scrims. */
  private void applyVideoFactors () {
    final float v = videoSpatial.getValue();
    final float m = MathUtils.clamp(videoEffects.getValue());

    applyHero();
    stage.setAlpha(MathUtils.clamp(1f - 2f * v));
    final float stageScale = 1f - .2f * v;
    stage.setScaleX(stageScale);
    stage.setScaleY(stageScale);
    final float nameScale = 1f - NAME_SHRINK * v;
    nameView.setScaleX(nameScale);
    nameView.setScaleY(nameScale);
    final float statusScale = 1f - STATUS_SHRINK * v;
    statusView.setScaleX(statusScale);
    statusView.setScaleY(statusScale);
    statusView.setTranslationY(-nameView.getMeasuredHeight() * NAME_SHRINK * v);

    // Fade-through: the text goes out and comes back in white (no color objects per frame)
    final float textAlpha = Math.abs(1f - 2f * m);
    nameView.setAlpha(textAlpha);
    statusView.setAlpha(textAlpha);
    final int imageAlpha = Math.round(255f * textAlpha);
    backButton.setImageAlpha(imageAlpha);
    routeButton.setImageAlpha(imageAlpha);
    final boolean dark = m >= .5f;
    if (dark != mediaDark) {
      mediaDark = dark;
      syncColors(true); // once per crossing
    }

    final float media = tabletop ? 0f : m; // in tabletop the controls stay on the surface below the fold
    muteButton.setMediaFactor(media);
    speakerButton.setMediaFactor(media);
    flipButton.setMediaFactor(media);
    videoButton.setMediaFactor(media);
    messageButton.setMediaFactor(media);
    answerVideoButton.setMediaFactor(media);
    if (swipeMedia != null) {
      swipeMedia.setVideoMediaFactor(media);
    }
    updateScrims();
    invalidate(); // the black stage
  }

  private void applyHero () {
    final float expanded = MathUtils.clamp(keyExpanded.getValue());
    hero.setAlpha((1f - expanded) * MathUtils.clamp(chrome.getValue()));
    hero.setTranslationY(heroDy * videoSpatial.getValue() - Screen.dp(CHROME_SHIFT) * (1f - chromeShift.getValue()));
  }

  private void applyControlsTransform () {
    controls.setAlpha(enterFactor * MathUtils.clamp(chrome.getValue()));
    controls.setTranslationY((1f - enterFactor) * Screen.dp(CONTROLS_ENTER) + Screen.dp(VIDEO_HUG) * hug.getValue() + (1f - chromeShift.getValue()) * Screen.dp(CHROME_SHIFT));
  }

  private void applyChrome () {
    final float shown = MathUtils.clamp(chrome.getValue());
    topBar.setAlpha(enterFactor * shown);
    topBar.setTranslationY(-Screen.dp(CHROME_SHIFT) * (1f - chromeShift.getValue()));
    applyControlsTransform();
    applyHero();
    updateScrims();
  }

  /** The controls sink 48dp over video (not in tabletop, not with the swipe pill or the retry row). */
  private void updateHug (boolean animated) {
    final float target = isVideoShown() && mode == MODE_END && !tabletop ? 1f : 0f;
    if (hug.getTarget() != target) {
      springTo(hug, target, animated && isLaidOutCompat());
      if (!animated || !isLaidOutCompat()) {
        applyControlsTransform();
      }
      updateVideoGeometry();
      updateObstruction();
    }
  }

  private int visualControlsTop () {
    return controlsTopPx + Math.round(Screen.dp(VIDEO_HUG) * hug.getTarget());
  }

  private void updateScrims () {
    if (layer == null) {
      return;
    }
    final float m = MathUtils.clamp(videoEffects.getValue());
    final float shown = MathUtils.clamp(chrome.getValue());
    final int topEnd = headerBottom + Screen.dp(SCRIM_REACH);
    final int bottomStart = tabletop ? -1 : visualControlsTop() - Screen.dp(SCRIM_REACH);
    // The top keeps 20% with the chrome hidden, for the status bar icons
    layer.setScrims(topEnd, m * (.5f + .5f * shown), bottomStart, tabletop ? 0f : m * shown);
  }

  // Chrome auto-hide over the other side's video

  private boolean touchExploration () {
    AccessibilityManager manager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
  }

  private boolean canAutoHide () {
    return presentation == XgramCallVideoLayer.PRESENT_REMOTE && connected && !isKeyExpanded() &&
      (tile == null || !tile.isDragging()) && !debugShown && !touchExploration();
  }

  private long chromeTimeout () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      AccessibilityManager manager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
      if (manager != null) {
        return manager.getRecommendedTimeoutMillis((int) CHROME_TIMEOUT_MS,
          AccessibilityManager.FLAG_CONTENT_CONTROLS | AccessibilityManager.FLAG_CONTENT_ICONS | AccessibilityManager.FLAG_CONTENT_TEXT);
      }
    }
    return CHROME_TIMEOUT_MS;
  }

  /** (Re)starts the auto-hide timer, or brings the chrome back when it may not hide. */
  private void scheduleChromeHide () {
    removeCallbacks(hideChrome);
    if (!canAutoHide()) {
      setChromeShown(true, isLaidOutCompat());
      return;
    }
    if (!chromeHidden) {
      postDelayed(hideChrome, chromeTimeout());
    }
  }

  private void setChromeShown (boolean shown, boolean animated) {
    if (chromeHidden == !shown) {
      return;
    }
    if (!shown && !canAutoHide()) {
      return;
    }
    chromeHidden = !shown;
    animated = animated && isLaidOutCompat();
    springTo(chrome, shown ? 1f : 0f, animated);
    springTo(chromeShift, shown ? 1f : 0f, animated);
    if (!animated) {
      applyChrome();
    }
    // Untouchable the moment hiding starts (TopBar and controls refuse the touch); TalkBack skips them meanwhile
    final int importance = shown ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
    topBar.setImportantForAccessibility(importance);
    controls.setImportantForAccessibility(importance);
    if (shown) {
      removeCallbacks(hideChrome);
    }
    updateVideoGeometry(); // the tile may use the whole stage while the chrome is hidden
  }

  private void onStageTap () {
    if (chromeHidden) {
      setChromeShown(true, true);
      scheduleChromeHide();
    } else if (canAutoHide()) {
      removeCallbacks(hideChrome);
      setChromeShown(false, true);
    }
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent ev) {
    final int action = ev.getActionMasked();
    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) && !chromeHidden && isVideoShown()) {
      scheduleChromeHide(); // every touch restarts the timer
    }
    return super.dispatchTouchEvent(ev);
  }

  @Override
  public boolean dispatchKeyEvent (KeyEvent event) {
    if (!chromeHidden && isVideoShown()) {
      scheduleChromeHide();
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!isVideoShown()) {
      stageTap = false;
      return super.onTouchEvent(e);
    }
    // A tap on the video toggles the chrome
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        stageTap = true;
        stageDownX = e.getX();
        stageDownY = e.getY();
        return true;
      case MotionEvent.ACTION_MOVE:
        if (stageTap && Math.hypot(e.getX() - stageDownX, e.getY() - stageDownY) > touchSlop) {
          stageTap = false;
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (stageTap) {
          stageTap = false;
          onStageTap();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        stageTap = false;
        return true;
    }
    return true;
  }

  // Snackbars float above the toggle row while the screen is attached (XgramSnackbar.setBottomObstruction)

  private void updateObstruction () {
    if (getHeight() <= 0 || !isAttachedToWindowCompat()) {
      return;
    }
    final int px = (getHeight() - bottomInset) - visualControlsTop() + Screen.dp(8f);
    XgramSnackbar.setBottomObstruction(this, Math.max(0, px));
  }

  private boolean isAttachedToWindowCompat () {
    return getWindowToken() != null;
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    removeCallbacks(hideChrome);
    XgramSnackbar.setBottomObstruction(this, 0);
  }

  public void performDestroy () {
    statusIndicator.stop();
    stage.setRinging(false, false);
    swipe.setActive(false);
    avatarView.performDestroy();
    emojiStatus.performDestroy();
    removeCallbacks(hideChrome);
    if (video != null) {
      video.removeListener(this);
      video = null;
    }
    if (layer != null) {
      layer.release(); // the views of the video and the tile's go with their EGL resources
    }
    setKeepScreenOn(false);
    XgramSnackbar.setBottomObstruction(this, 0);
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

    boolean isShown () {
      return spring.getTarget() == 1f;
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
      if (view == toggleRow) {
        syncSlotTouchables(); // the row's own touchability went to every child
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
    } else if (view instanceof XgramM3CallVideoToggle) {
      ((XgramM3CallVideoToggle) view).setTouchable(touchable);
    } else if (view instanceof XgramM3CallSwipe) {
      ((XgramM3CallSwipe) view).setTouchable(touchable);
    } else if (view instanceof ImageView) {
      view.setClickable(touchable);
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
    stamp = stamp * 31 + (mediaDark ? 1 : 0);
    stamp = stamp * 31 + tier;
    if (!force && stamp == colorStamp) {
      return;
    }
    colorStamp = stamp;

    // Over video (from the middle of the fade-through on): white text and icons
    final int content = mediaDark ? WHITE : onSurface;
    nameView.setTextColor(content);
    statusView.setTextColor(mediaDark ? WHITE_80 : variant);
    statusIndicator.setColorOverride(mediaDark ? WHITE_80 : 0);
    backButton.setColorFilter(Paints.getColorFilter(content));
    routeButton.setColorFilter(Paints.getColorFilter(content));
    keyChip.setTextColor(onSecondary);
    keyLock.setColorFilter(Paints.getColorFilter(onSecondary));
    keyTitle.setTextColor(onSurface);
    keyEmoji.setTextColor(onSurface);
    keyHint.setTextColor(variant);

    updateTint(accent);
    stage.color = haloColor;
    brandChip.setTint(haloColor); // the top bar's chip takes a step toward the halo

    stage.invalidate();
    brandChip.invalidate();
    backButton.invalidate();
    routeButton.invalidate();
    keyChip.invalidate();
    keyCard.invalidate();
    muteButton.invalidate();
    speakerButton.invalidate();
    flipButton.invalidate();
    videoButton.invalidate();
    messageButton.invalidate();
    answerVideoButton.invalidate();
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
    final float m = MathUtils.clamp(videoEffects.getValue());
    if (heroTint != 0 && height > 0 && m < 1f) {
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
    if (m > 0f && stageBottom > stageTop) {
      // The video stage: black under the video (and around a picture that fits)
      stagePaint.setColor(0xff000000);
      stagePaint.setAlpha(Math.round(255f * m));
      c.drawRect(0, stageTop, width, stageBottom, stagePaint);
    }
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
      updateTier(controlsWidth);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  /** The toggles' slot (four of them on the cover: 82dp) and their size tier, set only when they change. */
  private void updateTier (int controlsWidth) {
    final int count = videoAvailable ? 4 : 3;
    final int slot = Math.max(0, Math.min(Screen.dp(SLOT_MAX), controlsWidth / count));
    final int newTier = slot >= Screen.dp(SLOT_REGULAR) ? XgramM3CallVideoToggle.TIER_REGULAR : slot >= Screen.dp(SLOT_COMPACT) ? XgramM3CallVideoToggle.TIER_COMPACT : XgramM3CallVideoToggle.TIER_DENSE;
    if (slot == slotPx && newTier == tier) {
      return;
    }
    slotPx = slot;
    tier = newTier;
    setSlot(muteButton, slot);
    setSlot(speakerSlot, slot);
    setSlot(videoButton, slot);
    setSlot(messageButton, slot);
    setSlot(answerVideoButton, slot);
    muteButton.setTier(newTier);
    speakerButton.setTier(newTier);
    flipButton.setTier(newTier);
    videoButton.setTier(newTier);
    messageButton.setTier(newTier);
    answerVideoButton.setTier(newTier);
  }

  private static void setSlot (View view, int slot) {
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params.width != slot) {
      params.width = slot;
    }
    if (params instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;
      lp.leftMargin = lp.rightMargin = 0;
    }
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
    final boolean tabletop;
    if (foldTop > barBottom && foldTop + controlsH <= lower) {
      // Tabletop: the hero above the fold, the controls centered below it
      controlsTop = foldTop + Math.max(Screen.dp(16f), (lower - foldTop - controlsH) / 2);
      heroBottom = foldTop - Screen.dp(8f);
      tabletop = true;
    } else {
      controlsTop = lower - Screen.dp(CONTROLS_BOTTOM) - controlsH;
      heroBottom = controlsTop - Screen.dp(16f);
      tabletop = false;
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

    // Video geometry: the compact header, the stage, the scrims and the tile
    final boolean tabletopChanged = this.tabletop != tabletop;
    this.tabletop = tabletop;
    this.barBottomPx = barBottom;
    this.controlsTopPx = controlsTop;
    final int nameH = nameView.getMeasuredHeight(), statusH = statusView.getMeasuredHeight();
    this.heroDy = (barBottom + Screen.dp(HEADER_GAP)) - (heroTop + stage.getMeasuredHeight() + Screen.dp(NAME_GAP));
    this.headerBottom = barBottom + Screen.dp(HEADER_GAP) + Math.round(nameH * (1f - NAME_SHRINK)) + Screen.dp(STATUS_GAP) + Math.round(statusH * (1f - STATUS_SHRINK));
    nameView.setPivotX(nameView.getMeasuredWidth() / 2f);
    nameView.setPivotY(0f);
    statusView.setPivotX(statusView.getMeasuredWidth() / 2f);
    statusView.setPivotY(0f);
    // In two panes with dark status icons (light themes) the status bar strip stays surface, so they stay readable
    final View root = getRootView();
    final boolean narrower = root != null && root != this && width < root.getWidth() - 1;
    this.stageTop = narrower && Theme.needLightStatusBar() ? topInset : 0;
    this.stageBottom = tabletop ? foldTop : height;
    if (layer != null) {
      layer.setStageBounds(stageTop, stageBottom);
    }
    if (tabletopChanged) {
      updateHug(false);
    }
    updateSideInsets(width);
    updateVideoGeometry();
    applyVideoFactors();
    applyChrome();
    updateObstruction();
  }

  /** The window's side insets (system bars and the display cutout) where this screen touches the window's edge. */
  private void updateSideInsets (int width) {
    int insetLeft = 0, insetRight = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      final WindowInsets insets = getRootWindowInsets();
      if (insets != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          final android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          insetLeft = i.left;
          insetRight = i.right;
        } else {
          insetLeft = insets.getStableInsetLeft();
          insetRight = insets.getStableInsetRight();
        }
      }
    }
    getLocationInWindow(location);
    final View root = getRootView();
    final int windowWidth = root != null ? root.getWidth() : width;
    sideLeft = location[0] <= 0 ? insetLeft : 0;
    sideRight = location[0] + width >= windowWidth - 1 ? insetRight : 0;
  }

  /** The tile's size and where it may go: between the compact header and the controls (the whole stage while the chrome is hidden). */
  private void updateVideoGeometry () {
    final XgramM3CallVideoTile tile = this.tile;
    final int width = getWidth(), height = getHeight();
    if (tile == null || width <= 0 || height <= 0) {
      updateScrims();
      return;
    }
    // Portrait: 28% of the width, 96 to 144dp, 3:4; landscape: 28% of the height, 4:3
    final float density = Screen.density();
    final float widthDp = width / density, heightDp = height / density;
    int tileW, tileH;
    if (width <= height) {
      final float w = MathUtils.clamp((float) Math.floor(TILE_FRACTION * widthDp), TILE_MIN, TILE_MAX);
      tileW = Screen.dp(w);
      tileH = Math.round(tileW * 4f / 3f);
    } else {
      final float h = MathUtils.clamp((float) Math.floor(TILE_FRACTION * heightDp), TILE_MIN, TILE_MAX);
      tileH = Screen.dp(h);
      tileW = Math.round(tileH * 4f / 3f);
    }
    final int boundsLeft = Screen.dp(TILE_EDGE) + sideLeft;
    final int boundsRight = width - Screen.dp(TILE_EDGE) - sideRight;
    final int hiddenTop = topInset + Screen.dp(TILE_EDGE), hiddenBottom = height - bottomInset - Screen.dp(TILE_EDGE);
    int boundsTop, boundsBottom;
    if (chromeHidden) {
      boundsTop = hiddenTop;
      boundsBottom = hiddenBottom;
    } else {
      boundsTop = headerBottom + Screen.dp(TILE_GAP);
      boundsBottom = visualControlsTop() - Screen.dp(TILE_GAP);
    }
    if (tabletop) {
      boundsBottom = Math.min(boundsBottom, foldTop - Screen.dp(TILE_GAP));
    }
    if (boundsBottom - boundsTop < tileH) {
      boundsTop = hiddenTop;
      boundsBottom = tabletop ? Math.min(hiddenBottom, foldTop - Screen.dp(TILE_GAP)) : hiddenBottom;
    }
    if (boundsBottom - boundsTop < tileH) {
      tileH = Math.max(0, boundsBottom - boundsTop);
      tileW = tileH * 3 / 4;
    }
    if (boundsRight - boundsLeft < tileW) {
      tileW = Math.max(0, boundsRight - boundsLeft);
      tileH = tileW * 4 / 3;
    }
    tile.setTileSize(tileW, tileH); // hides itself under 64dp
    if (tile.getMeasuredWidth() != Math.max(1, tileW) || tile.getMeasuredHeight() != Math.max(1, tileH)) {
      tile.measure(MeasureSpec.makeMeasureSpec(Math.max(1, tileW), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(1, tileH), MeasureSpec.EXACTLY));
    }
    if (tile.getLeft() != 0 || tile.getTop() != 0 || tile.getWidth() != tile.getMeasuredWidth() || tile.getHeight() != tile.getMeasuredHeight()) {
      tile.layout(0, 0, tile.getMeasuredWidth(), tile.getMeasuredHeight());
    }
    tileBounds.set(boundsLeft, boundsTop, boundsRight, boundsBottom);
    tile.setTileBounds(boundsLeft, boundsTop, boundsRight, boundsBottom, isLaidOutCompat());
    updateScrims();
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
    int x;
    if (org.thunderdog.challegram.core.Lang.rtl()) {
      // Before the name (the left padding is the status width and its 7dp gap)
      x = Math.max(0, view.getPaddingLeft() + (int) layout.getLineLeft(0) - Screen.dp(7f) - emojiStatus.getWidth());
    } else {
      int end = view.getPaddingLeft() + (int) layout.getLineRight(0) + Screen.dp(7f);
      x = Math.min(end, view.getWidth() - view.getPaddingRight() + Screen.dp(7f));
    }
    int y = (view.getHeight() - Screen.dp(25f)) / 2;
    emojiStatus.draw(c, x, y);
  }

  // Top bar: keeps the brand chip clear of the key chip and the audio output button at the end

  /**
   * The M3 small app bar row. Before measuring, the end margins are set from the key chip's width while the key is
   * shown (its target, so the chip makes room as the key chip springs in) and from the audio output button while it is
   * shown: the brand chip then drops its label, its lock and its bars in turn when the rest no longer fits (the cover
   * screen), and springs its width there. Once per change, never per frame. Over video, while the chrome is hidden,
   * the row takes no touch (it goes to the stage).
   */
  private final class TopBar extends FrameLayoutFix {
    TopBar (Context context) {
      super(context);
    }

    @Override
    public boolean dispatchTouchEvent (MotionEvent ev) {
      return (!chromeHidden || ev.getActionMasked() != MotionEvent.ACTION_DOWN) && super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int width = MeasureSpec.getSize(widthMeasureSpec);
      int keyW = 0;
      if (keyShown != null && keyShown.getTarget() == 1f && keyChip.getVisibility() != View.GONE) {
        keyChip.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Screen.dp(KEY_CHIP_H), MeasureSpec.EXACTLY));
        keyW = keyChip.getMeasuredWidth();
      }
      final boolean routeShown = routeFade != null && routeFade.isShown();
      final int routeEnd = keyW > 0 ? keyW + Screen.dp(ROUTE_GAP) : 0;
      final int brandEnd = (keyW > 0 ? keyW + (routeShown ? Screen.dp(ROUTE_GAP) : Screen.dp(BAR_CHIP_GAP)) : 0) +
        (routeShown ? Screen.dp(TOP_BAR) + Screen.dp(BAR_CHIP_GAP) : 0);
      final boolean rtl = Lang.rtl();
      ViewGroup.LayoutParams params = brandChip.getLayoutParams();
      if (params instanceof FrameLayout.LayoutParams) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) params;
        if (rtl) {
          lp.leftMargin = brandEnd;
        } else {
          lp.rightMargin = brandEnd;
        }
      }
      params = routeButton.getLayoutParams();
      if (params instanceof FrameLayout.LayoutParams) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) params;
        if (rtl) {
          lp.leftMargin = routeEnd;
        } else {
          lp.rightMargin = routeEnd;
        }
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  // Brand chip

  /**
   * The tonal "Telegram call" chip of the top app bar row: a full-corner pill as tall as the key chip at the other end
   * (36dp), filled with surfaceContainerHigh lifted a step toward the avatar halo's hue (a plain surfaceContainerHigh
   * melts into the tinted top in dark; the halo step keeps it apart in light, dark and Pure black and ties it to the
   * hero), holding the Telegram glyph in primary, then once the call is connected the encryption lock (primary) before
   * the label (labelLarge onSurface: the call, then "End-to-end encrypted") and the signal bars at the end. When the
   * row is too narrow it drops, in turn, the label, the lock (the key chip has one), the bars, and last itself. The
   * pill's width springs (SPATIAL_DEFAULT) as the lock, the label and the bars come and go, the lock springs in, a new
   * label crossfades (EFFECTS_FAST), a press draws the onSurface state layer. The view is measured once per change at
   * the widest it can get; only the drawn pill moves, so nothing is laid out per frame. Main thread only.
   */
  private static final class BrandChip extends View {
    private static final float H = KEY_CHIP_H, PAD_START = 12f, PAD_END = 16f, GLYPH = 18f, GAP = 8f, LOCK = 16f, LOCK_GAP = 4f;
    private static final float BAR = 3f, BAR_GAP = 1.5f, BAR_STEP = 3f;
    private static final int BARS = 4;
    /** How far the fill moves from surfaceContainerHigh toward the halo color. */
    private static final float TINT_STEP = .35f;

    private final Drawable glyph, lockGlyph;
    private int glyphColor, lockColor;
    private @ColorInt int tint;
    private boolean encrypted;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF barRect = new RectF();

    private String label = "";
    private @Nullable String prevLabel;
    private float labelWidth, prevLabelWidth;
    private boolean labelFits = true, lockFits = true, barsFit = true, chipFits = true;

    private int barCount = -1;
    private boolean barsVisible;
    private long barsUpdate;
    private final Runnable barsInvalidate = () -> {
      barsUpdate = SystemClock.elapsedRealtime();
      invalidate();
    };

    private final XgramExpressive.Spring width, labelShown, labelSwap, bars, press, lock, shown;

    BrandChip (Context context) {
      super(context);
      setWillNotDraw(false);
      glyph = Drawables.get(XgramIconPack.map(R.drawable.baseline_telegram_24));
      lockGlyph = Drawables.get(R.drawable.baseline_lock_16);
      textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        textPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
      }
      lock = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> invalidate());
      width = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> invalidate()).setThreshold(.5f);
      labelShown = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 1f, (s, value) -> invalidate());
      labelSwap = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 1f, (s, value) -> invalidate());
      labelSwap.setEndListener(() -> prevLabel = null);
      bars = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> invalidate());
      press = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
      shown = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 1f, (s, value) -> setAlpha(MathUtils.clamp(value)));
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private boolean laidOut () {
      return getWidth() > 0 && getHeight() > 0;
    }

    void setLabel (String text, boolean animated) {
      if (text == null || text.equals(label)) {
        return;
      }
      final boolean crossfade = animated && laidOut() && !label.isEmpty() && labelShown.getValue() > 0f;
      prevLabel = crossfade ? label : null;
      prevLabelWidth = labelWidth;
      label = text;
      labelWidth = textPaint.measureText(text);
      setContentDescription(text);
      if (crossfade) {
        labelSwap.setValue(0f);
        labelSwap.animateTo(1f);
      } else {
        labelSwap.setValue(1f);
      }
      requestLayout(); // whether it still fits
      invalidate();
    }

    /** The signal bars: {@code count} redraws at most once a second, as Telegram X's CallStrengthView. */
    void setBars (int count, boolean visible, boolean animated) {
      final boolean countChanged = Math.max(barCount, 0) != Math.max(count, 0);
      barCount = count;
      if (countChanged) {
        final long now = SystemClock.elapsedRealtime();
        removeCallbacks(barsInvalidate);
        if (barsUpdate == 0L || now - barsUpdate >= 1000L) {
          barsUpdate = now;
          invalidate();
        } else {
          postDelayed(barsInvalidate, 1000L - (now - barsUpdate));
        }
      }
      if (barsVisible != visible) {
        barsVisible = visible;
        applyBarsTarget(animated);
        requestLayout(); // the label may no longer fit, and the pill's target width changes
      }
    }

    /** The encryption lock before the label, once the call is connected (end-to-end encrypted). */
    void setEncrypted (boolean encrypted, boolean animated) {
      if (this.encrypted == encrypted) {
        return;
      }
      this.encrypted = encrypted;
      applyLockTarget(animated);
      requestLayout(); // the pill's target width changes
    }

    private void applyLockTarget (boolean animated) {
      final float target = encrypted && lockFits ? 1f : 0f;
      if (lock.getTarget() == target && (animated || lock.getValue() == target)) {
        return;
      }
      if (animated && laidOut()) {
        lock.animateTo(target);
      } else {
        lock.setValue(target);
        invalidate();
      }
    }

    private void applyBarsTarget (boolean animated) {
      final float target = barsVisible && barsFit ? 1f : 0f;
      if (bars.getTarget() == target && (animated || bars.getValue() == target)) {
        return;
      }
      if (animated && laidOut()) {
        bars.animateTo(target);
      } else {
        bars.setValue(target);
        invalidate();
      }
    }

    /** The avatar halo's color: the fill takes a step toward it. */
    void setTint (@ColorInt int tint) {
      if (this.tint != tint) {
        this.tint = tint;
        invalidate();
      }
    }

    private float contentWidth (boolean withLabel, boolean withBars, boolean withLock) {
      float w = Screen.dp(PAD_START) + Screen.dp(GLYPH) + Screen.dp(PAD_END);
      if (withLock) {
        w += Screen.dp(GAP) + Screen.dp(LOCK);
      }
      if (withLabel) {
        w += Screen.dp(withLock ? LOCK_GAP : GAP) + labelWidth;
      }
      if (withBars) {
        w += Screen.dp(GAP) + barsWidth();
      }
      return w;
    }

    private static float barsWidth () {
      return Screen.dp(BAR) * BARS + Screen.dp(BAR_GAP) * (BARS - 1);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int avail = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(widthMeasureSpec);
      // The ladder, first that fits wins: everything; without the label; without the lock; without the bars; hidden
      boolean withLabel = true, withLock = encrypted, withBars = barsVisible, fitsAll = true;
      if (contentWidth(true, withBars, withLock) > avail) {
        withLabel = false;
        if (contentWidth(false, withBars, withLock) > avail) {
          withLock = false;
          if (contentWidth(false, withBars, false) > avail) {
            withBars = false;
            if (contentWidth(false, false, false) > avail) {
              fitsAll = false;
            }
          }
        }
      }
      labelFits = withLabel;
      final boolean newLockFits = withLock || !encrypted;
      final boolean newBarsFit = withBars || !barsVisible;
      final float widest = Math.max(contentWidth(true, true, true), prevLabel != null ? contentWidth(false, true, true) + Screen.dp(GAP) + prevLabelWidth : 0f);
      final int w = (int) Math.ceil(Math.min(avail, widest));
      final int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightMeasureSpec) : Screen.dp(H);
      setMeasuredDimension(w, h);
      final boolean animated = laidOut();
      if (lockFits != newLockFits) {
        lockFits = newLockFits;
        applyLockTarget(animated);
      }
      if (barsFit != newBarsFit) {
        barsFit = newBarsFit;
        applyBarsTarget(animated);
      }
      if (chipFits != fitsAll) {
        chipFits = fitsAll;
        setImportantForAccessibility(fitsAll ? View.IMPORTANT_FOR_ACCESSIBILITY_YES : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (animated) {
          shown.animateTo(fitsAll ? 1f : 0f);
        } else {
          shown.setValue(fitsAll ? 1f : 0f);
          setAlpha(fitsAll ? 1f : 0f);
        }
      }
      final float target = Math.min(w, contentWidth(withLabel, withBars, withLock));
      if (width.getTarget() != target || width.getValue() == 0f) {
        if (animated && width.getValue() > 0f) {
          width.animateTo(target);
        } else {
          width.setValue(target);
        }
      }
      final float labelTarget = labelFits ? 1f : 0f;
      if (labelShown.getTarget() != labelTarget) {
        if (animated) {
          labelShown.animateTo(labelTarget);
        } else {
          labelShown.setValue(labelTarget);
        }
      }
    }

    @Override
    protected void drawableStateChanged () {
      super.drawableStateChanged();
      if (press != null) {
        press.animateTo(isPressed() && isClickable() ? 1f : 0f);
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN && (!chipFits || !rect.contains(e.getX(), e.getY()))) {
        return false; // the view is as wide as the chip can get: only the drawn pill takes a press
      }
      return super.onTouchEvent(e);
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      removeCallbacks(barsInvalidate);
    }

    /** The tonal fill: surfaceContainerHigh (the floating step with Pure black) a step toward the halo's hue. */
    private int fillColor () {
      final int container = XgramExpressive.color(XgramAmoled.floatingRole(XgramExpressive.SURFACE_CONTAINER_HIGH));
      return tint != 0 ? ColorUtils.fromToArgb(container, tint, TINT_STEP) : container;
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean rtl = Lang.rtl();
      final float w = Math.min(getWidth(), Math.max(0f, width.getValue()));
      if (w <= 0f) {
        rect.setEmpty();
        return;
      }
      final float h = Screen.dp(H);
      final float top = (getHeight() - h) / 2f;
      final float left = rtl ? getWidth() - w : 0f;
      rect.set(left, top, left + w, top + h);
      final float radius = h / 2f;
      paint.setColor(fillColor());
      c.drawRoundRect(rect, radius, radius, paint);
      final float pressed = MathUtils.clamp(press.getValue());
      if (pressed > 0f) {
        paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.STATE_PRESSED * pressed));
        c.drawRoundRect(rect, radius, radius, paint);
      }

      final int content = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      final int labelColor = XgramExpressive.color(XgramExpressive.ON_SURFACE);
      final int accent = XgramExpressive.color(XgramExpressive.PRIMARY);
      final int save = c.save();
      c.clipRect(rect);

      // The Telegram glyph at the start, in primary
      final int g = Screen.dp(GLYPH);
      final int gx = Math.round(rtl ? rect.right - Screen.dp(PAD_START) - g : rect.left + Screen.dp(PAD_START));
      final int gy = Math.round(rect.centerY() - g / 2f);
      if (glyph != null) {
        if (glyphColor != accent) {
          glyphColor = accent;
          glyph.setColorFilter(Paints.getColorFilter(accent));
        }
        glyph.setBounds(gx, gy, gx + g, gy + g);
        glyph.draw(c);
      }

      // The encryption lock after it, springing in once the call is connected
      final float locked = MathUtils.clamp(lock.getValue());
      float cursor = Screen.dp(PAD_START) + g; // from the start edge of the pill
      if (locked > 0f && lockGlyph != null) {
        final int l = Screen.dp(LOCK);
        final float lockStart = cursor + Screen.dp(GAP);
        final int lx = Math.round(rtl ? rect.right - lockStart - l : rect.left + lockStart);
        final int ly = Math.round(rect.centerY() - l / 2f);
        if (lockColor != accent) {
          lockColor = accent;
          lockGlyph.setColorFilter(Paints.getColorFilter(accent));
        }
        lockGlyph.setAlpha(Math.round(255f * locked)); // one draw of this drawable per frame
        final int lockSave = c.save();
        final float lockScale = .6f + .4f * locked;
        c.scale(lockScale, lockScale, lx + l / 2f, ly + l / 2f);
        lockGlyph.setBounds(lx, ly, lx + l, ly + l);
        lockGlyph.draw(c);
        c.restoreToCount(lockSave);
      }
      cursor += (Screen.dp(GAP) + Screen.dp(LOCK)) * locked;

      // The label after them (the previous one fading out under the new one)
      final float labelAlpha = MathUtils.clamp(labelShown.getValue());
      if (labelAlpha > 0f) {
        final float baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f;
        final float labelOffset = cursor + Screen.dp(GAP) + (Screen.dp(LOCK_GAP) - Screen.dp(GAP)) * locked;
        final float textStart = rtl ? rect.right - labelOffset : rect.left + labelOffset;
        final float swap = MathUtils.clamp(labelSwap.getValue());
        if (prevLabel != null && swap < 1f) {
          drawLabel(c, prevLabel, prevLabelWidth, textStart, baseline, labelColor, labelAlpha * (1f - swap), rtl);
        }
        drawLabel(c, label, labelWidth, textStart, baseline, labelColor, labelAlpha * swap, rtl);
      }

      // The signal bars at the end
      final float barsShown = MathUtils.clamp(bars.getValue());
      if (barsShown > 0f) {
        final float total = barsWidth();
        float x = rtl ? rect.left + Screen.dp(PAD_END) : rect.right - Screen.dp(PAD_END) - total;
        final float bottom = rect.centerY() + Screen.dp(6.5f);
        final int on = XgramExpressive.withAlpha(content, barsShown);
        final int off = XgramExpressive.withAlpha(content, XgramExpressive.DISABLED_CONTENT * barsShown);
        final float bar = Screen.dp(BAR), barRadius = bar / 2f;
        for (int i = 0; i < BARS; i++) {
          barRect.set(x, bottom - Screen.dp(BAR_STEP) * (i + 1) - Screen.dp(1f), x + bar, bottom);
          paint.setColor(barCount > i ? on : off);
          c.drawRoundRect(barRect, barRadius, barRadius, paint);
          x += bar + Screen.dp(BAR_GAP);
        }
      }
      c.restoreToCount(save);
    }

    private void drawLabel (Canvas c, String text, float textWidth, float start, float baseline, int color, float alpha, boolean rtl) {
      if (alpha <= 0f || text == null || text.isEmpty()) {
        return;
      }
      textPaint.setColor(XgramExpressive.withAlpha(color, alpha));
      c.drawText(text, rtl ? start - textWidth : start, baseline, textPaint);
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
