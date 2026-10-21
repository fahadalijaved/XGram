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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramM3Sheets;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessageOptionsPagerController;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;

/**
 * Messages → Message menu → Popup menu (off by default). A long press on a message opens its menu as Telegram does: a
 * floating card next to the message, the quick reactions in a pill above the message, over a dim that leaves the
 * message itself bright, instead of the bottom sheet. The pill's expand button opens the sheet with the whole reaction
 * picker; who reacted and who viewed open in the sheet too. Telegram X's popup look with Expressive design off.
 *
 * Placement: the pill above the message and the card below it when both fit; else both above, or both below; else
 * the two stacked inside the safe area (the card's rows scroll). Horizontally they line up with the message's side.
 */
public final class XgramMessagePopup {
  private XgramMessagePopup () { }

  /** The pill: the strip's 40dp reactions and its 7dp padding. */
  private static final float PILL_HEIGHT = 54f;
  private static final float GAP = 8f, EDGE = 8f;
  private static final float CARD_MAX_WIDTH = 280f;
  private static final long SHOW_DURATION = 220L, HIDE_DURATION = 150L;

  public static boolean isEnabled () {
    return XgramSettings.instance().messagePopup();
  }

  /** What the chat does around the popup. */
  public interface Host {
    /** The popup opens (the keyboard steps aside, as for the sheet). */
    void onPopupOpen ();

    /** The popup is gone; {@code toSheet}: the message sheet opens next (keep the keyboard as it is for it). */
    void onPopupClosed (boolean toSheet);

    /** The message sheet: with the whole reaction picker open ({@code picker}), or its reaction and viewer pages. */
    void openSheet (boolean picker);
  }

  /** A popup on screen. */
  public static final class Handle {
    private final MenuMoreWrap card;
    private @Nullable TextView infoRow, readRow;

    private Handle (MenuMoreWrap card) {
      this.card = card;
    }

    /** The "read at" row on top of the card (under the info line), once the read date is known (it can come later). */
    public void setReadDate (@NonNull ViewController.OptionItem item) {
      if (readRow == null) {
        readRow = addInfoRow(card, item.name, item.icon, infoRow != null ? 1 : 0, true);
      } else {
        readRow.setText(item.name);
      }
    }
  }

  /**
   * Shows the popup for {@code msg}'s menu, or returns null when it can't (the message is not on screen): the caller
   * opens the sheet then. {@code metadata}: the reactions pill and the reaction and viewer row (not on the page More
   * opens).
   */
  @Nullable
  public static Handle show (@NonNull ViewController<?> c, @NonNull TGMessage msg, @NonNull ViewController.Options options, @NonNull OptionDelegate delegate, boolean metadata, @NonNull Host host) {
    final View view = msg.findCurrentView();
    final BaseActivity activity = UI.getContext(c.context());
    if (!(view instanceof MessageView) || activity == null || view.getWindowToken() == null || !view.isShown() || options.items == null || options.items.length == 0) {
      return null;
    }
    final Context context = view.getContext();
    final boolean m3 = XgramExpressive.isEnabled();
    final ThemeListenerList themeListeners = c.getThemeListeners();
    final PopupLayout popup = new PopupLayout(activity);
    final Root root = new Root(context, view, bubbleRect(msg, view));
    final Runnable[] afterClose = new Runnable[1];

    // Reactions pill
    View pill = null;
    MessageOptionsPagerController.State state = null;
    if (metadata && msg.canBeReacted()) {
      state = new MessageOptionsPagerController.State(msg, options, (v, reaction, isLongClick) -> {
        if (onReactionClick(c, msg, v, reaction, isLongClick)) {
          popup.hideWindow(true);
        }
      });
      if (state.availableReactions != null && state.availableReactions.length > 0 && XgramReactionBar.mode(msg, true) != XgramSettings.REACTION_BAR_OFF) {
        pill = newPill(context, state, m3, state.needShowReactionsPopupPicker ? () -> {
          afterClose[0] = () -> host.openSheet(true);
          popup.hideWindow(true);
        } : null);
      }
    }

    // The card: the options, and who reacted and who viewed (in the sheet)
    final MenuMoreWrap card = new MenuMoreWrap(context, /* scrollable */ true);
    card.init(themeListeners, null);
    card.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_CENTER);
    card.setRightNumber(0);
    for (ViewController.OptionItem item : options.items) {
      if (item == null || item.id == 0 || item.name == null) {
        continue; // a separator: the menu has none
      }
      TextView row = card.addItem(item.id, item.name, item.icon, null, v -> {
        if (delegate.onOptionItemPressed(v, v.getId())) {
          popup.hideWindow(true);
        }
      });
      if (item.id == R.id.btn_messageDelete || item.textColor == ViewController.OptionColor.RED) {
        setRowColor(row, Theme.getColor(ColorId.textNegative), Theme.getColor(ColorId.iconNegative));
      }
    }
    if (state != null && (state.needShowMessageReactionSenders || state.needShowMessageViews)) {
      final int total = msg.getMessageReactions().getTotalCount();
      CharSequence title = state.needShowMessageReactionSenders && total > 0 ? Lang.plural(R.string.xReacted, total) : Lang.getString(R.string.XgramPopupViewers);
      card.addItem(R.id.btn_xgramPopupDetails, title, R.drawable.baseline_visibility_24, null, v -> {
        afterClose[0] = () -> host.openSheet(false);
        popup.hideWindow(true);
      });
    }
    final Handle handle = new Handle(card);
    if (options.info != null && options.info.length() > 0) {
      // What the sheet says above its options: why a message failed, that it can't be saved, a post's views
      handle.infoRow = addInfoRow(card, options.info, 0, 0, false);
    }
    if (options.subtitle != null && options.subtitle.name != null) {
      handle.setReadDate(options.subtitle);
    }
    card.xgramSetMaxItemsWidth(Math.min(Screen.dp(CARD_MAX_WIDTH), Math.max(Screen.dp(196f), view.getWidth() - Screen.dp(EDGE * 2))));

