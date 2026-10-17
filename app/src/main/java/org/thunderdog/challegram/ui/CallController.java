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
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.RateLimiter;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.TextView;
import org.thunderdog.challegram.widget.voip.CallControlsLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewHandler;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class CallController extends ViewController<CallController.Arguments> implements TdlibCache.UserDataChangeListener, TdlibCache.CallStateChangeListener, View.OnClickListener, FactorAnimator.Target, Runnable, CallControlsLayout.CallControlCallback, Screen.StatusBarHeightChangeListener {
  private static final boolean DEBUG_FADE_BRANDING = true;

  private static class ButtonView extends View implements FactorAnimator.Target {
    private Drawable icon;
    private float factor;
    private boolean needCross;

    public ButtonView (Context context) {
      super(context);
    }

    public void setIcon (@DrawableRes int icon) {
      this.icon = Drawables.get(icon);
    }

    public void setNeedCross (boolean needCross) {
      this.needCross = needCross;
    }

    public boolean toggleActive () {
      setIsActive(!isActive, true);
      return isActive;
    }

    private boolean isActive;

    public void setIsActive (boolean isActive, boolean animated) {
      if (this.isActive != isActive) {
        this.isActive = isActive;
        if (animated) {
          animateFactor(isActive ? 1f : 0f);
        } else {
          forceFactor(isActive ? 1f : 0f);
        }
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      return getParent() != null && ((View) getParent()).getAlpha() == 1f && super.onTouchEvent(event);
    }

    private FactorAnimator animator;

    private void animateFactor (float toFactor) {
      if (animator == null) {
        animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
      }
      animator.animateTo(toFactor);
    }

    private void forceFactor (float toFactor) {
      if (animator != null) {
        animator.forceFactor(toFactor);
      }
      setFactor(toFactor);
    }

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    protected void onDraw (Canvas c) {
      if (icon == null) {
        return;
      }
      float cx = getMeasuredWidth() / 2;
      float cy = getMeasuredHeight() / 2;
      int backgroundColor = ColorUtils.fromToArgb(0x00ffffff, 0xffffffff, factor);
      if (factor != 0f) {
        c.drawCircle(cx, cy, Screen.dp(18f), Paints.fillingPaint(backgroundColor));
      }
      int iconColor = ColorUtils.fromToArgb(0xffffffff, 0xff000000, factor);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, Paints.getPorterDuffPaint(iconColor));
      if (factor != 0f && needCross) {
        DrawAlgorithms.drawCross(c, cx, cy, factor, iconColor, backgroundColor);
      }
    }
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  public static class Arguments {
    private TdApi.Call call;

    public Arguments (TdApi.Call call) {
      this.call = call;
    }
  }

  public CallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.Call call;
  private @Nullable TdApi.User user;
  private CallSettings callSettings;
  private boolean hadEmojiSinceStart;
  // XGram: the Material 3 Expressive call screen; null with Expressive design off (Telegram X's views are used then)
  private @Nullable com.fahadali.xgram.appearance.XgramM3CallScreen xgramScreen;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.call = args.call;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    this.hadEmojiSinceStart = call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    this.user = tdlib.cache().user(call.userId);
  }

  @Override
  public int getId () {
    return R.id.controller_call;
  }

  private AvatarView avatarView;
  private TextView nameView, stateView;
  private EmojiStatusHelper emojiStatusHelper;
  private float nameTextWidth;
  private TextPaint nameTextPaint;
  private LinearLayout brandWrap;
  private TextView debugView;
  private CallStrengthView strengthView;

  private static class CallStrengthView extends View {
    private final ViewHandler handler;

    public CallStrengthView (Context context) {
      super(context);
      handler = new ViewHandler();
    }

    private int barCount = -1;
    private long lastUpdateTime;

    private static final long MAX_FREQUENCY_MS = 1000l;

    public void setBarsCount (int callStrength) {
      boolean changed = Math.max(this.barCount, 0) != Math.max(callStrength, 0);
      this.barCount = callStrength;
      if (changed) {
        long now = SystemClock.elapsedRealtime();

        if (lastUpdateTime == 0 || now - lastUpdateTime >= MAX_FREQUENCY_MS) {
          lastUpdateTime = now;
          handler.cancelInvalidate(this);
          invalidate();
        } else {
          handler.invalidate(this, MAX_FREQUENCY_MS - (now - lastUpdateTime));
        }
      }
    }

    private static final int MAX_BARS_COUNT = 4;

    @Override
    protected void onDraw (Canvas c) {
      int size = Screen.dp(3f);
      int spacing = Screen.dp(1f);
      int totalSize = size * MAX_BARS_COUNT + spacing * (MAX_BARS_COUNT - 1);
      int startX = getMeasuredWidth() / 2 - totalSize / 2;
      int startY = getMeasuredHeight() / 2 + size * 2;
      int cx = startX;
      for (int i = 0; i < 4; i++) {
        RectF rectF = Paints.getRectF();
        rectF.set(cx, startY - size * (i + 1), cx + size, startY);
        c.drawRoundRect(rectF, spacing, spacing, Paints.fillingPaint(barCount > i ? 0xffffffff : 0x7fffffff));
        cx += size + spacing;
      }
    }
  }

  private TextView emojiViewSmall, emojiViewBig, emojiViewHint;
  private CallControlsLayout callControlsLayout;

  private FrameLayoutFix buttonWrap;
  private ButtonView muteButtonView, speakerButtonView;
  // XGram: video calls with Expressive design off: the Video cell, the stage and the tile (Telegram X's look)
  private @Nullable ButtonView xgramVideoButtonView;
  private @Nullable TextView xgramBrandView;
  private @Nullable com.fahadali.xgram.call.XgramCallVideoLayer xgramClassicLayer;
  private @Nullable com.fahadali.xgram.appearance.XgramM3CallVideoTile xgramClassicTile;
  private @Nullable FactorAnimator xgramAvatarFade;
  private @DrawableRes int xgramSpeakerIcon = R.drawable.baseline_volume_up_24;
  private static final int ANIMATOR_XGRAM_AVATAR = 7;

  private float lastHeaderFactor;

  @Override
  protected void applyCustomHeaderAnimations (float factor) {
    if (xgramScreen != null) { // XGram
      if (lastHeaderFactor != factor) {
        lastHeaderFactor = factor;
        xgramScreen.setEnterFactor(factor);
      }
      return;
    }
    if (lastHeaderFactor != factor) {
      lastHeaderFactor = factor;
      updateControlsAlpha();
      updateEmojiFactors();
      if (DEBUG_FADE_BRANDING) {
        brandWrap.setAlpha(factor);
      }
      avatarView.invalidate();
      if (xgramAvatarFade != null) {
        xgramSetClassicVideoFactor(xgramAvatarFade.getFactor()); // XGram: the scrim over video follows the header too
      }
    }
  }

  private BoolAnimator strengthAnimator;
  private static final int ANIMATOR_STRENGTH = 6;

  private void updateCallStrength () {
    if (xgramScreen != null) { // XGram: the bars in the branding chip
      boolean visible = callStrength >= 0 && call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR && callDuration >= 0;
      xgramScreen.setCallBars(callStrength, visible, isFocused());
      return;
    }
    boolean isVisible = callStrength >= 0 && call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR && callDuration >= 0;
    if (!isVisible == (strengthAnimator != null && strengthAnimator.getValue())) {
      if (strengthAnimator == null) {
        strengthAnimator = new BoolAnimator(ANIMATOR_STRENGTH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      strengthAnimator.setValue(isVisible, strengthView != null && lastHeaderFactor > 0f);
    }
  }

  private int callStrength = -1;

  private void setCallBarsCount (int count) {
    if (this.callStrength != count) {
      this.callStrength = count;
      if (strengthView != null) {
        strengthView.setBarsCount(count);
      }
      updateCallStrength();
    }
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    if (xgramScreen != null) { // XGram
      xgramScreen.setBottomInset(extraBottomInset);
      return;
    }
    Views.setPaddingBottom(buttonWrap, extraBottomInset);
    Views.setLayoutHeight(buttonWrap, Screen.dp(76f) + extraBottomInset);
    Views.setPaddingBottom(callControlsLayout, extraBottomInset);
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    if (xgramScreen != null) { // XGram
      xgramScreen.setTopInset(newHeight);
      return;
    }
    int startMargin = Math.max(Screen.dp(18f) + newHeight, Screen.dp(42f));
    Views.setTopMargin(brandWrap, startMargin);
    Views.setTopMargin(nameView, startMargin + Screen.dp(34f));
    Views.setTopMargin(stateView, startMargin + Screen.dp(94f));
  }

  @Override
  protected View onCreateView (final Context context) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return xgramCreateView(context); // XGram: Material 3 Expressive call screen
    }
    final FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateEmojiPosition();
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmojiPosition();
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.headerBackground, this);

    avatarView = new AvatarView(context) {
      private final Drawable topShadow = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.TOP, false);

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int shadowSize = Screen.dp(212f);
        if (topShadow.getBounds().right != width || topShadow.getBounds().bottom != shadowSize) {
          topShadow.setBounds(0, 0, width, shadowSize);
        }
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            return true;
          case MotionEvent.ACTION_UP:
            if (emojiExpandFactor == 1f && isEmojiExpanded) {
              setEmojiExpanded(false);
            }
            return true;
        }
        return false;
      }

      @Override
      protected void onDraw(Canvas c){
        super.onDraw(c);
        int alpha = (int) (255f * lastHeaderFactor * .5f);
        Drawables.setAlpha(topShadow, alpha);
        topShadow.draw(c);
      }
    };
    avatarView.setNoRound(true);
    avatarView.setNoPlaceholders(true);
    avatarView.setNeedFull(true);
    avatarView.setUser(tdlib, user, false);
    avatarView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(avatarView);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    // Top-left corner

    int startMargin = Math.max(Screen.dp(18f) + Screen.getStatusBarHeight(), Screen.dp(42f));

    params.topMargin = startMargin + Screen.dp(34f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    nameView = new EmojiTextView(context) {
      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        nameTextWidth = U.measureText(TD.getUserName(user), nameTextPaint);
        if (nameTextWidth > getMeasuredWidth() - getPaddingRight()) {
          CharSequence text = getText().subSequence(0, getLayout().getEllipsisStart(0)) + "...";
          nameTextWidth = U.measureText(text, nameTextPaint);
        }
      }

      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        emojiStatusHelper.draw(canvas, (int) Math.min(getMeasuredWidth() - emojiStatusHelper.getWidth(0), nameTextWidth + Screen.dp(7)), Screen.dp(9));
      }
    };
    nameView.setScrollDisabled(true);
    nameView.setSingleLine(true);
    nameView.setTextColor(0xffffffff);
    nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
    nameView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    Views.setSimpleShadow(nameView);
    nameView.setEllipsize(TextUtils.TruncateAt.END);
    nameView.setLayoutParams(params);
    contentView.addView(nameView);

    nameTextPaint = new TextPaint();
    nameTextPaint.setTextSize(Screen.dp(40));
    nameTextPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    emojiStatusHelper = new EmojiStatusHelper(tdlib, nameView, null);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = startMargin + Screen.dp(94f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    stateView = new TextView(context);
    stateView.setScrollDisabled(true);
    // stateView.setSingleLine(true);
    stateView.setMaxLines(2);
    stateView.setLineSpacing(Screen.dp(3f), 1f);
    stateView.setTextColor(0xffffffff);
    stateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    stateView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(stateView);
    // stateView.setEllipsize(TextUtils.TruncateAt.END);
    stateView.setLayoutParams(params);
    contentView.addView(stateView);

    Screen.addStatusBarHeightListener(this);
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = startMargin;
    params.leftMargin = params.rightMargin = Screen.dp(18f);
    brandWrap = new LinearLayout(context);
    if (DEBUG_FADE_BRANDING) {
      brandWrap.setAlpha(0f);
    }
    brandWrap.setOrientation(LinearLayout.HORIZONTAL);
    brandWrap.setLayoutParams(params);
    contentView.addView(brandWrap);

    LinearLayout.LayoutParams lp;

    lp = new LinearLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
    lp.topMargin = Screen.dp(2f);

    ImageView brandIcon = new ImageView(context);
    brandIcon.setScaleType(ImageView.ScaleType.CENTER);
    brandIcon.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.deproko_logo_telegram_18));
    brandIcon.setLayoutParams(lp);
    brandWrap.addView(brandIcon);

    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = Screen.dp(9f);

    TextView brandView = new TextView(context);
    brandView.setScrollDisabled(true);
    brandView.setSingleLine(true);
    brandView.setTextColor(0xffffffff);
    brandView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    brandView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(brandView);
    brandView.setEllipsize(TextUtils.TruncateAt.END);
    brandView.setLayoutParams(lp);
    brandView.setText(Lang.uppercase(Lang.getString(R.string.VoipBranding)));
    xgramBrandView = brandView;
    if (Log.checkLogLevel(Log.LEVEL_INFO) || BuildConfig.EXPERIMENTAL) {
      brandView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick (View v) {
          TGCallService.markLogViewed();
          if (debugView != null) {
            contentView.removeView(debugView);
            debugView = null;
          } else {
            final TextView view = new TextView(context);
            view.setScrollDisabled(true);
            view.setBackgroundColor(0xaaffffff);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setTextColor(0xff000000);
            view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
            view.post(new Runnable() {
              @Override
              public void run () {
                TGCallService service = TGCallService.currentInstance();

                SpannableStringBuilder b = new SpannableStringBuilder();
                if (service != null) {
                  b.append(service.getLibraryNameAndVersion());
                } else {
                  b.append("service unavailable");
                }
                b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoBold(), 0), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (service != null) {
                  CharSequence log = service.getDebugString();
                  if (!StringUtils.isEmpty(log)) {
                    b.append("\n\n");
                    b.append(log);
                  }
                }
                view.setText(b);
                if (view.getParent() != null) {
                  view.postDelayed(this, 500l);
                }
              }
            });
            debugView = view;
            contentView.addView(debugView);
          }
        }
      });
    }
    brandWrap.addView(brandView);

    lp = new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f));
    // lp.topMargin = Screen.dp(2f);
    lp.leftMargin = Screen.dp(8f);
    strengthView = new CallStrengthView(context);
    strengthView.setLayoutParams(lp);
    if (callStrength < 0) {
      strengthView.setAlpha(0f);
    }
    strengthView.setBarsCount(callStrength);
    brandWrap.addView(strengthView);

    // Emoji corner

    emojiViewSmall = new EmojiTextView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return (event.getAction() != MotionEvent.ACTION_DOWN || emojiExpandFactor == 0f) && super.onTouchEvent(event);
      }
    };
    emojiViewSmall.setScrollDisabled(true);
    emojiViewSmall.setSingleLine(true);
    emojiViewSmall.setTextColor(0xffffffff);
    emojiViewSmall.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    emojiViewSmall.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewSmall);
    emojiViewSmall.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewSmall.setPadding(Screen.dp(18f), Screen.dp(18f), Screen.dp(18f), Screen.dp(18f));
    emojiViewSmall.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    emojiViewSmall.setOnClickListener(this);
    emojiViewSmall.setId(R.id.btn_emoji);
    contentView.addView(emojiViewSmall);

    emojiViewBig = new EmojiTextView(context);
    emojiViewBig.setScrollDisabled(true);
    emojiViewBig.setSingleLine(true);
    emojiViewBig.setScaleX(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setScaleY(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setAlpha(0f);
    emojiViewBig.setTextColor(0xffffffff);
    int emojiBigSize = (int) (16f * DESIRED_EMOJI_EXPAND_FACTOR);
    EMOJI_EXPAND_FACTOR = (float) emojiBigSize / 16f;
    emojiViewBig.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emojiBigSize);
    emojiViewBig.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewBig);
    emojiViewBig.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewBig.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    contentView.addView(emojiViewBig);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(24f) * 2;
    params.rightMargin = params.leftMargin = Screen.dp(48f);

    emojiViewHint = new EmojiTextView(context);
    emojiViewHint.setScrollDisabled(true);
    emojiViewHint.setAlpha(0f);
    emojiViewHint.setTextColor(0xffffffff);
    emojiViewHint.setGravity(Gravity.CENTER);
    emojiViewHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
    emojiViewHint.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewHint);
    emojiViewHint.setLayoutParams(params);
    contentView.addView(emojiViewHint);

    // Call settings buttons

    muteButtonView = new ButtonView(context);
    muteButtonView.setId(R.id.btn_mute);
    muteButtonView.setOnClickListener(this);
    muteButtonView.setIcon(R.drawable.baseline_mic_24);
    muteButtonView.setNeedCross(true);
    muteButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.LEFT | Gravity.BOTTOM));

    ButtonView messageButtonView = new ButtonView(context);
    messageButtonView.setId(R.id.btn_openChat);
    messageButtonView.setOnClickListener(this);
    messageButtonView.setIcon(R.drawable.baseline_chat_bubble_24);
    messageButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

    speakerButtonView = new ButtonView(context);
    speakerButtonView.setId(R.id.btn_speaker);
    speakerButtonView.setOnClickListener(this);
    speakerButtonView.setIcon(R.drawable.baseline_volume_up_24);
    speakerButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.RIGHT | Gravity.BOTTOM));

    // XGram: with video, four cells [Mute][Video][Message][Speaker | Flip], centered at W * (2i + 1) / 8
    final boolean xgramVideoRow = com.fahadali.xgram.call.XgramCallVideo.isAvailable();
    final ButtonView videoButtonView;
    if (xgramVideoRow) {
      videoButtonView = new ButtonView(context);
      videoButtonView.setId(R.id.btn_video);
      videoButtonView.setOnClickListener(this);
      videoButtonView.setIcon(R.drawable.baseline_videocam_24);
      videoButtonView.setContentDescription(Lang.getString(R.string.Video));
      videoButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.LEFT | Gravity.BOTTOM));
    } else {
      videoButtonView = null;
    }
    xgramVideoButtonView = videoButtonView;

    buttonWrap = new FrameLayoutFix(context) {
      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (videoButtonView == null) {
          return;
        }
        final int width = right - left;
        final int cellBottom = (bottom - top) - getPaddingBottom();
        layoutCell(muteButtonView, width, 0, cellBottom);
        layoutCell(videoButtonView, width, 1, cellBottom);
        layoutCell(messageButtonView, width, 2, cellBottom);
        layoutCell(speakerButtonView, width, 3, cellBottom);
      }

      private void layoutCell (View view, int width, int index, int cellBottom) {
        final int cx = width * (2 * index + 1) / 8;
        final int w = view.getMeasuredWidth(), h = view.getMeasuredHeight();
        view.layout(cx - w / 2, cellBottom - h, cx - w / 2 + w, cellBottom);
      }
    };
    buttonWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(76f) + extraBottomInset, Gravity.BOTTOM));
    buttonWrap.addView(muteButtonView);
    if (videoButtonView != null) {
      buttonWrap.addView(videoButtonView);
    }
    buttonWrap.addView(messageButtonView);
    buttonWrap.addView(speakerButtonView);
    Views.setPaddingBottom(buttonWrap, extraBottomInset);
    Drawable drawable = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.BOTTOM, false);
    drawable.setAlpha((int) (255f * .3f));
    ViewUtils.setBackground(buttonWrap, drawable);
    contentView.addView(buttonWrap);

    // Answer controls

    callControlsLayout = new CallControlsLayout(context, this);
    Views.setPaddingBottom(callControlsLayout, extraBottomInset);
    callControlsLayout.setCallback(this);
    callControlsLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(callControlsLayout);
    callControlsLayout.setCall(tdlib, call, false);

    // XGram: video (the stage over the photo, under the texts; the tile above everything)
    if (xgramVideoRow) {
      xgramClassicLayer = new com.fahadali.xgram.call.XgramCallVideoLayer(context, com.fahadali.xgram.call.XgramCallVideoLayer.STYLE_CLASSIC, (presentation, animated) -> xgramOnClassicPresentation(presentation, animated));
      xgramClassicLayer.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      contentView.addView(xgramClassicLayer, contentView.indexOfChild(avatarView) + 1);
      xgramClassicTile = new com.fahadali.xgram.appearance.XgramM3CallVideoTile(context, false, () -> {
        if (xgramClassicLayer != null) {
          xgramClassicLayer.toggleSwap();
        }
      });
      contentView.addView(xgramClassicTile);
      xgramClassicLayer.setTile(xgramClassicTile);
      contentView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> xgramLayoutClassicVideo());
    }

    // XGram: tabletop posture
    contentView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
        v.post(this::xgramUpdateFold);
      }
    });
    if (context().xgramWindow() != null) {
      context().xgramWindow().addListener(xgramWindowListener);
    }

    // Data

    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.cache().addUserDataListener(call.userId, this);

    this.callSettings = tdlib.cache().getCallSettings(call.id);

    setTexts();
    updateCallState();

    if (callSettings != null) {
      muteButtonView.setIsActive(callSettings.isMicMuted(), false);
      speakerButtonView.setIsActive(callSettings.isSpeakerModeEnabled(), false);
    }
    xgramBindVideo();

    return contentView;
  }


  // XGram: Material 3 Expressive call screen (com.fahadali.xgram.appearance.XgramM3CallScreen). Every Telegram X
  // method below that touches a view checks xgramScreen first; the call logic (settings, accept, hang up, redial,
  // emoji, rating) stays Telegram X's.

  private View xgramCreateView (Context context) {
    final com.fahadali.xgram.appearance.XgramM3CallScreen screen = new com.fahadali.xgram.appearance.XgramM3CallScreen(context, tdlib, xgramCallback);
    xgramScreen = screen;
    screen.setTopInset(Screen.getStatusBarHeight());
    screen.setBottomInset(extraBottomInset);
    screen.setBrandClickable(Log.checkLogLevel(Log.LEVEL_INFO) || BuildConfig.EXPERIMENTAL);
    addThemeInvalidateListener(screen);
    Screen.addStatusBarHeightListener(this);

    // Tabletop posture
    screen.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
        v.post(this::xgramUpdateFold);
      }
    });
    if (context().xgramWindow() != null) {
      context().xgramWindow().addListener(xgramWindowListener);
    }

    // Data
    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.cache().addUserDataListener(call.userId, this);
    this.callSettings = tdlib.cache().getCallSettings(call.id);

    screen.setVideoAvailable(com.fahadali.xgram.call.XgramCallVideo.isAvailable());
    screen.setCall(call, false);
    setTexts();
    updateCallState();
    updateCallButtons();
    xgramBindVideo();
    return screen;
  }

  private final com.fahadali.xgram.appearance.XgramM3CallScreen.Callback xgramCallback = new com.fahadali.xgram.appearance.XgramM3CallScreen.Callback() {
    @Override
    public void onMuteClick () {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        boolean muted = !callSettings.isMicMuted();
        if (xgramScreen != null) {
          xgramScreen.setMuted(muted, true);
        }
        callSettings.setMicMuted(muted);
      }
    }

    @Override
    public void onSpeakerClick () {
      xgramToggleAudioRoute();
    }

    @Override
    public void onVideoClick () {
      xgramOnVideoClick();
    }

    @Override
    public void onFlipClick () {
      if (xgramVideo != null) {
        xgramVideo.flipCamera();
      }
    }

    @Override
    public void onAudioRouteClick () {
      xgramToggleAudioRoute();
    }

    @Override
    public void onAnswerVideoChanged (boolean on) {
      // The preview while it rings (only with the camera already allowed: nothing is asked for here)
      if (xgramVideo != null && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing) {
        xgramVideo.setWantsVideo(on && call.isVideo);
      }
    }

    @Override
    public void onVideoPresentationChanged (boolean video) {
      xgramScreenShowsVideo = video;
      xgramUpdateStatusBar();
    }

    @Override
    public void onMessageClick () {
      tdlib.ui().openPrivateChat(CallController.this, call.userId, null);
    }

    @Override
    public void onBrandClick () {
      xgramToggleDebug();
    }

    @Override
    public void onMinimize () {
      // As the system back: the chats come back and the call goes on (the call bar reopens this screen)
      if (navigationController != null && navigationController.getStackSize() > 1) {
        navigateBack();
      } else {
        context().performBackPress(false);
      }
    }

    @Override
    public boolean onAnswer () {
      // Without the microphone (or, for video, the camera) permission it is asked for first, and the call is answered
      // once the answer comes; false then, so the pill springs back
      return xgramAnswer();
    }

    @Override
    public void onDecline (boolean isHangUp) {
      CallController.this.onCallDecline(call, isHangUp);
    }

    @Override
    public void onRedial () {
      CallController.this.onCallRestart(call);
    }

    @Override
    public void onDismiss () {
      CallController.this.onCallClose(call);
    }
  };

  /** The call log overlay of debug builds, as Telegram X's branding click, in the M3 roles. */
  private void xgramToggleDebug () {
    final com.fahadali.xgram.appearance.XgramM3CallScreen screen = xgramScreen;
    if (screen == null) {
      return;
    }
    TGCallService.markLogViewed();
    if (debugView != null) {
      screen.removeView(debugView);
      debugView = null;
      screen.setDebugShown(false);
      return;
    }
    final TextView view = new TextView(context());
    view.setScrollDisabled(true);
    view.setBackgroundColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST), .94f));
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    view.setGravity(Gravity.CENTER_VERTICAL);
    view.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
    view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
    view.post(new Runnable() {
      @Override
      public void run () {
        TGCallService service = TGCallService.currentInstance();
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (service != null) {
          b.append(service.getLibraryNameAndVersion());
        } else {
          b.append("service unavailable");
        }
        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoBold(), 0), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (service != null) {
          CharSequence log = service.getDebugString();
          if (!StringUtils.isEmpty(log)) {
            b.append("\n\n");
            b.append(log);
          }
        }
        view.setText(b);
        if (view.getParent() != null) {
          view.postDelayed(this, 500l);
        }
      }
    });
    debugView = view;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      view.setTranslationZ(Screen.dp(8f)); // above the video tile and the chrome
    }
    screen.addView(debugView);
    screen.setDebugShown(true);
  }

  /** Pending (both ways), exchanging keys, or ready but not connected yet. */
  private boolean xgramInProgress () {
    switch (call.state.getConstructor()) {
      case TdApi.CallStatePending.CONSTRUCTOR:
      case TdApi.CallStateExchangingKeys.CONSTRUCTOR:
        return true;
      case TdApi.CallStateReady.CONSTRUCTOR:
        return callDuration < 0;
    }
    return false;
  }

  /** The status in sentence case (no upper case), with an ellipsis while an outgoing call is being set up. */
  private String xgramStatus (String str) {
    boolean incoming = call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing;
    if (xgramInProgress() && !incoming && !StringUtils.isEmpty(str) && !str.endsWith(Strings.ELLIPSIS) && !str.endsWith("...")) {
      return str + Strings.ELLIPSIS;
    }
    return str;
  }

  private void setTexts () {
    if (xgramScreen != null) { // XGram
      TdApi.User fresh = tdlib.cache().user(call.userId);
      if (fresh != null) {
        user = fresh;
      }
      xgramScreen.setUser(user);
      xgramScreen.setKeyHint(Lang.getString(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
      return;
    }
    if (emojiStatusHelper != null) {
      this.emojiStatusHelper.updateEmoji(tdlib, user, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.white);
        }
      }, R.drawable.baseline_premium_star_28, 32);
    }
    if (nameView != null) {
      this.nameView.setText(TD.getUserName(user));
      this.nameView.setPadding(0, 0, user != null && user.isPremium ? emojiStatusHelper.getWidth(Screen.dp(7)) : 0, 0);
      this.nameView.requestLayout();
    }
    if (emojiViewHint != null)
      this.emojiViewHint.setText(Lang.getString(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  @Override
  public void onCallAccept (TdApi.Call call) {
    if (xgramScreen == null && xgramVideo != null && call.isVideo && call.id == this.call.id) {
      xgramAnswer(); // XGram: Telegram X's answer of a video call asks for the camera too
      return;
    }
    tdlib.context().calls().acceptCall(context(), tdlib, call.id);
  }

  @Override
  public void onCallDecline (TdApi.Call call, boolean isHangUp) {
    tdlib.context().calls().hangUp(tdlib, call.id);
  }

  @Override
  public void onCallRestart (TdApi.Call call) {
    // XGram: call again the way it was (video once it was a video call or used video)
    tdlib.context().calls().makeVideoCall(this, call.userId, null, call.isVideo || xgramVideoUsed);
  }

  public boolean compareUserId (long userId) {
    return call.userId == userId;
  }

  @Override
  public void onCallClose (TdApi.Call call) {
    closeCall();
  }

  // XGram: the portrait lock is undone by whoever set it, even if the phone was unfolded in between (UI.isTablet()
  // changes then), and it is dropped as soon as the window is tablet sized.
  private boolean xgramLockedPortrait;

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (!UI.isTablet()) {
      context().setOrientation(BaseActivity.getAndroidOrientationPortrait());
      xgramLockedPortrait = true;
    }
  }

  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    xgramUnlockPortrait();
  }

  private void xgramUnlockPortrait () {
    if (xgramLockedPortrait) {
      xgramLockedPortrait = false;
      context().setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
  }

  // XGram: tabletop posture (Flex mode): the photo stays above the fold, the buttons below it.
  private final com.fahadali.xgram.layout.XgramWindow.Listener xgramWindowListener = (window, changes) -> {
    if (UI.isTablet()) {
      xgramUnlockPortrait();
    }
    xgramUpdateFold();
    if ((changes & com.fahadali.xgram.layout.XgramWindow.CHANGE_SIZE_CLASS) != 0) {
      // The cover and the inner selfie cameras are different sensors: the front camera opens again on the new one
      UI.removePendingRunnable(this.xgramReopenCamera);
      UI.post(this.xgramReopenCamera, 300L);
    }
  };

  private final Runnable xgramReopenCamera = () -> {
    if (!isDestroyed() && this.xgramVideo != null) {
      this.xgramVideo.reopenFrontCamera();
    }
  };

  private void xgramUpdateFold () {
    if (xgramScreen != null) {
      if (!isDestroyed()) {
        xgramScreen.setFoldTop(com.fahadali.xgram.layout.XgramWindow.tabletopFoldTop(xgramScreen));
        xgramUpdateStatusBar(); // the screen may have become a pane, or the whole window
      }
      return;
    }
    if (avatarView == null || isDestroyed() || !(avatarView.getParent() instanceof View)) {
      return;
    }
    View parent = (View) avatarView.getParent();
    int foldTop = com.fahadali.xgram.layout.XgramWindow.tabletopFoldTop(parent);
    Views.setBottomMargin(avatarView, foldTop > 0 ? parent.getHeight() - foldTop : 0);
  }

  private void updateLoop () {
    setIsLooping(!isDestroyed() && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
  }

  private boolean isLooping;

  private void setIsLooping (boolean isLooping) {
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (isLooping) {
        UI.post(this);
      } else {
        UI.removePendingRunnable(this);
      }
    }
  }

  @Override
  public void run () {
    if (!isDestroyed()) {
      updateCallState();
      if (isLooping) {
        UI.post(this, tdlib.context().calls().getTimeTillNextCallDurationUpdate(tdlib, call.id));
      }
    }
  }

  private boolean buttonsVisible;
  private FactorAnimator buttonsAnimator;
  private static final int ANIMATOR_BUTTONS_ID = 0;
  private float buttonsFactor;

  private void setButtonsVisible (boolean areVisible, boolean animated) {
    if (this.buttonsVisible != areVisible) {
      this.buttonsVisible = areVisible;
      if (animated) {
        if (buttonsAnimator == null) {
          buttonsAnimator = new FactorAnimator(ANIMATOR_BUTTONS_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.buttonsFactor);
        }
        buttonsAnimator.animateTo(areVisible ? 1f : 0f);
      } else {
        if (buttonsAnimator != null) {
          buttonsAnimator.forceFactor(areVisible ? 1f : 0f);
        }
        setButtonsFactor(areVisible ? 1f : 0f);
      }
    }
  }

  private void setButtonsFactor (float factor) {
    this.buttonsFactor = factor;
    updateControlsAlpha();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BUTTONS_ID: {
        setButtonsFactor(factor);
        break;
      }
      case ANIMATOR_FLASH_ID: {
        stateView.setAlpha(factor <= .5f ? 1f - (factor / .5f) : (factor - .5f) / .5f);
        break;
      }
      case ANIMATOR_EMOJI_VISIBILITY_ID: {
        setEmojiVisibilityFactor(factor);
        break;
      }
      case ANIMATOR_EMOJI_EXPAND_ID: {
        setEmojiExpandFactor(Math.max(0f, factor));
        break;
      }
      case ANIMATOR_STRENGTH: {
        if (strengthView != null) {
          strengthView.setAlpha(factor);
        }
        break;
      }
      case ANIMATOR_XGRAM_AVATAR: {
        xgramSetClassicVideoFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FLASH_ID: {
        if (finalFactor == 1f) {
          flashLimiter.run();
        }
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_emoji) {
      if (isEmojiVisible) {
        setEmojiExpanded(true);
      }
    } else if (viewId == R.id.btn_mute) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setMicMuted(((ButtonView) v).toggleActive());
      }
    } else if (viewId == R.id.btn_openChat) {
      tdlib.ui().openPrivateChat(this, call.userId, null);
    } else if (viewId == R.id.btn_speaker) {
      if (xgramVideo != null && xgramVideo.isCameraOn()) {
        xgramVideo.flipCamera(); // XGram: the cell flips the camera while it is on
        return;
      }
      xgramToggleAudioRoute();
    } else if (viewId == R.id.btn_video) {
      xgramOnVideoClick(); // XGram
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setTexts();
      }
    });
  }

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (!isDestroyed()) {
      updateCall(call);
      updateCallState();
    }
  }

  private boolean isClosed;

  private void closeCall () {
    isClosed = true;
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    navigateBack();
  }

  private TdApi.CallState previousCallState;

  private void updateCall (TdApi.Call call) {
    if (isClosed) {
      return;
    }
    this.previousCallState = this.call.state;
    boolean prevIsActive = this.call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    boolean callEnded = call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR || (call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonHungUp.CONSTRUCTOR);

    this.call = call;
    this.callDuration = 0;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    updateCallStrength();
    if (TD.isCancelled(call) || TD.isAcceptedOnOtherDevice(call) || TD.isDeclined(call) || (prevIsActive && callEnded) || TD.isMissed(call) || call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      closeCall();
    } else if (xgramScreen != null) { // XGram
      xgramScreen.setCall(call, navigationController != null);
    } else {
      callControlsLayout.setCall(tdlib, call, navigationController != null);
    }
    xgramSyncVideo();
  }

  @Override
  public void onCallStateChanged (final int callId, final int newState) {
    if (!isDestroyed()) {
      updateCallState();
    }
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) {
    if (!isDestroyed() && this.call != null && this.call.id == callId) {
      setCallBarsCount(barsCount);
    }
  }

  @Override
  public void onCallSettingsChanged (final int callId, final CallSettings settings) {
    if (!isDestroyed()) {
      callSettings = settings;
      updateCallButtons();
    }
  }

  private boolean isFlashing;
  private FactorAnimator flashAnimator;
  private static final int ANIMATOR_FLASH_ID = 1;

  public static final long CALL_FLASH_DURATION = 1100;
  public static final long CALL_FLASH_DELAY = 650l;

  private final RateLimiter flashLimiter = new RateLimiter(() -> {
    if (isFlashing) {
      flashAnimator.forceFactor(0f);
      if (isFlashing) {
        flashAnimator.animateTo(1f);
      }
    }
  }, 100l, null);

  private void setFlashing (boolean isFlashing) {
    if (this.isFlashing != isFlashing) {
      this.isFlashing = isFlashing;
      if (isFlashing) {
        if (flashAnimator == null) {
          flashAnimator = new FactorAnimator(ANIMATOR_FLASH_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CALL_FLASH_DURATION);
          flashAnimator.setStartDelay(CALL_FLASH_DELAY);
        }
        if (!flashAnimator.isAnimating()) {
          flashAnimator.forceFactor(0f);
          flashAnimator.animateTo(1f);
        }
      } else {
        if (flashAnimator != null && flashAnimator.getFactor() == 0f) {
          flashAnimator.forceFactor(0f);
        }
      }
    }
  }

  private long callDuration;

  private void updateCallState () {
    updateLoop();
    String str;
    callDuration = tdlib.context().calls().getCallDuration(tdlib, call.id);
    if (previousCallState != null && call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      str = TD.getCallState2(call, previousCallState, callDuration, false);
    } else {
      str = TD.getCallState(call, callDuration, false); // XGram: "Incoming video call" for one
      if (xgramVideo != null && xgramVideo.remoteVideoState() == org.thunderdog.challegram.voip.annotation.VideoState.PAUSED && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR) {
        str = str + " \u00b7 " + Lang.getString(R.string.XgramVideoPaused); // XGram: the other side paused its video
      }
      if (!call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && tdlib.context().isMultiUser()) {
        String longName = tdlib.accountLongName();
        if (longName != null) {
          str = str + "\n" + Lang.getString(R.string.VoipAnsweringAsAccount, longName);
        }
      }
    }
    if (xgramScreen != null) { // XGram
      xgramScreen.setStatus(xgramStatus(str), xgramInProgress());
      xgramScreen.setTogglesVisible(!TD.isFinished(call) && !(call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing), isFocused());
      xgramScreen.setConnected(call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR && callDuration >= 0);
    } else {
      stateView.setText(Lang.uppercase(str));
      setButtonsVisible(!TD.isFinished(call) && !(call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing), isFocused());
    }
    updateEmoji();
    updateFlashing();
    updateCallStrength();
  }

  private boolean hadFocus;

  private void updateFlashing () {
    if (xgramScreen != null) { // XGram: the avatar halo pulses instead of the status flashing
      hadFocus = isFocused() || hadFocus;
      xgramScreen.setRinging(TD.getCallNeedsFlashing(call), isFocused());
      return;
    }
    hadFocus = isFocused() || hadFocus;
    setFlashing(TD.getCallNeedsFlashing(call) && hadFocus);
  }

  @Override
  protected void onFocusStateChanged () {
    updateFlashing();
    xgramUpdateVisible(); // XGram: the camera runs only while you can see this screen
  }

  private void updateControlsAlpha () {
    float alpha = lastHeaderFactor * buttonsFactor;
    buttonWrap.setAlpha(alpha);
    buttonWrap.setTranslationY((1f - lastHeaderFactor) * buttonWrap.getMeasuredHeight() * .2f);
  }

  private void updateCallButtons () {
    if (xgramScreen != null) { // XGram
      xgramScreen.setMuted(callSettings != null && callSettings.isMicMuted(), isFocused());
      xgramScreen.setAudioRoute(callSettings != null && callSettings.getSpeakerMode() == CallSettings.SPEAKER_MODE_BLUETOOTH, callSettings != null && callSettings.isSpeakerModeEnabled(), isFocused());
      return;
    }
    if (buttonWrap != null) {
      muteButtonView.setIsActive(callSettings != null && callSettings.isMicMuted(), isFocused());
      xgramUpdateClassicButtons(isFocused());
    }
  }

  private void updateEmoji () {
    if (xgramScreen != null) { // XGram: the key chip and card
      boolean visible = call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
      boolean newKey = false;
      if (visible && !xgramScreen.hasKey()) {
        xgramScreen.setKey(((TdApi.CallStateReady) call.state).emojis);
        newKey = xgramScreen.hasKey();
      }
      xgramScreen.setKeyVisible(visible, isFocused());
      if (newKey && !hadEmojiSinceStart) {
        showEmojiTooltip();
      }
      return;
    }
    boolean emojiVisible = (call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
    if (emojiVisible && StringUtils.isEmpty(emojiViewSmall.getText())) {

      TdApi.CallStateReady ready = (TdApi.CallStateReady) call.state;

      StringBuilder b = new StringBuilder();
      for (String emoji : ready.emojis) {
        if (b.length() > 0) {
          b.append("  ");
        }
        b.append(emoji);
      }

      CharSequence result = Emoji.instance().replaceEmoji(b.toString());

      emojiViewSmall.setText(result);
      emojiViewBig.setText(result);

      if (!hadEmojiSinceStart) {
        showEmojiTooltip();
      }
    }
    updateEmojiFactors();
    setEmojiVisible(emojiVisible, isFocused());
  }

  private void showEmojiTooltip () {
    context().tooltipManager().builder(xgramScreen != null ? xgramScreen.getKeyAnchor() : emojiViewSmall).controller(this).show(tdlib, Lang.getStringBold(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  private boolean isEmojiVisible;
  private FactorAnimator emojiVisibilityAnimator;

  private static final int ANIMATOR_EMOJI_VISIBILITY_ID = 3;
  private static final int ANIMATOR_EMOJI_EXPAND_ID = 4;

  private void setEmojiVisible (boolean isVisible, boolean animated) {
    if (this.isEmojiVisible != isVisible) {
      this.isEmojiVisible = isVisible;
      float toFactor = isVisible ? 1f : 0f;

      if (animated) {
        if (emojiVisibilityAnimator == null) {
          emojiVisibilityAnimator = new FactorAnimator(ANIMATOR_EMOJI_VISIBILITY_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION_EMOJI_VISIBILITY, this.emojiVisibilityFactor);
        }
        emojiVisibilityAnimator.animateTo(toFactor);
      } else {
        if (emojiVisibilityAnimator != null) {
          emojiVisibilityAnimator.forceFactor(toFactor);
        }
        setEmojiVisibilityFactor(toFactor);
      }
    }
  }

  private static final long DURATION_EMOJI_VISIBILITY = 180l;

  private boolean isEmojiExpanded;
  private FactorAnimator emojiExpandAnimator;

  private void setEmojiExpanded (boolean isExpanded) {
    if (this.isEmojiExpanded != isExpanded) {
      this.isEmojiExpanded = isExpanded;
      if (emojiExpandAnimator == null) {
        emojiExpandAnimator = new FactorAnimator(ANIMATOR_EMOJI_EXPAND_ID, this, new OvershootInterpolator(1.02f), 310l, this.emojiExpandFactor);
      }
      emojiExpandAnimator.animateTo(isExpanded ? 1f : 0f);
    }
  }

  private float emojiVisibilityFactor;
  private float emojiExpandFactor;

  private void setEmojiVisibilityFactor (float factor) {
    if (this.emojiVisibilityFactor != factor) {
      this.emojiVisibilityFactor = factor;
      updateEmojiFactors();
    }
  }

  private void setEmojiExpandFactor (float factor) {
    if (this.emojiExpandFactor != factor) {
      this.emojiExpandFactor = factor;
      updateEmojiFactors();
    }
  }

  private static final float DESIRED_EMOJI_EXPAND_FACTOR = 2.25f;
  private static float EMOJI_EXPAND_FACTOR = DESIRED_EMOJI_EXPAND_FACTOR;

  private void updateEmojiFactors () {
    emojiViewSmall.setAlpha(MathUtils.clamp(emojiVisibilityFactor * (1f - Math.max(1f - lastHeaderFactor, emojiExpandFactor >= .5f ? (emojiExpandFactor - .5f) / .5f : 0f))));
    emojiViewSmall.setScaleX(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));
    emojiViewSmall.setScaleY(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));

    float bigAlpha = MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor);
    emojiViewBig.setAlpha(bigAlpha);
    emojiViewHint.setAlpha(bigAlpha);
    float factor = 1f / EMOJI_EXPAND_FACTOR;
    emojiViewBig.setScaleX(factor + (1f - factor) * emojiExpandFactor);
    emojiViewBig.setScaleY(factor + (1f - factor) * emojiExpandFactor);
    avatarView.setMainAlpha(1f - MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor));
    updateEmojiPosition();
  }

  private void updateEmojiPosition () {
    final int parentWidth = ((View) emojiViewBig.getParent()).getMeasuredWidth();
    final int parentHeight = ((View) emojiViewBig.getParent()).getMeasuredHeight();

    final int viewWidth = emojiViewBig.getMeasuredWidth();
    final int viewHeight = emojiViewBig.getMeasuredHeight();

    final int viewWidthSmall = emojiViewSmall.getMeasuredWidth();
    final int viewHeightSmall = emojiViewSmall.getMeasuredHeight();

    final int startLeft = parentWidth - viewWidthSmall;
    final int startTop = Screen.dp(42f) - emojiViewSmall.getPaddingTop();

    final int fromCenterX = startLeft + viewWidthSmall / 2;
    final int fromCenterY = startTop + viewHeightSmall / 2;

    final int toCenterX = parentWidth / 2;
    final int toCenterY = parentHeight / 2 - Screen.dp(24f);

    final int centerX = (int) (fromCenterX + (float) (toCenterX - fromCenterX) * emojiExpandFactor);
    final int centerY = (int) (fromCenterY + (float) (toCenterY - fromCenterY) * emojiExpandFactor);

    int x = centerX - viewWidth / 2;
    int y = centerY - viewHeight / 2;

    emojiViewBig.setTranslationX(x);
    emojiViewBig.setTranslationY(y);

    int xSmall = centerX - viewWidthSmall / 2;
    int ySmall = centerY - viewHeightSmall / 2;

    emojiViewSmall.setTranslationX(xSmall);
    emojiViewSmall.setTranslationY(ySmall);
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      destroyStackItemByIdExcludingLast(R.id.controller_call);
      ViewController<?> c = previousStackItem();
      if (c != null && c.getId() == R.id.controller_contacts) {
        destroyStackItemById(R.id.controller_contacts);
      }
      oneShot = true;
    }
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
    xgramCheckAnswerIntent(); // XGram: "Answer" in the notification of a video call
  }

  @Override
  protected int getPopupRestoreColor () {
    if (xgramScreen != null) { // XGram
      return com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE);
    }
    return 0xff000000;
  }

  @Override
  protected boolean forceFadeMode () {
    ViewController<?> c = previousStackItem();
    return c != null && c.getId() == R.id.controller_call;
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (xgramScreen != null && xgramScreen.isKeyExpanded()) { // XGram M3E: back closes the key card first
      if (commit) {
        xgramScreen.setKeyExpanded(false);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  public void replaceCall (TdApi.Call call) {
    final boolean newCall = this.call.id != call.id;
    tdlib.cache().unsubscribeFromCallUpdates(this.call.id, this);
    previousCallState = null;
    if (newCall) {
      xgramUnbindVideo(); // XGram: the old call's video goes at once (voice, no animation)
    }
    updateCall(call);
    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
    xgramBindVideo();
    updateCallState();
    xgramCheckAnswerIntent();
  }

  @Override
  public void destroy () {
    super.destroy();
    UI.removePendingRunnable(xgramReopenCamera);
    xgramUnbindVideo(); // XGram
    if (xgramAvatarFade != null) {
      xgramAvatarFade.cancel();
    }
    Screen.removeStatusBarHeightListener(this);
    if (context().xgramWindow() != null) {
      context().xgramWindow().removeListener(xgramWindowListener); // XGram
    }
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    tdlib.cache().removeUserDataListener(call.userId, this);
    if (xgramScreen != null) { // XGram
      xgramScreen.performDestroy();
    } else {
      avatarView.performDestroy();
    }
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }

  // XGram: video calls (com.fahadali.xgram.call.XgramCallVideo). The call's video lives in CallManager; this screen
  // shows it, says when it is visible (the camera runs only then), and runs the permission flows of the Video toggle and
  // of answering.

  private @Nullable com.fahadali.xgram.call.XgramCallVideo xgramVideo;
  private boolean xgramVideoUsed, xgramPeerRequested, xgramLightStatusOverridden;

  private final com.fahadali.xgram.call.XgramCallVideo.Listener xgramVideoListener = new com.fahadali.xgram.call.XgramCallVideo.Listener() {
    @Override
    public void onCallVideoChanged (com.fahadali.xgram.call.XgramCallVideo video) {
      if (video != xgramVideo || isDestroyed()) {
        return;
      }
      if (video.wasVideoUsed()) {
        xgramVideoUsed = true;
      }
      if (video.isReleased()) {
        xgramUnbindVideo();
        return;
      }
      if (xgramClassicLayer != null) {
        xgramClassicLayer.update(isFocused());
      }
      xgramUpdateClassicButtons(isFocused());
      xgramUpdateBrand();
      updateCallState(); // the paused video in the status
    }

    @Override
    public void onCameraFailed (com.fahadali.xgram.call.XgramCallVideo video) {
      if (video == xgramVideo && !isDestroyed()) {
        org.thunderdog.challegram.telegram.CallManager.xgramNotice(Lang.getString(R.string.XgramVideoCameraFailed), null, null);
      }
    }

    @Override
    public void onCameraSwitched (com.fahadali.xgram.call.XgramCallVideo video, boolean front, boolean ok) {
      if (video != xgramVideo || isDestroyed()) {
        return;
      }
      if (!ok) {
        org.thunderdog.challegram.telegram.CallManager.xgramNotice(Lang.getString(R.string.XgramVideoCameraFailed), null, null);
      }
      xgramUpdateClassicButtons(isFocused());
    }
  };

  private void xgramBindVideo () {
    final com.fahadali.xgram.call.XgramCallVideo video = tdlib.context().calls().video(call.id);
    if (video == xgramVideo) {
      xgramSyncVideo();
      return;
    }
    xgramUnbindVideo();
    xgramVideo = video;
    if (video == null) {
      return;
    }
    if (video.wasVideoUsed()) {
      xgramVideoUsed = true;
    }
    video.addListener(xgramVideoListener);
    if (xgramScreen != null) {
      xgramScreen.setVideo(video);
    } else if (xgramClassicLayer != null) {
      xgramClassicLayer.setPeerName(TD.getUserName(user));
      if (xgramClassicTile != null) {
        xgramClassicTile.setPeerName(TD.getUserName(user));
      }
      xgramClassicLayer.setVideo(video, false);
    }
    xgramFetchPeer();
    xgramSyncVideo();
    xgramUpdateVisible();
    xgramUpdateClassicButtons(false);
    xgramUpdateBrand();
  }

  private void xgramUnbindVideo () {
    final com.fahadali.xgram.call.XgramCallVideo video = xgramVideo;
    if (video == null) {
      return;
    }
    xgramVideo = null;
    video.removeListener(xgramVideoListener);
    video.setScreenVisible(this, false);
    if (xgramScreen != null) {
      xgramScreen.setVideo(null);
    }
    if (xgramClassicLayer != null) {
      xgramClassicLayer.setVideo(null, false);
    }
    xgramUpdateStatusBar();
  }

  /** While an incoming video call rings: the preview follows the answer-with-video toggle (Expressive only). */
  private void xgramSyncVideo () {
    final com.fahadali.xgram.call.XgramCallVideo video = xgramVideo;
    if (video == null || video.isReleased()) {
      return;
    }
    if (xgramScreen != null && !call.isOutgoing && call.isVideo && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
      video.setWantsVideo(xgramScreen.isAnswerWithVideo());
    }
  }

  private void xgramUpdateVisible () {
    if (xgramVideo != null) {
      xgramVideo.setScreenVisible(this, isFocused() && !isDestroyed());
    }
    xgramUpdateStatusBar();
  }

  /** Whether the other side's app can do video (the Video toggle's disabled look), once per screen. */
  private void xgramFetchPeer () {
    final TdApi.UserFullInfo cached = tdlib.cache().userFull(call.userId);
    if (cached != null) {
      xgramSetPeerSupportsVideo(cached.supportsVideoCalls);
      return;
    }
    if (xgramPeerRequested) {
      return;
    }
    xgramPeerRequested = true;
    final long userId = call.userId;
    tdlib.send(new TdApi.GetUserFullInfo(userId), (userFull, error) -> {
      if (userFull != null) {
        tdlib.ui().post(() -> {
          if (!isDestroyed() && call.userId == userId) {
            xgramSetPeerSupportsVideo(userFull.supportsVideoCalls);
          }
        });
      }
    });
  }

  private void xgramSetPeerSupportsVideo (boolean supports) {
    if (xgramVideo != null) {
      xgramVideo.setPeerSupportsVideo(supports);
    }
    if (xgramScreen != null) {
      xgramScreen.setPeerSupportsVideo(supports);
    }
    xgramUpdateClassicButtons(isFocused());
  }

  /** The speaker or the audio route (you chose it: the speaker that video turned on stays as you leave it). */
  private void xgramToggleAudioRoute () {
    if (TD.isFinished(call)) {
      return;
    }
    if (xgramVideo != null) {
      xgramVideo.autoSpeaker = false;
    }
    if (callSettings == null) {
      callSettings = new CallSettings(tdlib, call.id);
    }
    if (callSettings.isSpeakerModeEnabled()) {
      callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
    } else {
      callSettings.toggleSpeakerMode(this);
    }
  }

  /** The Video toggle: off; or on, once the other side can take it and the camera is allowed. */
  private void xgramOnVideoClick () {
    final com.fahadali.xgram.call.XgramCallVideo video = xgramVideo;
    if (video == null || TD.isFinished(call)) {
      return;
    }
    if (video.wantsVideo()) {
      video.setWantsVideo(false);
      return;
    }
    if (Boolean.FALSE.equals(video.peerSupportsVideo()) && !call.isVideo) {
      org.thunderdog.challegram.telegram.CallManager.xgramNotice(Lang.getStringBold(R.string.XgramVideoUnsupportedPeer, TD.getUserSingleName(call.userId, user)), null, null);
      return;
    }
    if (com.fahadali.xgram.call.XgramCallVideo.cameraGranted()) {
      video.setWantsVideo(true);
      return;
    }
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
      return;
    }
    final BaseActivity activity = context();
    activity.requestCustomPermissions(new String[] {android.Manifest.permission.CAMERA}, (code, permissions, grantResults, grantCount) -> {
      if (isDestroyed() || video.isReleased()) {
        return;
      }
      if (com.fahadali.xgram.call.XgramCallVideo.cameraGranted()) {
        video.setWantsVideo(true);
      } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
        tdlib.context().calls().showCameraPermissionAlert(); // denied for good: [Not now] [Open settings]
      }
      // Denied this time: nothing, you just said no
    });
  }

  /**
   * Answers the incoming call: with video for a video call (with Expressive design: as the toggle above the pill says),
   * asking for the microphone and the camera first when needed. True when it was answered right away; false when a
   * permission request went out (the answer comes with its result) or it could not be answered.
   */
  private boolean xgramAnswer () {
    final TdApi.Call target = call;
    final com.fahadali.xgram.call.XgramCallVideo video = xgramVideo;
    final boolean withVideo = target.isVideo && video != null && (xgramScreen == null || xgramScreen.isAnswerWithVideo());
    final boolean[] answered = new boolean[1];
    tdlib.context().calls().checkCallPermissions(context(), withVideo, (mic, camera, cameraPermanentlyDenied) -> {
      if (!mic) {
        tdlib.context().calls().showMicrophonePermissionAlert();
        return;
      }
      final TdApi.Call fresh = tdlib.cache().getCall(target.id);
      if (fresh == null || fresh.state.getConstructor() != TdApi.CallStatePending.CONSTRUCTOR || fresh.isOutgoing) {
        return;
      }
      if (video != null && !video.isReleased()) {
        video.setWantsVideo(withVideo && camera);
      }
      if (withVideo && !camera) {
        org.thunderdog.challegram.telegram.CallManager.xgramCameraNotice(Lang.getString(R.string.XgramVideoAnsweredNoCamera), cameraPermanentlyDenied);
      }
      answered[0] = true;
      tdlib.context().calls().acceptCall(context(), tdlib, fresh.id);
    });
    return answered[0];
  }

  /** "Answer" in the notification of a video call opens this screen with the call's token: it answers here. */
  private void xgramCheckAnswerIntent () {
    if (isDestroyed() || !isFocused()) {
      return;
    }
    final android.content.Intent intent = context().getIntent();
    if (intent == null) {
      return;
    }
    final long token = intent.getLongExtra(com.fahadali.xgram.call.XgramCallVideo.EXTRA_ANSWER_VIDEO, 0L);
    if (token == 0L) {
      return;
    }
    final int callId = intent.getIntExtra(com.fahadali.xgram.call.XgramCallVideo.EXTRA_ANSWER_CALL_ID, 0);
    intent.removeExtra(com.fahadali.xgram.call.XgramCallVideo.EXTRA_ANSWER_VIDEO);
    intent.removeExtra(com.fahadali.xgram.call.XgramCallVideo.EXTRA_ANSWER_CALL_ID);
    final com.fahadali.xgram.call.XgramCallVideo video = xgramVideo;
    if (video != null && callId == call.id && token == video.answerToken() && !call.isOutgoing &&
      call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
      xgramAnswer();
    }
  }

  /**
   * White status bar icons over video (Expressive), while this screen is focused and as wide as the window (in two
   * panes the strip above the pane stays surface, with the theme's icons): an override of the activity's, which every
   * later update of the bars keeps, until this screen gives it back.
   */
  private void xgramUpdateStatusBar () {
    final com.fahadali.xgram.appearance.XgramM3CallScreen screen = xgramScreen;
    boolean override = false;
    if (screen != null && !isDestroyed() && isFocused() && xgramVideo != null && screen.getWidth() > 0) {
      final View root = screen.getRootView();
      override = xgramScreenShowsVideo && root != null && screen.getWidth() >= root.getWidth() - 1;
    }
    if (override == xgramLightStatusOverridden) {
      return;
    }
    xgramLightStatusOverridden = override;
    context().xgramSetLightStatusBarOverride(override ? Boolean.FALSE : null);
  }

  private boolean xgramScreenShowsVideo;

  /** Telegram X's screen: the Video cell, and the speaker cell turned into Flip while the camera is on. */
  private void xgramUpdateClassicButtons (boolean animated) {
    if (buttonWrap == null || speakerButtonView == null) {
      return;
    }
    final com.fahadali.xgram.call.XgramCallVideo video = xgramVideo;
    final boolean cameraOn = video != null && !video.isReleased() && video.isCameraOn();
    if (xgramVideoButtonView != null) {
      xgramVideoButtonView.setIsActive(cameraOn, animated);
      final boolean peerCannot = video != null && Boolean.FALSE.equals(video.peerSupportsVideo()) && !call.isVideo;
      xgramVideoButtonView.setAlpha(peerCannot ? .4f : 1f);
    }
    final int icon = cameraOn ? (video.isFront() ? R.drawable.baseline_camera_rear_24 : R.drawable.baseline_camera_front_24) : R.drawable.baseline_volume_up_24;
    if (icon != xgramSpeakerIcon) {
      xgramSpeakerIcon = icon;
      speakerButtonView.setIcon(icon);
      speakerButtonView.invalidate();
    }
    speakerButtonView.setContentDescription(cameraOn ? Lang.getString(video.isFront() ? R.string.XgramVideoFlipToRear : R.string.XgramVideoFlipToFront) : null);
    speakerButtonView.setIsActive(!cameraOn && callSettings != null && callSettings.isSpeakerModeEnabled(), animated);
  }

  private void xgramUpdateBrand () {
    if (xgramBrandView != null) {
      final boolean video = com.fahadali.xgram.call.XgramCallVideo.isAvailable() && (call.isVideo || xgramVideoUsed);
      xgramBrandView.setText(Lang.uppercase(Lang.getString(video ? R.string.XgramVideoCallBranding : R.string.VoipBranding)));
    }
  }

  /** Telegram X's screen: the photo fades away under video. */
  private void xgramOnClassicPresentation (int presentation, boolean animated) {
    final boolean video = presentation != com.fahadali.xgram.call.XgramCallVideoLayer.PRESENT_VOICE;
    if (xgramAvatarFade == null) {
      xgramAvatarFade = new FactorAnimator(ANIMATOR_XGRAM_AVATAR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, 0f);
    }
    if (animated) {
      xgramAvatarFade.animateTo(video ? 1f : 0f);
    } else {
      xgramAvatarFade.forceFactor(video ? 1f : 0f);
    }
    if (avatarView != null && avatarView.getParent() instanceof View) {
      ((View) avatarView.getParent()).setKeepScreenOn(video);
    }
  }

  private void xgramSetClassicVideoFactor (float factor) {
    if (avatarView != null) {
      avatarView.setAlpha(1f - factor);
    }
    if (xgramClassicLayer != null) {
      // The photo's own shadow goes with it: the same scrim over the video
      xgramClassicLayer.setScrims(Screen.dp(212f), factor * lastHeaderFactor, -1, 0f);
    }
  }

  /** Telegram X's screen: the stage follows the photo (tabletop: above the fold), the tile goes between the state and the buttons. */
  private void xgramLayoutClassicVideo () {
    final com.fahadali.xgram.appearance.XgramM3CallVideoTile tile = xgramClassicTile;
    if (tile == null || avatarView == null || stateView == null || isDestroyed()) {
      return;
    }
    final View parent = (View) tile.getParent();
    if (parent == null || parent.getWidth() <= 0) {
      return;
    }
    if (xgramClassicLayer != null) {
      xgramClassicLayer.setStageBounds(avatarView.getTop(), avatarView.getBottom());
    }
    final int width = parent.getWidth(), height = parent.getHeight();
    final float density = Screen.density();
    int tileW, tileH;
    if (width <= height) {
      tileW = Screen.dp(MathUtils.clamp((float) Math.floor(.28f * width / density), 96f, 144f));
      tileH = Math.round(tileW * 4f / 3f);
    } else {
      tileH = Screen.dp(MathUtils.clamp((float) Math.floor(.28f * height / density), 96f, 144f));
      tileW = Math.round(tileH * 4f / 3f);
    }
    final int left = Screen.dp(16f), right = width - Screen.dp(16f);
    final int top = stateView.getBottom() + Screen.dp(16f);
    final int bottom = Math.min(avatarView.getBottom(), height - (Screen.dp(76f) + extraBottomInset)) - Screen.dp(16f);
    if (bottom - top < tileH) {
      tileH = Math.max(0, bottom - top);
      tileW = tileH * 3 / 4;
    }
    tile.setTileSize(tileW, tileH);
    final int mw = Math.max(1, tileW), mh = Math.max(1, tileH);
    if (tile.getMeasuredWidth() != mw || tile.getMeasuredHeight() != mh) {
      tile.measure(View.MeasureSpec.makeMeasureSpec(mw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(mh, View.MeasureSpec.EXACTLY));
    }
    if (tile.getLeft() != 0 || tile.getTop() != 0 || tile.getWidth() != mw || tile.getHeight() != mh) {
      tile.layout(0, 0, mw, mh);
    }
    tile.setTileBounds(left, top, right, Math.max(top, bottom), true);
  }
}
