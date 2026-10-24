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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramSnackbar;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TranslationsManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.TranslationControllerV2;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.lang.ref.WeakReference;
import java.text.BreakIterator;
import java.util.Arrays;

import me.vkryl.core.collection.IntList;
import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Select text: part of a message's text or caption selected right in its bubble, without leaving the chat. It starts
 * from the message menu (Select text: the word where the menu was opened, or the whole text), or with Long press on
 * text selects it (Settings → XGram → Messages) from a long press on the text that then moves. The text itself draws
 * the selection behind its glyphs, with the quote highlight's geometry (Text.setXgramSelection); the two handles and
 * the toolbar (Copy, Quote, Translate, Share, Select all) float over the chat in a layer above the list
 * ({@link XgramTextSelectionLayer}). Dragging a handle (they swap when they cross) or the long pressed finger moves
 * an end, with the system magnifier (Android 9+), and scrolls the chat near its top or bottom.
 *
 * One selection at a time. It stays while the chat scrolls and its message is in the list (the toolbar steps aside
 * while the list moves), and ends with a tap outside it, Back, another message's menu, selecting messages, leaving the
 * chat, or when its message is edited, deleted or leaves the list. A tap on the selection shows or hides the toolbar,
 * a double tap selects the word there, a long press selects the word there and drags from it; links and the message
 * menu wait until the selection ends. TalkBack keeps the sheet with a selectable text (XgramQuote). Not offered where
 * the chat protects its content: none of the actions would be allowed there.
 */
public final class XgramTextSelection {
  /** The message menu's Select text, and the toolbar's Select all. */
  public static final int ICON = R.drawable.baseline_select_all_24;

  /**
   * What {@link #onMessageTouch} did with a touch: not the selection's; taken; the long pressed finger moved (it
   * selects now: what the message caught lets go); lifted without moving (the long press does what it did before).
   */
  public static final int TOUCH_NONE = 0, TOUCH_HANDLED = 1, TOUCH_SELECTING = 2, TOUCH_STILL = 3;

  static final int HANDLE_NONE = 0, HANDLE_START = 1, HANDLE_END = 2;
  private static final int DRAG_NONE = 0, DRAG_START = HANDLE_START, DRAG_END = HANDLE_END, DRAG_PRESS = 3;

  /** The toolbar's actions. */
  static final int ACTION_COPY = 1, ACTION_QUOTE = 1 << 1, ACTION_TRANSLATE = 1 << 2, ACTION_SHARE = 1 << 3, ACTION_SELECT_ALL = 1 << 4, ACTION_FORWARD = 1 << 5;

  /** Expressive: the selection in primary at this alpha (readable under the glyphs on both bubbles). */
  private static final float EXPRESSIVE_ALPHA = .35f;
  /** Near the list's top or bottom (dp) a drag scrolls the chat, up to this much a frame (dp). */
  private static final float AUTO_SCROLL_ZONE = 56f, AUTO_SCROLL_MAX = 16f;
  /** The toolbar's distance from the selection and from the list's edges (dp). */
  private static final float TOOLBAR_GAP = 8f, TOOLBAR_EDGE = 8f;

  private static @Nullable XgramTextSelection current;

  // Where the message menu was opened (MessageView): Select text starts at the word there. Held weakly: every menu
  // sets it and only Select text takes it, so a chat left after another option must not stay reachable from here

  private static @Nullable WeakReference<TGMessage> menuMessage;
  private static float menuX, menuY;

  // The touch on the message with the selection, from its touch down to its end (onMessageTouch)

  private static @Nullable XgramTextSelection touchOwner;
  private static @Nullable MessageView touchOwnerView;

  // Menu

  /** Whether {@code msg} offers Select text: text or a caption in its bubble, in a chat that lets it be copied. */
  static boolean canSelect (@Nullable MessagesController m, TGMessage msg) {
    if (m == null || msg.isSponsoredMessage() || msg.isEventLog() || !msg.canBeSaved()) {
      // Protected content: no Copy, Share or Quote, and the translation sheet would let the text out
      return false;
    }
    final TdApi.Message message = XgramQuote.textMessage(msg);
    return message != null && msg.xgramSelectableText(message.id) != null;
  }

  /** Adds Select text (after Copy). */
  public static void fill (MessagesController m, TGMessage msg, IntList ids, IntList icons, StringList strings) {
    if (canSelect(m, msg)) {
      ids.append(R.id.btn_xgramSelectText);
      strings.append(R.string.XgramSelText);
      icons.append(ICON);
    }
  }

  /** The message menu opens for {@code msg}, tapped at ({@code x}, {@code y}) in its view. */
  public static void setMenuPoint (TGMessage msg, float x, float y) {
    menuMessage = new WeakReference<>(msg);
    menuX = x;
    menuY = y;
  }

