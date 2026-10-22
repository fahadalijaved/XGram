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
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
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
import org.thunderdog.challegram.widget.EmojiPacksInfoView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * Messages → Message menu → Popup menu (off by default). A long press on a message opens its menu as Telegram does: a
 * floating card next to the message, the quick reactions in a pill above the message, over a dim that leaves the
 * message itself bright, instead of the bottom sheet. The keyboard stays as it is: the popup sits above it.
 *
 * Pages, in place (the popup only moves with its message, it cross-fades): the pill's expand button turns the pill and
 * the card into every reaction in a grid (its last cell: the sheet's whole picker, with custom emoji and search);
 * "Seen by" / "N reacted" turns the card into who viewed and who reacted, with Back on top. Telegram X's popup look
 * with Expressive design off.
 *
 * Placement: the pill above the message and the card below it when both fit; else both above, or both below; else
 * the card on the side with more room, as tall as that room (its rows scroll), so the message stays in sight; only
 * when neither side has room for two rows, the two stacked inside the safe area over the message. Horizontally the
 * pill and the visible card line up with the message's side, inside its pane. When the list moves the message (a new
 * message, the composer growing) the popup moves with it, and closes when it no longer fits or the message changes.
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
  /** A tap on "Seen by" before who viewed loaded opens them when they come within this long, in ms. */
  private static final long DETAILS_WAIT = 3000L;
  /** Who reacted, per request. */
  private static final int REACTIONS_PAGE = 100;

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

    /** The emoji packs the message's custom emoji are from (the card's line about them), the keyboard hidden first. */
    void openEmojiPacks (long[] emojiPackIds);
  }

  /** A popup on screen. */
  public static final class Handle {
    private final MenuMoreWrap card;
    private @Nullable TextView infoRow, readRow;

    private Handle (MenuMoreWrap card) {
      this.card = card;
    }

    /**
     * The "read at" row on top of the card (under the info line), once the read date is known. A date that comes later
     * fills the row show() kept for it (readDatePending): the card's height stays, so its rows never move once placed.
     */
    public void setReadDate (@NonNull ViewController.OptionItem item) {
      if (readRow == null) {
        readRow = addInfoRow(card, item.name, item.icon, infoRow != null ? 1 : 0, true);
      } else {
        readRow.setText(item.name);
        // The time is longer than "Read": the card may widen for it (as wide as its widest row, see addItem)
        readRow.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        readRow.setTag(readRow.getMeasuredWidth());
        // From the row, not the card: the card's request alone skips its scroll column when the card keeps its width,
        // and the row kept that measure's text height (its text drawn at the top of the row, unaligned)
        readRow.requestLayout();
      }
    }
  }

  private interface ReactionClick {
    void onReactionClick (View v, TGReaction reaction, boolean isLongClick);
  }

  /**
   * Back while the popup shows goes where it goes with the keyboard closed: to the popup (who viewed or the grid back to
   * the menu, the menu closes), and the keyboard stays, as the popup keeps it. On Android 13+ (predictive back) the
   * keyboard's own callback sits above the activity's and took Back first: the keyboard went, the message moved down
   * with the chat, and the popup closed from any page (its placement lost). This callback, above the keyboard's while
   * the popup shows, hands Back to the activity's.
   */
  private static final class BackAboveKeyboard {
    private final BaseActivity activity;
    /** An android.window.OnBackInvokedCallback (Android 13+), while registered. */
    private @Nullable Object callback;

    BackAboveKeyboard (BaseActivity activity) {
      this.activity = activity;
    }

    void register () {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && callback == null) {
        final android.window.OnBackInvokedCallback c = () -> activity.getOnBackPressedDispatcher().onBackPressed();
        callback = c;
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, c);
      }
    }

    void unregister () {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && callback != null) {
        activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) callback);
        callback = null;
      }
    }
  }

  /**
   * Shows the popup for {@code msg}'s menu, or returns null when it can't (the message is not on screen): the caller
   * opens the sheet then. {@code metadata}: the reactions pill and the reaction and viewer row (not on the page More
   * opens). {@code readDatePending}: the read date is still loading (it comes through {@link Handle#setReadDate}).
   */
  @Nullable
  public static Handle show (@NonNull ViewController<?> c, @NonNull TGMessage msg, @NonNull ViewController.Options options, @NonNull OptionDelegate delegate, boolean metadata, boolean readDatePending, @NonNull Host host) {
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
    final Root root = new Root(context, view, msg);
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
    // Who viewed and who reacted: as the sheet's header has them when the message takes reactions (State); a message
    // that can't take them still shows who read it, as Telegram X's menu does there (patchReadReceiptsOptions), and
    // who reacted before reactions went off
    final boolean views = state != null ? state.needShowMessageViews : metadata && msg.canGetViewers() && !(msg.isUnread() && !msg.noUnread());
    final boolean reactions = state != null ? state.needShowMessageReactionSenders : metadata && msg.canGetAddedReactions() && !Td.isEmpty(msg.getMessageReactions().getReactions()) && msg.getMessageReactions().getTotalCount() > 0;
    if (views || reactions) {
      final int total = msg.getMessageReactions().getTotalCount();
      final boolean reactedTitle = reactions && total > 0;
      final CharSequence title = reactedTitle ? Lang.plural(R.string.xReacted, total) : Lang.getString(R.string.XgramPopupViewers);
      // Who viewed and who reacted load while the menu opens (as Telegram's), so a tap shows them at once. A tap before
      // they are here turns the row into "Loading…", and opens them when they come soon after (a late answer, after a
      // reconnect, only puts the row back: the next tap opens them).
      final DetailsState details = new DetailsState();
      final SenderClick onSender = sender -> {
        afterClose[0] = () -> tdlib.ui().openSenderProfile(c, sender, null);
        popup.hideWindow(true);
      };
      final Runnable openDetails = () -> {
        if (popup.isWindowHidden() || !root.isOnMenu() || details.viewers == null) {
          return; // closed, or the grid took the popup meanwhile
        }
        if (details.viewers.isEmpty()) {
          UI.showToast(R.string.XgramPopupNoViewers, android.widget.Toast.LENGTH_SHORT);
          return;
        }
        MenuMoreWrap page = newDetails(context, tdlib, msg, themeListeners, details.title != null ? details.title : title, details.viewers, () -> root.showPage(Root.PAGE_MENU, null), onSender);
        page.xgramSetMaxItemsWidth(Math.min(Screen.dp(CARD_MAX_WIDTH), maxWidth));
        details.page = page;
        // Who reacted past the first page comes a page at a time, as the list nears its end (or ends in sight)
        page.xgramSetOnScroll(() -> {
          if (page.xgramScrollRemaining() < Screen.dp(MenuMoreWrap.ITEM_HEIGHT * 5)) {
            loadMoreReactions(tdlib, msg, details, onSender);
          }
        });
        root.showPage(Root.PAGE_DETAILS, page);
      };
      final TextView row = card.addItem(R.id.btn_xgramPopupDetails, title, R.drawable.baseline_visibility_24, null, v -> {
        if (details.viewers == null) {
          details.tappedAt = SystemClock.uptimeMillis();
          setRowText((TextView) v, Lang.getString(R.string.LoadingMessageSeen));
        } else {
          openDetails.run();
        }
      });
      loadDetails(tdlib, msg, views, reactions, (viewers, nextReactionsOffset) -> {
        details.viewers = viewers;
        details.reactionsOffset = nextReactionsOffset;
        if (!reactedTitle) {
          int seen = 0;
          for (Viewer viewer : viewers) {
            if (viewer.viewed) {
              seen++;
            }
          }
          if (seen > 0) {
            details.title = Lang.getString(R.string.XgramPopupViewersCount, seen); // "Seen by 5"
          }
        }
        setRowText(row, details.title != null ? details.title : title); // also in place of "Loading…"
        if (details.tappedAt != 0) {
          final boolean soon = SystemClock.uptimeMillis() - details.tappedAt <= DETAILS_WAIT;
          details.tappedAt = 0;
          if (soon) {
            openDetails.run();
          }
        }
      });
    }
    final Handle handle = new Handle(card);
    if (options.info != null && options.info.length() > 0) {
      // What the sheet says above its options: why a message failed, that it can't be saved, a post's views
      handle.infoRow = addInfoRow(card, options.info, 0, 0, false);
    }
    if (options.subtitle != null && options.subtitle.name != null) {
      handle.setReadDate(options.subtitle);
    } else if (readDatePending) {
      // Its row now, with the time to come: added after placement it grew the card over the pill (the menu above the
      // message) or moved every row down as the user aimed. Only a read message loads a read date, so "Read" is true
      // even when the time never comes (hidden by privacy).
      handle.readRow = addInfoRow(card, Lang.getString(R.string.XgramPopupReadPending), R.drawable.deproko_baseline_check_double_24, handle.infoRow != null ? 1 : 0, true);
    }
    final long[] emojiPackIds = msg.getUniqueEmojiPackIdList();
    final EmojiPacksInfoView emojiPacks = emojiPackIds.length > 0 ? new EmojiPacksInfoView(context, c, tdlib) : null;
    if (emojiPacks != null) {
      // As both sheets have it with their info lines: the packs the message's custom emoji are from, their name a link
      // that opens them (once the popup is gone). As wide as the card (it has no width of its own), under its info rows;
      // a read date that comes later goes above it.
      emojiPacks.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      final int horizontal = Screen.dp(m3 ? 12f : 17f), vertical = Screen.dp(8f); // the card's info line's
      emojiPacks.setPadding(horizontal, vertical, horizontal, vertical);
      if (m3) {
        // Its own 14sp style: the Expressive one it starts with is shared (bodySmall), and setTextSize would resize
        // every text that uses it (the chat header's subtitle, the sheets' emoji packs line)
        emojiPacks.setTextStyleProvider(com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(XgramExpressive.TS_BODY_M, 14f));
      } else {
        emojiPacks.setTextSize(14f);
      }
      emojiPacks.update(msg.getFirstEmojiId(), emojiPackIds, new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          afterClose[0] = () -> host.openEmojiPacks(emojiPackIds);
          popup.hideWindow(true);
        }
      }, false);
      card.xgramAddView(emojiPacks, (handle.infoRow != null ? 1 : 0) + (handle.readRow != null ? 1 : 0));
    }
    card.xgramSetMaxItemsWidth(Math.min(Screen.dp(CARD_MAX_WIDTH), maxWidth));

    if (root.pill != null) {
      root.addView(root.pill);
    }
    root.card = card;
    root.addView(card);
    // The keyboard (or a bar) came or went under the popup, or the message moved where the popup can't follow it (or
    // changed its size, or left the screen): the popup closes
    root.onPlacementLost = () -> popup.hideWindow(true);

    popup.init(false);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);
    popup.setHideBackground(true); // the root draws the dim, with the message left bright
    final BackAboveKeyboard backAboveKeyboard = new BackAboveKeyboard(activity);
    popup.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismissPrepare (PopupLayout p) {
        backAboveKeyboard.unregister(); // closing: Back goes to what is under it again (a sheet an option opened)
      }

      @Override
      public void onPopupDismiss (PopupLayout p) {
        backAboveKeyboard.unregister(); // also when it went without closing (the activity was busy)
        Runnable action = afterClose[0];
        afterClose[0] = null;
        root.releaseLists(); // a list that leaves the window does not recycle its rows: their stickers stay alive
        if (emojiPacks != null) {
          emojiPacks.performDestroy(); // its custom emoji's receivers
        }
        host.onPopupClosed();
        if (action != null) {
          action.run();
        }
      }
    });
    // Back (the gesture, the key) on who viewed or on the reaction grid goes back to the menu, as the page's own Back row
    // does; on the menu it closes the popup. With the keyboard open too, which stays (BackAboveKeyboard).
    popup.setBackListener((fromTop, commit) -> {
      if (root.isOnMenu() || popup.isWindowHidden()) {
        return false;
      }
      if (commit) {
        root.showPage(Root.PAGE_MENU, null);
      }
      return true;
    });
    host.onPopupOpen();
    backAboveKeyboard.register(); // before the show: a popup the busy activity drops is dismissed (and unregisters) in it
    // Only while it is in the window: the passcode takes it out for a while (PopupLayout.hideTemporarily), and Back
    // then belongs to the passcode's keyboard (else it went to the activity, which leaves the app on the passcode)
    popup.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (@NonNull View v) {
        if (!popup.isWindowHidden()) {
          backAboveKeyboard.register();
        }
      }

      @Override
      public void onViewDetachedFromWindow (@NonNull View v) {
        backAboveKeyboard.unregister();
      }
    });
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
        if (!p.hadCompletelyShown()) {
          // Closed while it opened (More tapped at once): its show is done. A window still opening keeps the activity
          // busy, which dropped the popup More's page opens while this one fades out (no show listener to call here).
          p.onCustomShowComplete();
        }
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

  /** The message's bubble (or its whole row without bubbles) in the message view's coordinates, into {@code rect}. */
  private static Rect bubbleRect (TGMessage msg, View view, Rect rect) {
    rect.setEmpty();
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
        // As wide as its reactions (up to the room it is given), the expand button at its end. Only when there is more
        // than the strip shows (it is cut, or the grid's last cell opens the whole picker): with every reaction in
        // sight, the grid would repeat the pill (as the sheet, which has no expand button then).
        final int max = MeasureSpec.getSize(widthMeasureSpec);
        final View strip = getChildAt(0);
        final View expand = getChildCount() > 1 ? getChildAt(1) : null;
        final int height = Screen.dp(PILL_HEIGHT);
        final int room = Math.max(0, max - getPaddingLeft() - getPaddingRight());
        boolean needExpand = expand != null && state.needShowReactionsPopupPicker;
        if (expand != null && !needExpand) {
          // the strip is as wide as its reactions when they fit: as wide as the room, they don't (or only just do)
          strip.measure(MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
          needExpand = strip.getMeasuredWidth() >= room;
        }
        if (expand != null) {
          final int visibility = needExpand ? View.VISIBLE : View.GONE;
          if (expand.getVisibility() != visibility) {
            expand.setVisibility(visibility);
          }
        }
        final int expandWidth = needExpand ? Screen.dp(48f) : 0;
        if (expand == null || needExpand) {
          strip.measure(MeasureSpec.makeMeasureSpec(Math.max(0, room - expandWidth), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        if (needExpand) {
          expand.measure(MeasureSpec.makeMeasureSpec(expandWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(Math.min(max, strip.getMeasuredWidth() + expandWidth + getPaddingLeft() + getPaddingRight()), height);
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        final View strip = getChildAt(0);
        final View expand = getChildCount() > 1 && getChildAt(1).getVisibility() != View.GONE ? getChildAt(1) : null;
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

  /**
   * The card's row for who viewed: what loaded (null until then), when a tap came before it (0: none waiting), its
   * counted title; where the next page of who reacted starts (empty: no more), and the page on screen that gets its rows.
   */
  private static final class DetailsState {
    @Nullable List<Viewer> viewers;
    long tappedAt;
    @Nullable CharSequence title;
    @Nullable String reactionsOffset;
    boolean loadingMore;
    @Nullable MenuMoreWrap page;
  }

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
    /** {@code nextReactionsOffset}: where who reacted goes on (empty or null: that was all). */
    void onViewers (List<Viewer> viewers, @Nullable String nextReactionsOffset);
  }

  private static int newestFirst (Viewer a, Viewer b) {
    return Integer.compare(b.date, a.date);
  }

  /**
   * Who reacted into {@code result}: a new person at its end, someone already there gets their first reaction (one row
   * per person). Whether someone already there changed.
   */
  private static boolean addReactions (List<Viewer> result, TdApi.AddedReaction[] reactions) {
    boolean changed = false;
    for (TdApi.AddedReaction reaction : reactions) {
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
      } else if (viewer.reaction == null) {
        changed = true;
      }
      if (viewer.reaction == null) {
        viewer.reaction = reaction.type;
      }
    }
    return changed;
  }

  /** Who viewed (with when) and who reacted (with what), one row per person, the newest first. UI thread callback. */
  private static void loadDetails (Tdlib tdlib, TGMessage msg, boolean views, boolean reactions, ViewersCallback done) {
    // Viewers by the message's id (as Telegram X's Seen page); an album's reactions sit on its oldest item
    final long chatId = msg.getChatId(), viewersMessageId = msg.getId(), reactionsMessageId = msg.getSmallestId();
    final List<Viewer> result = new ArrayList<>();
    final String[] nextOffset = new String[1];
    final Runnable finish = () -> tdlib.ui().post(() -> {
      Collections.sort(result, XgramMessagePopup::newestFirst);
      done.onViewers(result, nextOffset[0]);
    });
    final Runnable loadReactions = () -> {
      if (!reactions) {
        finish.run();
        return;
      }
      // The first page only, while the menu opens; the details page asks for the rest as it scrolls (loadMoreReactions)
      tdlib.send(new TdApi.GetMessageAddedReactions(chatId, reactionsMessageId, null, "", REACTIONS_PAGE), (added, error) -> {
        if (added != null) {
          addReactions(result, added.reactions);
          nextOffset[0] = added.nextOffset;
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

  /**
   * The next page of who reacted, once the details page nears its end: merged in, its people added to the page on
   * screen (under its rows when they are older than all of them, else the rows are made again, newest first).
   */
  private static void loadMoreReactions (Tdlib tdlib, TGMessage msg, DetailsState details, SenderClick onSender) {
    final String offset = details.reactionsOffset;
    if (details.loadingMore || details.viewers == null || StringUtils.isEmpty(offset)) {
      return;
    }
    details.loadingMore = true;
    tdlib.send(new TdApi.GetMessageAddedReactions(msg.getChatId(), msg.getSmallestId(), null, offset, REACTIONS_PAGE), (added, error) -> tdlib.ui().post(() -> {
      details.loadingMore = false;
      final List<Viewer> list = details.viewers;
      if (added == null || list == null) {
        details.reactionsOffset = null; // an error (the message is gone): no more pages
        return;
      }
      details.reactionsOffset = added.nextOffset;
      final int oldSize = list.size();
      boolean inOrder = !addReactions(list, added.reactions);
      for (int i = Math.max(1, oldSize); i < list.size() && inOrder; i++) {
        inOrder = list.get(i - 1).date >= list.get(i).date;
      }
      final MenuMoreWrap page = details.page;
      if (inOrder) {
        if (page != null) {
          for (int i = oldSize; i < list.size(); i++) {
            addViewerRow(page, tdlib, msg, list.get(i), onSender);
          }
        }
      } else {
        Collections.sort(list, XgramMessagePopup::newestFirst);
        if (page != null) {
          page.removeItemsFrom(1); // all but Back
          for (Viewer viewer : list) {
            addViewerRow(page, tdlib, msg, viewer, onSender);
          }
        }
      }
      if (list.size() == oldSize && !StringUtils.isEmpty(details.reactionsOffset)) {
        // Only people already listed (no new row to scroll to, nor a layout to ask again): the page after it
        loadMoreReactions(tdlib, msg, details, onSender);
      }
    }));
  }

  /** The card's second page: Back, then one row per person (photo, name, the reaction and when), a tap opens them. */
  private static MenuMoreWrap newDetails (Context context, Tdlib tdlib, TGMessage msg, ThemeListenerList themeListeners, CharSequence title, List<Viewer> viewers, Runnable onBack, SenderClick onSender) {
    final MenuMoreWrap details = new MenuMoreWrap(context, /* scrollable */ true);
    details.init(themeListeners, null);
    details.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_CENTER);
    details.setRightNumber(0);
    details.addItem(R.id.btn_xgramPopupBack, title, R.drawable.baseline_arrow_back_24, null, v -> onBack.run());
    for (Viewer viewer : viewers) {
      addViewerRow(details, tdlib, msg, viewer, onSender);
    }
    return details;
  }

  /** A person's row on the details page: photo, name, what they reacted with and when. */
  private static void addViewerRow (MenuMoreWrap details, Tdlib tdlib, TGMessage msg, Viewer viewer, SenderClick onSender) {
    final String time = viewer.viewed ? TGUser.getActionDateStatus(tdlib, viewer.date, msg.getMessage()) : Lang.getRelativeTimestamp(viewer.date, TimeUnit.SECONDS);
    final String emoji = viewer.reaction != null ? reactionEmoji(tdlib, viewer.reaction) : null;
    final String subtitle = emoji != null ? emoji + "  " + time : time;
    HapticMenuHelper.MenuItem item = new HapticMenuHelper.MenuItem(R.id.btn_xgramPopupViewer, tdlib.senderName(viewer.sender), subtitle, 0, tdlib, viewer.sender, false);
    details.addItem(tdlib, item, v -> onSender.onSender(viewer.sender));
  }

  /**
   * A reaction as text for its row: the emoji; a custom emoji's own emoji (its sticker's, once it is known: the
   * message's reactions have usually loaded it); a star for a paid one.
   */
  @Nullable
  private static String reactionEmoji (Tdlib tdlib, TdApi.ReactionType type) {
    if (type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) {
      return ((TdApi.ReactionTypeEmoji) type).emoji;
    } else if (type.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR) {
      final TdlibEmojiManager.Entry entry = tdlib.emoji().findOrRequest(((TdApi.ReactionTypeCustomEmoji) type).customEmojiId, null);
      return entry != null && entry.value != null && !StringUtils.isEmpty(entry.value.emoji) ? entry.value.emoji : null;
    } else if (type.getConstructor() == TdApi.ReactionTypePaid.CONSTRUCTOR) {
      return "⭐";
    }
    return null;
  }

  /** A card row's new text; the card may widen for it (it is as wide as its widest row, see MenuMoreWrap.addItem). */
  private static void setRowText (TextView row, CharSequence text) {
    if (TextUtils.equals(row.getText(), text)) {
      return;
    }
    row.setText(text);
    row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    row.setTag(row.getMeasuredWidth());
    row.requestLayout();
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
    private final TGMessage msg;
    private final Rect bubble = new Rect();
    /** The bubble as the last frame found it, and a location, for the tracking before each frame. */
    private final Rect trackedBubble = new Rect();
    private final int[] trackedLocation = new int[2];
    private final ViewTreeObserver.OnPreDrawListener tracker = () -> {
      track();
      return true;
    };
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

    // Placement, fixed at the first layout: a page switch never moves the popup (the message moving moves it, track)
    private boolean placed, alignEnd, cardBelow, gridBelow;
    /** The pill's top; the visible card's top when it grows down (below the message), else its bottom. */
    private int pillTop, cardEdge, gridEdge;
    /** How far the pill moved while a grid was open (that grid stays where it is); the next grid starts at the pill. */
    private int gridLag;
    private int safeTop, safeBottom, safeLeft, safeRight;

    Root (Context context, View anchor, TGMessage msg) {
      super(context);
      this.anchor = anchor;
      this.msg = msg;
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
        gridEdge += gridLag;
        gridLag = 0;
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
      // As wide as the message's pane at most (two panes open: the chat's half, not the window), 8dp in on each side
      final int paneWidth = anchor.getWidth() > 0 ? anchor.getWidth() - Screen.dp(EDGE) * 2 : safe.width();
      final int roomWidth = Math.max(0, Math.min(safe.width(), paneWidth));
      final int roomHeight = Math.max(0, safe.height());
      int used = 0;
      if (pill != null) {
        pill.measure(MeasureSpec.makeMeasureSpec(roomWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Screen.dp(PILL_HEIGHT), MeasureSpec.EXACTLY));
        used = pill.getMeasuredHeight() + gap;
      }
      // The card, and who viewed in its place: as tall as the room on the card's side once placed. Above the message the
      // card stays under the pill: a line it gains after placement (the emoji pack's name, longer than "Loading…") grows
      // it up with the pill on top (onLayout) while the pill stays in the safe area, past that its rows scroll instead of
      // growing over the reactions; who viewed may take the pill's band (the pill fades out for it).
      final int cardRoom = placed ? Math.max(0, cardBelow ? safeBottom - cardEdge : cardEdge - safeTop) : Math.max(0, roomHeight - used);
      final int menuRoom = placed && !cardBelow && pill != null ? Math.max(0, cardEdge - (safeTop + pill.getMeasuredHeight() + gap)) : cardRoom;
      for (View v : new View[] {card, details}) {
        if (v == null) {
          continue;
        }
        // The rooms are the visible card's: the view adds the card's insets (the classic look's shadow above and below)
        final int room = (v == card ? menuRoom : cardRoom) + 2 * insetY(v);
        if (v instanceof MenuMoreWrap) {
          ((MenuMoreWrap) v).xgramSetMaxHeight(Math.max(Screen.dp(MenuMoreWrap.ITEM_HEIGHT * 2), room));
        }
        v.measure(MeasureSpec.makeMeasureSpec(roomWidth + 2 * insetX(v), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST));
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
        placementLost(); // the keyboard came or went: the message is somewhere else now
      }
      if (placed && !cardBelow && pill != null && card != null) {
        // Above the message the pill sits on the card's top, the card's bottom by the message: a card that changed its
        // height after placement keeps its rows by the message where they are and the pill on its top (as place() put
        // it), inside the safe area (onMeasure keeps the card under it)
        final int top = Math.max(safeTop, cardEdge - (card.getMeasuredHeight() - 2 * insetY(card)) - Screen.dp(GAP) - pill.getMeasuredHeight());
        if (top != pillTop) {
          if (page != PAGE_GRID) {
            gridEdge += top - pillTop; // the grid starts where the pill does
          } else {
            gridLag += top - pillTop; // an open grid stays where it is; the next one catches up (showPage)
          }
          pillTop = top;
        }
      }
      if (pill != null) {
        layoutAt(pill, pillTop);
        pill.setPivotX(alignEnd ? pill.getWidth() : 0);
        pill.setPivotY(pill.getHeight());
      }
      for (View v : new View[] {card, details}) {
        if (v != null) {
          // The visible card's edge at cardEdge (the classic look's shadow reaches past it); it grows from its corner
          final int sx = insetX(v), sy = insetY(v);
          layoutAt(v, cardBelow ? cardEdge - sy : cardEdge + sy - v.getMeasuredHeight());
          v.setPivotX(alignEnd ? v.getWidth() - sx : sx);
          v.setPivotY(cardBelow ? sy : v.getHeight() - sy);
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
      bubbleRect(msg, anchor, bubble); // now, as track() measures it in each frame after this one
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
      // The visible card's height (cardTop and cardEdge are its edges): the view's less the classic look's shadow
      final int cardInset = card != null ? insetY(card) : 0;
      int cardHeight = card != null ? card.getMeasuredHeight() - 2 * cardInset : 0;

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
        // A long menu, or a short window (the keyboard open, the Fold folded): the card on the side with more room, as
        // tall as that room (its rows scroll), so the message stays in sight. Below the message the pill stays above
        // it when it fits there (Telegram's), else it goes on top of the card; above the message, on top of the card.
        final boolean pillFitsAbove = pill == null || bubble.top - gap - pillHeight >= safeTop;
        final int roomBelow = safeBottom - (bubble.bottom + gap) - (pillFitsAbove ? 0 : pillBlock);
        final int roomAbove = bubble.top - gap - pillBlock - safeTop;
        if (card != null && Math.max(roomBelow, roomAbove) >= Screen.dp(MenuMoreWrap.ITEM_HEIGHT * 2 + MenuMoreWrap.PADDING * 2)) {
          cardBelow = roomBelow >= roomAbove;
          // Capped now, before this layout places it (a cap from the next measure left a frame of the whole card over
          // the message); the measures after this one keep the cap, from cardEdge
          card.measure(MeasureSpec.makeMeasureSpec(card.getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((cardBelow ? roomBelow : roomAbove) + 2 * cardInset, MeasureSpec.AT_MOST));
          cardHeight = card.getMeasuredHeight() - 2 * cardInset;
          if (cardBelow) {
            pillTop = pillFitsAbove ? bubble.top - gap - pillHeight : bubble.bottom + gap;
            cardTop = pillFitsAbove ? bubble.bottom + gap : pillTop + pillBlock;
          } else {
            cardTop = bubble.top - gap - cardHeight;
            pillTop = cardTop - pillBlock;
          }
        } else {
          // a message taller than the screen, or a tiny window: stacked inside the safe area, over the message (the
          // card's rows scroll)
          final int block = pillBlock + cardHeight;
          int top = bubble.top - pillBlock;
          top = Math.max(safeTop, Math.min(top, safeBottom - block));
          pillTop = top;
          cardTop = top + pillBlock;
          cardBelow = true;
        }
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
      // The visible edge on the message's and the safe area's: a menu's card sits inset in its view (the Expressive
      // container's inset, the classic look's shadow), so its view reaches past them by that inset
      final int sx = insetX(v);
      int left = alignEnd ? bubble.right + sx - w : bubble.left - sx;
      left = Math.max(safeLeft - sx, Math.min(left, safeRight + sx - w));
      left = Math.max(0, left); // a pane narrower than the view: its start edge wins
      v.layout(left, top, left + w, top + h);
    }

    /** How far the visible card sits inside {@code v}'s sides (0 for the pill and the grid: they fill their views). */
    private static int insetX (View v) {
      return v instanceof MenuMoreWrap ? ((MenuMoreWrap) v).xgramCardInsetX() : 0;
    }

    /** How far the visible card sits inside {@code v}'s top and bottom (the classic look's shadow). */
    private static int insetY (View v) {
      return v instanceof MenuMoreWrap ? ((MenuMoreWrap) v).xgramCardInsetY() : 0;
    }

    /** The placement no longer holds: the popup closes (posted: not from inside a layout or a pre-draw pass). */
    private void placementLost () {
      final Runnable lost = onPlacementLost;
      if (lost != null) {
        onPlacementLost = null;
        post(lost);
      }
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      getViewTreeObserver().addOnPreDrawListener(tracker); // the popup is in the chat's window: one pass for both
    }

    @Override
    protected void onDetachedFromWindow () {
      getViewTreeObserver().removeOnPreDrawListener(tracker); // still the window's observer here
      super.onDetachedFromWindow();
    }

    /**
     * Before each frame: the message moved under the popup (a new message pushed the list up, the composer grew a
     * line), so the hole and the popup move with it while all of it still fits in the safe area. The popup closes when
     * it no longer fits, when the message changed its size or side (an edit, a first reaction) or left its view.
     */
    private void track () {
      if (!placed || onPlacementLost == null || isLayoutRequested() || anchor.isLayoutRequested()) {
        return;
      }
      if (anchor.getWindowToken() == null || !anchor.isShown() || !(anchor instanceof MessageView) || ((MessageView) anchor).getMessage() != msg || msg.isDestroyed()) {
        placementLost();
        return;
      }
      getLocationOnScreen(trackedLocation);
      final int rootX = trackedLocation[0], rootY = trackedLocation[1];
      anchor.getLocationOnScreen(trackedLocation);
      bubbleRect(msg, anchor, trackedBubble);
      trackedBubble.offset(trackedLocation[0] - rootX, trackedLocation[1] - rootY);
      if (trackedBubble.equals(bubble)) {
        return;
      }
      final int dy = trackedBubble.top - bubble.top;
      if (trackedBubble.left != bubble.left || trackedBubble.width() != bubble.width() || trackedBubble.height() != bubble.height() || !fitsMovedBy(dy)) {
        placementLost();
        return;
      }
      bubble.offset(0, dy);
      hole.offset(0, dy);
      pillTop += dy;
      cardEdge += dy;
      gridEdge += dy;
      for (View v : new View[] {pill, card, details, grid}) {
        if (v != null) {
          v.offsetTopAndBottom(dy); // in this frame (a new layout would draw it in the next one)
        }
      }
      invalidate(); // the hole
    }

    /** Whether every part, on the page or not (a page can come back), stays in the safe area moved by {@code dy}. */
    private boolean fitsMovedBy (int dy) {
      int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
      if (pill != null) {
        top = pillTop;
        bottom = pillTop + pill.getHeight();
      }
      for (View v : new View[] {card, details}) {
        if (v != null) {
          final int h = v.getHeight() - 2 * insetY(v);
          final int vTop = cardBelow ? cardEdge : cardEdge - h;
          top = Math.min(top, vTop);
          bottom = Math.max(bottom, vTop + h);
        }
      }
      if (grid != null) {
        final int gridTop = gridBelow ? gridEdge : gridEdge - grid.getHeight();
        top = Math.min(top, gridTop);
        bottom = Math.max(bottom, gridTop + grid.getHeight());
      }
      return top == Integer.MAX_VALUE || (top + dy >= safeTop && bottom + dy <= safeBottom);
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
