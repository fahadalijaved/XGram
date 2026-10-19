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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramMotion;
import com.fahadali.xgram.appearance.XgramPillStateLayer;
import com.fahadali.xgram.appearance.XgramShapes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.RecentEmoji;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.emoji.section.StickerSectionView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * Create or edit a forum topic: name, color (new topics only, as in the official app) and icon.
 *
 * <p>The icon comes from Telegram's free default topic icons and, as in the official apps, from every custom emoji the
 * account can use: the recently used custom emoji and each installed custom emoji pack in the user's order, a section
 * (title and grid) each in the same list. A pack's emoji are asked for (GetStickerSet, at most two at a time, the newest
 * on screen first) only when its section scrolls into view; until then its first stickers (the set's covers) show. A
 * strip of tabs (the default icons, recent, each pack's cover) sits under the name and stays pinned at the top once it
 * scrolls there; a tap jumps to that section. Telegram lets only Premium accounts use a custom emoji that is not a
 * default icon: without Premium the packs show with a lock and a tap explains why. A topic's own custom emoji shows
 * (selected) for every account, so it can be kept.
 */
public final class ForumTopicEditController extends RecyclerViewController<ForumTopicEditController.Args> {
  public static final class Args {
    public final TdApi.Chat chat;
    @Nullable public final TdApi.ForumTopic topic;

    /** @param topic null to create a new topic */
    public Args (TdApi.Chat chat, @Nullable TdApi.ForumTopic topic) {
      this.chat = chat;
      this.topic = topic;
    }
  }

  private static final int VIEW_TYPE_HEADER = 0, VIEW_TYPE_TITLE = 1, VIEW_TYPE_ICON = 2, VIEW_TYPE_TABS = 3;
  /** Adapter position of the tab strip's slot, while the list has more than the default icons. */
  private static final int STRIP_POSITION = 1;
  /** M3E: space under the list for the Done button (56dp, 16dp from the bottom) plus a 16dp gap. */
  private static final float DONE_CLEARANCE = 88f;
  /** M3E: space above the first and below the last row of icons inside their container. */
  private static final float GRID_PADDING = 8f;
  /**
   * Icon cells are at least this wide, so the span count follows the list's width (7 on most phones, 8 on wide ones,
   * 16 on an unfolded Fold); rows are as tall as a cell is wide, 44 to 56dp.
   */
  private static final float MIN_CELL = 44f, MAX_ROW = 56f;
  private static final int MIN_SPAN = 6;
  /** The tab strip: 48dp tall with 44dp tabs; with Expressive design an 8dp gap above and below its container. */
  private static final float STRIP_HEIGHT = 48f, STRIP_GAP = 8f, TAB_WIDTH = 44f;
  /** Packs whose contents are being asked for at once. */
  private static final int MAX_PACK_REQUESTS = 2;
  /** A pack whose contents failed to load is asked for again this many times, 2s, 4s, 6s later (or as told). */
  private static final int MAX_PACK_RETRIES = 3;
  private static final long PACK_RETRY_DELAY = 2000L;
  /** Title rows kept for reuse (the default is 5: many small packs on a wide grid build new ones on every jump). */
  private static final int TITLE_POOL = 24;
  /** RecyclerView's own default for a view type it was not told about. */
  private static final int DEFAULT_POOL = 5;
  /** Without Premium, a pack's emoji are drawn at this opacity, with a lock in their corner. */
  private static final float LOCKED_ALPHA = .5f;
  /** At most this many custom emoji go to GetCustomEmojiStickers. */
  private static final int MAX_RECENT = 200;

  private static final int SECTION_DEFAULT = 0, SECTION_RECENT = 1, SECTION_PACK = 2;
  private static final int LOAD_NONE = 0, LOAD_QUEUED = 1, LOAD_LOADING = 2, LOAD_DONE = 3;
  private static final int TAB_GLYPH = 0, TAB_COVER = 1;
  /** Tab payload: only its selection changed (animated). */
  private static final Object TAB_SELECTION = new Object();
  /** Tag of a cell whose emoji isn't known yet (a pack still loading). */
  private static final Object PLACEHOLDER = new Object();

  /**
   * A run of icons under one title: the default icons ("no icon" first), the recently used custom emoji (the topic's
   * own first; without Premium only that one) or a pack.
   */
  private static final class Section {
    final int kind;
    @Nullable final TdApi.StickerSetInfo info;
    @Nullable final TGStickerSetInfo cover;
    /** Recent and packs: the emoji, a pack's filled from its covers until it loads. */
    @Nullable TGStickerObj[] items;
    int titlePosition, firstCell, cellCount;
    int loadState = LOAD_NONE;
    /** Recent: only the topic's own emoji ("Current icon": without Premium, or no recently used ones). */
    boolean currentOnly;
    /** A pack: failed loads so far, and the time before which it isn't asked for again. */
    int failures;
    long retryAt;

    Section (int kind, @Nullable TdApi.StickerSetInfo info, @Nullable TGStickerSetInfo cover) {
      this.kind = kind;
      this.info = info;
      this.cover = cover;
    }
  }

  /** Expressive design when the page was built (it keeps that look until it is closed). */
  private boolean expressive;
  /** Telegram Premium when the page was built: the packs are usable (a tap checks again). */
  private boolean premium;
  private int color;
  private long customEmojiId, initialCustomEmojiId;
  private String name = "";
  private final List<TdApi.Sticker> icons = new ArrayList<>();
  private TGStickerObj[] defaultItems = new TGStickerObj[0];
  private final ArrayList<Section> sections = new ArrayList<>();
  @Nullable private Section defaultSection;
  private int itemCount = 1;
  private IconView preview;
  @Nullable private ColorRow colorRow; // M3E round 6: a new topic's color swatches
  private MaterialEditTextGroup nameInput;
  private Adapter adapter;
  private GridLayoutManager gridManager;
  private boolean isSaving;

  // Loading. The default icons and the installed packs are asked for together and go in at once with the strip's slot
  // (the list grows once, nothing drawn moves); the recent custom emoji may need the server, so they follow on their
  // own, below the default icons.

  /** Answers the first insert still waits for (the default icons, the installed packs). */
  private int pendingFirst;
  private boolean firstLoaded;
  /** GetCustomEmojiStickers (the topic's own emoji, the recent ones) has not answered yet. */
  private boolean recentsPending;
  /** Ids asked for that came from the recently used emoji, not the topic's own. */
  private int recentOnlyCount;
  @Nullable private TdApi.StickerSetInfo[] loadedPacks;
  /** The recent emoji when they answer before the first insert (they go in with it). */
  @Nullable private TdApi.Sticker[] loadedRecents;
  /** The tab strip's slot is in the list: more than the default icons, or the recent ones on their way. */
  private boolean withStrip;
  private final ArrayDeque<Section> packQueue = new ArrayDeque<>();
  private int packRequests;
  /** The grid's span and height the cell pool was sized for. */
  private int poolSpan, poolHeight;
  @Nullable private CellPool cellPool;

  // Tab strip

  @Nullable private StripLayout strip;
  @Nullable private RecyclerView tabsView;
  @Nullable private LinearLayoutManager tabsManager;
  @Nullable private TabsAdapter tabsAdapter;
  private int selectedTab;
  /** The tab tapped last: it stays selected until the list is dragged (a short last pack never reaches the top). */
  private int pinnedTab = -1;

  /** Every emoji view built for this page (the visible ones, the recycled pool and the tabs), destroyed with it. */
  private final ArrayList<Destroyable> destroyables = new ArrayList<>();

  public ForumTopicEditController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean isCreating () {
    return getArgumentsStrict().topic == null;
  }

  private boolean isGeneral () {
    TdApi.ForumTopic topic = getArgumentsStrict().topic;
    return topic != null && topic.info.isGeneral;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramTopicEdit;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(isCreating() ? R.string.XgramNewTopic : R.string.XgramEditTopic);
  }

  @Override
  protected View onCreateView (Context context) {
    View view = super.onCreateView(context);
    if (view instanceof FrameLayout && !isGeneral()) {
      createStrip(context, (FrameLayout) view);
    }
    return view;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    expressive = XgramExpressive.isEnabled();
    premium = tdlib.hasPremium();
    TdApi.ForumTopic topic = getArgumentsStrict().topic;
    if (topic != null) {
      name = topic.info.name;
      color = topic.info.icon != null ? topic.info.icon.color : ForumTopicIconDrawable.serverColors()[0];
      customEmojiId = topic.info.icon != null ? topic.info.icon.customEmojiId : 0;
      initialCustomEmojiId = customEmojiId;
    } else {
      int[] colors = ForumTopicIconDrawable.serverColors();
      color = colors[(int) (Math.random() * colors.length)];
    }
    layoutSections(); // the header alone until the icons and packs are known (see onFirstLoaded)

    adapter = new Adapter();
    final CellPool pool = cellPool = new CellPool();
    final GridLayoutManager manager = gridManager = new GridLayoutManager(context, MIN_SPAN) {
      @Override
      public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
        // The span count follows the list's width: rotation, two panes, the Fold opening
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        if (available > 0) {
          int span = Math.max(MIN_SPAN, available / Screen.dp(MIN_CELL));
          boolean spanChanged = span != getSpanCount();
          if (spanChanged) {
            setSpanCount(span);
          }
          sizePool(span, getHeight()); // before cells go back to it
          if (spanChanged && expressive) {
            // The decoration's insets (8dp above a section's first row and below its last) follow the span count, but
            // RecyclerView keeps a cell's insets until it is bound again: the cells go back to the pool and are laid
            // out anew. Only the cells: the header keeps the name field's focus and the keyboard.
            for (int i = getChildCount() - 1; i >= 0; i--) {
              View child = getChildAt(i);
              if (child != null && getItemViewType(child) == VIEW_TYPE_ICON) {
                removeAndRecycleView(child, recycler);
              }
            }
          }
        }
        super.onLayoutChildren(recycler, state);
      }
    };
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return adapter.getItemViewType(position) == VIEW_TYPE_ICON ? 1 : manager.getSpanCount();
      }

      // Both from the sections in O(1) / O(sections): the defaults walk every position before this one, and a list
      // of installed packs has thousands.

      @Override
      public int getSpanIndex (int position, int spanCount) {
        if (adapter.getItemViewType(position) != VIEW_TYPE_ICON) {
          return 0;
        }
        Section section = sectionAt(position);
        return section != null ? (position - section.firstCell) % spanCount : 0;
      }

      @Override
      public int getSpanGroupIndex (int adapterPosition, int spanCount) {
        return rowOf(adapterPosition, spanCount);
      }
    });
    recyclerView.setLayoutManager(manager);
    recyclerView.setItemAnimator(null);
    // Cells: sized from the grid once it is laid out (sizePool); titles: enough for a screen of small packs
    pool.setMaxRecycledViews(VIEW_TYPE_ICON, 192);
    pool.setMaxRecycledViews(VIEW_TYPE_TITLE, TITLE_POOL);
    recyclerView.setRecycledViewPool(pool);
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView view, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          pinnedTab = -1;
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView view, int dx, int dy) {
        syncStrip();
      }
    });
    recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> syncStrip());
    if (expressive) {
      // M3E (until the page is drawn in Compose): the name in a surfaceBright card and each section's icons in a
      // surfaceBright container, all 16dp from the edges; the list ends clear of the Done button.
      recyclerView.addItemDecoration(new ExpressiveDecoration());
      addThemeInvalidateListener(recyclerView);
      applyExpressivePadding(recyclerView);
      // M3E round 6: the page title large above the card, the app bar's small title fading in as it scrolls away
      com.fahadali.xgram.appearance.XgramM3Bars.attachLargeTitle(this, recyclerView);
    }

    if (!isGeneral()) {
      loadIcons();
    }
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    setDoneVisible(true, false);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (isCreating() && nameInput != null) {
      Keyboard.show(nameInput.getEditText());
    }
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    if (expressive && getRecyclerView() != null) {
      applyExpressivePadding(getRecyclerView()); // the base class has just set the bottom padding to the inset alone
    }
  }

  private void applyExpressivePadding (RecyclerView recyclerView) {
    int inset = Screen.dp(XgramExpressive.LIST_INSET);
    recyclerView.setPadding(inset, recyclerView.getPaddingTop(), inset, extraBottomInset + Screen.dp(DONE_CLEARANCE));
    recyclerView.setClipToPadding(false);
  }

  /**
   * The cell pool holds a screen of cells for the grid's span and height (rows at their smallest, the ones laid out
   * into the padding under the list and a few more): a far tab jump lays out a new screen from it while the old one is
   * still attached. It only grows, so the keyboard or turning the phone back doesn't churn it.
   */
  private void sizePool (int span, int height) {
    final CellPool pool = cellPool;
    if (pool == null || height <= 0 || (span == poolSpan && height == poolHeight)) {
      return;
    }
    poolSpan = span;
    poolHeight = height;
    final int cells = span * (height / Screen.dp(MIN_CELL) + 4);
    if (cells > pool.limit(VIEW_TYPE_ICON)) {
      pool.setMaxRecycledViews(VIEW_TYPE_ICON, cells);
    }
  }

  private static long emojiIdOf (@Nullable TdApi.Sticker sticker) {
    return sticker != null && sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji ? ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId : 0;
  }

  /** One of Telegram's default topic icons (the ones every account may use), or null. */
  @Nullable
  private TdApi.Sticker findIcon (long customEmojiId) {
    for (TdApi.Sticker sticker : icons) {
      if (emojiIdOf(sticker) == customEmojiId) {
        return sticker;
      }
    }
    return null;
  }

  private TGStickerObj newGridSticker (TdApi.Sticker sticker) {
    TGStickerObj obj = new TGStickerObj(tdlib, sticker, null, sticker.fullType);
    obj.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW); // decoded at the emoji keyboard's size
    return obj;
  }

  // Sections

  private boolean hasStrip () {
    return withStrip;
  }

  /** Adapter positions of every section after a change in their number or size. */
  private void layoutSections () {
    int position = hasStrip() ? STRIP_POSITION + 1 : 1;
    for (int i = 0; i < sections.size(); i++) {
      Section section = sections.get(i);
      section.titlePosition = position;
      section.firstCell = position + 1;
      position += 1 + section.cellCount;
    }
    itemCount = position;
  }

  /** Index of the section whose title or cells are at {@code position}; -1 above the first. */
  private int sectionIndexAt (int position) {
    int low = 0, high = sections.size() - 1, found = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (sections.get(mid).titlePosition <= position) {
        found = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return found;
  }

  @Nullable
  private Section sectionAt (int position) {
    int index = sectionIndexAt(position);
    return index >= 0 ? sections.get(index) : null;
  }

  /** The grid row of {@code position} (GridLayoutManager's span group): the header, the strip, then each section. */
  private int rowOf (int position, int spanCount) {
    if (position <= 0) {
      return 0;
    }
    int row = 1;
    if (hasStrip()) {
      if (position == STRIP_POSITION) {
        return row;
      }
      row++;
    }
    for (int i = 0; i < sections.size(); i++) {
      Section section = sections.get(i);
      if (position == section.titlePosition) {
        return row;
      }
      row++;
      if (position < section.firstCell + section.cellCount) {
        return row + (position - section.firstCell) / spanCount;
      }
      row += (section.cellCount + spanCount - 1) / spanCount;
    }
    return row;
  }

  private void notifyNoIconChanged () {
    if (adapter != null && defaultSection != null) {
      adapter.notifyItemChanged(defaultSection.firstCell); // the "no icon" bubble shows the color and the name
    }
  }

  @Nullable
  private TGStickerObj stickerAt (Section section, int index) {
    if (section.kind == SECTION_DEFAULT) {
      int iconIndex = index - 1; // after "no icon"
      return iconIndex >= 0 && iconIndex < defaultItems.length ? defaultItems[iconIndex] : null;
    }
    TGStickerObj[] items = section.items;
    if (items == null || index < 0 || index >= items.length) {
      return null;
    }
    TGStickerObj sticker = items[index];
    TdApi.StickerSetInfo info = section.info;
    if (sticker == null && info != null && info.covers != null && index < info.covers.length && info.covers[index] != null) {
      sticker = items[index] = newGridSticker(info.covers[index]);
    }
    return sticker;
  }

  // Loading

  /** Asks for the default icons, the installed packs and the recent custom emoji at once (see onFirstLoaded). */
  private void loadIcons () {
    final long[] extraIds = extraCustomEmojiIds();
    recentsPending = extraIds.length > 0;
    pendingFirst = 2;
    tdlib.send(new TdApi.GetForumTopicDefaultIcons(), (stickers, error) -> runOnUiThreadOptional(() -> {
      if (stickers != null) {
        ArrayList<TGStickerObj> items = new ArrayList<>(stickers.stickers.length);
        for (TdApi.Sticker sticker : stickers.stickers) {
          if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji) {
            icons.add(sticker);
            items.add(newGridSticker(sticker));
          }
        }
        defaultItems = items.toArray(new TGStickerObj[0]);
        if (preview != null) {
          preview.bindPreview(this, null);
        }
      }
      onFirstLoaded();
    }));
    tdlib.send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeCustomEmoji()), (sets, error) -> runOnUiThreadOptional(() -> {
      loadedPacks = sets != null ? sets.sets : null;
      onFirstLoaded();
    }));
    if (extraIds.length > 0) {
      tdlib.send(new TdApi.GetCustomEmojiStickers(extraIds), (stickers, error) -> runOnUiThreadOptional(() ->
        onRecentsLoaded(stickers != null ? inOrder(extraIds, stickers.stickers) : new TdApi.Sticker[0])
      ));
    }
  }

  /**
   * The topic's own custom emoji, for every account (it may always be kept; left out later if it is a default icon),
   * then, with Premium, the custom emoji used last in messages.
   */
  private long[] extraCustomEmojiIds () {
    final List<RecentEmoji> recents = premium ? Emoji.instance().getRecents() : null;
    final int recentCount = recents != null ? recents.size() : 0;
    long[] ids = new long[Math.min(MAX_RECENT, recentCount + 1)];
    int count = 0;
    if (initialCustomEmojiId != 0) {
      ids[count++] = initialCustomEmojiId;
    }
    for (int i = 0; i < recentCount && count < ids.length; i++) {
      RecentEmoji recent = recents.get(i);
      if (!recent.isCustomEmoji() || recent.customEmojiId == 0 || indexOf(ids, count, recent.customEmojiId) != -1) {
        continue;
      }
      ids[count++] = recent.customEmojiId;
    }
    recentOnlyCount = initialCustomEmojiId != 0 ? count - 1 : count;
    return count == ids.length ? ids : Arrays.copyOf(ids, count);
  }

  /** The recent section will most likely show: recently used emoji were asked for, or the topic's own non-default one. */
  private boolean expectsRecents () {
    return recentsPending && (recentOnlyCount > 0 || (initialCustomEmojiId != 0 && findIcon(initialCustomEmojiId) == null));
  }

  /** The recent section, or null when empty; the topic's own emoji is left out when it is one of the default icons. */
  @Nullable
  private Section newRecentSection (TdApi.Sticker[] stickers) {
    final boolean ownIsDefault = initialCustomEmojiId != 0 && findIcon(initialCustomEmojiId) != null;
    ArrayList<TGStickerObj> list = new ArrayList<>(stickers.length);
    for (TdApi.Sticker sticker : stickers) {
      if (ownIsDefault && emojiIdOf(sticker) == initialCustomEmojiId) {
        continue; // it shows among the default icons
      }
      list.add(newGridSticker(sticker));
    }
    if (list.isEmpty()) {
      return null;
    }
    TGStickerObj[] items = list.toArray(new TGStickerObj[0]);
    Section section = new Section(SECTION_RECENT, null, null);
    section.items = items;
    section.cellCount = items.length;
    section.loadState = LOAD_DONE;
    section.currentOnly = recentOnlyCount == 0;
    return section;
  }

  private static int indexOf (long[] ids, int count, long id) {
    for (int i = 0; i < count; i++) {
      if (ids[i] == id) {
        return i;
      }
    }
    return -1;
  }

  /** GetCustomEmojiStickers answers in any order: back in the order of {@code ids}, unknown ones left out. */
  private static TdApi.Sticker[] inOrder (long[] ids, TdApi.Sticker[] stickers) {
    LongSparseArray<TdApi.Sticker> byId = new LongSparseArray<>(stickers.length);
    for (TdApi.Sticker sticker : stickers) {
      long id = emojiIdOf(sticker);
      if (id != 0) {
        byId.put(id, sticker);
      }
    }
    ArrayList<TdApi.Sticker> result = new ArrayList<>(ids.length);
    for (long id : ids) {
      TdApi.Sticker sticker = byId.get(id);
      if (sticker != null) {
        result.add(sticker);
      }
    }
    return result.toArray(new TdApi.Sticker[0]);
  }

  /** The default icons and the installed packs are known: they go in under the header at once, the strip's slot too. */
  private void onFirstLoaded () {
    if (--pendingFirst > 0) {
      return;
    }
    firstLoaded = true;
    final TdApi.StickerSetInfo[] packs = loadedPacks;
    final TdApi.Sticker[] recents = loadedRecents;
    loadedPacks = null;
    loadedRecents = null;
    Section section = defaultSection = new Section(SECTION_DEFAULT, null, null);
    section.cellCount = 1 + defaultItems.length; // "no icon", then the default icons
    section.loadState = LOAD_DONE;
    sections.add(section);
    if (recents != null) {
      Section recent = newRecentSection(recents); // they answered first
      if (recent != null) {
        sections.add(recent);
      }
    }
    if (packs != null) {
      for (TdApi.StickerSetInfo info : packs) {
        if (info.size <= 0) {
          continue;
        }
        Section pack = new Section(SECTION_PACK, info, new TGStickerSetInfo(tdlib, info));
        pack.items = new TGStickerObj[info.size]; // filled from the covers on bind, all of it once the pack loads
        pack.cellCount = info.size;
        sections.add(pack);
      }
    }
    // The slot is kept for recent emoji still on their way, so they won't move the default icons when they come
    withStrip = sections.size() > 1 || expectsRecents();
    layoutSections();
    // One insert under the header, not a full reload: the name field keeps its focus and the keyboard
    adapter.notifyItemRangeInserted(1, itemCount - 1);
    if (tabsAdapter != null) {
      tabsAdapter.notifyDataSetChanged();
    }
    postSyncStrip();
  }

  /** The topic's own emoji and the recent ones (in that order, unknown ones left out). */
  private void onRecentsLoaded (TdApi.Sticker[] stickers) {
    recentsPending = false;
    if (!firstLoaded) {
      loadedRecents = stickers; // they go in with the default icons
      return;
    }
    final Section recent = newRecentSection(stickers);
    final boolean hadStrip = withStrip;
    if (recent == null) {
      if (hadStrip && sections.size() <= 1) {
        // The slot was kept for them and none came
        withStrip = false;
        layoutSections();
        adapter.notifyItemRemoved(STRIP_POSITION);
        if (tabsAdapter != null) {
          tabsAdapter.notifyDataSetChanged();
        }
        postSyncStrip();
      }
      return;
    }
    // Right after the default icons: below the rows on screen, which stay where they are
    sections.add(1, recent);
    withStrip = true;
    layoutSections();
    if (!hadStrip) {
      adapter.notifyItemInserted(STRIP_POSITION);
    }
    adapter.notifyItemRangeInserted(recent.titlePosition, 1 + recent.cellCount);
    // The packs' tabs move one along, the selected one with them
    if (selectedTab >= 1) {
      selectedTab++;
    }
    if (pinnedTab >= 1) {
      pinnedTab++;
    }
    if (tabsAdapter != null) {
      if (hadStrip) {
        tabsAdapter.notifyItemInserted(1);
      } else {
        tabsAdapter.notifyDataSetChanged();
      }
    }
    postSyncStrip();
  }

  private void postSyncStrip () {
    CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView != null) {
      recyclerView.post(this::syncStrip);
    }
  }

  /** A pack's section is on screen: its emoji are asked for, the newest on screen first, a few at a time. */
  private void requestPack (Section section) {
    if (section.kind != SECTION_PACK || section.loadState != LOAD_NONE || section.info == null) {
      return;
    }
    if (section.retryAt != 0 && SystemClock.uptimeMillis() < section.retryAt) {
      return; // it failed a moment ago: asked for again once the wait is over (see onPackLoaded)
    }
    section.loadState = LOAD_QUEUED;
    packQueue.addFirst(section);
    pumpPacks();
  }

  private void pumpPacks () {
    while (packRequests < MAX_PACK_REQUESTS && !packQueue.isEmpty()) {
      final Section section = packQueue.pollFirst();
      if (section == null || section.loadState != LOAD_QUEUED || section.info == null) {
        continue;
      }
      if (!isNearViewport(section)) {
        section.loadState = LOAD_NONE; // flung past: asked for again when it shows
        continue;
      }
      section.loadState = LOAD_LOADING;
      packRequests++;
      final long setId = section.info.id;
      tdlib.send(new TdApi.GetStickerSet(setId), (set, error) -> runOnUiThreadOptional(() -> {
        packRequests--;
        onPackLoaded(section, set, error);
        pumpPacks();
      }));
    }
  }

  private boolean isNearViewport (Section section) {
    if (gridManager == null) {
      return true;
    }
    int first = gridManager.findFirstVisibleItemPosition(), last = gridManager.findLastVisibleItemPosition();
    if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
      return true;
    }
    int margin = gridManager.getSpanCount() * 16; // about a screen of rows either way
    return section.firstCell + section.cellCount > first - margin && section.titlePosition < last + margin;
  }

  private void onPackLoaded (final Section section, @Nullable TdApi.StickerSet set, @Nullable TdApi.Error error) {
    if (section.loadState != LOAD_LOADING) {
      return;
    }
    if (set == null) {
      // A flood wait or a lost connection: asked for again a little later, by the next bind or by itself while it is
      // still near the screen. A pack that is gone (400), or one that keeps failing, keeps its covers.
      if ((error == null || error.code != 400) && ++section.failures <= MAX_PACK_RETRIES) {
        final long fallback = PACK_RETRY_DELAY * section.failures;
        final long delay = error != null ? XgramForums.retryAfterMs(error, fallback) : fallback;
        section.loadState = LOAD_NONE;
        section.retryAt = SystemClock.uptimeMillis() + delay;
        runOnUiThreadOptional(() -> {
          if (section.loadState == LOAD_NONE && isNearViewport(section)) {
            requestPack(section);
          }
        }, null, delay);
      } else {
        section.loadState = LOAD_DONE;
      }
      return;
    }
    section.loadState = LOAD_DONE;
    TGStickerObj[] items = new TGStickerObj[set.stickers.length];
    for (int i = 0; i < items.length; i++) {
      items[i] = newGridSticker(set.stickers[i]);
    }
    final int oldCount = section.cellCount, newCount = items.length, firstCell = section.firstCell;
    section.items = items;
    section.cellCount = newCount;
    if (newCount != oldCount) {
      layoutSections(); // the pack changed since it was listed: the sections after it move
    }
    if (Math.min(oldCount, newCount) > 0) {
      adapter.notifyItemRangeChanged(firstCell, Math.min(oldCount, newCount));
    }
    if (newCount > oldCount) {
      adapter.notifyItemRangeInserted(firstCell + oldCount, newCount - oldCount);
    } else if (newCount < oldCount) {
      adapter.notifyItemRangeRemoved(firstCell + newCount, oldCount - newCount);
    }
  }

  // Selection

  private void onCellClicked (View view) {
    Object tag = view.getTag();
    if (tag == null) {
      onIconSelected(0, null); // "no icon"
      return;
    }
    if (!(tag instanceof TGStickerObj)) {
      return; // its pack is still loading
    }
    TdApi.Sticker sticker = ((TGStickerObj) tag).getSticker();
    long emojiId = emojiIdOf(sticker);
    if (emojiId == 0) {
      return;
    }
    // The topic's own emoji can always be picked again: keeping it sends no icon (see onDoneClick)
    if (emojiId != initialCustomEmojiId && findIcon(emojiId) == null && !tdlib.hasPremium()) {
      showPremiumHint(view);
      return;
    }
    onIconSelected(emojiId, sticker);
  }

  private void showPremiumHint (View anchor) {
    context().tooltipManager().builder(anchor).controller(this).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.XgramTopicIconsPremium).hideDelayed();
  }

  private void onIconSelected (long customEmojiId, @Nullable TdApi.Sticker sticker) {
    this.customEmojiId = customEmojiId;
    if (preview != null) {
      preview.bindPreview(this, sticker);
    }
    if (itemCount > 1) {
      adapter.notifyItemRangeChanged(1, itemCount - 1); // the emoji may show in more than one section
    }
  }

  private void onPreviewClicked () {
    // The server keeps a topic's color forever; only new topics can pick one.
    if (!isCreating() || customEmojiId != 0) {
      return;
    }
    int[] colors = ForumTopicIconDrawable.serverColors();
    int index = 0;
    for (int i = 0; i < colors.length; i++) {
      if (colors[i] == color) {
        index = i;
        break;
      }
    }
    color = colors[(index + 1) % colors.length];
    preview.bindPreview(this, null);
    notifyNoIconChanged();
    if (colorRow != null) {
      colorRow.onColorChanged(index); // M3E round 6: the ring moves to the new swatch
    }
  }

  @Override
  protected void onDoneClick () {
    if (isSaving) {
      return;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      UI.forceVibrateError(getValue());
      return;
    }
    TdApi.Chat chat = getArgumentsStrict().chat;
    TdApi.ForumTopic topic = getArgumentsStrict().topic;
    boolean iconChanged = topic == null || (!isGeneral() && (topic.info.icon == null || topic.info.icon.customEmojiId != customEmojiId));
    // Only Premium accounts may set a custom emoji that is not one of the default topic icons
    if (iconChanged && customEmojiId != 0 && findIcon(customEmojiId) == null && !tdlib.hasPremium()) {
      showPremiumHint(getDoneButton());
      return;
    }
    isSaving = true;
    setDoneProgress(true);
    if (topic == null) {
      tdlib.send(new TdApi.CreateForumTopic(chat.id, trimmed, false, new TdApi.ForumTopicIcon(color, customEmojiId)), (info, error) -> runOnUiThreadOptional(() -> {
        isSaving = false;
        setDoneProgress(false);
        if (error != null) {
          UI.showError(error);
        } else {
          // MessagesController removes this editor from the stack once the topic is shown.
          XgramForums.openTopic(this, chat, info.forumTopicId, 0);
        }
      }));
    } else {
      tdlib.send(new TdApi.EditForumTopic(chat.id, topic.info.forumTopicId, trimmed, iconChanged, customEmojiId), (ok, error) -> runOnUiThreadOptional(() -> {
        isSaving = false;
        setDoneProgress(false);
        if (error != null) {
          UI.showError(error);
        } else {
          navigateBack();
        }
      }));
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (preview != null) {
      preview.performDestroy();
    }
    for (int i = 0; i < destroyables.size(); i++) {
      destroyables.get(i).performDestroy();
    }
    destroyables.clear();
    packQueue.clear();
  }

  // Tab strip

  private int stripSlotHeight () {
    return Screen.dp(expressive ? STRIP_HEIGHT + STRIP_GAP * 2f : STRIP_HEIGHT);
  }

  /**
   * The strip floats over the page above the list: on its slot (an empty row under the name) while that is on screen,
   * pinned at the list's top once the slot scrolls past it. Built once, shown once the list has more than the default
   * icons.
   */
  private void createStrip (Context context, FrameLayout wrap) {
    StripLayout strip = this.strip = new StripLayout(context);
    strip.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, stripSlotHeight(), Gravity.TOP));
    strip.setVisibility(View.INVISIBLE);

    TabsAdapter tabsAdapter = this.tabsAdapter = new TabsAdapter();
    LinearLayoutManager tabsManager = this.tabsManager = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, Lang.rtl());
    RecyclerView tabsView = this.tabsView = new RecyclerView(context);
    tabsView.setLayoutManager(tabsManager);
    tabsView.setItemAnimator(null);
    tabsView.setHasFixedSize(true);
    tabsView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    tabsView.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
    tabsView.setClipToPadding(false);
    tabsView.setAdapter(tabsAdapter);
    FrameLayout.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(STRIP_HEIGHT), Gravity.TOP);
    if (expressive) {
      // M3E: the tabs in a surfaceBright container with list corners, 16dp from the edges as the cards
      params.leftMargin = params.rightMargin = Screen.dp(XgramExpressive.LIST_INSET);
      params.topMargin = Screen.dp(STRIP_GAP);
      XgramExpressive.MorphDrawable container = new XgramExpressive.MorphDrawable(0, XgramExpressive.LIST_OUTER_CORNER, Float.NaN);
      container.setColorRole(XgramExpressive.SURFACE_BRIGHT);
      tabsView.setBackground(container);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        tabsView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        tabsView.setClipToOutline(true);
      }
    }
    strip.addView(tabsView, params);
    addThemeInvalidateListener(strip);
    addThemeInvalidateListener(tabsView);
    wrap.addView(strip);
  }

  /** Moves the strip onto its slot or pins it at the top, and selects the tab of the section under it. */
  private void syncStrip () {
    final StripLayout strip = this.strip;
    final CustomRecyclerView recyclerView = getRecyclerView();
    if (strip == null || recyclerView == null || gridManager == null) {
      return;
    }
    if (!hasStrip()) {
      if (strip.getVisibility() != View.INVISIBLE) {
        strip.setVisibility(View.INVISIBLE);
      }
      return;
    }
    final int minTop = recyclerView.getPaddingTop();
    final RecyclerView.ViewHolder slot = recyclerView.findViewHolderForAdapterPosition(STRIP_POSITION);
    final float top;
    final boolean stuck;
    if (slot != null) {
      float slotTop = slot.itemView.getTop() + slot.itemView.getTranslationY();
      stuck = slotTop < minTop;
      top = Math.max(minTop, slotTop);
    } else {
      int first = gridManager.findFirstVisibleItemPosition();
      if (first == RecyclerView.NO_POSITION || first < STRIP_POSITION) {
        // Not laid out yet, or the slot is below the screen
        if (strip.getVisibility() != View.INVISIBLE) {
          strip.setVisibility(View.INVISIBLE);
        }
        return;
      }
      stuck = true;
      top = minTop;
    }
    strip.setTranslationY(recyclerView.getTop() + recyclerView.getTranslationY() + top);
    strip.setStuck(stuck);
    if (strip.getVisibility() != View.VISIBLE) {
      strip.setVisibility(View.VISIBLE);
    }
    updateTabFromScroll(recyclerView, top + stripSlotHeight());
  }

  private void updateTabFromScroll (RecyclerView recyclerView, float y) {
    if (pinnedTab >= 0) {
      setSelectedTab(pinnedTab);
      return;
    }
    View child = recyclerView.findChildViewUnder(recyclerView.getWidth() / 2f, y + 1f);
    if (child == null) {
      return; // between two sections: the last one stays
    }
    int position = recyclerView.getChildAdapterPosition(child);
    if (position != RecyclerView.NO_POSITION) {
      setSelectedTab(Math.max(0, sectionIndexAt(position)));
    }
  }

  private void setSelectedTab (int index) {
    if (index == selectedTab || index < 0 || index >= sections.size()) {
      return;
    }
    int previous = selectedTab;
    selectedTab = index;
    if (tabsAdapter == null || tabsView == null || tabsManager == null) {
      return;
    }
    int count = tabsAdapter.getItemCount();
    if (previous >= 0 && previous < count) {
      tabsAdapter.notifyItemChanged(previous, TAB_SELECTION);
    }
    if (index < count) {
      tabsAdapter.notifyItemChanged(index, TAB_SELECTION);
      if (tabsView.getWidth() > 0) {
        int first = tabsManager.findFirstCompletelyVisibleItemPosition(), last = tabsManager.findLastCompletelyVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || index < first || index > last) {
          tabsView.smoothScrollToPosition(index);
        }
      }
    }
  }

  /** A tab: its section's title right under the pinned strip (a short way on the list's spring, a long way at once). */
  private void scrollToSection (int index) {
    final CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView == null || gridManager == null || index < 0 || index >= sections.size()) {
      return;
    }
    pinnedTab = index;
    setSelectedTab(index);
    if (index == 0 && strip != null && !strip.stuck) {
      return; // the default icons already show under the strip, with the name above them
    }
    final int target = sections.get(index).titlePosition;
    recyclerView.stopScroll();
    View view = gridManager.findViewByPosition(target);
    if (view != null) {
      int dy = gridManager.getDecoratedTop(view) - recyclerView.getPaddingTop() - stripSlotHeight();
      if (dy != 0) {
        XgramMotion.smoothScrollByOr(recyclerView, 0, dy);
      }
    } else {
      gridManager.scrollToPositionWithOffset(target, stripSlotHeight());
    }
  }

  // Views

  /**
   * Topic icon: the colored letter bubble or a custom emoji. The cell keeps one image and one animation receiver for
   * its whole life; the emoji only animates while the cell is attached, and a recycled cell lets go of it.
   */
  private static final class IconView extends View implements Destroyable {
    private final Tdlib tdlib;
    private final boolean expressive, squareCell;
    private final float iconFraction;
    private final ImageReceiver image;
    private final GifReceiver gif;
    /** M3E: the round state layer, sized to the cell (at most 48dp). */
    @Nullable XgramExpressive.MorphDrawable pressMorph;
    @Nullable private ForumTopicIconDrawable bubble;
    private int bubbleColor;
    @Nullable private String bubbleName;
    @Nullable private TGStickerObj sticker;
    private boolean needsTint;
    /** The preview: the emoji asked for (it may still be loading). */
    private long previewEmojiId;
    private boolean isSelected;
    /** A pack's emoji without Premium: dimmed, with a lock in its corner. */
    private boolean locked;
    @Nullable private Drawable lock;
    @Nullable private Paint lockPaint;
    private int lockColor;
    private int iconLeft, iconTop, iconSize;
    private Path selectedShape;
    private Matrix selectedMatrix;
    private float selectedShapeSize, selectedShapeX, selectedShapeY;

    IconView (Context context, Tdlib tdlib, boolean expressive, float iconFraction, boolean squareCell) {
      super(context);
      this.tdlib = tdlib;
      this.expressive = expressive;
      this.iconFraction = iconFraction;
      this.squareCell = squareCell;
      this.image = new ImageReceiver(this, 0);
      this.gif = new GifReceiver(this);
    }

    void setBubble (int color, String name) {
      if (bubble != null && bubbleColor == color && TextUtils.equals(bubbleName, name)) {
        return;
      }
      bubbleColor = color;
      bubbleName = name;
      bubble = new ForumTopicIconDrawable(false, color, name);
      invalidate();
    }

    void clearBubble () {
      if (bubble != null) {
        bubble = null;
        bubbleName = null;
        invalidate();
      }
    }

    /** null: no emoji (the bubble, if any, shows instead). */
    void setSticker (@Nullable TGStickerObj sticker) {
      if (this.sticker == sticker) {
        return;
      }
      this.sticker = sticker;
      final boolean empty = sticker == null || sticker.isEmpty();
      image.requestFile(empty ? null : sticker.getImage());
      gif.requestFile(empty ? null : sticker.getPreviewAnimation());
      needsTint = !empty && sticker.needThemedColorFilter();
      TdApi.Sticker raw = empty ? null : sticker.getSticker();
      setContentDescription(raw != null ? raw.emoji : null);
      invalidate();
    }

    /** The page's icon: the bubble in the page's color and name, or its custom emoji ({@code known} when at hand). */
    void bindPreview (ForumTopicEditController c, @Nullable TdApi.Sticker known) {
      setBubble(c.color, c.name);
      final long emojiId = c.customEmojiId;
      if (emojiId == previewEmojiId && (emojiId == 0 || sticker != null)) {
        return;
      }
      previewEmojiId = emojiId;
      if (emojiId == 0) {
        setSticker(null);
        return;
      }
      TdApi.Sticker found = known != null ? known : c.findIcon(emojiId);
      if (found != null) {
        setSticker(new TGStickerObj(tdlib, found, null, found.fullType));
        return;
      }
      setSticker(null); // the bubble until the emoji is known
      tdlib.emoji().findOrRequest(emojiId, (entry, inPlace) -> {
        Runnable act = () -> {
          if (previewEmojiId == emojiId && sticker == null && entry.value != null) {
            setSticker(new TGStickerObj(tdlib, entry.value, null, entry.value.fullType));
          }
        };
        if (inPlace) {
          act.run();
        } else {
          tdlib.ui().post(act);
        }
      });
    }

    void setSelectedIcon (boolean isSelected) {
      if (this.isSelected != isSelected) {
        this.isSelected = isSelected;
        invalidate();
      }
    }

    void setLocked (boolean locked) {
      if (this.locked != locked) {
        this.locked = locked;
        if (locked && lock == null) {
          lock = Drawables.get(getResources(), XgramIconPack.map(R.drawable.baseline_lock_16));
        }
        invalidate();
      }
    }

    /** The lock: on a disc of the color behind the cell, at the emoji's bottom end corner. */
    private void drawLock (Canvas c) {
      if (lock == null) {
        return;
      }
      final float radius = Screen.dp(8f), inset = Screen.dp(4f);
      final float cx = Lang.rtl() ? iconLeft + inset : iconLeft + iconSize - inset;
      final float cy = iconTop + iconSize - inset;
      final int behind = expressive ? XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT) : Theme.getColor(ColorId.background);
      c.drawCircle(cx, cy, radius, Paints.fillingPaint(behind));
      final int color = expressive ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.icon);
      if (lockPaint == null || lockColor != color) {
        lockPaint = Paints.createPorterDuffPaint(lockPaint, lockColor = color);
      }
      final int save = c.save();
      c.scale(.75f, .75f, cx, cy); // 12dp in a 16dp disc
      Drawables.drawCentered(c, lock, cx, cy, lockPaint);
      c.restoreToCount(save);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (!squareCell || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        return;
      }
      int width = MeasureSpec.getSize(widthMeasureSpec);
      setMeasuredDimension(width, Math.max(Screen.dp(MIN_CELL), Math.min(Screen.dp(MAX_ROW), width)));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      int size = Math.min(w, h);
      iconSize = Math.round(size * iconFraction);
      iconLeft = (w - iconSize) / 2;
      iconTop = (h - iconSize) / 2;
      image.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
      gif.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
      if (pressMorph != null) {
        float box = Math.min(48f, Screen.px(size));
        pressMorph.setSize(box, box, Gravity.CENTER);
      }
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      image.attach();
      gif.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      image.detach();
      gif.detach();
    }

    @Override
    public void performDestroy () {
      image.destroy();
      gif.destroy();
    }

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      final int size = Math.min(width, height);
      if (isSelected && expressive) {
        // M3E: the selected icon sits on a tonal cookie in secondary container instead of a ring
        float shapeSize = size - Screen.dp(6f), x = width / 2f, y = height / 2f;
        if (selectedShape == null) {
          selectedShape = new Path();
          selectedMatrix = new Matrix();
        }
        if (selectedShapeSize != shapeSize || selectedShapeX != x || selectedShapeY != y) {
          selectedShapeSize = shapeSize;
          selectedShapeX = x;
          selectedShapeY = y;
          XgramShapes.toPath(XgramShapes.COOKIE_12, selectedShape);
          XgramShapes.fit(selectedShape, x, y, shapeSize, 0f, selectedMatrix);
        }
        c.drawPath(selectedShape, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER)));
      } else if (isSelected) {
        Paint paint = Paints.getProgressPaint(Theme.getColor(ColorId.iconActive), Screen.dp(2f));
        c.drawCircle(width / 2f, height / 2f, size / 2f - Screen.dp(2.5f), paint);
      }
      if (sticker != null) {
        if (needsTint) {
          // An emoji drawn in the text color (as in messages), so a white glyph stays visible on light themes
          int tint = expressive ? XgramExpressive.color(XgramExpressive.ON_SURFACE) : Theme.getColor(ColorId.text);
          image.setPorterDuffColorFilter(tint);
          gif.setPorterDuffColorFilter(tint);
        } else {
          image.disablePorterDuffColorFilter();
          gif.disablePorterDuffColorFilter();
        }
        if (locked) {
          image.setPaintAlpha(LOCKED_ALPHA);
          gif.setPaintAlpha(LOCKED_ALPHA);
        }
        if (gif.needPlaceholder()) {
          image.draw(c);
        }
        gif.draw(c);
        if (locked) {
          image.restorePaintAlpha();
          gif.restorePaintAlpha();
          drawLock(c);
        }
      } else if (bubble != null) {
        bubble.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
        bubble.draw(c);
      }
    }
  }

  /** A section title; a pack's carries a lock without Telegram Premium. */
  private static final class TitleView extends TextView {
    @Nullable private Drawable lock;
    private boolean locked;
    private int lockColor;

    TitleView (Context context) {
      super(context);
    }

    void setLocked (boolean locked) {
      if (locked && lock == null) {
        lock = Drawables.get(getResources(), XgramIconPack.map(R.drawable.baseline_lock_16));
      }
      if (locked && lock != null) {
        int color = getCurrentTextColor();
        if (lockColor != color) {
          lockColor = color;
          lock.setColorFilter(Paints.createColorFilter(color));
        }
      }
      if (this.locked != locked) {
        this.locked = locked;
        Drawable start = locked ? lock : null;
        if (Lang.rtl()) {
          setCompoundDrawablesWithIntrinsicBounds(null, null, start, null);
        } else {
          setCompoundDrawablesWithIntrinsicBounds(start, null, null, null);
        }
        setCompoundDrawablePadding(locked ? Screen.dp(6f) : 0);
      }
    }
  }

  /**
   * The tab of the default icons or the recent ones: a glyph on the selected tab's circle, as the emoji keyboard's
   * pack tabs (StickerSectionView) beside it.
   */
  private static final class GlyphTab extends View implements FactorAnimator.Target {
    private final boolean expressive;
    @Nullable private Drawable icon;
    private int iconRes;
    private float factor;
    @Nullable private FactorAnimator animator;
    @Nullable private Paint restPaint, activePaint;
    private int restColor, activeColor;

    GlyphTab (Context context, boolean expressive) {
      super(context);
      this.expressive = expressive;
      if (expressive) {
        // M3E: presses in the 36dp circle the selected tab sits on
        XgramPillStateLayer.attach(this, new XgramPillStateLayer.Shape() {
          @Override
          public float getStateLayerShape (@NonNull View view, @NonNull RectF outRect) {
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            final float radius = Math.min(Screen.dp(18f), Math.min(getWidth(), getHeight()) / 2f);
            outRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            return XgramPillStateLayer.CORNER_FULL;
          }

          @Override
          public boolean isStateLayerOnIndicator (@NonNull View view) {
            return factor >= .5f;
          }
        });
      }
    }

    void setIcon (@DrawableRes int res) {
      int mapped = XgramIconPack.map(res);
      if (iconRes != mapped) {
        iconRes = mapped;
        icon = Drawables.get(getResources(), mapped);
        invalidate();
      }
    }

    void setSelectionFactor (float factor, boolean animated) {
      if (animated && this.factor != factor) {
        if (animator == null) {
          animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, this.factor);
        }
        animator.animateTo(factor);
      } else {
        if (animator != null) {
          animator.forceFactor(factor);
        }
        setFactor(factor);
      }
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
      final float cx = getWidth() / 2f, cy = getHeight() / 2f;
      if (factor > 0f) {
        final int fill = expressive ?
          XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), factor) :
          ColorUtils.alphaColor(factor, Theme.chatSelectionColor());
        c.drawCircle(cx, cy, Screen.dp(18f) - Screen.dp(4f) * (1f - factor), Paints.fillingPaint(fill));
      }
      final int rest = expressive ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.icon);
      final int active = expressive ? XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER) : Theme.getColor(ColorId.iconActive);
      if (restPaint == null || restColor != rest) {
        restPaint = Paints.createPorterDuffPaint(restPaint, restColor = rest);
      }
      if (activePaint == null || activeColor != active) {
        activePaint = Paints.createPorterDuffPaint(activePaint, activeColor = active);
      }
      // The rest color fades into the active one (two passes, no color per frame)
      if (factor < 1f) {
        restPaint.setAlpha(Math.round((rest >>> 24) * (1f - factor)));
        Drawables.drawCentered(c, icon, cx, cy, restPaint);
      }
      if (factor > 0f) {
        activePaint.setAlpha(Math.round((active >>> 24) * factor));
        Drawables.drawCentered(c, icon, cx, cy, activePaint);
      }
    }
  }

  /**
   * Holds the tab strip. Classic look: a filling bar with a separator, as the emoji keyboard's. Expressive: the page's
   * background only while pinned, so the rows scrolling under the strip disappear behind it.
   *
   * <p>On its slot the strip is a row of the page: a vertical drag that starts on it scrolls the list (the tabs keep
   * taps and horizontal drags). Pinned, it stays put under a drag, as a pinned header.
   */
  private final class StripLayout extends FrameLayout {
    private final int touchSlop;
    private boolean stuck;
    /** A gesture that started on the unpinned strip, not yet known to be a tap, a horizontal or a vertical drag. */
    private boolean tracking;
    /** The gesture goes to the list (a vertical drag that started on the unpinned strip). */
    private boolean forwarding;
    private int trackedPointerId;
    private long downTime;
    /** Where the finger went down, in the page's coordinates (the strip moves as the list scrolls). */
    private float downX, downY;

    StripLayout (Context context) {
      super(context);
      touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setStuck (boolean stuck) {
      if (this.stuck != stuck) {
        this.stuck = stuck;
        invalidate();
      }
    }

    /** From this view's coordinates to the page's (its parent's, which also holds the list). */
    private float pageX (float x) {
      return x + getLeft() + getTranslationX();
    }

    private float pageY (float y) {
      return y + getTop() + getTranslationY();
    }

    @Nullable
    private CustomRecyclerView forwardTarget () {
      CustomRecyclerView list = getRecyclerView();
      return list != null && list.getParent() == getParent() ? list : null;
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          forwarding = false;
          tracking = !stuck;
          trackedPointerId = e.getPointerId(0);
          downTime = e.getDownTime();
          downX = pageX(e.getX());
          downY = pageY(e.getY());
          CustomRecyclerView list = tracking ? forwardTarget() : null;
          if (list != null && list.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING) {
            list.stopScroll(); // a touch on a row of a flung list stops it
          }
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!tracking) {
            break;
          }
          int index = e.findPointerIndex(trackedPointerId);
          if (index < 0) {
            tracking = false;
            break;
          }
          float dx = pageX(e.getX(index)) - downX, dy = pageY(e.getY(index)) - downY;
          if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
            tracking = false;
            if (startForwarding(e, index)) {
              return true; // the tab under the finger gets a cancel
            }
          }
          break;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          tracking = false;
          break;
      }
      return false;
    }

    /** The list never saw the finger go down: it gets a down where it did, then this move, and drags from there. */
    private boolean startForwarding (MotionEvent e, int index) {
      final CustomRecyclerView list = forwardTarget();
      if (list == null) {
        return false;
      }
      MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties()};
      properties[0].id = trackedPointerId;
      properties[0].toolType = e.getToolType(index);
      MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
      coords[0].x = downX - list.getLeft() - list.getTranslationX();
      coords[0].y = downY - list.getTop() - list.getTranslationY();
      coords[0].pressure = 1f;
      coords[0].size = 1f;
      MotionEvent down = MotionEvent.obtain(downTime, e.getEventTime(), MotionEvent.ACTION_DOWN, 1, properties, coords,
        e.getMetaState(), e.getButtonState(), 1f, 1f, e.getDeviceId(), 0, e.getSource(), e.getFlags());
      list.dispatchTouchEvent(down);
      down.recycle();
      forwarding = true;
      forward(list, e);
      if (getParent() != null) {
        getParent().requestDisallowInterceptTouchEvent(true); // the page's parents leave this drag alone
      }
      return true;
    }

    private void forward (CustomRecyclerView list, MotionEvent e) {
      MotionEvent copy = MotionEvent.obtain(e);
      copy.offsetLocation(pageX(0f) - list.getLeft() - list.getTranslationX(), pageY(0f) - list.getTop() - list.getTranslationY());
      list.dispatchTouchEvent(copy);
      copy.recycle();
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (forwarding) {
        CustomRecyclerView list = forwardTarget();
        if (list != null) {
          forward(list, e);
        }
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
          forwarding = false;
        }
        return true;
      }
      // Pinned, the strip covers rows of the list: a touch on its gaps must not reach an emoji hidden under it
      return stuck || super.onTouchEvent(e);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (!expressive) {
        c.drawColor(Theme.getColor(ColorId.filling));
        int separator = Screen.separatorSize();
        c.drawRect(0, getHeight() - separator, getWidth(), getHeight(), Paints.fillingPaint(Theme.getColor(ColorId.separator)));
      } else if (stuck) {
        c.drawColor(Theme.getColor(ColorId.background));
      }
      super.dispatchDraw(c);
    }
  }

  private final class TabsAdapter extends RecyclerView.Adapter<Holder> {
    @Override
    public int getItemCount () {
      return hasStrip() ? sections.size() : 0;
    }

    @Override
    public int getItemViewType (int position) {
      return sections.get(position).kind == SECTION_PACK ? TAB_COVER : TAB_GLYPH;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      final View view;
      if (viewType == TAB_COVER) {
        // Telegram X's emoji keyboard tab: the pack's cover, M3E secondaryContainer when selected
        StickerSectionView cover = new StickerSectionView(parent.getContext());
        destroyables.add(cover);
        view = cover;
      } else {
        view = new GlyphTab(parent.getContext(), expressive);
      }
      if (!expressive) {
        RippleSupport.setTransparentSelector(view);
      }
      view.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(TAB_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));
      final Holder holder = new Holder(view);
      view.setOnClickListener(v -> {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
          scrollToSection(position);
        }
      });
      return holder;
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position) {
      Section section = sections.get(position);
      float factor = position == selectedTab ? 1f : 0f;
      if (holder.itemView instanceof StickerSectionView) {
        StickerSectionView view = (StickerSectionView) holder.itemView;
        if (section.cover != null) {
          view.setStickerSet(section.cover);
        }
        view.setSelectionFactor(factor, false);
        view.setContentDescription(section.info != null ? section.info.title : null);
      } else if (holder.itemView instanceof GlyphTab) {
        GlyphTab view = (GlyphTab) holder.itemView;
        @DrawableRes final int icon;
        @StringRes final int label;
        if (section.kind != SECTION_RECENT) {
          icon = R.drawable.baseline_forum_24;
          label = R.string.XgramTopicIcon;
        } else if (section.currentOnly) {
          icon = R.drawable.baseline_check_circle_24;
          label = R.string.XgramTopicIconCurrent;
        } else {
          icon = R.drawable.baseline_access_time_24;
          label = R.string.XgramTopicIconsRecent;
        }
        view.setIcon(icon);
        view.setSelectionFactor(factor, false);
        view.setContentDescription(Lang.getString(label));
      }
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
      if (!payloads.contains(TAB_SELECTION)) {
        onBindViewHolder(holder, position);
        return;
      }
      float factor = position == selectedTab ? 1f : 0f;
      if (holder.itemView instanceof StickerSectionView) {
        ((StickerSectionView) holder.itemView).setSelectionFactor(factor, true);
      } else if (holder.itemView instanceof GlyphTab) {
        ((GlyphTab) holder.itemView).setSelectionFactor(factor, true);
      }
    }

    @Override
    public void onViewAttachedToWindow (@NonNull Holder holder) {
      if (holder.itemView instanceof StickerSectionView) {
        ((StickerSectionView) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull Holder holder) {
      if (holder.itemView instanceof StickerSectionView) {
        ((StickerSectionView) holder.itemView).detach();
      }
    }
  }

  /**
   * M3E round 6: the six server colors of a new topic as 40dp swatches spread over the card; the chosen one has a 2dp
   * ring in onSurface and a check, which spring in (SPATIAL_FAST) as the old ring fades. 48dp touch slots.
   */
  private final class ColorRow extends View {
    private static final float SWATCH = 40f, RING_GAP = 3f, RING = 2f, SIDE = 16f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final XgramExpressive.Spring select;
    private int previousIndex = -1;
    @Nullable private Drawable check;

    ColorRow (Context context) {
      super(context);
      ring.setStyle(Paint.Style.STROKE);
      ring.setStrokeWidth(Screen.dp(RING));
      select = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 1f, (s, value) -> invalidate());
      setClickable(true);
      setContentDescription(Lang.getString(R.string.XgramR6B2TopicColor));
    }

    /** The color changed elsewhere (a tap on the preview): the ring springs from {@code fromIndex} to the new one. */
    void onColorChanged (int fromIndex) {
      previousIndex = fromIndex;
      select.setValue(0f);
      select.animateTo(1f);
      invalidate();
    }

    private int selectedIndex (int[] colors) {
      for (int i = 0; i < colors.length; i++) {
        if (colors[i] == color) {
          return i;
        }
      }
      return -1;
    }

    private float slotWidth (int count) {
      return count > 0 ? (getWidth() - Screen.dp(SIDE) * 2f) / count : 0f;
    }

    private float centerX (int index, int count) {
      float x = Screen.dp(SIDE) + slotWidth(count) * (index + .5f);
      return Lang.rtl() ? getWidth() - x : x;
    }

    @Override
    public boolean onTouchEvent (android.view.MotionEvent e) {
      final int[] colors = ForumTopicIconDrawable.serverColors();
      switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_DOWN:
          return true;
        case android.view.MotionEvent.ACTION_UP: {
          float slot = slotWidth(colors.length);
          if (slot <= 0f) {
            return true;
          }
          float x = Lang.rtl() ? getWidth() - e.getX() : e.getX();
          int index = (int) Math.floor((x - Screen.dp(SIDE)) / slot);
          if (index >= 0 && index < colors.length && colors[index] != color) {
            previousIndex = selectedIndex(colors);
            color = colors[index];
            select.setValue(0f);
            select.animateTo(1f);
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (preview != null) {
              preview.bindPreview(ForumTopicEditController.this, null);
            }
            notifyNoIconChanged(); // the "no icon" bubble takes the color too
          }
          return true;
        }
      }
      return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw (Canvas c) {
      final int[] colors = ForumTopicIconDrawable.serverColors();
      final int selected = selectedIndex(colors);
      final float cy = getHeight() / 2f, radius = Screen.dp(SWATCH) / 2f;
      final float value = select.getValue();
      final int ringColor = XgramExpressive.color(XgramExpressive.ON_SURFACE);
      for (int i = 0; i < colors.length; i++) {
        final int swatch = colors[i] | 0xff000000;
        final float cx = centerX(i, colors.length);
        float ringFactor = i == selected ? value : i == previousIndex ? 1f - me.vkryl.core.MathUtils.clamp(value) : 0f;
        // The chosen swatch shrinks a little inside its ring, as M3's selected color chips
        final float swatchRadius = radius - (Screen.dp(RING_GAP) + Screen.dp(RING)) * me.vkryl.core.MathUtils.clamp(ringFactor);
        fill.setColor(swatch);
        c.drawCircle(cx, cy, swatchRadius, fill);
        if (ringFactor > 0f) {
          ring.setColor(XgramExpressive.withAlpha(ringColor, me.vkryl.core.MathUtils.clamp(ringFactor)));
          c.drawCircle(cx, cy, radius - Screen.dp(RING) / 2f, ring);
        }
        if (i == selected) {
          if (check == null) {
            check = org.thunderdog.challegram.tool.Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_check_24));
          }
          if (check != null) {
            final boolean light = androidx.core.graphics.ColorUtils.calculateLuminance(swatch) > .5;
            final int checkColor = XgramExpressive.withAlpha(light ? 0xff000000 : 0xffffffff, me.vkryl.core.MathUtils.clamp(value) * (light ? .7f : 1f));
            final float scale = .6f + .4f * value;
            final int save = c.save();
            c.scale(scale, scale, cx, cy);
            org.thunderdog.challegram.tool.Drawables.drawCentered(c, check, cx, cy, Paints.getPorterDuffPaint(checkColor));
            c.restoreToCount(save);
          }
        }
      }
    }
  }

  /**
   * M3E: 8dp above the name card, and a surfaceBright container (list corners) behind each section's icons with 8dp
   * above its first and below its last row. The list's 16dp side padding insets the card, the titles and the grids alike.
   */
  private final class ExpressiveDecoration extends RecyclerView.ItemDecoration {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    // The visible part of each section's container in this frame (a few at a time; grown, never per frame)
    private int[] boxSections = new int[16];
    private float[] boxTops = new float[16], boxBottoms = new float[16];
    private boolean[] boxFirstRows = new boolean[16], boxLastRows = new boolean[16];

    private int spanCount () {
      return gridManager != null ? gridManager.getSpanCount() : MIN_SPAN;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION || position >= adapter.getItemCount()) {
        return;
      }
      int viewType = adapter.getItemViewType(position);
      if (viewType == VIEW_TYPE_HEADER) {
        outRect.top = Screen.dp(8f);
      } else if (viewType == VIEW_TYPE_ICON) {
        Section section = sectionAt(position);
        if (section == null) {
          return;
        }
        int span = spanCount();
        int row = (position - section.firstCell) / span;
        if (row == 0) {
          outRect.top = Screen.dp(GRID_PADDING);
        }
        if (row == (section.cellCount - 1) / span) {
          outRect.bottom = Screen.dp(GRID_PADDING);
        }
      }
    }

    private void ensureCapacity (int size) {
      if (size <= boxSections.length) {
        return;
      }
      int capacity = Math.max(size, boxSections.length * 2);
      boxSections = Arrays.copyOf(boxSections, capacity);
      boxTops = Arrays.copyOf(boxTops, capacity);
      boxBottoms = Arrays.copyOf(boxBottoms, capacity);
      boxFirstRows = Arrays.copyOf(boxFirstRows, capacity);
      boxLastRows = Arrays.copyOf(boxLastRows, capacity);
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      RecyclerView.LayoutManager manager = parent.getLayoutManager();
      final int itemCount = adapter.getItemCount();
      if (manager == null || sections.isEmpty()) {
        return;
      }
      final int span = spanCount();
      int boxCount = 0;
      for (int i = 0; i < parent.getChildCount(); i++) {
        View child = parent.getChildAt(i);
        int position = parent.getChildAdapterPosition(child);
        if (position == RecyclerView.NO_POSITION || position >= itemCount || adapter.getItemViewType(position) != VIEW_TYPE_ICON) {
          continue;
        }
        int sectionIndex = sectionIndexAt(position);
        if (sectionIndex < 0) {
          continue;
        }
        Section section = sections.get(sectionIndex);
        int box = -1;
        for (int j = boxCount - 1; j >= 0; j--) {
          if (boxSections[j] == sectionIndex) {
            box = j;
            break;
          }
        }
        if (box == -1) {
          ensureCapacity(boxCount + 1);
          box = boxCount++;
          boxSections[box] = sectionIndex;
          boxTops[box] = Float.MAX_VALUE;
          boxBottoms[box] = -Float.MAX_VALUE;
          boxFirstRows[box] = false;
          boxLastRows[box] = false;
        }
        int row = (position - section.firstCell) / span;
        boxFirstRows[box] |= row == 0;
        boxLastRows[box] |= row == (section.cellCount - 1) / span;
        boxTops[box] = Math.min(boxTops[box], manager.getDecoratedTop(child) + child.getTranslationY());
        boxBottoms[box] = Math.max(boxBottoms[box], manager.getDecoratedBottom(child) + child.getTranslationY());
      }
      // Rows scrolled away keep their side of a container square, so it runs on past the screen edge.
      final float outer = Screen.dpf(XgramExpressive.LIST_OUTER_CORNER);
      paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT));
      for (int box = 0; box < boxCount; box++) {
        if (boxTops[box] >= boxBottoms[box]) {
          continue;
        }
        float topRadius = boxFirstRows[box] ? outer : 0f, bottomRadius = boxLastRows[box] ? outer : 0f;
        radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
        radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
        rect.set(parent.getPaddingLeft(), boxTops[box], parent.getWidth() - parent.getPaddingRight(), boxBottoms[box]);
        path.rewind();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        c.drawPath(path, paint);
      }
    }
  }

  private static final class Holder extends RecyclerView.ViewHolder {
    Holder (View itemView) {
      super(itemView);
    }
  }

  /**
   * The list's view pool. A view it has no room for is let go of at once (a cell's emoji receivers destroyed, a
   * title's theme listener removed) instead of being kept until the page closes.
   */
  private final class CellPool extends RecyclerView.RecycledViewPool {
    private final SparseIntArray limits = new SparseIntArray();

    int limit (int viewType) {
      return limits.get(viewType, DEFAULT_POOL);
    }

    @Override
    public void setMaxRecycledViews (int viewType, int max) {
      limits.put(viewType, max);
      while (getRecycledViewCount(viewType) > max) {
        RecyclerView.ViewHolder holder = getRecycledView(viewType);
        if (holder == null) {
          break;
        }
        release(holder);
      }
      super.setMaxRecycledViews(viewType, max);
    }

    @Override
    public void putRecycledView (RecyclerView.ViewHolder scrap) {
      final int viewType = scrap.getItemViewType();
      if (getRecycledViewCount(viewType) >= limit(viewType)) {
        release(scrap); // full: RecyclerView drops it
      }
      super.putRecycledView(scrap);
    }

    private void release (RecyclerView.ViewHolder holder) {
      final View view = holder.itemView;
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
        destroyables.remove(view);
      }
      if (view instanceof TitleView) {
        removeThemeListenerByTarget(view);
      }
    }
  }

  private final class Adapter extends RecyclerView.Adapter<Holder> {
    @Override
    public int getItemCount () {
      // Header, then (unless General, once the icons are known) the tab strip's slot when there is more than the
      // default icons, then each section's title and icons: "no icon" and the default icons, the recent custom emoji
      // (or the topic's own), every installed pack.
      return itemCount;
    }

    @Override
    public int getItemViewType (int position) {
      if (position == 0) {
        return VIEW_TYPE_HEADER;
      }
      if (position == STRIP_POSITION && hasStrip()) {
        return VIEW_TYPE_TABS;
      }
      Section section = sectionAt(position);
      return section != null && section.titlePosition == position ? VIEW_TYPE_TITLE : VIEW_TYPE_ICON;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      Context context = parent.getContext();
      switch (viewType) {
        case VIEW_TYPE_HEADER: {
          LinearLayout layout = new LinearLayout(context);
          layout.setOrientation(LinearLayout.HORIZONTAL);
          layout.setGravity(Gravity.CENTER_VERTICAL);
          layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          if (expressive) {
            // M3E: a surfaceBright card with list corners; the icon's glyph starts 16dp in
            XgramExpressive.MorphDrawable card = new XgramExpressive.MorphDrawable(0, XgramExpressive.LIST_OUTER_CORNER, Float.NaN);
            card.setColorRole(XgramExpressive.SURFACE_BRIGHT);
            layout.setBackground(card);
            layout.setPadding(Screen.dp(4f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
          } else {
            layout.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
            RippleSupport.setSimpleWhiteBackground(layout);
          }

          preview = new IconView(context, tdlib, expressive, .62f, false);
          preview.setOnClickListener(v -> onPreviewClicked());
          layout.addView(preview, new LinearLayout.LayoutParams(Screen.dp(64f), Screen.dp(64f)));

          MaterialEditTextGroup input = nameInput = new MaterialEditTextGroup(context, tdlib);
          input.setHint(R.string.XgramTopicNameHint);
          input.setMaxLength(128);
          input.setText(name);
          input.setTextListener((v, text) -> {
            name = text.toString();
            preview.bindPreview(ForumTopicEditController.this, null);
            notifyNoIconChanged();
          });
          LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
          params.leftMargin = Screen.dp(8f);
          layout.addView(input, params);
          addThemeInvalidateListener(layout);
          preview.bindPreview(ForumTopicEditController.this, null);
          if (expressive && isCreating()) {
            // M3E round 6: a new topic picks its color from a row of swatches under the name, in the same card
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setBackground(layout.getBackground());
            layout.setBackground(null);
            layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(layout);
            ColorRow colorRow = ForumTopicEditController.this.colorRow = new ColorRow(context);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f));
            rowParams.bottomMargin = Screen.dp(8f);
            card.addView(colorRow, rowParams);
            addThemeInvalidateListener(card);
            addThemeInvalidateListener(colorRow);
            return new Holder(card);
          }
          return new Holder(layout);
        }
        case VIEW_TYPE_TITLE: {
          TitleView title = new TitleView(context);
          title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
          title.setTextColor(Theme.getColor(ColorId.textNeutral));
          addThemeTextColorListener(title, ColorId.textNeutral);
          title.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(8f));
          if (expressive) {
            // M3E: a titleSmall section header in primary (textNeutral resolves to it), 16dp from the container's edge
            XgramExpressive.applyText(title, XgramExpressive.TS_TITLE_S, false);
            title.setPadding(Screen.dp(16f), Screen.dp(20f), Screen.dp(16f), Screen.dp(8f));
          }
          title.setMaxLines(3);
          title.setEllipsize(TextUtils.TruncateAt.END);
          title.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new Holder(title);
        }
        case VIEW_TYPE_TABS: {
          // The tab strip's slot: the strip itself floats over the list (see syncStrip)
          View slot = new View(context);
          slot.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stripSlotHeight()));
          return new Holder(slot);
        }
        case VIEW_TYPE_ICON:
        default: {
          IconView view = new IconView(context, tdlib, expressive, .68f, true);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          if (expressive) {
            // M3E: a round state layer (48dp, or the cell's size) instead of a full-cell rectangle
            XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(view, 0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_FULL);
            morph.setSize(48f, 48f, Gravity.CENTER);
            view.pressMorph = morph;
          } else {
            RippleSupport.setTransparentSelector(view);
          }
          view.setOnClickListener(v -> onCellClicked(v));
          destroyables.add(view);
          return new Holder(view);
        }
      }
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position) {
      int viewType = getItemViewType(position);
      if (viewType == VIEW_TYPE_TITLE) {
        bindTitle((TitleView) holder.itemView, position);
      } else if (viewType == VIEW_TYPE_ICON) {
        bindIcon((IconView) holder.itemView, position);
      }
    }

    @Override
    public void onViewRecycled (@NonNull Holder holder) {
      if (holder.itemView instanceof IconView && holder.itemView != preview) {
        ((IconView) holder.itemView).setSticker(null); // a pooled cell holds no emoji
      }
    }

    private void bindTitle (TitleView title, int position) {
      Section section = sectionAt(position);
      if (section == null) {
        return;
      }
      switch (section.kind) {
        case SECTION_DEFAULT:
          title.setText(Lang.getString(isCreating() ? R.string.XgramTopicIconCreateHint : R.string.XgramTopicIcon));
          title.setLocked(false);
          break;
        case SECTION_RECENT:
          title.setText(Lang.getString(section.currentOnly ? R.string.XgramTopicIconCurrent : R.string.XgramTopicIconsRecent));
          title.setLocked(false);
          break;
        case SECTION_PACK:
        default:
          title.setText(section.info != null ? section.info.title : "");
          title.setLocked(!premium);
          requestPack(section); // its title shows: the pack's emoji are next
          break;
      }
    }

    private void bindIcon (IconView view, int position) {
      Section section = sectionAt(position);
      if (section == null) {
        view.clearBubble();
        view.setSticker(null);
        view.setTag(PLACEHOLDER);
        view.setSelectedIcon(false);
        view.setLocked(false);
        return;
      }
      int index = position - section.firstCell;
      if (section.kind == SECTION_DEFAULT && index == 0) {
        // "No icon": the colored letter bubble.
        view.setTag(null);
        view.setBubble(color, name);
        view.setSticker(null);
        view.setSelectedIcon(customEmojiId == 0);
        view.setLocked(false);
        return;
      }
      if (section.kind == SECTION_PACK && section.loadState == LOAD_NONE) {
        requestPack(section);
      }
      TGStickerObj sticker = stickerAt(section, index);
      view.clearBubble();
      view.setSticker(sticker);
      view.setTag(sticker != null ? sticker : PLACEHOLDER);
      long emojiId = sticker != null ? emojiIdOf(sticker.getSticker()) : 0;
      view.setSelectedIcon(emojiId != 0 && emojiId == customEmojiId);
      // Without Premium a pack's emoji can't be picked (a tap explains why), unless it is the topic's own or a default
      view.setLocked(!premium && section.kind == SECTION_PACK && emojiId != 0 && emojiId != initialCustomEmojiId && findIcon(emojiId) == null);
    }
  }
}
