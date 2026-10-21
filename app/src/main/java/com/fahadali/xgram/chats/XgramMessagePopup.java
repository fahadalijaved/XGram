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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramM3Sheets;
import com.fahadali.xgram.appearance.XgramMotion;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessageOptionsPagerController;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;
import tgx.td.Td;

/**
 * Messages → Message menu → Popup menu (off by default). A long press on a message opens its menu as Telegram does: a
 * floating card next to the message, the quick reactions in a pill above the message, over a dim that leaves the
 * message itself bright, instead of the bottom sheet. The keyboard stays as it is: the popup sits above it.
 *
 * Pages, in place (the popup never moves once it is open, it cross-fades): the pill's expand button turns the pill and
 * the card into every reaction in a grid (its last cell: the sheet's whole picker, with custom emoji and search);
 * "Seen by" / "N reacted" turns the card into who viewed and who reacted, with Back on top. Telegram X's popup look
 * with Expressive design off.
 *
 * Placement: the pill above the message and the card below it when both fit; else both above, or both below; else
 * the two stacked inside the safe area (the card's rows scroll). Horizontally they line up with the message's side,
 * inside its pane.
 */
public final class XgramMessagePopup {
  private XgramMessagePopup () { }

  /** The pill: the strip's 40dp reactions and its 7dp padding. */
  private static final float PILL_HEIGHT = 54f;
  private static final float GAP = 8f, EDGE = 8f;
  private static final float CARD_MAX_WIDTH = 280f;
  /** A cell of the reaction grid: a 40dp reaction and 2dp around it. */
  private static final float GRID_CELL = 44f, GRID_PADDING = 8f;
  private static final long SHOW_DURATION = 220L, HIDE_DURATION = 150L, PAGE_DURATION = 220L;

  public static boolean isEnabled () {
    return XgramSettings.instance().messagePopup();
  }

  /** What the chat does around the popup. */
  public interface Host {
    /** The popup opens. */
    void onPopupOpen ();

    /** The popup is gone. */
    void onPopupClosed ();

    /** The message sheet with its whole reaction picker open (custom emoji, search). */
    void openReactionPicker ();
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

  private interface ReactionClick {
    void onReactionClick (View v, TGReaction reaction, boolean isLongClick);
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
    final Tdlib tdlib = msg.tdlib();
    final boolean m3 = XgramExpressive.isEnabled();
    final ThemeListenerList themeListeners = c.getThemeListeners();
    final PopupLayout popup = new PopupLayout(activity);
    final Root root = new Root(context, view, bubbleRect(msg, view));
    final Runnable[] afterClose = new Runnable[1];
    final int maxWidth = Math.max(Screen.dp(196f), view.getWidth() - Screen.dp(EDGE * 2));

    final ReactionClick onReaction = (v, reaction, isLongClick) -> {
      if (onReactionClick(c, msg, v, reaction, isLongClick)) {
        popup.hideWindow(true);
      }
    };

    // Reactions pill
    MessageOptionsPagerController.State state = null;
    if (metadata && msg.canBeReacted()) {
      state = new MessageOptionsPagerController.State(msg, options, onReaction::onReactionClick);
      if (state.availableReactions != null && state.availableReactions.length > 0 && XgramReactionBar.mode(msg, true) != XgramSettings.REACTION_BAR_OFF) {
        final MessageOptionsPagerController.State pillState = state;
        root.pill = newPill(context, state, m3, () -> {
          // Every reaction in place of the pill and the card; the grid's last cell opens the sheet's whole picker
          View grid = newReactionGrid(context, tdlib, pillState, m3, maxWidth, onReaction, pillState.needShowReactionsPopupPicker ? () -> {
            afterClose[0] = host::openReactionPicker;
            popup.hideWindow(true);
          } : null);
          root.showPage(Root.PAGE_GRID, grid);
        });
      }
    }