  /** The menu's Select text: the word where the menu was opened, else the whole text the menu's Copy takes. */
  public static void startFromMenu (MessagesController m, TGMessage msg) {
    final boolean hasPoint = menuMessage != null && menuMessage.get() == msg;
    menuMessage = null;
    dismiss();
    if (m.isDestroyed() || msg.isDestroyed() || !canSelect(m, msg)) {
      return;
    }
    if (isTouchExploration(m.context())) {
      XgramQuote.open(m, msg); // TalkBack: the text in a sheet, where TalkBack selects it by itself
      return;
    }
    final View view = msg.findCurrentView();
    if (!(view instanceof MessageView) || view.getWindowToken() == null) {
      return;
    }
    long messageId = hasPoint ? msg.xgramSelectableTextAt(menuX, menuY) : 0;
    TextWrapper wrapper = messageId != 0 ? msg.xgramSelectableText(messageId) : null;
    BreakIterator words = null;
    int[] range = null;
    if (wrapper != null) {
      final int offset = wrapper.getXgramOffsetAt(menuX, menuY, true);
      if (offset != -1) {
        words = newWords(wrapper.getText());
        range = wordAt(words, wrapper.getText(), offset);
      }
    }
    if (range == null) {
      final TdApi.Message message = XgramQuote.textMessage(msg);
      messageId = message != null ? message.id : 0;
      wrapper = messageId != 0 ? msg.xgramSelectableText(messageId) : null;
      if (wrapper == null || wrapper.getText().isEmpty()) {
        return;
      }
      words = newWords(wrapper.getText());
      range = new int[] {0, wrapper.getText().length()};
    }
    start(m, msg, (MessageView) view, messageId, wrapper, words, range[0], range[1]);
  }

  /**
   * Long press on text selects it: a long press on {@code msg}'s text at ({@code x}, {@code y}) selects the word there
   * and waits for the finger to move ({@link #onMessageTouch}). False when the setting is off or the press is not on
   * text that can be selected: the long press goes as before.
   */
  public static boolean startPress (MessageView view, TGMessage msg, float x, float y) {
    if (msg == null || !XgramSettings.instance().textSelectDrag()) {
      return false;
    }
    final MessagesController m = msg.messagesController();
    if (m == null || m.inSelectMode() || isTouchExploration(view.getContext()) || !canSelect(m, msg)) {
      return false;
    }
    final long messageId = msg.xgramSelectableTextAt(x, y);
    final TextWrapper wrapper = messageId != 0 ? msg.xgramSelectableText(messageId) : null;
    final int offset = wrapper != null ? wrapper.getXgramOffsetAt(x, y, true) : -1;
    if (offset == -1) {
      return false;
    }
    final BreakIterator words = newWords(wrapper.getText());
    final int[] word = wordAt(words, wrapper.getText(), offset);
    if (word == null) {
      return false;
    }
    dismiss();
    final XgramTextSelection selection = start(m, msg, view, messageId, wrapper, words, word[0], word[1]);
    if (selection == null) {
      return false;
    }
    selection.beginPress(view, x, y, true);
    touchOwner = selection;
    touchOwnerView = view;
    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    return true;
  }

