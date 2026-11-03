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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.chats.XgramMessagePopup;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;

import java.lang.ref.WeakReference;

import me.vkryl.core.StringUtils;

/**
 * XGram: Appearance › Menus › Compact popups ({@link XgramSettings#menuStyle()}). Telegram X's option menus
 * ({@link ViewController#showOptions(ViewController.Options, OptionDelegate, ThemeDelegate)}) open as a small floating
 * card next to what was tapped, as official Telegram's popups, instead of a bottom sheet across the screen.
 * <p>
 * The card is the ⋮ menus' and the R5 menus' ({@link MenuMoreWrap}: the M3E surfaceContainer card, 16dp corners,
 * bodyLarge labels, onSurfaceVariant icons; Telegram X's popup with Expressive design off), scrolling when long, at most
 * 320dp wide, placed by {@link MenuMoreWrap#xgramSetPopupAnchor}: below the tapped row or button when it fits there,
 * else above it (above first for what sits in the bottom quarter, the composer), inside the window's safe area (status
 * bar, cutout, navigation bar, keyboard, the chat's pane), growing out of the anchor. A tap outside or Back closes it,
 * also with the keyboard open (the keyboard stays). Red options are in the error color, blue in primary.
 * <p>
 * Only simple menus become a card ({@link #fitsPopup}); the rest stay sheets: menus with a title or a subtitle,
 * confirmations (a description with a Cancel option, or a single action with Cancel, or a question), a description
 * with links or longer than two short lines, rows whose labels don't fit the card, menus that can't be closed by a
 * touch outside or that tag their rows, menus opened with no tap just before (after a load: there is nothing to open
 * at), and those their caller marks ({@link ViewController#xgramSheetOnce()}, {@link ViewController.Options#xgramSheetOnly}).
 * Settings choice lists (showSettings), the message menu sheet, share, sticker set and attach sheets never come here.
 * <p>
 * Where it opens: {@link ViewController.Options#xgramAnchor} when the caller gives one, else the smallest tappable view
 * under the last touch (at most a third of the window tall; a row wider than the card: at the finger along it, half a
 * card in from its ends; a list's row: that place in its list, which doesn't move with its rows), else a small square at
 * the touch. The touch is recorded for every window (BaseActivity and PopupLayout feed {@link #onTouch}).
 * Main thread only.
 */
public final class XgramCompactMenu {
  private XgramCompactMenu () { }

  /** How long after the finger lifts a menu still opens at it (a tap's click runs right after the lift). */
  private static final long TAP_WINDOW_MS = 1200L;
  /** A finger still down for at most this long: a long press, whose menu opens while it is held. */
  private static final long HOLD_WINDOW_MS = 30000L;
  /** The widest the card gets (dp), as the R5 menus (XgramR5Chat.showMenu). */
  private static final float MAX_WIDTH = 320f;
  /** A description (a sentence, not a name or a link: {@link #isLabel}) longer than this keeps the sheet. */
  private static final int MAX_INFO_LENGTH = 120;
  /** A description with more lines than this keeps the sheet. */
  private static final int MAX_INFO_LINES = 2;
  /** The anchor when nothing tappable is under the finger: a square this size (dp) around the touch. */
  private static final float TOUCH_BOX = 24f;

  // The last touch, in screen coordinates

  private static float downX, downY;
  private static long downTime, upTime;
  private static boolean fingerDown;
  private static @Nullable WeakReference<View> touchRoot;

  /** Compact popups are on (Appearance › Menus). */
  public static boolean isEnabled () {
    return XgramSettings.instance().menuStyle() == XgramSettings.MENU_STYLE_COMPACT;
  }

  /** The name of a {@link XgramSettings#menuStyle()} value: Sheets or Compact popups. */
  @NonNull
  public static String styleName (int style) {
    return Lang.getString(style == XgramSettings.MENU_STYLE_COMPACT ? R.string.XgramMenuStyleCompact : R.string.XgramMenuStyleSheets);
  }