    // The card: the options, then who viewed and who reacted
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
      final boolean views = state.needShowMessageViews, reactions = state.needShowMessageReactionSenders;
      final int total = msg.getMessageReactions().getTotalCount();
      final CharSequence title = reactions && total > 0 ? Lang.plural(R.string.xReacted, total) : Lang.getString(R.string.XgramPopupViewers);
      final TextView[] detailsRow = new TextView[1];
      final boolean[] loading = new boolean[1];
      detailsRow[0] = card.addItem(R.id.btn_xgramPopupDetails, title, R.drawable.baseline_visibility_24, null, v -> {
        if (loading[0]) {
          return;
        }
        loading[0] = true;
        detailsRow[0].setText(Lang.getString(R.string.LoadingInformation));
        loadDetails(tdlib, msg, views, reactions, entries -> {
          loading[0] = false;
          detailsRow[0].setText(title);
          if (popup.isWindowHidden() || !root.isOnMenu()) {
            return; // closed, or the grid took the popup meanwhile
          }
          if (entries.isEmpty()) {
            UI.showToast(R.string.XgramPopupNoViewers, android.widget.Toast.LENGTH_SHORT);
            return;
          }
          MenuMoreWrap details = newDetails(context, tdlib, msg, themeListeners, title, entries, () -> root.showPage(Root.PAGE_MENU, null), sender -> {
            afterClose[0] = () -> tdlib.ui().openSenderProfile(c, sender, null);
            popup.hideWindow(true);
          });
          details.xgramSetMaxItemsWidth(Math.min(Screen.dp(CARD_MAX_WIDTH), maxWidth));
          root.showPage(Root.PAGE_DETAILS, details);
        });
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
    card.xgramSetMaxItemsWidth(Math.min(Screen.dp(CARD_MAX_WIDTH), maxWidth));

    if (root.pill != null) {
      root.addView(root.pill);
    }
    root.card = card;
    root.addView(card);
    // The keyboard (or a bar) came or went under the popup: the message moved, so the popup closes
    root.onPlacementLost = () -> popup.hideWindow(true);

    popup.init(false);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);
    popup.setHideBackground(true); // the root draws the dim, with the message left bright
    popup.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismiss (PopupLayout p) {
        Runnable action = afterClose[0];
        afterClose[0] = null;
        root.releaseLists(); // a list that leaves the window does not recycle its rows: their stickers stay alive
        host.onPopupClosed();
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
        if (XgramMotion.reduce()) {
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

  /** The popup's floating container: full corners for the pill, large ones for the grid; elevated like the card. */
  private static void applyContainer (View v, boolean m3, float cornerDp) {
    GradientDrawable background = new GradientDrawable();
    background.setCornerRadius(Screen.dp(cornerDp));
    // the strip's own pill color with Expressive design (so the two read as one), the menu's floating color otherwise
    background.setColor(m3 ? XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH) : Theme.headerFloatBackgroundColor());
    v.setBackground(background);
    v.setElevation(Screen.dp(m3 ? XgramExpressive.ELEVATION_2 : 3f));
    v.setClipToOutline(true);
    v.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
  }

  /** The reactions pill: the strip, and the expand button (every reaction, in a grid in place of the popup). */
  private static View newPill (Context context, MessageOptionsPagerController.State state, boolean m3, @NonNull Runnable onExpand) {
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
    applyContainer(pill, m3, PILL_HEIGHT / 2f);
    pill.setPadding(Screen.dp(m3 ? 0f : 4f), 0, Screen.dp(4f), 0);
    return pill;
  }

  // Every reaction (the expand button)

  /** A cell of the reaction grid: the reaction (its static icon), on the chosen color when it is chosen. */
  @SuppressLint("ViewConstructor")
  private static final class ReactionCell extends FrameLayout {
    final StickerSmallView sticker;
    final ImageView more;
    private final RectF chosenRect = new RectF();
    private boolean chosen;

    ReactionCell (Context context, Tdlib tdlib, boolean m3) {
      super(context);
      setWillNotDraw(false);
      sticker = new StickerSmallView(context, 0) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent event) {
          return false; // the cell takes the taps
        }
      };
      sticker.init(tdlib);
      addView(sticker, new FrameLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f), Gravity.CENTER));
      more = new ImageView(context);
      more.setScaleType(ImageView.ScaleType.CENTER);
      more.setImageResource(XgramIconPack.map(R.drawable.baseline_add_24));
      more.setColorFilter(Paints.getColorFilter(m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.icon)));
      more.setVisibility(View.GONE);
      addView(more, new FrameLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f), Gravity.CENTER));
      if (m3) {
        setBackground(com.fahadali.xgram.appearance.XgramExpressiveC.iconButtonRipple(XgramExpressive.color(XgramExpressive.ON_SURFACE), com.fahadali.xgram.appearance.XgramExpressiveC.ICON_BUTTON_BOX));
      } else {
        RippleSupport.setTransparentSelector(this);
      }
      Views.setClickable(this);
      setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(GRID_CELL), Screen.dp(GRID_CELL)));
    }

    void setChosen (boolean chosen) {
      if (this.chosen != chosen) {
        this.chosen = chosen;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (chosen) {
        final float inset = Screen.dp(2f);
        chosenRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        final float radius = chosenRect.height() / 2f;
        c.drawRoundRect(chosenRect, radius, radius, Paints.fillingPaint(XgramExpressive.isEnabled() ? XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER) : Theme.getColor(ColorId.fillingPositive)));
      }
      super.onDraw(c);
    }
  }

  private static final class CellHolder extends RecyclerView.ViewHolder {
    CellHolder (@NonNull ReactionCell cell) {
      super(cell);
    }
  }

  /**
   * Every reaction the message can take, in a grid as wide as the menu may be (4 to 8 columns), its rows scrolling
   * when the room ends; {@code onMore}: a last cell that opens the sheet's whole picker.
   */
  private static View newReactionGrid (Context context, Tdlib tdlib, MessageOptionsPagerController.State state, boolean m3, int maxWidth, ReactionClick click, @Nullable Runnable onMore) {
    final TdApi.AvailableReaction[] reactions = state.availableReactions != null ? state.availableReactions : new TdApi.AvailableReaction[0];
    final int padding = Screen.dp(GRID_PADDING);
    final int columns = Math.max(4, Math.min(8, (maxWidth - padding * 2) / Screen.dp(GRID_CELL)));
    final RecyclerView grid = new RecyclerView(context);
    grid.setLayoutManager(new GridLayoutManager(context, columns));
    grid.setPadding(padding, padding, padding, padding);
    grid.setClipToPadding(false);
    grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
    grid.setVerticalFadingEdgeEnabled(true);
    grid.setFadingEdgeLength(Screen.dp(16f));
    grid.setAdapter(new RecyclerView.Adapter<CellHolder>() {
      @NonNull
      @Override
      public CellHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        return new CellHolder(new ReactionCell(parent.getContext(), tdlib, m3));
      }

      @Override
      public void onBindViewHolder (@NonNull CellHolder holder, int position) {
        final ReactionCell cell = (ReactionCell) holder.itemView;
        if (position >= reactions.length) {
          cell.sticker.setSticker(null);
          cell.sticker.setVisibility(View.GONE);
          cell.more.setVisibility(View.VISIBLE);
          cell.setChosen(false);
          cell.setContentDescription(Lang.getString(R.string.XgramMoreReactions));
          cell.setOnClickListener(v -> {
            if (onMore != null) {
              onMore.run();
            }
          });
          cell.setOnLongClickListener(null);
          return;
        }
        final TdApi.ReactionType type = reactions[position].type;
        final TGReaction reaction = tdlib.getReaction(type);
        cell.more.setVisibility(View.GONE);
        cell.sticker.setVisibility(View.VISIBLE);
        cell.sticker.setSticker(reaction != null ? reaction.staticCenterAnimationSicker() : null);
        cell.setChosen(state.chosenReactions != null && state.chosenReactions.contains(TD.makeReactionKey(type)));
        cell.setContentDescription(reaction != null ? reaction.getTitle() : null);
        if (reaction != null) {
          cell.setOnClickListener(v -> click.onReactionClick(v, reaction, false));
          cell.setOnLongClickListener(v -> {
            click.onReactionClick(v, reaction, true);
            return true;
          });
        } else {
          cell.setOnClickListener(null);
          cell.setOnLongClickListener(null);
        }
      }

      @Override
      public int getItemCount () {
        return reactions.length + (onMore != null ? 1 : 0);
      }

      @Override
      public void onViewAttachedToWindow (@NonNull CellHolder holder) {
        ((ReactionCell) holder.itemView).sticker.attach();
      }

      @Override
      public void onViewDetachedFromWindow (@NonNull CellHolder holder) {
        ((ReactionCell) holder.itemView).sticker.detach();
      }

      @Override
      public void onViewRecycled (@NonNull CellHolder holder) {
        ((ReactionCell) holder.itemView).sticker.performDestroy();
      }
    });
    final int width = columns * Screen.dp(GRID_CELL) + padding * 2;
    final int rows = (reactions.length + (onMore != null ? 1 : 0) + columns - 1) / columns;
    final int fullHeight = rows * Screen.dp(GRID_CELL) + padding * 2;
    final FrameLayout container = new FrameLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // every row when they fit, else as tall as the room (the grid scrolls)
        final int w = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
        final int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? fullHeight : Math.min(fullHeight, MeasureSpec.getSize(heightMeasureSpec));
        grid.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
      }
    };
    container.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    applyContainer(container, m3, 24f);
    return container;
  }

  // Who viewed and who reacted (Seen by)

  private static final class Viewer {
    final TdApi.MessageSender sender;
    int date;
    boolean viewed;
    @Nullable TdApi.ReactionType reaction;

    Viewer (TdApi.MessageSender sender, int date) {
      this.sender = sender;
      this.date = date;
    }
  }

  private interface ViewersCallback {
    void onViewers (List<Viewer> viewers);
  }

  /** Who viewed (with when) and who reacted (with what), one row per person, the newest first. UI thread callback. */
  private static void loadDetails (Tdlib tdlib, TGMessage msg, boolean views, boolean reactions, ViewersCallback done) {
    // Viewers by the message's id (as Telegram X's Seen page); an album's reactions sit on its oldest item
    final long chatId = msg.getChatId(), viewersMessageId = msg.getId(), reactionsMessageId = msg.getSmallestId();
    final List<Viewer> result = new ArrayList<>();
    final Runnable finish = () -> tdlib.ui().post(() -> {
      Collections.sort(result, (a, b) -> Integer.compare(b.date, a.date));
      done.onViewers(result);
    });
    final Runnable loadReactions = () -> {
      if (!reactions) {
        finish.run();
        return;
      }
      tdlib.send(new TdApi.GetMessageAddedReactions(chatId, reactionsMessageId, null, "", 100), (added, error) -> {
        if (added != null) {
          for (TdApi.AddedReaction reaction : added.reactions) {
            Viewer viewer = null;
            for (Viewer v : result) {
              if (Td.getSenderId(v.sender) == Td.getSenderId(reaction.senderId)) {
                viewer = v;
                break;
              }
            }
            if (viewer == null) {
              viewer = new Viewer(reaction.senderId, reaction.date);
              result.add(viewer);
            }
            if (viewer.reaction == null) {
              viewer.reaction = reaction.type;
            }
          }
        }
        finish.run();
      });
    };
    if (!views) {
      loadReactions.run();
      return;
    }
    tdlib.send(new TdApi.GetMessageViewers(chatId, viewersMessageId), (viewers, error) -> {
      if (viewers != null) {
        for (TdApi.MessageViewer viewer : viewers.viewers) {
          Viewer v = new Viewer(new TdApi.MessageSenderUser(viewer.userId), viewer.viewDate);
          v.viewed = true;
          result.add(v);
        }
      }
      loadReactions.run();
    });
  }

  private interface SenderClick {
    void onSender (TdApi.MessageSender sender);
  }

  /** The card's second page: Back, then one row per person (photo, name, the reaction and when), a tap opens them. */
  private static MenuMoreWrap newDetails (Context context, Tdlib tdlib, TGMessage msg, ThemeListenerList themeListeners, CharSequence title, List<Viewer> viewers, Runnable onBack, SenderClick onSender) {
    final MenuMoreWrap details = new MenuMoreWrap(context, /* scrollable */ true);
    details.init(themeListeners, null);
    details.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_CENTER);
    details.setRightNumber(0);
    details.addItem(R.id.btn_xgramPopupBack, title, R.drawable.baseline_arrow_back_24, null, v -> onBack.run());
    for (Viewer viewer : viewers) {
      final String time = viewer.viewed ? TGUser.getActionDateStatus(tdlib, viewer.date, msg.getMessage()) : Lang.getRelativeTimestamp(viewer.date, TimeUnit.SECONDS);
      String subtitle = time;
      if (viewer.reaction != null && viewer.reaction.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) {
        subtitle = ((TdApi.ReactionTypeEmoji) viewer.reaction).emoji + "  " + time;
      } else if (viewer.reaction != null && viewer.reaction.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR) {
        subtitle = "⭐  " + time;
      }
      HapticMenuHelper.MenuItem item = new HapticMenuHelper.MenuItem(R.id.btn_xgramPopupViewer, tdlib.senderName(viewer.sender), subtitle, 0, tdlib, viewer.sender, false);
      details.addItem(tdlib, item, v -> onSender.onSender(viewer.sender));
    }
    return details;
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
   * The popup's window-filling root: the dim (a hole leaves the message bright), the pill and the card placed next to
   * the message at the first layout, and the pages that take their places later (the grid, who viewed) where they were.
   */
  @SuppressLint("ViewConstructor")
  private static final class Root extends FrameLayout {
    static final int PAGE_MENU = 0, PAGE_DETAILS = 1, PAGE_GRID = 2;

    private final View anchor;
    private final Rect bubbleInAnchor;
    private final Rect bubble = new Rect();
    private final RectF hole = new RectF();
    private final Path holePath = new Path();
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable View pill, card, details, grid;
    @Nullable Runnable onPlacementLost;
    float factor;
    private int page = PAGE_MENU;
    private int placedSafeBottom;
    /** Each view's part of the page cross-fade (0 when it is not on the page); its alpha is this times the popup's. */
    private float pillPart = 1f, cardPart = 1f, detailsPart, gridPart;
    private @Nullable ValueAnimator pageAnimator;

    // Placement, fixed at the first layout: a page switch never moves the popup
    private boolean placed, alignEnd, cardBelow, gridBelow;
    /** The pill's top; the card's top when it grows down (below the message), else its bottom. */
    private int pillTop, cardEdge, gridEdge;
    private int safeTop, safeBottom, safeLeft, safeRight;

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
      applyParts();
      invalidate();
    }

    private void applyParts () {
      final float alpha = Math.max(0f, Math.min(1f, factor));
      final float scale = .86f + .14f * alpha;
      apply(pill, alpha * pillPart, scale);
      apply(card, alpha * cardPart, scale * (page == PAGE_DETAILS ? .96f + .04f * cardPart : 1f));
      apply(details, alpha * detailsPart, scale * (.96f + .04f * detailsPart));
      apply(grid, alpha * gridPart, scale * (.92f + .08f * gridPart));
    }

    private static void apply (@Nullable View v, float alpha, float scale) {
      if (v != null) {
        v.setAlpha(alpha);
        v.setScaleX(scale);
        v.setScaleY(scale);
        final int visibility = alpha > 0f ? View.VISIBLE : View.INVISIBLE;
        if (v.getVisibility() != visibility) {
          v.setVisibility(visibility);
        }
      }
    }

    boolean isOnMenu () {
      return page == PAGE_MENU;
    }

    /** The lists' rows release their reaction stickers (setAdapter(null) recycles them). */
    void releaseLists () {
      for (View v : new View[] {pill, grid}) {
        if (v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0 && ((ViewGroup) v).getChildAt(0) instanceof RecyclerView) {
          ((RecyclerView) ((ViewGroup) v).getChildAt(0)).setAdapter(null);
        }
      }
    }

    /** Cross-fades to {@code page}; {@code view} is its new view (the grid, who viewed), null to go back to the menu. */
    void showPage (int page, @Nullable View view) {
      if (!placed || this.page == page) {
        return;
      }
      if (page == PAGE_DETAILS && view != null) {
        if (details != null) {
          removeView(details);
        }
        details = view;
        detailsPart = 0f;
        addView(view);
      } else if (page == PAGE_GRID && view != null) {
        if (grid != null) {
          removeView(grid);
        }
        grid = view;
        gridPart = 0f;
        addView(view);
      }
      this.page = page;
      applyParts();
      if (pageAnimator != null) {
        pageAnimator.cancel();
      }
      final float pillFrom = pillPart, cardFrom = cardPart, detailsFrom = detailsPart, gridFrom = gridPart;
      // the pill goes with the grid, and with who viewed when that grows up into its band (the menu above the message)
      final float pillTo = page == PAGE_GRID || (page == PAGE_DETAILS && !cardBelow) ? 0f : 1f, cardTo = page == PAGE_MENU ? 1f : 0f;
      final float detailsTo = page == PAGE_DETAILS ? 1f : 0f, gridTo = page == PAGE_GRID ? 1f : 0f;
      if (XgramMotion.reduce()) {
        pillPart = pillTo;
        cardPart = cardTo;
        detailsPart = detailsTo;
        gridPart = gridTo;
        applyParts();
        return;
      }
      pageAnimator = ValueAnimator.ofFloat(0f, 1f);
      pageAnimator.setDuration(PAGE_DURATION);
      pageAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      pageAnimator.addUpdateListener(a -> {
        final float f = (float) a.getAnimatedValue();
        pillPart = pillFrom + (pillTo - pillFrom) * f;
        cardPart = cardFrom + (cardTo - cardFrom) * f;
        detailsPart = detailsFrom + (detailsTo - detailsFrom) * f;
        gridPart = gridFrom + (gridTo - gridFrom) * f;
        applyParts();
      });
      pageAnimator.start();
    }

    /**
     * Where the popup may draw, in its own coordinates: 8dp inside the bars, the cutouts and the keyboard (which stays
     * open under the popup). The popup's own insets, counted once (its parent already keeps it off some of them).
     */
    private Rect safeRect () {
      return com.fahadali.xgram.appearance.XgramPopupBounds.safeRect(this, 0);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      final Rect safe = safeRect();
      final int gap = Screen.dp(GAP);
      final int roomWidth = Math.max(0, safe.width());
      final int roomHeight = Math.max(0, safe.height());
      int used = 0;
      if (pill != null) {
        pill.measure(MeasureSpec.makeMeasureSpec(roomWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Screen.dp(PILL_HEIGHT), MeasureSpec.EXACTLY));
        used = pill.getMeasuredHeight() + gap;
      }
      // The card, and who viewed in its place: as tall as the room on the card's side once placed
      final int cardRoom = placed ? Math.max(0, cardBelow ? safeBottom - cardEdge : cardEdge - safeTop) : Math.max(0, roomHeight - used);
      for (View v : new View[] {card, details}) {
        if (v instanceof MenuMoreWrap) {
          ((MenuMoreWrap) v).xgramSetMaxHeight(Math.max(Screen.dp(MenuMoreWrap.ITEM_HEIGHT * 2), cardRoom));
        }
        if (v != null) {
          v.measure(MeasureSpec.makeMeasureSpec(roomWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(cardRoom, MeasureSpec.AT_MOST));
        }
      }
      if (grid != null) {
        // The grid starts where the pill (or the card) starts and grows away from the message's side of the screen
        final int gridRoom = Math.max(Screen.dp(GRID_CELL * 2), gridBelow ? safeBottom - gridEdge : gridEdge - safeTop);
        grid.measure(MeasureSpec.makeMeasureSpec(roomWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(gridRoom, MeasureSpec.AT_MOST));
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      final int width = r - l, height = b - t;
      if (!placed) {
        place(width, height);
      } else if (onPlacementLost != null && Math.abs(safeRect().bottom - placedSafeBottom) > Screen.dp(24f)) {
        final Runnable lost = onPlacementLost;
        onPlacementLost = null;
        post(lost); // the keyboard came or went: the message is somewhere else now
      }
      if (pill != null) {
        layoutAt(pill, pillTop);
        pill.setPivotX(alignEnd ? pill.getWidth() : 0);
        pill.setPivotY(pill.getHeight());
      }
      for (View v : new View[] {card, details}) {
        if (v != null) {
          layoutAt(v, cardBelow ? cardEdge : cardEdge - v.getMeasuredHeight());
          v.setPivotX(alignEnd ? v.getWidth() : 0);
          v.setPivotY(cardBelow ? 0 : v.getHeight());
        }
      }
      if (grid != null) {
        layoutAt(grid, gridBelow ? gridEdge : gridEdge - grid.getMeasuredHeight());
        grid.setPivotX(alignEnd ? grid.getWidth() : 0);
        grid.setPivotY(gridBelow ? 0 : grid.getHeight());
      }
    }

    /** Where everything goes, once: the bubble, the safe area, the pill, the card, and the side the grid grows to. */
    private void place (int width, int height) {
      // The message's bubble in this root's coordinates (own arrays: Views' location helpers share one)
      final int[] location = new int[2];
      getLocationOnScreen(location);
      final int rootX = location[0], rootY = location[1];
      anchor.getLocationOnScreen(location);
      final int anchorLeft = location[0] - rootX, anchorRight = anchorLeft + anchor.getWidth();
      bubble.set(bubbleInAnchor);
      bubble.offset(anchorLeft, location[1] - rootY);
      hole.set(bubble);
      hole.inset(-Screen.dp(4f), -Screen.dp(4f));

      final Rect safe = safeRect();
      final int edge = Screen.dp(EDGE), gap = Screen.dp(GAP);
      safeTop = safe.top;
      safeBottom = safe.bottom;
      placedSafeBottom = safe.bottom;
      // Inside the message's own pane (two panes open: the chat's, not over the chat list)
      safeLeft = Math.max(safe.left, anchorLeft + edge);
      safeRight = Math.min(safe.right, anchorRight - edge);
      final int pillHeight = pill != null ? pill.getMeasuredHeight() : 0;
      final int pillBlock = pill != null ? pillHeight + gap : 0;
      final int cardHeight = card != null ? card.getMeasuredHeight() : 0;

      int cardTop;
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
      cardEdge = cardBelow ? cardTop : cardTop + cardHeight;
      // The grid: from the pill's top downwards when there is room for a few rows, else up from the pill's bottom
      final int gridStart = pill != null ? pillTop : cardTop;
      gridBelow = safeBottom - gridStart >= Screen.dp(GRID_CELL * 4) || safeBottom - gridStart >= (gridStart + pillHeight) - safeTop;
      gridEdge = gridBelow ? gridStart : gridStart + Math.max(pillHeight, Screen.dp(PILL_HEIGHT));
      // Horizontally on the message's side of its pane: its start for incoming messages, its end for outgoing ones
      alignEnd = bubble.centerX() > (anchorLeft + anchorRight) / 2;
      placed = true;
    }

    private void layoutAt (View v, int top) {
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
