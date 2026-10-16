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
package com.fahadali.xgram.topics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.emoji.AnimatedEmojiDrawable;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.text.Counter;

import me.vkryl.core.ColorUtils;

/**
 * Strip of topic tabs in a forum that uses the tabs layout: a switch between top and side placement, "All", one tab
 * per topic and "+" to create one. Horizontal above the messages, or vertical along their side. Tapping a tab
 * switches the chat to that topic in place.
 */
public final class ForumTabsView extends RecyclerView implements ForumTopicList.Listener {
  public interface Callback {
    /** @param tab forum topic id, or {@link XgramForums#TAB_ALL} */
    void onTabSelected (int tab);
    void onTabLongPressed (TdApi.ForumTopic topic);
    void onCreateTopicRequested ();
    void onTogglePlacementRequested ();
  }

  private static final int TYPE_TOGGLE = 0, TYPE_ALL = 1, TYPE_TOPIC = 2, TYPE_ADD = 3;
  /** Items before the first topic: the placement switch and "All". */
  private static final int TOPICS_OFFSET = 2;

  public static int getBarHeight () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Screen.dp(XgramM3TopicTabs.BAR_HEIGHT_DP); // XGram: Material 3 tabs, 48dp touch targets
    }
    return Screen.dp(44f);
  }

  public static int getSideWidth () {
    return Screen.dp(72f);
  }

  private final Tdlib tdlib;
  private final ViewController<?> parent;
  private final boolean vertical;
  private final Adapter adapter;
  private final LinearLayoutManager manager;
  @Nullable private Callback callback;
  @Nullable private ForumTopicList list;
  private long chatId;
  private int selectedTab = XgramForums.TAB_ALL;
  private boolean canCreate;
  /** XGram: the Material 3 Expressive pill, fades and scroll spring; null with Expressive design off. */
  @Nullable private XgramM3TopicTabs xgramLook;
  private int xgramSelectedCache = -1;

  public ForumTabsView (Context context, Tdlib tdlib, ViewController<?> parent, boolean vertical) {
    super(context);
    this.tdlib = tdlib;
    this.parent = parent;
    this.vertical = vertical;
    setLayoutManager(manager = new LinearLayoutManager(context, vertical ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL, !vertical && Lang.rtl()));
    setItemAnimator(null);
    setHasFixedSize(true);
    setOverScrollMode(OVER_SCROLL_NEVER);
    setHorizontalScrollBarEnabled(false);
    setVerticalScrollBarEnabled(false);
    setClipToPadding(false);
    if (vertical) {
      setPadding(0, Screen.dp(4f), 0, Screen.dp(8f));
    } else if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      setPadding(Screen.dp(4f), 0, Screen.dp(4f), 0); // XGram: the 48dp icon buttons put their icons 16dp from the edges
    } else {
      setPadding(Screen.dp(4f), 0, Screen.dp(6f), 0);
    }
    setAdapter(adapter = new Adapter());
    ViewSupport.setThemedBackground(this, com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.background : ColorId.filling, parent); // XGram: Expressive tabs sit on the page layer
    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        checkLoadMore();
      }
    });
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive, as the chat list's folder tabs (see XgramM3TopicTabs)
      xgramLook = new XgramM3TopicTabs(this, manager, vertical, this::xgramSelectedPosition);
    }
  }

  /**
   * XGram M3E: the strip is painted in the app bar's colour of the moment ({@code source}: MessagesController's, the
   * selection and search fades included) instead of its own surfaceContainer, so it never shows as a band between the
   * selection app bar and the card backing below it. On top, the strip has no fill of its own (the bar stack's backing,
   * drawn in that colour, shows through); along the side it fills itself from {@code source}. Its edge fades take the
   * same colour. Call {@link #xgramInvalidateAppBarColor()} when that colour changes. Nothing with Expressive design off.
   */
  public void xgramSetAppBarColor (@Nullable com.fahadali.xgram.appearance.XgramM3Chat.ColorSource source) {
    if (xgramLook == null) {
      return;
    }
    xgramLook.setFadeColor(source);
    if (source == null) {
      ViewSupport.setThemedBackground(this, ColorId.background, null);
    } else if (vertical) {
      setBackground(new XgramAppBarFill(source));
    } else {
      setBackground(null);
    }
    invalidate();
  }

  /** XGram M3E: redraws the fill and the fades in the app bar's current colour (see {@link #xgramSetAppBarColor}). */
  public void xgramInvalidateAppBarColor () {
    if (xgramLook != null) {
      invalidate();
    }
  }

  /** XGram M3E: the side strip's fill, read from its colour source on every draw (no allocation). */
  private static final class XgramAppBarFill extends Drawable {
    private final com.fahadali.xgram.appearance.XgramM3Chat.ColorSource source;
    private final Paint paint = new Paint();

    XgramAppBarFill (com.fahadali.xgram.appearance.XgramM3Chat.ColorSource source) {
      this.source = source;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      paint.setColor(source.color());
      c.drawRect(getBounds(), paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable android.graphics.ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }

  /** XGram: {@link #positionOf} of the selected tab, checked in O(1) on every frame, searched only when topics moved. */
  private int xgramSelectedPosition () {
    if (selectedTab == XgramForums.TAB_ALL) {
      return 1;
    }
    int position = xgramSelectedCache;
    int index = position - TOPICS_OFFSET;
    if (list != null && index >= 0 && index < list.size() && list.get(index).info.forumTopicId == selectedTab) {
      return position;
    }
    return xgramSelectedCache = positionOf(selectedTab);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Binds the strip to a forum and selects a tab. */
  public void setChat (TdApi.Chat chat, int selectedTab) {
    boolean canCreate = XgramForums.canCreateTopics(tdlib, chat);
    if (this.chatId != chat.id || list == null) {
      if (list != null) {
        list.removeListener(this);
      }
      this.chatId = chat.id;
      this.canCreate = canCreate;
      this.selectedTab = selectedTab;
      if (xgramLook != null) {
        xgramLook.reset();
      }
      list = ForumTopicList.obtain(tdlib, chat.id);
      list.addListener(this);
      if (!list.isLoaded()) {
        list.loadMore();
      } else {
        // A cached list may be stale (updates don't carry order or last messages): fetch its first page again.
        list.resyncFirstPage();
      }
      adapter.notifyDataSetChanged();
      scrollToSelected(false);
    } else {
      if (this.canCreate != canCreate) {
        this.canCreate = canCreate;
        adapter.notifyDataSetChanged();
      }
      setSelectedTab(selectedTab);
    }
  }

  public void setSelectedTab (int tab) {
    if (this.selectedTab == tab) {
      return;
    }
    int oldPosition = positionOf(this.selectedTab);
    this.selectedTab = tab;
    if (xgramLook != null) {
      xgramLook.moveSelection(oldPosition, positionOf(tab)); // XGram: the pill springs over from the old tab
    }
    if (oldPosition != -1) {
      adapter.notifyItemChanged(oldPosition);
    }
    int newPosition = positionOf(tab);
    if (newPosition != -1) {
      adapter.notifyItemChanged(newPosition);
    }
    scrollToSelected(true);
  }

  public int getSelectedTab () {
    return selectedTab;
  }

  @Nullable
  public TdApi.ForumTopic findTopic (int forumTopicId) {
    return list != null ? list.find(forumTopicId) : null;
  }

  private void scrollToSelected (boolean smooth) {
    int position = positionOf(selectedTab);
    if (position == -1) {
      return;
    }
    if (smooth && xgramLook != null && xgramLook.scrollToCenter(position)) {
      return; // XGram: the picked tab springs to the middle
    }
    int first = manager.findFirstCompletelyVisibleItemPosition();
    int last = manager.findLastCompletelyVisibleItemPosition();
    if (first != NO_POSITION && position >= first && position <= last) {
      return;
    }
    if (smooth) {
      smoothScrollToPosition(position);
    } else {
      manager.scrollToPositionWithOffset(position, Screen.dp(24f));
    }
  }

  private int positionOf (int tab) {
    if (tab == XgramForums.TAB_ALL) {
      return 1;
    }
    int index = list != null ? list.indexOf(tab) : -1;
    return index != -1 ? index + TOPICS_OFFSET : -1;
  }

  private int topicCount () {
    return list != null ? list.size() : 0;
  }

  private void checkLoadMore () {
    if (list != null && list.canLoadMore()) {
      int last = manager.findLastVisibleItemPosition();
      if (last != NO_POSITION && last >= topicCount() - 5) {
        list.loadMore();
      }
    }
  }

  public void performDestroy () {
    if (list != null) {
      // The list stays cached for the next visit.
      list.removeListener(this);
      list = null;
    }
    chatId = 0;
    if (xgramLook != null) {
      xgramLook.reset();
    }
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view instanceof TabView) {
        ((TabView) view).performDestroy();
      }
    }
  }

  // ForumTopicList.Listener

  @Override
  public void onTopicsLoaded (ForumTopicList list) {
    adapter.notifyDataSetChanged();
    scrollToSelected(false);
  }

  @Override
  public void onTopicInserted (ForumTopicList list, int index) {
    adapter.notifyItemInserted(index + TOPICS_OFFSET);
  }

  @Override
  public void onTopicRemoved (ForumTopicList list, int index) {
    adapter.notifyItemRemoved(index + TOPICS_OFFSET);
  }

  @Override
  public void onTopicMoved (ForumTopicList list, int fromIndex, int toIndex) {
    adapter.notifyItemMoved(fromIndex + TOPICS_OFFSET, toIndex + TOPICS_OFFSET);
    adapter.notifyItemChanged(toIndex + TOPICS_OFFSET);
  }

  @Override
  public void onTopicChanged (ForumTopicList list, int index) {
    adapter.notifyItemChanged(index + TOPICS_OFFSET);
  }

  @Override
  public void onLoadMoreAvailable (ForumTopicList list) {
    checkLoadMore(); // the next page failed or had to wait: try again without a scroll
  }

  // Adapter

  private static final class Holder extends ViewHolder {
    Holder (View itemView) {
      super(itemView);
    }
  }

  private final class Adapter extends RecyclerView.Adapter<Holder> {
    Adapter () {
      setHasStableIds(true);
    }

    @Override
    public int getItemCount () {
      return TOPICS_OFFSET + topicCount() + (canCreate ? 1 : 0);
    }

    @Override
    public int getItemViewType (int position) {
      if (position == 0) {
        return TYPE_TOGGLE;
      }
      if (position == 1) {
        return TYPE_ALL;
      }
      return position < TOPICS_OFFSET + topicCount() ? TYPE_TOPIC : TYPE_ADD;
    }

    @Override
    public long getItemId (int position) {
      switch (getItemViewType(position)) {
        case TYPE_TOGGLE:
          return -3;
        case TYPE_ALL:
          return -1;
        case TYPE_ADD:
          return -2;
        default:
          return list.get(position - TOPICS_OFFSET).info.forumTopicId;
      }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parentView, int viewType) {
      TabView view = new TabView(getContext(), tdlib, vertical);
      view.setLayoutParams(vertical ?
        new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) :
        new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, getBarHeight()));
      if (xgramLook != null) {
        // XGram: Material 3 Expressive: the placement switch and "+" are standard icon buttons (corners spring on
        // press), a tab presses in its pill
        if (viewType == TYPE_TOGGLE || viewType == TYPE_ADD) {
          com.fahadali.xgram.appearance.XgramAppMotion.iconButton(view);
          view.setContentDescription(Lang.getString(viewType == TYPE_ADD ? R.string.XgramNewTopic : vertical ? R.string.XgramM3TopicTabsOnTop : R.string.XgramM3TopicTabsOnSide));
        } else {
          view.setBackground(XgramM3TopicTabs.tabBackground(vertical));
        }
      } else {
        RippleSupport.setTransparentSelector(view);
      }
      parent.addThemeInvalidateListener(view);
      view.setOnClickListener(v -> {
        TabView tab = (TabView) v;
        if (callback == null) {
          return;
        }
        switch (tab.type) {
          case TYPE_TOGGLE:
            callback.onTogglePlacementRequested();
            break;
          case TYPE_ADD:
            callback.onCreateTopicRequested();
            break;
          default: {
            int id = tab.topic != null ? tab.topic.info.forumTopicId : XgramForums.TAB_ALL;
            setSelectedTab(id);
            callback.onTabSelected(id);
            break;
          }
        }
      });
      view.setOnLongClickListener(v -> {
        TabView tab = (TabView) v;
        if (callback != null && tab.topic != null) {
          callback.onTabLongPressed(tab.topic);
          return true;
        }
        return false;
      });
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position) {
      TabView view = (TabView) holder.itemView;
      int type = getItemViewType(position);
      TdApi.ForumTopic topic = type == TYPE_TOPIC ? list.get(position - TOPICS_OFFSET) : null;
      boolean selected = type == TYPE_ALL ? selectedTab == XgramForums.TAB_ALL : topic != null && topic.info.forumTopicId == selectedTab;
      view.bind(type, topic, chatId, selected);
    }

    @Override
    public void onViewAttachedToWindow (@NonNull Holder holder) {
      ((TabView) holder.itemView).attach();
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull Holder holder) {
      ((TabView) holder.itemView).detach();
    }
  }

  // Tab

  private static final class TabView extends View implements Counter.Callback {
    private static final float MAX_TITLE_WIDTH_DP = 150f;
    private static TextPaint sideTitlePaint;

    private final Tdlib tdlib;
    private final boolean vertical;
    private final Counter counter;
    private int type = -1;
    @Nullable private TdApi.ForumTopic topic;
    private boolean selected;

    private String title = "";
    private String displayTitle = "";
    /** Side tabs: the title in up to two centered lines. */
    private String line1 = "", line2 = "";
    private float titleWidth;
    @Nullable private ForumTopicIconDrawable bubble;
    @Nullable private AnimatedEmojiDrawable emoji;
    private long emojiId;
    private boolean isAttached;

    TabView (Context context, Tdlib tdlib, boolean vertical) {
      super(context);
      this.tdlib = tdlib;
      this.vertical = vertical;
      this.counter = new Counter.Builder().textSize(11f).callback((Counter.Callback) this).build();
    }

    // XGram: Material 3 Expressive tab (see XgramM3TopicTabs): the label color springs (effects fast) between
    // onSurfaceVariant and onSecondaryContainer (rail: onSurface) as the pill moves, the unread count is an M3 badge.
    private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    @Nullable private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramSelection;
    private long xgramBoundKey = Long.MIN_VALUE;
    private int xgramBadgeCount = -1;
    private boolean xgramBadgeMuted;
    private String xgramBadgeText = "";
    private float xgramBadgeTextWidth;

    /** XGram: Expressive tab label: ON_SECONDARY_CONTAINER on the selected pill, ON_SURFACE_VARIANT otherwise. */
    private static int xgramLabelColor (boolean selected) {
      return com.fahadali.xgram.appearance.XgramExpressive.color(selected ? com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    }

    private static TextPaint titlePaint (boolean selected) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        TextPaint paint = XgramM3TopicTabs.labelPaint(); // labelLarge, as the folder tabs
        paint.setColor(xgramLabelColor(selected));
        return paint;
      }
      return Paints.getBoldPaint14(false, Theme.getColor(selected ? ColorId.textNeutral : ColorId.textLight));
    }

    private static TextPaint sideTitlePaint (boolean selected) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        TextPaint paint = XgramM3TopicTabs.railLabelPaint(); // labelMedium, as a navigation rail
        paint.setColor(xgramLabelColor(selected));
        return paint;
      }
      if (sideTitlePaint == null) {
        sideTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        sideTitlePaint.setTypeface(Fonts.getRobotoMedium());
      }
      sideTitlePaint.setTextSize(Screen.dp(11f));
      sideTitlePaint.setColor(Theme.getColor(selected ? ColorId.textNeutral : ColorId.textLight));
      return sideTitlePaint;
    }

    void bind (int type, @Nullable TdApi.ForumTopic topic, long chatId, boolean selected) {
      this.type = type;
      this.topic = topic;
      this.selected = selected;
      String title;
      switch (type) {
        case TYPE_ALL:
          title = Lang.getString(R.string.XgramTabAll);
          break;
        case TYPE_TOPIC:
          title = XgramForums.topicTitle(topic.info);
          break;
        default:
          title = "";
          break;
      }
      if (!title.equals(this.title)) {
        this.title = title;
        layoutTitle();
      }
      if (topic != null) {
        bubble = new ForumTopicIconDrawable(topic.info);
        setEmoji(topic.info.icon != null ? topic.info.icon.customEmojiId : 0);
        counter.setCount(topic.unreadCount, XgramForums.isTopicMuted(tdlib, chatId, topic), false);
      } else {
        bubble = null;
        setEmoji(0);
        counter.setCount(0, false, false);
      }
      if (xgram) {
        xgramBind(type, topic, chatId, selected);
      }
      requestLayout();
      invalidate();
    }

    /** XGram: the selection spring (animated when the same tab is rebound) and the badge text. */
    private void xgramBind (int type, @Nullable TdApi.ForumTopic topic, long chatId, boolean selected) {
      long key = topic != null ? topic.info.forumTopicId : -1L - type;
      float target = selected ? 1f : 0f;
      if (xgramSelection == null) {
        xgramSelection = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST, target, (s, value) -> invalidate());
      } else if (key == xgramBoundKey) {
        xgramSelection.animateTo(target);
      } else {
        xgramSelection.setValue(target);
      }
      xgramBoundKey = key;
      int count = topic != null ? Math.max(0, topic.unreadCount) : 0;
      if (count != xgramBadgeCount) {
        xgramBadgeCount = count;
        xgramBadgeText = count > 0 ? Strings.buildCounter(count) : "";
        xgramBadgeTextWidth = XgramM3TopicTabs.badgeTextWidth(xgramBadgeText);
      }
      xgramBadgeMuted = count > 0 && XgramForums.isTopicMuted(tdlib, chatId, topic);
      if (type == TYPE_ALL || type == TYPE_TOPIC) {
        setContentDescription(title);
      }
    }

    private float xgramSelectionFactor () {
      return xgramSelection != null ? Math.max(0f, Math.min(1f, xgramSelection.getValue())) : selected ? 1f : 0f;
    }

    private void layoutTitle () {
      if (vertical) {
        TextPaint paint = sideTitlePaint(false);
        int maxWidth = getSideWidth() - Screen.dp(8f);
        int fit = paint.breakText(title, true, maxWidth, null);
        if (fit >= title.length()) {
          line1 = title;
          line2 = "";
        } else {
          int space = title.lastIndexOf(' ', fit);
          int cut = space > 0 ? space : fit;
          line1 = title.substring(0, cut).trim();
          line2 = TextUtils.ellipsize(title.substring(cut).trim(), paint, maxWidth, TextUtils.TruncateAt.END).toString();
        }
      } else {
        displayTitle = TextUtils.ellipsize(title, titlePaint(false), Screen.dp(MAX_TITLE_WIDTH_DP), TextUtils.TruncateAt.END).toString();
        titleWidth = displayTitle.isEmpty() ? 0 : titlePaint(false).measureText(displayTitle);
      }
    }

    private void setEmoji (long customEmojiId) {
      if (emojiId == customEmojiId) {
        return;
      }
      emojiId = customEmojiId;
      if (emoji != null) {
        emoji.detach();
        emoji.performDestroy();
        emoji = null;
      }
      if (customEmojiId == 0) {
        return;
      }
      tdlib.emoji().findOrRequest(customEmojiId, (entry, inPlace) -> {
        Runnable act = () -> {
          if (emojiId != customEmojiId || emoji != null || entry.value == null) {
            return;
          }
          AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(this);
          drawable.setSticker(new TGStickerObj(tdlib, entry.value, null, entry.value.fullType), false);
          if (isAttached) {
            drawable.attach();
          }
          emoji = drawable;
          invalidate();
        };
        if (inPlace) {
          act.run();
        } else {
          tdlib.ui().post(act);
        }
      });
    }

    void attach () {
      isAttached = true;
      if (emoji != null) {
        emoji.attach();
      }
    }

    void detach () {
      isAttached = false;
      if (emoji != null) {
        emoji.detach();
      }
    }

    void performDestroy () {
      if (emoji != null) {
        emoji.performDestroy();
        emoji = null;
      }
      emojiId = 0;
    }

    private static int horizontalPadding () {
      return Screen.dp(12f);
    }

    private static int gap () {
      return Screen.dp(6f);
    }

    private int counterWidth () {
      return (int) counter.getScaledWidth(0);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (xgram) {
        xgramMeasure(widthMeasureSpec, heightMeasureSpec);
        return;
      }
      if (vertical) {
        int height;
        switch (type) {
          case TYPE_TOGGLE:
            height = Screen.dp(44f);
            break;
          case TYPE_ADD:
            height = Screen.dp(52f);
            break;
          default:
            height = Screen.dp(8f) + Screen.dp(30f) + Screen.dp(5f) + Screen.dp(13f) * (line2.isEmpty() ? 1 : 2) + Screen.dp(8f);
            break;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
        return;
      }
      int width = horizontalPadding() * 2;
      if (type == TYPE_ADD || type == TYPE_TOGGLE) {
        width = Screen.dp(type == TYPE_TOGGLE ? 40f : 48f);
      } else {
        if (topic != null) {
          width += Screen.dp(20f) + gap();
        }
        width += (int) Math.ceil(titleWidth);
        int counterWidth = counterWidth();
        if (counterWidth > 0) {
          width += gap() + counterWidth;
        }
      }
      setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
    }

    private void drawIcon (Canvas c, @DrawableRes int res, float cx, float cy, boolean active) {
      Drawable icon = Drawables.get(getResources(), res);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f,
        Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? xgramLabelColor(active) : Theme.getColor(active ? ColorId.textNeutral : ColorId.icon)));
    }

    @Override
    protected void onDraw (Canvas c) {
      if (xgram) {
        if (vertical) {
          xgramDrawVertical(c);
        } else {
          xgramDrawHorizontal(c);
        }
        return;
      }
      if (vertical) {
        drawVertical(c);
      } else {
        drawHorizontal(c);
      }
    }

    // XGram: Material 3 Expressive tabs. The pill is drawn by XgramM3TopicTabs under the tabs.

    /**
     * Top: a tab is 16dp, [20dp icon, 8dp], label, [8dp, badge], 16dp wide in the 48dp bar, the pill around all of it;
     * the switch and "+" are 48dp wide icon buttons. Side: 48dp and 56dp icon buttons, and rail items of a 32dp
     * indicator, 4dp, one or two 16dp label lines and 8dp.
     */
    private void xgramMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (vertical) {
        int height;
        switch (type) {
          case TYPE_TOGGLE:
            height = Screen.dp(XgramM3TopicTabs.RAIL_TOGGLE_HEIGHT_DP);
            break;
          case TYPE_ADD:
            height = Screen.dp(XgramM3TopicTabs.RAIL_ADD_HEIGHT_DP);
            break;
          default:
            height = XgramM3TopicTabs.railItemHeight(line2.isEmpty() ? 1 : 2);
            break;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
        return;
      }
      int width;
      if (type == TYPE_ADD || type == TYPE_TOGGLE) {
        width = Screen.dp(XgramM3TopicTabs.ICON_BUTTON_WIDTH_DP);
      } else {
        width = Screen.dp(XgramM3TopicTabs.TAB_PADDING_DP) * 2 + (int) Math.ceil(titleWidth);
        if (topic != null) {
          width += Screen.dp(XgramM3TopicTabs.TAB_ICON_DP) + Screen.dp(XgramM3TopicTabs.TAB_GAP_DP);
        }
        if (!xgramBadgeText.isEmpty()) {
          width += Screen.dp(XgramM3TopicTabs.TAB_GAP_DP) + (int) Math.ceil(XgramM3TopicTabs.badgeWidth(xgramBadgeTextWidth));
        }
      }
      setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
    }

    /** One of the two colors, never a blend: Paints' color filters are cached for every color they get. */
    private void xgramDrawIcon (Canvas c, @DrawableRes int res, float cx, float cy, int color) {
      Drawable icon = Drawables.get(getResources(), res);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(color));
    }

    private void xgramDrawHorizontal (Canvas c) {
      int width = getMeasuredWidth(), height = getMeasuredHeight();
      float centerY = height / 2f;
      int inactive = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
      if (type == TYPE_TOGGLE || type == TYPE_ADD) {
        xgramDrawIcon(c, type == TYPE_TOGGLE ? R.drawable.xgram_tabs_side_24 : R.drawable.baseline_add_24, width / 2f, centerY, inactive);
        return;
      }
      float f = xgramSelectionFactor();
      boolean rtl = Lang.rtl();
      int gap = Screen.dp(XgramM3TopicTabs.TAB_GAP_DP);
      int padding = Screen.dp(XgramM3TopicTabs.TAB_PADDING_DP);
      int x = rtl ? width - padding : padding;
      if (topic != null) {
        int size = Screen.dp(XgramM3TopicTabs.TAB_ICON_DP);
        int left = rtl ? x - size : x;
        int top = (int) (centerY - size / 2f);
        Drawable icon = emoji != null ? emoji : bubble;
        if (icon != null) {
          icon.setBounds(left, top, left + size, top + size);
          icon.draw(c);
        }
        x = rtl ? x - size - gap : x + size + gap;
      }
      if (!displayTitle.isEmpty()) {
        TextPaint paint = XgramM3TopicTabs.labelPaint();
        paint.setColor(ColorUtils.fromToArgb(inactive, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER), f));
        c.drawText(displayTitle, rtl ? x - titleWidth : x, XgramM3TopicTabs.baseline(paint, centerY), paint);
        x = rtl ? (int) (x - titleWidth - gap) : (int) (x + titleWidth + gap);
      }
      if (!xgramBadgeText.isEmpty()) {
        float badgeWidth = XgramM3TopicTabs.badgeWidth(xgramBadgeTextWidth);
        XgramM3TopicTabs.drawBadge(c, rtl ? x - badgeWidth : x, centerY, xgramBadgeText, xgramBadgeTextWidth, xgramBadgeMuted);
      }
    }

    private void xgramDrawVertical (Canvas c) {
      int width = getMeasuredWidth(), height = getMeasuredHeight();
      float centerX = width / 2f;
      int inactive = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
      if (type == TYPE_TOGGLE || type == TYPE_ADD) {
        xgramDrawIcon(c, type == TYPE_TOGGLE ? R.drawable.xgram_tabs_top_24 : R.drawable.baseline_add_24, centerX, height / 2f, inactive);
        return;
      }
      float f = xgramSelectionFactor();
      float iconCenterY = XgramM3TopicTabs.railIconCenterY();
      int iconSize = Screen.dp(XgramM3TopicTabs.RAIL_ICON_DP);
      if (type == TYPE_ALL) {
        xgramDrawIcon(c, R.drawable.baseline_forum_24, centerX, iconCenterY, f >= .5f ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER) : inactive);
      } else {
        Drawable icon = emoji != null ? emoji : bubble;
        if (icon != null) {
          int left = (int) (centerX - iconSize / 2f), top = (int) (iconCenterY - iconSize / 2f);
          icon.setBounds(left, top, left + iconSize, top + iconSize);
          icon.draw(c);
        }
      }
      TextPaint paint = XgramM3TopicTabs.railLabelPaint();
      paint.setColor(ColorUtils.fromToArgb(inactive, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), f));
      float lineHeight = XgramM3TopicTabs.railLineHeight();
      float lineCenter = XgramM3TopicTabs.railLabelTop() + lineHeight / 2f;
      if (!line1.isEmpty()) {
        c.drawText(line1, centerX - paint.measureText(line1) / 2f, XgramM3TopicTabs.baseline(paint, lineCenter), paint);
      }
      if (!line2.isEmpty()) {
        c.drawText(line2, centerX - paint.measureText(line2) / 2f, XgramM3TopicTabs.baseline(paint, lineCenter + lineHeight), paint);
      }
      if (!xgramBadgeText.isEmpty()) {
        // On the icon's top end corner, as on a navigation rail
        float badgeWidth = XgramM3TopicTabs.badgeWidth(xgramBadgeTextWidth);
        boolean rtl = Lang.rtl();
        float left = rtl ? centerX - iconSize / 2f + Screen.dp(6f) - badgeWidth : centerX + iconSize / 2f - Screen.dp(6f);
        left = Math.max(Screen.dp(2f), Math.min(left, width - Screen.dp(2f) - badgeWidth));
        XgramM3TopicTabs.drawBadge(c, left, iconCenterY - iconSize / 2f + Screen.dp(2f), xgramBadgeText, xgramBadgeTextWidth, xgramBadgeMuted);
      }
    }

    private void drawHorizontal (Canvas c) {
      int width = getMeasuredWidth(), height = getMeasuredHeight();
      float centerY = height / 2f;
      if (type == TYPE_TOGGLE) {
        drawIcon(c, R.drawable.xgram_tabs_side_24, width / 2f, centerY, false);
        return;
      }
      if (selected) {
        int pillHeight = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? 32f : 30f);
        RectF rect = Paints.getRectF(Screen.dp(2f), centerY - pillHeight / 2f, width - Screen.dp(2f), centerY + pillHeight / 2f);
        int pillColor = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER) : ColorUtils.alphaColor(.14f, Theme.getColor(ColorId.textNeutral));
        c.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, Paints.fillingPaint(pillColor));
      }
      if (type == TYPE_ADD) {
        drawIcon(c, R.drawable.baseline_add_24, width / 2f, centerY, false);
        return;
      }
      boolean rtl = Lang.rtl();
      int x = rtl ? width - horizontalPadding() : horizontalPadding();
      if (topic != null) {
        int size = Screen.dp(20f);
        int left = rtl ? x - size : x;
        int top = (int) (centerY - size / 2f);
        Drawable icon = emoji != null ? emoji : bubble;
        if (icon != null) {
          icon.setBounds(left, top, left + size, top + size);
          icon.draw(c);
        }
        x = rtl ? x - size - gap() : x + size + gap();
      }
      if (!displayTitle.isEmpty()) {
        TextPaint paint = titlePaint(selected);
        float baseline = centerY - (paint.descent() + paint.ascent()) / 2f;
        c.drawText(displayTitle, rtl ? x - titleWidth : x, baseline, paint);
        x = rtl ? (int) (x - titleWidth - gap()) : (int) (x + titleWidth + gap());
      }
      if (counterWidth() > 0) {
        counter.draw(c, rtl ? x - counterWidth() / 2f : x + counterWidth() / 2f, centerY, Gravity.CENTER, 1f);
      }
    }

    private void drawVertical (Canvas c) {
      int width = getMeasuredWidth(), height = getMeasuredHeight();
      float centerX = width / 2f;
      if (type == TYPE_TOGGLE) {
        drawIcon(c, R.drawable.xgram_tabs_top_24, centerX, height / 2f, false);
        return;
      }
      if (type == TYPE_ADD) {
        drawIcon(c, R.drawable.baseline_add_24, centerX, height / 2f, false);
        return;
      }
      if (selected && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram: navigation rail style, a 56x32dp SECONDARY_CONTAINER pill behind the icon.
        float pillWidth = Math.min(Screen.dp(56f), width - Screen.dp(4f)), pillHeight = Screen.dp(32f);
        float pillCenterY = Screen.dp(8f) + Screen.dp(30f) / 2f;
        RectF pill = Paints.getRectF(centerX - pillWidth / 2f, pillCenterY - pillHeight / 2f, centerX + pillWidth / 2f, pillCenterY + pillHeight / 2f);
        c.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER)));
      } else if (selected) {
        // Highlight behind the tab and an accent bar on the edge next to the messages' opposite side.
        RectF rect = Paints.getRectF(Screen.dp(4f), Screen.dp(2f), width - Screen.dp(4f), height - Screen.dp(2f));
        c.drawRoundRect(rect, Screen.dp(10f), Screen.dp(10f), Paints.fillingPaint(ColorUtils.alphaColor(.12f, Theme.getColor(ColorId.textNeutral))));
        float barLeft = Lang.rtl() ? width - Screen.dp(3f) : 0;
        RectF bar = Paints.getRectF(barLeft, Screen.dp(12f), barLeft + Screen.dp(3f), height - Screen.dp(12f));
        c.drawRoundRect(bar, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(Theme.getColor(ColorId.textNeutral)));
      }
      int iconSize = Screen.dp(30f);
      int iconTop = Screen.dp(8f);
      float iconCenterY = iconTop + iconSize / 2f;
      if (type == TYPE_ALL) {
        drawIcon(c, R.drawable.baseline_forum_24, centerX, iconCenterY, selected);
      } else {
        Drawable icon = emoji != null ? emoji : bubble;
        if (icon != null) {
          int left = (int) (centerX - iconSize / 2f);
          icon.setBounds(left, iconTop, left + iconSize, iconTop + iconSize);
          icon.draw(c);
        }
      }
      TextPaint paint = sideTitlePaint(selected);
      float lineHeight = Screen.dp(13f);
      float baseline = iconTop + iconSize + Screen.dp(5f) + lineHeight - Screen.dp(2.5f);
      if (!line1.isEmpty()) {
        c.drawText(line1, centerX - paint.measureText(line1) / 2f, baseline, paint);
      }
      if (!line2.isEmpty()) {
        c.drawText(line2, centerX - paint.measureText(line2) / 2f, baseline + lineHeight, paint);
      }
      if (counterWidth() > 0) {
        float cx = Math.min(centerX + iconSize / 2f, width - counterWidth() / 2f - Screen.dp(2f));
        counter.draw(c, cx, iconTop + Screen.dp(2f), Gravity.CENTER, 1f);
      }
    }

    @Override
    public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
      if (sizeChanged) {
        requestLayout();
      }
      invalidate();
    }

    @Override
    public boolean needAnimateChanges (Counter counter) {
      return false;
    }
  }
}