    if (pill != null) {
      root.addView(pill);
      root.pill = pill;
    }
    root.addView(card);
    root.card = card;

    popup.init(false);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);
    popup.setHideBackground(true); // the root draws the dim, with the message left bright
    popup.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismiss (PopupLayout p) {
        Runnable action = afterClose[0];
        afterClose[0] = null;
        host.onPopupClosed(action != null);
        if (action != null) {
          action.run();
        }
      }
    });
    host.onPopupOpen();
    popup.showAnimatedPopupView(root, new PopupLayout.AnimatedPopupProvider() {
      private ValueAnimator animator;

      @Override
      public void prepareShowAnimation () {
        root.setFactor(0f);
      }

      @Override
      public void launchShowAnimation (PopupLayout p) {
        animate(p, 1f, SHOW_DURATION, () -> {
          p.setRevealFactor(1f); // the dim's touches close the popup from now on
          p.onCustomShowComplete();
        });
      }

      @Override
      public boolean launchHideAnimation (PopupLayout p, me.vkryl.android.animator.FactorAnimator originalAnimator) {
        animate(p, 0f, HIDE_DURATION, p::onCustomHideAnimationComplete);
        return true;
      }

      private void animate (PopupLayout p, float to, long duration, Runnable after) {
        if (animator != null) {
          animator.cancel();
        }
        final float from = root.factor;
        if (com.fahadali.xgram.appearance.XgramMotion.reduce()) {
          root.setFactor(to);
          after.run();
          return;
        }
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(duration);
        animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        animator.addUpdateListener(a -> root.setFactor((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
          private boolean cancelled;

          @Override
          public void onAnimationCancel (Animator animation) {
            cancelled = true;
          }

          @Override
          public void onAnimationEnd (Animator animation) {
            if (!cancelled) {
              after.run();
            }
          }
        });
        animator.start();
      }
    });
    return handle;
  }

  /** The message's bubble (or its whole row without bubbles) in the message view's coordinates. */
  private static Rect bubbleRect (TGMessage msg, View view) {
    Rect rect = new Rect();
    if (msg.useBubbles()) {
      rect.set(msg.getActualLeftContentEdge(), msg.getTopContentEdge(), msg.getActualRightContentEdge(), msg.getBottomContentEdge());
    }
    if (rect.width() <= 0 || rect.height() <= 0) {
      rect.set(0, 0, view.getWidth(), view.getHeight());
    }
    rect.intersect(0, 0, view.getWidth(), view.getHeight());
    return rect;
  }

  /** As the sheet's strip: a tap toggles the reaction, a long press sends it big. Whether the popup should close. */
  private static boolean onReactionClick (ViewController<?> c, TGMessage msg, View v, TGReaction reaction, boolean isLongClick) {
    final int[] position = Views.getLocationOnScreen(v);
    final Point start = new Point(position[0] + v.getMeasuredWidth() / 2, position[1] + v.getMeasuredHeight() / 2);
    final boolean hasReaction = msg.getMessageReactions().hasReaction(reaction.type);
    if (Config.DISABLE_ANONYMOUS_NON_OWNER_REACTIONS && msg.tdlib().isAnonymousAdminNonCreator(msg.getChatId()) && (isLongClick || !hasReaction)) {
      if (!isLongClick) {
        c.context().tooltipManager().builder(v).show(msg.tdlib(), R.string.error_ANONYMOUS_REACTIONS_DISABLED).hideDelayed();
      }
      return false;
    }
    if (Config.PROTECT_ANONYMOUS_REACTIONS && !(hasReaction && !isLongClick) && !msg.messagesController().callNonAnonymousProtection(msg.getId() + reaction.getId(), c.context().tooltipManager().builder(v))) {
      return false;
    }
    final boolean toggled = msg.getMessageReactions().toggleReaction(reaction.type, isLongClick, true, result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        msg.tdlib().ui().post(() -> {
          UI.showError((TdApi.Error) result);
          msg.cancelScheduledSetReactionAnimation();
        });
      }
    });
    if (toggled) {
      if (isLongClick) {
        msg.scheduleSetReactionAnimationFullscreenFromBottomSheet(reaction, start);
      } else {
        msg.scheduleSetReactionAnimationFromBottomSheet(reaction, start);
      }
    }
    return true;
  }

  /** The reactions pill: the strip, and the expand button when there are more reactions to pick from. */
  private static View newPill (Context context, MessageOptionsPagerController.State state, boolean m3, @Nullable Runnable onExpand) {
    final FrameLayout pill = new FrameLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // As wide as its reactions (up to the room it is given), the expand button at its end
        final int max = MeasureSpec.getSize(widthMeasureSpec);
        final View strip = getChildAt(0);
        final View expand = getChildCount() > 1 ? getChildAt(1) : null;
        final int expandWidth = expand != null ? Screen.dp(48f) : 0;
        final int height = Screen.dp(PILL_HEIGHT);
        strip.measure(MeasureSpec.makeMeasureSpec(Math.max(0, max - expandWidth - getPaddingLeft() - getPaddingRight()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        if (expand != null) {
          expand.measure(MeasureSpec.makeMeasureSpec(expandWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(Math.min(max, strip.getMeasuredWidth() + expandWidth + getPaddingLeft() + getPaddingRight()), height);
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        final View strip = getChildAt(0);
        final View expand = getChildCount() > 1 ? getChildAt(1) : null;
        final int width = right - left;
        final boolean rtl = Lang.rtl();
        final int expandWidth = expand != null ? expand.getMeasuredWidth() : 0;
        final int stripLeft = rtl ? getPaddingLeft() + expandWidth : getPaddingLeft();
        strip.layout(stripLeft, 0, stripLeft + strip.getMeasuredWidth(), strip.getMeasuredHeight());
        if (expand != null) {
          final int expandLeft = rtl ? getPaddingLeft() : width - getPaddingRight() - expandWidth;
          expand.layout(expandLeft, 0, expandLeft + expandWidth, expand.getMeasuredHeight());
        }
      }
    };
    final ReactionsSelectorRecyclerView strip = new ReactionsSelectorRecyclerView(context, state);
    strip.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(PILL_HEIGHT)));
    // The end fade goes into the pill's color: the strip's own pill with Expressive design (Telegram X's strip fades
    // into the page background, not the popup's)
    strip.setNeedDrawBorderGradient(m3);
    if (m3) {
      strip.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (@NonNull Rect outRect, @NonNull View child, @NonNull RecyclerView parent, @NonNull RecyclerView.State rvState) {
          outRect.set(Screen.dp(1f), 0, Screen.dp(1f), 0); // cancels the strip's own -1dp overlap, as in the sheet's row
        }
      });
    }
    pill.addView(strip);
    if (onExpand != null) {
      ImageView expand = new ImageView(context);
      expand.setLayoutParams(new FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(PILL_HEIGHT)));
      expand.setScaleType(ImageView.ScaleType.CENTER);
      if (m3) {
        expand.setImageResource(R.drawable.xgram_w2_menu_sheets_expand_more_24);
        XgramM3Sheets.attachTonalIconButton(expand);
        expand.setColorFilter(Paints.getColorFilter(XgramM3Sheets.tonalContentColor()));
      } else {
        expand.setImageResource(XgramIconPack.map(R.drawable.baseline_small_arrow_down_24));
        RippleSupport.setTransparentSelector(expand);
        expand.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
      }
      expand.setContentDescription(Lang.getString(R.string.XgramMoreReactions));
      expand.setOnClickListener(v -> onExpand.run());
      Views.setClickable(expand);
      pill.addView(expand);
    }
    // The pill's container: the strip's own pill color with Expressive design (so the two read as one), the menu's
    // floating color otherwise (as the card); full corners, elevated like the card
    GradientDrawable background = new GradientDrawable();
    background.setCornerRadius(Screen.dp(PILL_HEIGHT / 2f));
    background.setColor(m3 ? XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH) : Theme.headerFloatBackgroundColor());
    pill.setBackground(background);
    pill.setPadding(Screen.dp(m3 ? 0f : 4f), 0, Screen.dp(m3 ? 4f : 4f), 0);
    pill.setElevation(Screen.dp(m3 ? XgramExpressive.ELEVATION_2 : 3f));
    pill.setClipToOutline(true);
    pill.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
    return pill;
  }

  /**
   * A row of text in the info color, not tappable, at {@code index} in the card: the read date (one line, its icon) or
   * the sheet's info line (up to four lines, smaller).
   */
  private static TextView addInfoRow (MenuMoreWrap card, CharSequence text, int icon, int index, boolean singleLine) {
    TextView row = card.addItem(0, text, icon, null, null);
    row.setClickable(false);
    row.setBackground(null);
    row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    final int textColor = XgramExpressive.isEnabled() ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.textLight);
    setRowColor(row, textColor, textColor);
    if (!singleLine) {
      row.setSingleLine(false);
      row.setMaxLines(4);
      row.setEllipsize(android.text.TextUtils.TruncateAt.END);
      row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f);
      final int vertical = Screen.dp(8f);
      row.setPadding(row.getPaddingLeft(), vertical, row.getPaddingRight(), vertical);
      ViewGroup.LayoutParams params = row.getLayoutParams();
      if (params != null) {
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        row.setLayoutParams(params);
      }
    }
    if (row.getParent() instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) row.getParent();
      parent.removeView(row);
      parent.addView(row, Math.min(index, parent.getChildCount()));
    }
    return row;
  }

  private static void setRowColor (TextView row, int textColor, int iconColor) {
    row.setTextColor(textColor);
    for (Drawable d : row.getCompoundDrawables()) {
      if (d != null) {
        d.setColorFilter(Paints.getColorFilter(iconColor));
      }
    }
  }

  /**
   * The popup's window-filling root: the dim (a hole leaves the message bright), the pill and the card, placed next to
   * the message when it is laid out.
   */
  @SuppressLint("ViewConstructor")
  private static final class Root extends FrameLayout {
    private final View anchor;
    private final Rect bubbleInAnchor;
    private final RectF hole = new RectF();
    private final Path holePath = new Path();
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable View pill, card;
    float factor;
    private boolean cardBelow;
    private int lastLeft, lastTop;

    Root (Context context, View anchor, Rect bubbleInAnchor) {
      super(context);
      this.anchor = anchor;
      this.bubbleInAnchor = bubbleInAnchor;
      setWillNotDraw(false);
      setClipChildren(false);
      setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void setFactor (float factor) {
      this.factor = factor;
      final float alpha = Math.max(0f, Math.min(1f, factor));
      final float scale = .86f + .14f * alpha;
      for (View v : new View[] {pill, card}) {
        if (v != null) {
          v.setAlpha(alpha);
          v.setScaleX(scale);
          v.setScaleY(scale);
        }
      }
      invalidate();
    }

    private Insets safeInsets () {
      WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
      if (insets == null) {
        return Insets.of(0, Screen.getStatusBarHeight(), 0, 0);
      }
      return insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      final Insets safe = safeInsets();
      final int edge = Screen.dp(EDGE);
      final int roomWidth = Math.max(0, width - safe.left - safe.right - edge * 2);
      final int roomHeight = Math.max(0, height - safe.top - safe.bottom - edge * 2);
      int used = 0;
      if (pill != null) {
        pill.measure(MeasureSpec.makeMeasureSpec(roomWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Screen.dp(PILL_HEIGHT), MeasureSpec.EXACTLY));
        used = pill.getMeasuredHeight() + Screen.dp(GAP);
      }
      if (card instanceof MenuMoreWrap) {
        ((MenuMoreWrap) card).xgramSetMaxHeight(Math.max(Screen.dp(MenuMoreWrap.ITEM_HEIGHT * 2), roomHeight - used));
      }
      if (card != null) {
        card.measure(MeasureSpec.makeMeasureSpec(roomWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Math.max(0, roomHeight - used), MeasureSpec.AT_MOST));
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      final int width = r - l, height = b - t;
      // The message's bubble in this root's coordinates (own arrays: Views' location helpers share one)
      final int[] location = new int[2];
      getLocationOnScreen(location);
      final int rootX = location[0], rootY = location[1];
      anchor.getLocationOnScreen(location);
      final int anchorLeft = location[0] - rootX, anchorRight = anchorLeft + anchor.getWidth();
      final Rect bubble = new Rect(bubbleInAnchor);
      bubble.offset(anchorLeft, location[1] - rootY);
      hole.set(bubble);
      hole.inset(-Screen.dp(4f), -Screen.dp(4f));

      final Insets safe = safeInsets();
      final int edge = Screen.dp(EDGE), gap = Screen.dp(GAP);
      final int safeTop = safe.top + edge, safeBottom = height - safe.bottom - edge;
      // Inside the message's own pane (two panes open: the chat's, not over the chat list)
      final int safeLeft = Math.max(safe.left, anchorLeft) + edge, safeRight = Math.min(width - safe.right, anchorRight) - edge;
      final int pillHeight = pill != null ? pill.getMeasuredHeight() : 0;
      final int pillBlock = pill != null ? pillHeight + gap : 0;
      final int cardHeight = card != null ? card.getMeasuredHeight() : 0;

      int pillTop, cardTop;
      if ((pill == null || bubble.top - gap - pillHeight >= safeTop) && bubble.bottom + gap + cardHeight <= safeBottom) {
        // Telegram's: the reactions above the message, the menu below it
        pillTop = bubble.top - gap - pillHeight;
        cardTop = bubble.bottom + gap;
        cardBelow = true;
      } else if (bubble.top - gap - cardHeight - pillBlock >= safeTop) {
        // both above
        cardTop = bubble.top - gap - cardHeight;
        pillTop = cardTop - pillBlock;
        cardBelow = false;
      } else if (bubble.bottom + gap + pillBlock + cardHeight <= safeBottom) {
        // both below
        pillTop = bubble.bottom + gap;
        cardTop = pillTop + pillBlock;
        cardBelow = true;
      } else {
        // a tall message or menu: stacked inside the safe area, over the message (the card's rows scroll)
        final int block = pillBlock + cardHeight;
        int top = bubble.top - pillBlock;
        top = Math.max(safeTop, Math.min(top, safeBottom - block));
        pillTop = top;
        cardTop = top + pillBlock;
        cardBelow = true;
      }

      // Horizontally on the message's side of its pane: its start for incoming messages, its end for outgoing ones
      final boolean alignEnd = bubble.centerX() > (anchorLeft + anchorRight) / 2;
      if (pill != null) {
        layoutAt(pill, alignEnd, bubble, safeLeft, safeRight, pillTop);
        pill.setPivotX(alignEnd ? pill.getWidth() : 0);
        pill.setPivotY(pill.getHeight());
      }
      if (card != null) {
        layoutAt(card, alignEnd, bubble, safeLeft, safeRight, cardTop);
        card.setPivotX(alignEnd ? card.getWidth() : 0);
        card.setPivotY(cardBelow ? 0 : card.getHeight());
      }
    }

    private static void layoutAt (View v, boolean alignEnd, Rect bubble, int safeLeft, int safeRight, int top) {
      final int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
      int left = alignEnd ? bubble.right - w : bubble.left;
      left = Math.max(safeLeft, Math.min(left, safeRight - w));
      left = Math.max(0, left); // a pane narrower than the view: its start edge wins
      v.layout(left, top, left + w, top + h);
    }

    @Override
    protected void onDraw (Canvas c) {
      final float alpha = Math.max(0f, Math.min(1f, factor));
      if (alpha <= 0f) {
        return;
      }
      final float dim = XgramExpressive.isEnabled() ? .32f : Theme.getPopupOverlayAlpha();
      dimPaint.setColor(ColorUtils.color((int) (255f * dim * alpha), 0));
      // The dim around the message: an even-odd path with the bubble's rounded box cut out
      holePath.rewind();
      holePath.setFillType(Path.FillType.EVEN_ODD);
      holePath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
      if (!hole.isEmpty()) {
        final float radius = Math.min(Screen.dp(20f), hole.height() / 2f);
        holePath.addRoundRect(hole, radius, radius, Path.Direction.CW);
      }
      c.drawPath(holePath, dimPaint);
    }
  }
}