  /**
   * Records where the finger goes down and when it lifts. {@code root}: the window's root view the touch came to (the
   * activity's decor view, or a popup that is a window of its own); a later call for the same touch (a popup inside the
   * activity's views) replaces it with the innermost one.
   */
  public static void onTouch (@Nullable View root, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        downX = e.getRawX();
        downY = e.getRawY();
        downTime = SystemClock.uptimeMillis();
        upTime = 0;
        fingerDown = true;
        touchRoot = root != null ? new WeakReference<>(root) : null;
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (fingerDown) {
          fingerDown = false;
          upTime = SystemClock.uptimeMillis();
        }
        break;
      }
    }
  }

  private static boolean hasRecentTouch () {
    final long now = SystemClock.uptimeMillis();
    if (fingerDown) {
      return downTime != 0 && now - downTime <= HOLD_WINDOW_MS;
    }
    return upTime != 0 && now - upTime <= TAP_WINDOW_MS;
  }

  // Which menus

  /**
   * Whether {@code options} is a simple menu for a card: rows (no title, no subtitle), no confirmation, a description
   * (if any) that is a name or a link, or a short sentence without links, and a delegate that lets a touch outside close
   * it and doesn't tag its rows (MenuMoreWrap keeps its rows' widths in their tags).
   */
  public static boolean fitsPopup (@NonNull ViewController.Options options, @Nullable OptionDelegate delegate) {
    if (options.items == null || options.items.length == 0) {
      return false;
    }
    if (!StringUtils.isEmpty(options.title) || options.subtitle != null) {
      return false; // a titled sheet: a confirm sheet's headline, the chat's emoji status
    }
    if (delegate != null) {
      if (delegate.disableCancelOnTouchdown()) {
        return false;
      }
      try {
        for (int i = 0; i < options.items.length; i++) {
          if (delegate.getTagForItem(i) != null) {
            return false;
          }
        }
      } catch (Throwable t) {
        return false;
      }
    }
    if (XgramR5Sheets.confirmActionIndex(options.items) != -1) {
      return false; // one action and Cancel: yes or no
    }
    int rows = 0;
    boolean hasCancel = false;
    for (ViewController.OptionItem item : options.items) {
      if (item == null) {
        return false;
      }
      if (item == ViewController.OptionItem.SEPARATOR) {
        continue;
      }
      if (XgramR5Sheets.isPlainCancel(item)) {
        hasCancel = true;
        continue;
      }
      if (item.name != null && TextUtils.indexOf(item.name, '\n') != -1) {
        return false; // a row with a second line
      }
      rows++;
    }
    if (rows == 0) {
      return false;
    }
    final CharSequence info = options.info;
    if (!StringUtils.isEmpty(info)) {
      if (hasCancel) {
        return false; // Telegram X's confirmations: what happens, the actions, then Cancel
      }
      if (isQuestion(info) || hasLinks(info)) {
        return false;
      }
      if (!isLabel(info) && (info.length() > MAX_INFO_LENGTH || lineCount(info) > MAX_INFO_LINES)) {
        return false; // a description to read (buildCard also keeps the sheet for one over four lines at the card's width)
      }
    }
    return true;
  }

  private static boolean hasWhitespace (@NonNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      if (Character.isWhitespace(text.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static int lineCount (@NonNull CharSequence text) {
    int lines = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        lines++;
      }
    }
    return lines;
  }

  /** The text ends with a question mark (Latin, Arabic, full width), quotes and spaces aside. */
  private static boolean isQuestion (@NonNull CharSequence text) {
    for (int i = text.length() - 1; i >= 0; i--) {
      final char c = text.charAt(i);
      if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '”' || c == '»' || c == ')') {
        continue;
      }
      return c == '?' || c == '؟' || c == '？';
    }
    return false;
  }

  /** Links in the text: clickable spans, Telegram entities that open something, or an address inside a sentence. */
  private static boolean hasLinks (@NonNull CharSequence text) {
    if (text instanceof Spanned) {
      final Spanned spanned = (Spanned) text;
      if (spanned.getSpans(0, spanned.length(), ClickableSpan.class).length > 0) {
        return true;
      }
      for (CustomTypefaceSpan span : spanned.getSpans(0, spanned.length(), CustomTypefaceSpan.class)) {
        if (span.getOnClickListener() != null || isLinkEntity(span.getTextEntityType())) {
          return true;
        }
      }
    }
    final String s = text.toString();
    return hasWhitespace(s) && (s.contains("://") || s.contains("t.me/"));
  }

  private static boolean isLinkEntity (@Nullable TdApi.TextEntityType type) {
    return type instanceof TdApi.TextEntityTypeUrl || type instanceof TdApi.TextEntityTypeTextUrl ||
      type instanceof TdApi.TextEntityTypeMention || type instanceof TdApi.TextEntityTypeMentionName ||
      type instanceof TdApi.TextEntityTypeEmailAddress || type instanceof TdApi.TextEntityTypePhoneNumber ||
      type instanceof TdApi.TextEntityTypeHashtag || type instanceof TdApi.TextEntityTypeCashtag ||
      type instanceof TdApi.TextEntityTypeBotCommand || type instanceof TdApi.TextEntityTypeBankCardNumber;
  }

  /**
   * A description without spaces: a link, a username, a number, shown on one line, shortened in the middle. Not text in
   * a script written without spaces (Chinese, Japanese, Thai, ...): that is a sentence. UnicodeBlock, not UnicodeScript
   * (API 24): the legacy variants' minSdk.
   */
  private static boolean isLabel (@NonNull CharSequence text) {
    if (hasWhitespace(text)) {
      return false;
    }
    for (int i = 0; i < text.length(); ) {
      final int codePoint = Character.codePointAt(text, i);
      final Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
      if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
        block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.THAI ||
        block == Character.UnicodeBlock.LAO ||
        block == Character.UnicodeBlock.KHMER ||
        block == Character.UnicodeBlock.MYANMAR ||
        block == Character.UnicodeBlock.TIBETAN) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  // Where

  /** What the card opens at: the caller's anchor, or the last touch and what it went to ({@link #locate}). */
  private static final class Target {
    final int x, y;
    /** The tapped view or the caller's anchor; null: nothing tappable under the finger. */
    final @Nullable View hit;
    /** {@link #hit} is the caller's anchor ({@link ViewController.Options#xgramAnchor}). */
    final boolean given;
    /** The list the touch went to: the tapped row's, or the one under a finger on nothing tappable (a link in a message). */
    final @Nullable View list;

    Target (int x, int y, @Nullable View hit, boolean given, @Nullable View list) {
      this.x = x;
      this.y = y;
      this.hit = hit;
      this.given = given;
      this.list = list;
    }
  }

  /** The descent's state ({@link #findTappable}): a view under the finger took the touch, and the list that did. */
  private static final class Probe {
    /**
     * A layer passed over: the voice / video record layer while nothing records. Over every screen, its idle 202dp
     * RecordButton around the mic stays visible and clickable but takes no touch (RecordButton.onTouchEvent).
     */
    final @Nullable View skip;
    boolean taken;
    @Nullable View list;

    Probe (@Nullable View skip) {
      this.skip = skip;
    }
  }

  private static final class Anchor {
    final View view;
    final @Nullable Rect bounds;
    final int placement;
    /**
     * A place taken when the card opened, not a view of its own: on the activity's content view, or a row's place in its
     * list. It means nothing once the window changes its width (the card closes then).
     */
    final boolean fixed;

    Anchor (View view, @Nullable Rect bounds, int placement, boolean fixed) {
      this.view = view;
      this.bounds = bounds;
      this.placement = placement;
      this.fixed = fixed;
    }
  }

  /**
   * The caller's anchor, else the last touch and the view it went to; null without a touch just before, or when its
   * window is gone and it was outside the activity's content (the sheet shows then).
   */
  @Nullable
  private static Target locate (@NonNull ViewController.Options options, @NonNull View content, @Nullable View skipLayer) {
    final View given = options.xgramAnchor;
    if (given != null && given.getWindowToken() != null && given.isShown() && given.getWidth() > 0 && given.getHeight() > 0) {
      final Rect onScreen = XgramPopupBounds.boundsOnScreen(given, new Rect());
      return new Target(onScreen.centerX(), onScreen.centerY(), given, true, null);
    }
    if (!hasRecentTouch()) {
      return null;
    }
    final int x = Math.round(downX), y = Math.round(downY);
    final View root = touchRoot != null ? touchRoot.get() : null;
    // The window the finger was in is gone (a sheet the tap closed): only the touch point is left
    final boolean usable = root != null && root.getWindowToken() != null && root.getWidth() > 0;
    if (!usable) {
      return XgramPopupBounds.boundsOnScreen(content, new Rect()).contains(x, y) ? new Target(x, y, null, false, null) : null;
    }
    final View window = root.getRootView();
    final int maxHeight = Math.max(Screen.dp(56f), (window != null && window.getHeight() > 0 ? window.getHeight() : Screen.currentHeight()) / 3);
    final Probe probe = new Probe(skipLayer);
    final View hit = findTappable(root, root, x, y, maxHeight, new Rect(), probe, 0);
    final View list = hit != null ? Views.findAncestor(hit, RecyclerView.class, false) : probe.list;
    return new Target(x, y, hit, false, list);
  }

  /**
   * Where the card ({@code cardWidth} wide) opens for {@code t}. A view in the activity's own views outside lists is
   * followed while the card shows (the composer moving with the keyboard); a row of a list (or a touch on a list on
   * nothing tappable) is that place in the list, which stays put while its rows move (a new message reorders the chats,
   * a row is rebound) and is in its chat (the in-app emoji panel and the chat pane count); one in a popup (a sheet or a
   * menu the tap closes, the media viewer) becomes a fixed place on the activity's content view, so the card doesn't
   * close with that popup.
   */
  @NonNull
  private static Anchor findAnchor (@NonNull Target t, @NonNull View content, int cardWidth) {
    final Rect contentOnScreen = XgramPopupBounds.boundsOnScreen(content, new Rect());
    final Rect screen = new Rect();
    final View hit = t.hit;
    // A row wider than the card (a chat, a settings row): at the finger along it, as tall as the row
    final boolean alongRow = hit != null && !t.given && hit.getWidth() > cardWidth;
    if (hit != null) {
      XgramPopupBounds.boundsOnScreen(hit, screen);
      if (alongRow) {
        // ...but at least half the card in from the row's ends: centred there, the card stays over its row (in the
        // list's pane with two panes open), not across the divider
        final int half = Screen.dp(1f);
        final int reach = Math.min(cardWidth, screen.width()) / 2;
        final int fx = Math.max(screen.left + reach, Math.min(screen.right - reach, t.x));
        screen.left = fx - half;
        screen.right = fx + half;
      }
    } else {
      final int box = Screen.dp(TOUCH_BOX) / 2;
      screen.set(t.x - box, t.y - box, t.x + box, t.y + box);
    }
    final int placement = screen.centerY() > contentOnScreen.top + contentOnScreen.height() * 3 / 4 ?
      XgramPopupBounds.PLACE_PREFER_ABOVE : // the composer, a bottom bar: above, the keyboard and emoji panel are no room
      XgramPopupBounds.PLACE_BELOW_FIRST;
    if (hit != null && t.list == null && PopupLayout.parentOf(hit) == null) {
      Rect bounds = null;
      if (alongRow) {
        final Rect hitOnScreen = XgramPopupBounds.boundsOnScreen(hit, new Rect());
        bounds = new Rect(screen.left - hitOnScreen.left, 0, screen.right - hitOnScreen.left, hit.getHeight());
      }
      return new Anchor(hit, bounds, placement, false);
    }
    if (t.list != null && PopupLayout.parentOf(t.list) == null) {
      final Rect listOnScreen = XgramPopupBounds.boundsOnScreen(t.list, new Rect());
      final Rect bounds = new Rect(screen); // the row (its strip at the finger) or the square at the touch
      bounds.offset(-listOnScreen.left, -listOnScreen.top);
      return new Anchor(t.list, bounds, placement, true);
    }
    final Rect bounds = new Rect(screen);
    bounds.offset(-contentOnScreen.left, -contentOnScreen.top);
    return new Anchor(content, bounds, placement, true);
  }

  /**
   * The innermost clickable or long-clickable view under the screen point ({@code x}, {@code y}) in {@code view} (below
   * {@code root}), at most {@code maxHeight} tall (a whole page or list is no anchor), as a touch goes: the topmost
   * child under the point first; a child that lets touches through (nothing tappable in it, not clickable itself: the
   * reactions overlay over every screen) passes the point on to the views under it, and {@link Probe#skip} is passed
   * over. A clickable child, or a list, takes the touch: nothing under it is tried ({@code probe}: taken, and the list).
   * {@code tmp} is scratch.
   */
  @Nullable
  private static View findTappable (@NonNull View view, @NonNull View root, int x, int y, int maxHeight, @NonNull Rect tmp, @NonNull Probe probe, int depth) {
    if (depth < 64 && view instanceof ViewGroup) {
      final ViewGroup group = (ViewGroup) view;
      for (int i = group.getChildCount() - 1; i >= 0; i--) {
        final View child = group.getChildAt(i);
        if (child == null || child == probe.skip || child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0f || child.getWidth() <= 0 || child.getHeight() <= 0) {
          continue;
        }
        XgramPopupBounds.boundsOnScreen(child, tmp);
        if (!tmp.contains(x, y)) {
          continue;
        }
        final View found = findTappable(child, root, x, y, maxHeight, tmp, probe, depth + 1);
        if (found == null && !probe.taken && (child instanceof RecyclerView || child instanceof ScrollView || child.isClickable() || child.isLongClickable())) {
          probe.taken = true; // it takes the touch itself (a list on a row that isn't tappable, a view too tall to open at)
        }
        if (found != null || probe.taken) {
          if (found == null && probe.list == null && child instanceof RecyclerView) {
            probe.list = child; // the innermost list the touch went to
          }
          return found;
        }
      }
    }
    return view != root && (view.isClickable() || view.isLongClickable()) && view.getHeight() <= maxHeight ? view : null;
  }

  // The card

  /**
   * Shows {@code options} as a compact card for {@code c}, or returns null (nothing shown) when it should be the sheet:
   * see {@link #fitsPopup}, {@link #locate} and {@link #buildCard}. A tap on a row goes to {@code delegate} (else to the current
   * screen, if it is an OptionDelegate), and closes the card when it returns true, as the sheet does.
   */
  @Nullable
  public static PopupLayout show (@NonNull ViewController<?> c, @NonNull ViewController.Options options, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme) {
    if (!fitsPopup(options, delegate)) {
      return null;
    }
    final BaseActivity activity = c.context();
    if (activity == null || activity.isFinishing()) {
      return null;
    }
    final View content = activity.getContentView();
    if (content == null || content.getWindowToken() == null || content.getWidth() <= 0 || content.getHeight() <= 0) {
      return null;
    }
    int maxWidth = Math.max(Screen.dp(196f), Math.min(Screen.dp(MAX_WIDTH), content.getWidth() - Screen.dp(16f)));
    final PopupLayout popup;
    final MenuMoreWrap card;
    final Anchor anchor;
    try {
      // The voice / video record layer (over every screen) is passed over while nothing records
      final org.thunderdog.challegram.player.RecordAudioVideoController recorder = activity.getRecordAudioVideoController();
      final Target target = locate(options, content, recorder != null && !recorder.isOpen() ? recorder.prepareViews() : null);
      if (target == null) {
        return null;
      }
      final View hit = target.hit;
      if (hit != null && !target.given && target.list != null && hit.getParent() == target.list && hit.getWidth() < maxWidth) {
        // A list's row narrower than the card (a list pane dragged narrow): no wider than the row, as XgramR5Chat.showMenu
        maxWidth = Math.max(Screen.dp(196f), hit.getWidth());
      }
      popup = new PopupLayout(activity);
      card = buildCard(c, activity, popup, options, delegate, forcedTheme, maxWidth);
      if (card == null) {
        return null;
      }
      anchor = findAnchor(target, content, card.getItemsWidth());
    } catch (Throwable t) {
      Log.e("XGram: cannot build a compact menu, showing the sheet", t);
      return null;
    }

    // Over a sheet, the media viewer or the camera (windows of their own) the card is a window too, else it would open
    // under them (also under a sheet this card closes, while it slides away); otherwise it is in the activity's own
    // views, as the ⋮ and R5 menus
    final boolean ownWindow = forcedTheme != null || activity.xgramHasWindowPopup(false);
    popup.setTag(c);
    if (ownWindow) {
      popup.init(true);
      popup.setNeedFullScreen(true);
    } else {
      popup.init(false);
      popup.setNeedRootInsets();
      popup.setOverlayStatusBar(true);
    }
    popup.setDismissOtherPopUps(!options.ignoreOtherPopUps);

    card.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_CENTER);
    card.setRightNumber(0);
    card.xgramSetMayCoverAnchor(true); // a long menu moves over its anchor rather than scroll while the screen has room
    card.xgramSetPopupAnchor(anchor.view, anchor.bounds, anchor.placement);
    if (anchor.fixed) {
      // A place taken at opening means nothing once the window changes its width (folded, unfolded, rotated): the card closes
      popup.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        final int oldWidth = oldRight - oldLeft;
        if (oldWidth > 0 && right - left != oldWidth && !popup.isWindowHidden()) {
          v.post(() -> {
            if (!popup.isWindowHidden()) {
              popup.hideWindow(true);
            }
          });
        }
      });
    }

    // Back closes the card, also with the keyboard open (which stays): on Android 13+ the keyboard's own Back comes
    // first otherwise (XgramMessagePopup.BackAboveKeyboard)
    final XgramMessagePopup.BackAboveKeyboard back = new XgramMessagePopup.BackAboveKeyboard(activity);
    popup.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismissPrepare (PopupLayout p) {
        back.unregister();
      }

      @Override
      public void onPopupDismiss (PopupLayout p) {
        back.unregister();
      }
    });
    popup.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (@NonNull View v) {
        if (!popup.isWindowHidden()) {
          back.register();
        }
      }

      @Override
      public void onViewDetachedFromWindow (@NonNull View v) {
        back.unregister();
      }
    });
    back.register();
    popup.showMoreView(card);
    return popup;
  }

  /** The card's rows, or null when a label would not fit it (the sheet shows it whole). */
  @Nullable
  private static MenuMoreWrap buildCard (@NonNull ViewController<?> c, @NonNull BaseActivity activity, @NonNull PopupLayout popup, @NonNull ViewController.Options options, @Nullable OptionDelegate delegate, @Nullable ThemeDelegate forcedTheme, int maxWidth) {
    final ThemeListenerList listeners = c.getThemeListeners();
    final MenuMoreWrap card = new MenuMoreWrap(activity, /* scrollable */ true);
    card.init(listeners, forcedTheme);
    card.xgramSetMaxItemsWidth(maxWidth);

    // The same rules as the sheet's rows (ViewController.showOptions)
    final View.OnClickListener onClick = v -> {
      if (popup.isWindowHidden()) {
        return; // closing: a second tap is not a second choice
      }
      if (delegate != null) {
        if (delegate.onOptionItemPressed(v, v.getId())) {
          popup.hideWindow(true);
        }
      } else {
        final ViewController<?> current = activity.navigation().getCurrentStackItem();
        if (current instanceof OptionDelegate && ((OptionDelegate) current).onOptionItemPressed(v, v.getId())) {
          popup.hideWindow(true);
        }
      }
    };

    final int rowMaxWidth = maxWidth - 2 * Screen.dp(MenuMoreWrap.PADDING);
    boolean dividerNext = false;
    int rows = 0;
    if (!StringUtils.isEmpty(options.info)) {
      final TextView infoRow = addInfoRow(card, options.info, listeners, forcedTheme, rowMaxWidth);
      if (infoRow == null) {
        return null; // over four lines at the card's width: the sheet shows it whole
      }
      // Its wrapped height in place of the 48dp row the card counts (which side it opens on, its height cap)
      card.xgramSetExtraItemsHeight(infoRow.getMeasuredHeight() - Screen.dp(MenuMoreWrap.ITEM_HEIGHT));
      dividerNext = true;
    }
    for (ViewController.OptionItem item : options.items) {
      if (item == ViewController.OptionItem.SEPARATOR) {
        if (rows > 0) {
          dividerNext = true; // a new group: a divider above its first row
        }
        continue;
      }
      if (XgramR5Sheets.isPlainCancel(item)) {
        continue; // a tap outside or Back cancels
      }
      final TextView row = card.addItem(item.id, item.name, item.icon, null, onClick);
      if (row.getTag() instanceof Integer && (Integer) row.getTag() > rowMaxWidth) {
        return null;
      }
      colorRow(row, item, listeners, forcedTheme);
      if (dividerNext) {
        addDivider(row, listeners, forcedTheme);
      }
      dividerNext = false;
      rows++;
    }
    return rows > 0 ? card : null;
  }

  /**
   * The sheet's colors for a row (OptionsLayout.genOptionView): red is the error color, blue primary, green the
   * positive color, light onSurfaceVariant. Its own theme listeners replace the ones addItem gave it (onSurface), which
   * would have turned it back on a theme change.
   */
  private static void colorRow (@NonNull TextView row, @NonNull ViewController.OptionItem item, @Nullable ThemeListenerList listeners, @Nullable ThemeDelegate forcedTheme) {
    if (item.textColor != ViewController.OptionColor.NORMAL) {
      final int colorId = OptionsLayout.getOptionColorId(item.textColor);
      if (listeners != null) {
        listeners.removeThemeListenerByTarget(row);
      }
      row.setTextColor(XgramR5Sheets.themeColor(forcedTheme, colorId));
      if (listeners != null && forcedTheme == null) {
        listeners.addThemeColorListener(row, colorId);
      }
    }
    if (item.icon != 0 && item.iconColor != ViewController.OptionColor.NORMAL) {
      final int colorId = item.iconColor == ViewController.OptionColor.INACTIVE ? ColorId.controlInactive : OptionsLayout.getOptionColorId(item.iconColor);
      for (Drawable d : row.getCompoundDrawables()) {
        if (d == null) {
          continue;
        }
        if (listeners != null) {
          listeners.removeThemeListenerByTarget(d);
        }
        d.setColorFilter(Paints.getColorFilter(XgramR5Sheets.themeColor(forcedTheme, colorId)));
        if (listeners != null && forcedTheme == null) {
          listeners.addThemeFilterListener(d, colorId);
        }
      }
    }
  }

  /**
   * The menu's description as the card's first row, not tappable, in onSurfaceVariant bodyMedium: a link or a name on
   * one line shortened in the middle, a short sentence on up to four lines (as the message popup's info row). Null
   * when the sentence takes more than four lines at {@code rowMaxWidth} (the card's widest row).
   */
  @Nullable
  private static TextView addInfoRow (@NonNull MenuMoreWrap card, @NonNull CharSequence info, @Nullable ThemeListenerList listeners, @Nullable ThemeDelegate forcedTheme, int rowMaxWidth) {
    final TextView row = card.addItem(View.NO_ID, info, 0, null, null);
    row.setOnClickListener(null);
    row.setClickable(false);
    row.setLongClickable(false);
    row.setFocusable(false);
    row.setBackground(null);
    row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    if (listeners != null) {
      listeners.removeThemeListenerByTarget(row);
    }
    row.setTextColor(XgramR5Sheets.themeColor(forcedTheme, ColorId.textLight));
    if (XgramExpressive.isEnabled()) {
      XgramExpressive.applyText(row, XgramExpressive.TS_BODY_M, false);
    } else {
      row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    }
    final boolean label = isLabel(info);
    if (label) {
      row.setSingleLine(true);
      row.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    } else {
      row.setSingleLine(false);
      row.setMaxLines(4);
      row.setEllipsize(TextUtils.TruncateAt.END);
    }
    final int vertical = Screen.dp(10f);
    row.setPadding(row.getPaddingLeft(), vertical, row.getPaddingRight(), vertical);
    final ViewGroup.LayoutParams params = row.getLayoutParams();
    if (params != null) {
      params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
      row.setLayoutParams(params);
    }
    // Its width as the card measures rows (MenuMoreWrap.getItemsWidth), at most the card's: the measured height is the
    // wrapped one (the card counts it, buildCard)
    row.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, rowMaxWidth), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    row.setTag(row.getMeasuredWidth());
    final Layout layout = row.getLayout();
    if (!label && layout != null) {
      final int lines = layout.getLineCount();
      if (lines > 4 || (lines > 0 && layout.getEllipsisCount(lines - 1) > 0)) {
        return null; // cut: the sheet shows the whole text (no theme listener of its own yet)
      }
    }
    if (listeners != null && forcedTheme == null) {
      listeners.addThemeColorListener(row, ColorId.textLight);
    }
    return row;
  }

  /** The group divider along the top of {@code row} (Android 6+: a view's foreground). */
  private static void addDivider (@NonNull View row, @Nullable ThemeListenerList listeners, @Nullable ThemeDelegate forcedTheme) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return;
    }
    row.setForeground(new GroupDivider(forcedTheme));
    if (listeners != null && forcedTheme == null) {
      listeners.addThemeInvalidateListener(row);
    }
  }

  /**
   * The 1dp divider of a group of rows, 12dp from the card's sides, along the top of the group's first row:
   * outlineVariant with Expressive design (the forced theme's over media), Telegram X's separator otherwise.
   */
  private static final class GroupDivider extends Drawable {
    private final Paint paint = new Paint();
    private final @Nullable ThemeDelegate forcedTheme;

    GroupDivider (@Nullable ThemeDelegate forcedTheme) {
      this.forcedTheme = forcedTheme;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      final Rect b = getBounds();
      paint.setColor(XgramExpressive.isEnabled() ?
        XgramR6B3Shell.color(XgramExpressive.OUTLINE_VARIANT, forcedTheme) :
        XgramR5Sheets.themeColor(forcedTheme, ColorId.separator));
      final int inset = Screen.dp(12f);
      c.drawRect(b.left + inset, b.top, b.right - inset, b.top + Math.max(1, Screen.dp(1f)), paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable android.graphics.ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