  /**
   * Every touch of a message's view, before the view's own handling: the message with the selection takes its touches
   * (a tap, a double tap, a long press), and the long press of {@link #startPress} goes on here. See TOUCH_*.
   */
  public static int onMessageTouch (MessageView view, TGMessage msg, MotionEvent e) {
    final int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      touchOwner = null;
      touchOwnerView = null;
      final XgramTextSelection selection = current;
      if (selection == null || selection.ended || selection.msg != msg) {
        return TOUCH_NONE;
      }
      touchOwner = selection;
      touchOwnerView = view;
      selection.onOwnTouch(view, e);
      return TOUCH_HANDLED;
    }
    final XgramTextSelection selection = touchOwner;
    if (selection == null || touchOwnerView != view) {
      return TOUCH_NONE;
    }
    final int result;
    if (selection.drag == DRAG_PRESS && selection.pressView == view) {
      result = selection.onPressTouch(e);
    } else {
      selection.onOwnTouch(view, e);
      result = TOUCH_HANDLED;
    }
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      touchOwner = null;
      touchOwnerView = null;
    }
    return result;
  }

  // The selection's end, from the outside

  /** Ends the selection, wherever it is. */
  public static void dismiss () {
    final XgramTextSelection selection = current;
    if (selection != null) {
      selection.finish();
    }
  }

  /** Ends the selection in {@code m}'s chat. */
  public static void dismiss (MessagesController m) {
    final XgramTextSelection selection = current;
    if (selection != null && selection.controller == m) {
      selection.finish();
    }
  }

  /** Back in {@code m}'s chat: ends its selection ({@code commit}), or says it would. */
  public static boolean onBackPressed (MessagesController m, boolean commit) {
    final XgramTextSelection selection = current;
    if (selection == null || selection.ended || selection.controller != m) {
      return false;
    }
    if (commit) {
      selection.finish();
    }
    return true;
  }

  /** A finger moves the selection (a handle, or a long press on text): the list's own gestures (pull to the next chat) wait. */
  static boolean isDragging () {
    final XgramTextSelection selection = current;
    return selection != null && !selection.ended && selection.drag != DRAG_NONE;
  }

  /** {@code msg} is destroyed (deleted, or merged into an album): its selection ends. Any thread. */
  public static void onDestroyed (TGMessage msg) {
    final XgramTextSelection selection = current;
    if (selection != null && selection.msg == msg) {
      UI.post(selection.endLater);
    }
  }

  /** {@code msg} is about to be drawn in {@code view}: the selection follows its view, a new layout and the theme. */
  public static void beforeDraw (TGMessage msg, MessageView view) {
    final XgramTextSelection selection = current;
    if (selection != null && selection.msg == msg && !selection.ended) {
      selection.onBeforeDraw(view);
    }
  }

  /** {@code msg} was drawn: the handles move when the text is drawn elsewhere in its view than before. */
  public static void afterDraw (TGMessage msg) {
    final XgramTextSelection selection = current;
    if (selection != null && selection.msg == msg && !selection.ended) {
      selection.onAfterDraw();
    }
  }

  // Per selection

  private final MessagesController controller;
  private final TGMessage msg;
  /** The message whose text or caption is selected (an album item's caption, or the message itself). */
  private final long messageId;
  private final RecyclerView list;
  private final XgramTextSelectionLayer layer;
  private final BreakIterator words;
  private final String text;
  private @NonNull MessageView view;
  private @NonNull TextWrapper wrapper;
  private int start = -1, end = -1;
  private int color;
  private boolean ended;
  private boolean canQuote, canTranslate;

  // The selection's ends and bounds in the view (Text.getXgramSelectionEnds), read before each frame
  private final float[] ends = new float[Text.XGRAM_ENDS_SIZE], drawnEnds = new float[Text.XGRAM_ENDS_SIZE];
  private boolean hasEnds;
  private final int[] location = new int[2];
  private final Rect textRect = new Rect();

  // A drag: a handle, or the long pressed finger (the word it pressed is where the selection grows from)
  private int drag = DRAG_NONE;
  private @Nullable MessageView pressView;
  private boolean pressPending, pressFromSetting;
  private int anchorStart, anchorEnd;
  /** On the screen: where the long press was, and the finger now. */
  private float pressDownX, pressDownY, fingerX, fingerY;
  /** From the finger to the point that picks the offset: a handle's text, which the finger never covers. */
  private float dragOffsetX, dragOffsetY;
  private boolean autoScrollPosted;
  /** android.widget.Magnifier (Android 9+) while dragging, over {@link #magnifierView}. */
  private @Nullable Object magnifier;
  private @Nullable View magnifierView;

  // The toolbar: out of the way while the list moves, or after a tap on the selection

  private boolean listMoving, toolbarOff;

  // Touches on the message itself

  private @Nullable MessageView ownView;
  private float ownDownX, ownDownY;
  private boolean ownTap;
  /** The last tap's lift (event time) and point; whether it ends the selection (endLater) unless a second tap comes. */
  private long lastTapTime;
  private float lastTapX, lastTapY;
  private boolean lastTapEnds;
  /** This touch came down soon enough after the last tap and near it: its lift is a double tap, as Android times one. */
  private boolean secondTap;

  // Taps elsewhere in the list

  private boolean listTap;
  private float listDownX, listDownY;
  private long listDownTime;

  private final Runnable endLater = this::finish;
  private final Runnable ownLongPress = this::onOwnLongPress;

  private XgramTextSelection (MessagesController controller, TGMessage msg, MessageView view, long messageId, TextWrapper wrapper, BreakIterator words, RecyclerView list) {
    this.controller = controller;
    this.msg = msg;
    this.view = view;
    this.messageId = messageId;
    this.wrapper = wrapper;
    this.words = words;
    this.text = wrapper.getText();
    this.list = list;
    this.color = selectionColor();
    this.layer = new XgramTextSelectionLayer(view.getContext(), this);
    checkActions();
  }

  private static @Nullable XgramTextSelection start (MessagesController m, TGMessage msg, MessageView view, long messageId, TextWrapper wrapper, BreakIterator words, int start, int end) {
    final RecyclerView list = m.getMessagesView();
    if (list == null || !(list.getParent() instanceof ViewGroup) || list.getWindowToken() == null) {
      return null;
    }
    final XgramTextSelection selection = new XgramTextSelection(m, msg, view, messageId, wrapper, words, list);
    current = selection;
    // Over everything in the chat: it draws only inside the list and takes only the touches of its handles and toolbar
    ((ViewGroup) list.getParent()).addView(selection.layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    list.addOnItemTouchListener(selection.listTouch);
    list.addOnScrollListener(selection.listScroll);
    selection.setRange(start, end, false);
    notifyBack(m);
    return selection;
  }

  private void finish () {
    if (ended) {
      return;
    }
    ended = true;
    if (current == this) {
      current = null;
    }
    drag = DRAG_NONE;
    pressView = null;
    UI.cancel(endLater);
    if (ownView != null) {
      ownView.removeCallbacks(ownLongPress);
    }
    list.removeCallbacks(autoScroll);
    autoScrollPosted = false;
    dismissMagnifier();
    wrapper.setXgramSelection(-1, -1, 0);
    msg.invalidate();
    // Out of sight at once; taken out of the chat's views after the touch or the frame that ended the selection
    layer.setVisibility(View.GONE);
    UI.post(() -> {
      list.removeOnItemTouchListener(listTouch);
      list.removeOnScrollListener(listScroll);
      final ViewParent parent = layer.getParent();
      if (parent instanceof ViewGroup) {
        ((ViewGroup) parent).removeView(layer);
      }
    });
    notifyBack(controller);
  }

  /** Back goes to the selection while it shows, also on Android 13+ where the system asks before a back gesture. */
  private static void notifyBack (MessagesController m) {
    final BaseActivity activity = m.context();
    if (activity != null) {
      activity.notifyBackPressAvailabilityChanged();
    }
  }

  private void checkActions () {
    final TdApi.Message message = msg.getMessage(messageId);
    // Quote: a reply quoting exactly the selection, as XgramQuote sends it; never in secret chats (TDLib takes no
    // quotes there), nor of a translation or an edit on its way (the quote must be in the message's own text)
    canQuote = message != null && originalText() != null && !ChatId.isSecret(msg.getChatId()) && !msg.xgramIsDeleted() &&
      !msg.isNotSent() && msg.canReplyTo() && controller.canWriteMessagesOrWaitingForReply() && !msg.tdlib().messageBeingEdited(message);
    // Translate: XGram's translation sheet, as the menu's Translate (never in secret chats)
    canTranslate = XgramTranslate.canOffer(msg, true) && !msg.xgramSelectableTextTranslated(messageId);
  }

  // Range

  private void setRange (int start, int end, boolean tick) {
    final int length = text.length();
    start = Math.max(0, Math.min(length, start));
    end = Math.max(0, Math.min(length, end));
    // Never between the two halves of a character
    if (start > 0 && start < length && Character.isLowSurrogate(text.charAt(start)) && Character.isHighSurrogate(text.charAt(start - 1))) {
      start--;
    }
    if (end > 0 && end < length && Character.isLowSurrogate(text.charAt(end)) && Character.isHighSurrogate(text.charAt(end - 1))) {
      end++;
    }
    if (start >= end || (start == this.start && end == this.end)) {
      return;
    }
    this.start = start;
    this.end = end;
    wrapper.setXgramSelection(start, end, color);
    msg.invalidate();
    layer.invalidate();
    if (tick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
    }
  }

  private static int selectionColor () {
    // Expressive: primary at .35; Telegram X: its own text selection colour
    return XgramExpressive.isEnabled() ? XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), EXPRESSIVE_ALPHA) : Theme.getColor(ColorId.textSelectionHighlight);
  }

  private static int handleColor () {
    return XgramExpressive.isEnabled() ? XgramExpressive.color(XgramExpressive.PRIMARY) : Theme.getColor(ColorId.inputActive);
  }

  // The message, drawn

  private void onBeforeDraw (MessageView view) {
    this.view = view;
    final TextWrapper shown = msg.xgramSelectableText(messageId);
    boolean changed = false;
    if (shown != wrapper) {
      wrapper.setXgramSelection(-1, -1, 0);
      if (shown == null || !text.equals(shown.getText())) {
        // Edited, translated or gone: the selection ends with the text it selected
        UI.post(endLater);
        return;
      }
      // Laid out anew with the same text (a search highlight): the selection moves over
      wrapper = shown;
      changed = true;
      checkActions();
    }
    final int color = selectionColor();
    if (changed || color != this.color) {
      this.color = color;
      wrapper.setXgramSelection(start, end, color);
    }
  }

  private void onAfterDraw () {
    final boolean has = wrapper.getXgramSelectionEnds(drawnEnds);
    if (has != hasEnds || (has && !Arrays.equals(drawnEnds, ends))) {
      layer.invalidate(); // drawn elsewhere than the layer read before this frame: the next one moves the handles
    }
  }

  /** The view showing the message now; false when it has none in the list (scrolled away, deleted). */
  private boolean checkView () {
    if (msg.isDestroyed()) {
      return false;
    }
    if (view.getMessage() == msg && view.getWindowToken() != null && view.getParent() != null) {
      return true;
    }
    final View found = msg.findCurrentView();
    if (found instanceof MessageView && ((MessageView) found).getMessage() == msg && found.getWindowToken() != null && found.getParent() != null) {
      view = (MessageView) found;
      return true;
    }
    return false;
  }

  // The layer: handles and toolbar, placed before every frame

  void onLayerPreDraw () {
    if (ended) {
      return;
    }
    if (!checkView()) {
      UI.post(endLater);
      return;
    }
    layer.getLocationInWindow(location);
    final int layerX = location[0], layerY = location[1];
    view.getLocationInWindow(location);
    final float viewX = location[0] - layerX, viewY = location[1] - layerY;
    list.getLocationInWindow(location);
    // What of the list is in sight: under the pinned message and the other bars, above the message box
    final float left = location[0] - layerX, right = left + list.getWidth();
    final float top = location[1] - layerY + list.getPaddingTop() + controller.getTopOffset();
    final float bottom = location[1] - layerY + list.getHeight() - list.getPaddingBottom();

    hasEnds = wrapper.getXgramSelectionEnds(ends);
    // No handles while the long pressed finger selects (Android's own text has none then either)
    final boolean handles = hasEnds && drag != DRAG_PRESS;
    final float startX = viewX + ends[0], startY = viewY + ends[2];
    final float endX = viewX + ends[4], endY = viewY + ends[6];
    final boolean startShown = handles && viewY + ends[1] < bottom && startY > top;
    final boolean endShown = handles && viewY + ends[5] < bottom && endY > top;
    // The start of left-to-right text hangs its drop to the left, its end to the right; right-to-left the other way
    layer.setHandles(startShown, startX, startY, ends[3] == 0f, endShown, endX, endY, ends[7] != 0f, handleColor(), left, top, right, bottom);

    // The toolbar: above the selection, else below it and its handles, else at the top of the list over the text
    boolean toolbar = hasEnds && drag == DRAG_NONE && !listMoving && !toolbarOff;
    final float selectionTop = viewY + ends[9], selectionBottom = viewY + ends[11];
    if (selectionBottom <= top || selectionTop >= bottom) {
      toolbar = false; // out of sight
    }
    if (toolbar) {
      final float edge = Screen.dp(TOOLBAR_EDGE), gap = Screen.dp(TOOLBAR_GAP);
      layer.setToolbarActions(toolbarActions(), Math.round(right - left - edge * 2f));
      final int width = layer.getToolbarWidth(), height = layer.getToolbarHeight();
      float y = selectionTop - gap - height;
      boolean above = true;
      if (y < top + edge) {
        float below = selectionBottom;
        if (startShown) {
          below = Math.max(below, startY + layer.getHandleHeight());
        }
        if (endShown) {
          below = Math.max(below, endY + layer.getHandleHeight());
        }
        y = below + gap;
        above = false;
        if (y + height > bottom - edge) {
          y = top + edge;
          above = true;
        }
      }
      final float centerX = viewX + (ends[8] + ends[10]) / 2f;
      final float x = Math.max(left + edge, Math.min(right - edge - width, centerX - width / 2f));
      layer.placeToolbar(x, y, above);
    }
    layer.setToolbarShown(toolbar);
  }

  private int toolbarActions () {
    int actions = ACTION_COPY | ACTION_FORWARD | ACTION_SHARE;
    if (canQuote) {
      actions |= ACTION_QUOTE;
    }
    if (canTranslate) {
      actions |= ACTION_TRANSLATE;
    }
    if (start > 0 || end < text.length()) {
      actions |= ACTION_SELECT_ALL;
    }
    return actions;
  }

  /** A touch down on the layer that is neither a handle nor the toolbar: outside the list (the bars, the message box) it ends the selection. */
  void onLayerOutsideDown (boolean inList) {
    if (!inList && !ended) {
      UI.post(endLater);
    }
  }

  /** The layer's touch on a handle: taken at its touch down (false when that is not on a handle), then dragged. */
  boolean onLayerTouch (MotionEvent e) {
    if (ended) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        final int handle = drag == DRAG_NONE && hasEnds ? layer.handleAt(e.getX(), e.getY()) : HANDLE_NONE;
        if (handle == HANDLE_NONE) {
          return false;
        }
        drag = handle;
        fingerX = e.getRawX();
        fingerY = e.getRawY();
        // The offset follows the text the handle stands for (its line, where the offset is), as far from the finger
        // as the finger took it, so the finger never covers the text it moves through
        view.getLocationOnScreen(location);
        final boolean atStart = handle == HANDLE_START;
        dragOffsetX = location[0] + (atStart ? ends[0] : ends[4]) - fingerX;
        dragOffsetY = location[1] + (atStart ? (ends[1] + ends[2]) / 2f : (ends[5] + ends[6]) / 2f) - fingerY;
        final ViewParent parent = layer.getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(true);
        }
        layer.invalidate();
        showMagnifier();
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (drag == DRAG_START || drag == DRAG_END) {
          fingerX = e.getRawX();
          fingerY = e.getRawY();
          applyDrag();
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (drag == DRAG_START || drag == DRAG_END) {
          endDrag();
        }
        return true;
      }
    }
    return true;
  }

  // Touches on the message with the selection

  private void onOwnTouch (MessageView view, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        ownView = view;
        ownDownX = e.getX();
        ownDownY = e.getY();
        ownTap = true;
        // A double tap is timed from the first lift to this touch down: the first tap's end waits for this touch
        final float slop = ViewConfiguration.get(view.getContext()).getScaledDoubleTapSlop();
        secondTap = lastTapTime != 0 && e.getEventTime() - lastTapTime <= ViewConfiguration.getDoubleTapTimeout() &&
          Math.abs(ownDownX - lastTapX) <= slop && Math.abs(ownDownY - lastTapY) <= slop;
        if (secondTap) {
          UI.cancel(endLater);
        }
        view.removeCallbacks(ownLongPress);
        view.postDelayed(ownLongPress, ViewConfiguration.getLongPressTimeout());
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        // Moving further is the list's scroll (it takes the touch then): the selection stays, unless a tap before ends it
        if (ownTap && Math.max(Math.abs(e.getX() - ownDownX), Math.abs(e.getY() - ownDownY)) > Screen.getTouchSlop()) {
          ownTap = false;
          view.removeCallbacks(ownLongPress);
          onSecondTapLost();
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        view.removeCallbacks(ownLongPress);
        if (ownTap) {
          ownTap = false;
          onOwnTap(view, e.getX(), e.getY(), e.getEventTime());
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        view.removeCallbacks(ownLongPress);
        ownTap = false;
        onSecondTapLost();
        break;
      }
    }
  }

  private void onOwnTap (MessageView view, float x, float y, long upTime) {
    final boolean doubleTap = secondTap;
    secondTap = false;
    if (ended) {
      return;
    }
    final long atId = msg.xgramSelectableTextAt(x, y);
    final TextWrapper at = atId != 0 ? msg.xgramSelectableText(atId) : null;
    final int offset = at != null ? at.getXgramOffsetAt(x, y, true) : -1;
    if (offset != -1 && doubleTap) {
      // A double tap: the word there
      lastTapTime = 0;
      lastTapEnds = false;
      UI.cancel(endLater);
      selectWordAt(view, atId, at, offset, false, x, y);
      return;
    }
    lastTapTime = upTime;
    lastTapX = x;
    lastTapY = y;
    lastTapEnds = false;
    if (offset != -1 && atId == messageId && at == wrapper && offset >= start && offset <= end) {
      // On the selection: the toolbar comes and goes
      toolbarOff = !toolbarOff;
      layer.invalidate();
    } else if (offset != -1) {
      lastTapEnds = true;
      UI.post(endLater, ViewConfiguration.getDoubleTapTimeout()); // unless a second tap comes down there in time
    } else {
      finish();
    }
  }

  /** The touch after a tap came down as a second tap but became a scroll or a long press off the text: the tap's end goes on. */
  private void onSecondTapLost () {
    if (secondTap) {
      secondTap = false;
      if (lastTapEnds && !ended) {
        UI.post(endLater);
      }
    }
  }

  private void onOwnLongPress () {
    final MessageView view = ownView;
    if (ended || !ownTap || view == null || touchOwner != this) {
      return;
    }
    ownTap = false;
    final float x = ownDownX, y = ownDownY;
    final long atId = msg.xgramSelectableTextAt(x, y);
    final TextWrapper at = atId != 0 ? msg.xgramSelectableText(atId) : null;
    final int offset = at != null ? at.getXgramOffsetAt(x, y, true) : -1;
    if (offset == -1) {
      onSecondTapLost();
      return;
    }
    secondTap = false;
    lastTapEnds = false;
    UI.cancel(endLater);
    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    selectWordAt(view, atId, at, offset, true, x, y);
  }

  /** The word at {@code offset} of {@code at}; {@code press}: the finger then drags from it. */
  private void selectWordAt (MessageView view, long atId, TextWrapper at, int offset, boolean press, float x, float y) {
    if (atId == messageId && at == wrapper) {
      final int[] word = wordAt(words, text, offset);
      if (word == null) {
        return;
      }
      setRange(word[0], word[1], false);
      toolbarOff = false;
      if (press) {
        beginPress(view, x, y, false);
      }
      layer.invalidate();
      return;
    }
    // Another caption of the album: the selection moves there
    final BreakIterator otherWords = newWords(at.getText());
    final int[] word = wordAt(otherWords, at.getText(), offset);
    if (word == null) {
      return;
    }
    finish();
    final XgramTextSelection selection = start(controller, msg, view, atId, at, otherWords, word[0], word[1]);
    if (selection != null && press) {
      selection.beginPress(view, x, y, false);
      touchOwner = selection;
      touchOwnerView = view;
    }
  }

  // The long pressed finger

  private void beginPress (MessageView view, float x, float y, boolean fromSetting) {
    drag = DRAG_PRESS;
    pressView = view;
    pressFromSetting = fromSetting;
    // Long press on text selects it: held still, the long press does what it did before, so nothing is kept until
    // the finger moves; a long press on the selected message keeps its word
    pressPending = fromSetting;
    anchorStart = start;
    anchorEnd = end;
    view.getLocationOnScreen(location);
    pressDownX = fingerX = location[0] + x;
    pressDownY = fingerY = location[1] + y;
    dragOffsetX = dragOffsetY = 0f;
    final ViewParent parent = view.getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(true); // no scroll, no swipe to reply
    }
    layer.invalidate();
  }

  private int onPressTouch (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE: {
        fingerX = e.getRawX();
        fingerY = e.getRawY();
        int result = TOUCH_HANDLED;
        if (pressPending) {
          if (Math.max(Math.abs(fingerX - pressDownX), Math.abs(fingerY - pressDownY)) <= Screen.getTouchSlop()) {
            return TOUCH_HANDLED;
          }
          pressPending = false;
          result = pressFromSetting ? TOUCH_SELECTING : TOUCH_HANDLED;
        }
        applyDrag();
        return result;
      }
      case MotionEvent.ACTION_UP: {
        if (pressPending && pressFromSetting) {
          finish();
          return TOUCH_STILL;
        }
        endDrag();
        return TOUCH_HANDLED;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (pressPending && pressFromSetting) {
          finish();
          return TOUCH_NONE; // the message's view cancels what it caught, as before
        }
        endDrag();
        return TOUCH_HANDLED;
      }
    }
    return TOUCH_HANDLED;
  }

  // Dragging

  private void applyDrag () {
    if (ended || drag == DRAG_NONE || (drag == DRAG_PRESS && pressPending)) {
      return;
    }
    final int offset = offsetAtScreen(fingerX + dragOffsetX, fingerY + dragOffsetY);
    if (offset != -1) {
      switch (drag) {
        case DRAG_PRESS: {
          // From the pressed word, a word at a time, as Android's own text grows
          if (offset < anchorStart) {
            setRange(wordStart(offset), anchorEnd, true);
          } else if (offset > anchorEnd) {
            setRange(anchorStart, wordEnd(offset), true);
          } else {
            setRange(anchorStart, anchorEnd, true);
          }
          break;
        }
        case DRAG_START: {
          if (offset < end) {
            setRange(offset, end, true);
          } else if (offset > end) {
            drag = DRAG_END; // past the other end: the handles swap
            setRange(end, offset, true);
          }
          break;
        }
        case DRAG_END: {
          if (offset > start) {
            setRange(start, offset, true);
          } else if (offset < start) {
            drag = DRAG_START;
            setRange(offset, start, true);
          }
          break;
        }
      }
    }
    showMagnifier();
    updateAutoScroll();
  }

  private void endDrag () {
    drag = DRAG_NONE;
    pressView = null;
    pressPending = false;
    list.removeCallbacks(autoScroll);
    autoScrollPosted = false;
    dismissMagnifier();
    toolbarOff = false;
    layer.invalidate();
  }

  private int offsetAtScreen (float x, float y) {
    view.getLocationOnScreen(location);
    return wrapper.getXgramOffsetAt(x - location[0], y - location[1], false);
  }

  private int wordStart (int offset) {
    final int[] segment = segmentAt(words, text.length(), Math.min(offset, text.length() - 1));
    return segment != null ? segment[0] : offset;
  }

  private int wordEnd (int offset) {
    final int[] segment = segmentAt(words, text.length(), Math.max(0, offset - 1));
    return segment != null ? segment[1] : offset;
  }

  // The chat scrolls while the finger is near its top or bottom and the text goes on past it

  private final Runnable autoScroll = this::onAutoScroll;

  private void onAutoScroll () {
    autoScrollPosted = false;
    if (ended || drag == DRAG_NONE) {
      return;
    }
    final int dy = autoScrollDelta();
    if (dy != 0) {
      list.scrollBy(0, dy);
      applyDrag(); // the text moved under the finger
      updateAutoScroll();
    }
  }

  private void updateAutoScroll () {
    if (!autoScrollPosted && !ended && drag != DRAG_NONE && autoScrollDelta() != 0) {
      autoScrollPosted = true;
      list.postOnAnimation(autoScroll);
    }
  }

  private int autoScrollDelta () {
    final Text layout = wrapper.getCurrent();
    if (layout == null || (drag == DRAG_PRESS && pressPending)) {
      return 0;
    }
    list.getLocationOnScreen(location);
    final int listY = location[1];
    final float finger = fingerY - listY;
    final int top = list.getPaddingTop() + controller.getTopOffset();
    final int bottom = list.getHeight() - list.getPaddingBottom();
    final float zone = Screen.dp(AUTO_SCROLL_ZONE);
    layout.toRect(textRect);
    view.getLocationOnScreen(location);
    final float textTop = location[1] - listY + textRect.top, textBottom = location[1] - listY + textRect.bottom;
    final float depth;
    final int direction;
    if (finger < top + zone && textTop < top && list.canScrollVertically(-1)) {
      depth = (top + zone - finger) / zone;
      direction = -1;
    } else if (finger > bottom - zone && textBottom > bottom && list.canScrollVertically(1)) {
      depth = (finger - bottom + zone) / zone;
      direction = 1;
    } else {
      return 0;
    }
    final float f = Math.max(0f, Math.min(1f, depth));
    return direction * Math.max(1, Math.round(f * f * Screen.dp(AUTO_SCROLL_MAX)));
  }

  // The magnifier over the end that moves (Android 9+)

  @SuppressWarnings("deprecation")
  private void showMagnifier () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      if (ended || drag == DRAG_NONE || (drag == DRAG_PRESS && pressPending)) {
        return;
      }
      hasEnds = wrapper.getXgramSelectionEnds(ends);
      if (!hasEnds) {
        return;
      }
      android.widget.Magnifier magnifier = this.magnifier instanceof android.widget.Magnifier && magnifierView == view ? (android.widget.Magnifier) this.magnifier : null;
      if (magnifier == null) {
        dismissMagnifier();
        magnifier = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new android.widget.Magnifier.Builder(view).build() : new android.widget.Magnifier(view);
        this.magnifier = magnifier;
        this.magnifierView = view;
      }
      // The line of the end that moves, at the finger
      final boolean atStart = drag == DRAG_START || (drag == DRAG_PRESS && start < anchorStart);
      final float lineY = atStart ? (ends[1] + ends[2]) / 2f : (ends[5] + ends[6]) / 2f;
      view.getLocationOnScreen(location);
      final float x = Math.max(0f, Math.min(view.getWidth(), fingerX + dragOffsetX - location[0]));
      magnifier.show(x, lineY);
    }
  }

  private void dismissMagnifier () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && magnifier instanceof android.widget.Magnifier) {
      ((android.widget.Magnifier) magnifier).dismiss();
    }
    magnifier = null;
    magnifierView = null;
  }

  // Taps elsewhere in the list, and its scroll

  private final RecyclerView.OnItemTouchListener listTouch = new RecyclerView.SimpleOnItemTouchListener() {
    @Override
    public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
      onListTouch(rv, e);
      return false; // only watched: the list and its messages take the touch as always
    }
  };

  private final RecyclerView.OnScrollListener listScroll = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
      listMoving = newState != RecyclerView.SCROLL_STATE_IDLE;
      layer.invalidate();
    }

    @Override
    public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
      layer.invalidate(); // the handles and the toolbar follow (onLayerPreDraw)
    }
  };

  private void onListTouch (RecyclerView rv, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        // The message with the selection takes its own touches (onMessageTouch)
        final View child = rv.findChildViewUnder(e.getX(), e.getY());
        listTap = child == null || (child != view && view.getParent() != child);
        listDownX = e.getX();
        listDownY = e.getY();
        listDownTime = e.getEventTime();
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listTap && Math.max(Math.abs(e.getX() - listDownX), Math.abs(e.getY() - listDownY)) > Screen.getTouchSlop()) {
          listTap = false; // a scroll: the selection stays
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (listTap && e.getEventTime() - listDownTime < ViewConfiguration.getLongPressTimeout()) {
          UI.post(endLater); // a tap elsewhere in the chat (it goes on to what it taps)
        }
        listTap = false;
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        listTap = false;
        break;
      }
    }
  }

  // Actions

  void onAction (int id) {
    if (ended) {
      return;
    }
    if (id == R.id.btn_xgramSelCopy) {
      copy();
    } else if (id == R.id.btn_xgramSelQuote) {
      quote();
    } else if (id == R.id.btn_xgramSelTranslate) {
      translate();
    } else if (id == R.id.btn_xgramSelForward) {
      forward();
    } else if (id == R.id.btn_xgramSelShare) {
      share();
    } else if (id == R.id.btn_xgramSelAll) {
      setRange(0, text.length(), false);
      toolbarOff = false;
    }
  }

  /** The message's own text when the bubble shows it as it is: not a translation, nor an edit on its way. */
  private @Nullable TdApi.FormattedText originalText () {
    if (msg.xgramSelectableTextTranslated(messageId)) {
      return null;
    }
    final TdApi.Message message = msg.getMessage(messageId);
    final TdApi.FormattedText formatted = message != null && message.content != null ? Td.textOrCaption(message.content) : null;
    return formatted != null && text.equals(formatted.text) ? formatted : null;
  }

  /**
   * What is selected: with its formatting in the message's own text (whole characters, custom emoji and dates, no
   * spaces at the ends, as the menu's Quote takes it), else as plain text. Null when only spaces are selected.
   */
  private @Nullable TdApi.FormattedText selectedText () {
    if (start < 0 || end <= start || XgramQuoteText.isBlank(text.substring(start, end))) {
      return null;
    }
    final TdApi.FormattedText original = originalText();
    if (original != null) {
      final int[] range = XgramQuoteText.range(original, start, end);
      return range != null ? Td.substring(original, range[0], range[1]) : null;
    }
    return new TdApi.FormattedText(text.substring(start, end).trim(), new TdApi.TextEntity[0]);
  }

  private void copy () {
    final TdApi.FormattedText selected = selectedText();
    if (selected == null) {
      return;
    }
    final CharSequence copyText = TD.toCopyText(selected);
    finish();
    if (XgramExpressive.isEnabled()) {
      // Expressive: the M3 snackbar says so, in the chat
      UI.copyText(copyText, 0);
      if (!XgramSnackbar.show(Lang.getString(R.string.XgramSelCopied), Toast.LENGTH_SHORT, R.drawable.baseline_content_copy_24, null, null)) {
        UI.showToast(R.string.CopiedText, Toast.LENGTH_SHORT);
      }
    } else {
      UI.copyText(copyText, R.string.CopiedText);
    }
  }

  private void quote () {
    final TdApi.Message message = msg.getMessage(messageId);
    final TdApi.FormattedText original = originalText();
    final int selectionStart = start, selectionEnd = end;
    final boolean allowed = canQuote;
    finish();
    if (allowed && message != null && original != null && !controller.isDestroyed()) {
      XgramQuote.reply(controller, message, original, selectionStart, selectionEnd); // the menu's Quote, for exactly this
    }
  }

  private void translate () {
    final TdApi.FormattedText selected = selectedText();
    if (selected == null || !canTranslate || controller.isDestroyed()) {
      return;
    }
    final String language = msg.getOriginalMessageLanguage();
    final long chatId = msg.getChatId();
    finish();
    final TranslationControllerV2.Wrapper popup = new TranslationControllerV2.Wrapper(controller.context(), controller.tdlib(), controller);
    popup.setArguments(new TranslationControllerV2.Args(new TranslationsManager.Translatable() {
      @Nullable
      @Override
      public String getOriginalMessageLanguage () {
        return language;
      }

      @Override
      public TdApi.FormattedText getTextToTranslate () {
        return selected;
      }

      @Override
      public long xgramChatId () {
        return chatId; // third-party services follow the chat's rules, as for the whole message
      }
    }));
    popup.setTextColorSet(TextColorSets.Regular.NORMAL);
    popup.show();
  }

  /** Forward: XGram's own chat picker with exactly the selection (its formatting kept), not Android's share sheet. */
  private void forward () {
    final TdApi.FormattedText selected = selectedText();
    if (selected == null) {
      return;
    }
    final MessagesController c = controller;
    finish();
    final org.thunderdog.challegram.ui.ShareController share = new org.thunderdog.challegram.ui.ShareController(c.context(), c.tdlib());
    share.setArguments(new org.thunderdog.challegram.ui.ShareController.Args(selected));
    share.show();
  }

  private void share () {
    final TdApi.FormattedText selected = selectedText();
    if (selected == null) {
      return;
    }
    final BaseActivity activity = controller.context();
    finish();
    final Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, selected.text);
    try {
      activity.startActivity(Intent.createChooser(intent, Lang.getString(R.string.Share)));
    } catch (ActivityNotFoundException ignored) {
      // No app takes text
    }
  }

  // Words

  private static BreakIterator newWords (String text) {
    final BreakIterator words = BreakIterator.getWordInstance();
    words.setText(text);
    return words;
  }

  /** [start, end) of the word at {@code offset}; on a space or a line break the word before it, else after it; null when there is none. */
  private static @Nullable int[] wordAt (BreakIterator words, String text, int offset) {
    final int length = text.length();
    if (length == 0) {
      return null;
    }
    final int[] segment = segmentAt(words, length, Math.max(0, Math.min(length - 1, offset)));
    if (segment == null || !isBlank(text, segment)) {
      return segment;
    }
    final int[] before = segment[0] > 0 ? segmentAt(words, length, segment[0] - 1) : null;
    if (before != null && !isBlank(text, before)) {
      return before;
    }
    final int[] after = segment[1] < length ? segmentAt(words, length, segment[1]) : null;
    return after != null && !isBlank(text, after) ? after : null;
  }

  /** The BreakIterator segment holding the character at {@code offset} (a word, a run of spaces, an emoji, a sign). */
  private static @Nullable int[] segmentAt (BreakIterator words, int length, int offset) {
    try {
      int start = words.isBoundary(offset) ? offset : words.preceding(offset);
      if (start == BreakIterator.DONE) {
        start = 0;
      }
      int end = words.following(start);
      if (end == BreakIterator.DONE) {
        end = length;
      }
      return start < end ? new int[] {start, end} : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static boolean isBlank (String text, int[] range) {
    for (int i = range[0]; i < range[1]; i++) {
      if (!Character.isWhitespace(text.charAt(i)) && !Character.isSpaceChar(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isTouchExploration (Context context) {
    final AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
  }
}
