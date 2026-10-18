package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.CircleButton;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class CircleCounterBadgeView extends FrameLayout implements FactorAnimator.Target {
  private static final float DISABLED_BUTTON_ALPHA = .4f;

  public static final int BUTTON_WRAPPER_WIDTH = Screen.dp(118);
  public static final int BUTTON_PADDING = Screen.dp(24f);
  public static final int PADDING = Screen.dp(4);

  public static final int ANIMATOR_ENABLED = 0;

  private final CircleButton circleButton;
  private final CounterBadgeView counterBadgeView;
  private final BoolAnimator animatorEnabled = new BoolAnimator(ANIMATOR_ENABLED, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);

  public CircleCounterBadgeView (ViewController<?> controller, int id, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
    super(controller.context());
    Context context = controller.context();

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(Screen.dp(BUTTON_WRAPPER_WIDTH), Screen.dp(74f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - PADDING;

    FrameLayoutFix.LayoutParams fParams = FrameLayoutFix.newParams(Screen.dp(24f) * 2 + PADDING * 2, Screen.dp(24f) * 2 + PADDING * 2, Gravity.RIGHT | Gravity.BOTTOM);
    FrameLayoutFix.LayoutParams fParams2 = FrameLayoutFix.newParams(BUTTON_PADDING + fParams.width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
    fParams2.bottomMargin = Screen.dp(24f) * 2 - Screen.dp(28f) / 2;

    setLayoutParams(params);

    counterBadgeView = new CounterBadgeView(context);
    counterBadgeView.setLayoutParams(fParams2);
    counterBadgeView.setPadding(BUTTON_PADDING, 0, 0, 0);

    circleButton = new CircleButton(context);
    circleButton.setId(id);
    if (onClickListener != null) {
      circleButton.setOnClickListener(onClickListener);
    }
    if (onLongClickListener != null) {
      circleButton.setOnLongClickListener(onLongClickListener);
    }
    circleButton.setTag(counterBadgeView);
    circleButton.setLayoutParams(fParams);

    controller.addThemeInvalidateListener(counterBadgeView);
    controller.addThemeInvalidateListener(circleButton);

    addView(circleButton);
    addView(counterBadgeView);

    setEnabled(true, false);
  }

  public void init (@DrawableRes int icon, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId) {
    circleButton.init(icon, size, padding, backgroundColorId, iconColorId);
  }

  public void setInProgress (boolean inProgress) {
    circleButton.setInProgress(inProgress);
  }

  /**
   * XGram M3E: this view's right and bottom margins (before {@link #xgramMakeShadowRoom()}) that put its chat FAB's
   * container 16dp from the edges, the M3 FAB margins: the button is laid out at 56dp (Telegram X's 48dp circle and
   * its 4dp padding) and the container (CircleButton.XGRAM_CHAT_FAB_DP) is centred in it, so 12dp for the 48dp FAB.
   * Telegram X's 12dp with Expressive design off.
   */
  public static int xgramFabMargin () {
    final int button = Screen.dp(24f) * 2 + PADDING * 2;
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Screen.dp(16f) - PADDING;
    }
    return Math.max(0, Screen.dp(16f) - Math.max(0, (button - Screen.dp(CircleButton.XGRAM_CHAT_FAB_DP)) / 2));
  }

  /** XGram M3E: room for the FAB's shadow inside this view (see CircleButton.xgramMakeShadowRoom), after its final layout params. */
  public void xgramMakeShadowRoom () {
    circleButton.xgramMakeShadowRoom();
  }

  public CircleButton getCircleButton () {
    return circleButton;
  }

  /**
   * XGram M3E: sets this view's bottom margin as the distance of the FAB's slot from its parent's bottom, as before
   * {@link #xgramMakeShadowRoom()}: the room it moved inside (the button's own bottom margin, 0 until then) comes off
   * the margin, so a later margin change (the gallery's camera FAB over the system bar) does not lift the FAB by it.
   */
  public void xgramSetBottomMargin (int bottomMargin) {
    final int inside = ((ViewGroup.MarginLayoutParams) circleButton.getLayoutParams()).bottomMargin;
    Views.setBottomMargin(this, Math.max(0, bottomMargin - inside));
  }

  /**
   * XGram M3E: a chat FAB fades itself (its shadow with it), not this view: a view faded with alpha is drawn through
   * a layer of its own bounds, which cut the FAB's shadow off at them. This view's alpha otherwise (Expressive off).
   */
  public void xgramSetFadeAlpha (float alpha) {
    if (circleButton.xgramIsChatFab()) {
      if (getAlpha() != 1f) {
        setAlpha(1f);
      }
      circleButton.setAlpha(alpha);
      counterBadgeView.setAlpha(alpha);
    } else {
      setAlpha(alpha);
    }
  }

  public void setEnabled (boolean enabled, boolean animated) {
    animatorEnabled.setValue(enabled, animated);
  }

  public boolean getEnabled () {
    return animatorEnabled.getValue();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ENABLED: {
        circleButton.setIconAlpha(DISABLED_BUTTON_ALPHA + (1 - DISABLED_BUTTON_ALPHA) * factor);
        break;
      }
    }
  }
}
