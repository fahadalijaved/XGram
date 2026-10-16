package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiPacksInfoView;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.core.StringUtils;

public class MessageOptionsController extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<MessageOptionsController.Args> {
  private Options options;
  private long[] emojiPackIds;
  private long emojiPackFirstEmoji;
  private View.OnClickListener listener;
  private OptionsAdapter adapter;
  private ThemeListenerList themeProvider;
  private Runnable hideWindowDelegate;
  // XGram: Message menu > Reaction bar > Bottom: the reaction bar as a row of the options, and its expand button's action
  private @Nullable MessageOptionsPagerController.State xgramReactionBarState;
  private @Nullable Runnable xgramExpandReactions;


  public MessageOptionsController (Context context, Tdlib tdlib, ThemeListenerList themeProvider) {
    super(context, tdlib);
    this.themeProvider = themeProvider;
  }

  @Override
  public void setArguments (MessageOptionsController.Args args) {
    super.setArguments(args);
    this.options = args.options;
    this.listener = args.listener;
    this.emojiPackIds = args.emojiPackIds;
    this.emojiPackFirstEmoji = args.emojiPackFirstEmoji;
    this.hideWindowDelegate = args.hideWindowDelegate;
    this.xgramReactionBarState = args.xgramReactionBarState;
    this.xgramExpandReactions = args.xgramExpandReactions;
  }

  @Override
  public boolean needBottomDecorationOffsets (RecyclerView parent) {
    return false;
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptions;
  }

  @Override
  protected int getRecyclerBackground () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.background : ColorId.filling; // XGram: one tone with the sheet header
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new OptionsAdapter(context, this, options, emojiPackFirstEmoji, emojiPackIds, listener, themeProvider);
    LinearLayoutManager manager = new LinearLayoutManager(context);
    addThemeInvalidateListener(recyclerView);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    com.fahadali.xgram.appearance.XgramMotion.stretch(recyclerView, com.fahadali.xgram.appearance.XgramMotion.EDGE_BOTTOM);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(null);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive menu: the options as segmented tile groups, 16dp inset, 2dp gaps, 8dp between groups
      recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
          int position = parent.getChildAdapterPosition(view);
          if (position != RecyclerView.NO_POSITION && adapter.xgramIsReactionBar(position)) {
            // the reaction bar row (Reaction bar: Bottom) is a group of its own: 8dp around it, the navigation bar room
            // under it when it is the last row (its own view keeps the 16dp insets)
            outRect.set(0, Screen.dp(8f), 0, adapter.xgramReactionBarLast() ? Screen.dp(8f) + xgramNavigationBarSpace(parent) : 0);
            return;
          }
          int index = position != RecyclerView.NO_POSITION ? adapter.xgramOptionIndex(position) : -1;
          if (index < 0) {
            return;
          }
          int inset = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_INSET);
          int top = index == 0 ? Screen.dp(8f) : adapter.xgramSameGroup(index - 1, index) ? Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_GAP) : Screen.dp(8f);
          int bottom = index == adapter.options.items.length - 1 && !adapter.xgramReactionBarLast() ? Screen.dp(8f) + xgramNavigationBarSpace(parent) : 0;
          outRect.set(inset, top, inset, bottom);
        }
      });
    }
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  public static class Args {
    public Options options;
    public View.OnClickListener listener;
    public long[] emojiPackIds;
    public long emojiPackFirstEmoji;
    public Runnable hideWindowDelegate;

    public Args (Options options, View.OnClickListener listener, long emojiPackFirstEmoji, long[] emojiPackIds, Runnable hideWindowDelegate) {
      this.options = options;
      this.listener = listener;
      this.emojiPackIds = emojiPackIds;
      this.emojiPackFirstEmoji = emojiPackFirstEmoji;
      this.hideWindowDelegate = hideWindowDelegate;
    }

    // XGram: Message menu > Reaction bar > Bottom
    public @Nullable MessageOptionsPagerController.State xgramReactionBarState;
    public @Nullable Runnable xgramExpandReactions;

    /** XGram: adds the reaction bar row (null: none) and what its expand button opens (null: no button). */
    public Args xgramReactionBar (@Nullable MessageOptionsPagerController.State state, @Nullable Runnable onExpand) {
      this.xgramReactionBarState = state;
      this.xgramExpandReactions = onExpand;
      return this;
    }
  }


  private static class OptionHolder extends RecyclerView.ViewHolder {
    public OptionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static OptionHolder create (Context context, ViewController<?> parent, int viewType, View.OnClickListener onClickListener) {
      if (viewType == OptionsAdapter.TYPE_OPTION) {
        EmojiTextView text = new EmojiTextView(context);
        text.setScrollDisabled(true);
        text.setTypeface(Fonts.getRobotoRegular());
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        text.setOnClickListener(onClickListener);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        text.setPadding(Screen.dp(17f), Screen.dp(1f), Screen.dp(17f), 0);
        text.setCompoundDrawablePadding(Screen.dp(18f));
        text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f)));
        Views.setClickable(text);
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          // XGram: Material 3 Expressive menu rows: 56dp segmented tiles (surfaceBright, outer 20 / inner 4, 16 pressed),
          // bodyLarge, 24dp icon at 16dp, text at 56dp
          text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f)));
          text.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false));
          text.setPadding(Screen.dp(16f), Screen.dp(1f), Screen.dp(16f), 0);
          text.setCompoundDrawablePadding(Screen.dp(16f));
          com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable morph = com.fahadali.xgram.appearance.XgramExpressive.attachPressMorph(text,
            com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT),
            com.fahadali.xgram.appearance.XgramExpressive.CORNER_NONE, com.fahadali.xgram.appearance.XgramExpressive.LIST_PRESSED_CORNER);
          morph.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT);
          morph.setSegment(com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_SINGLE);
        } else {
          RippleSupport.setTransparentSelector(text);
        }
        return new OptionHolder(text);
      } else if (viewType == OptionsAdapter.TYPE_EMOJI_PACK_INFO) {
        EmojiPacksInfoView textView = new EmojiPacksInfoView(context, parent, parent.tdlib());
        return new OptionHolder(textView);
      } else if (viewType == OptionsAdapter.TYPE_SUBTITLE) {
        EmojiTextView textView = OptionsLayout.genSubtitle(context);
        return new OptionHolder(textView);
      } else {
        CustomTextView textView = new CustomTextView(context, parent.tdlib());
        textView.setTextColorId(ColorId.textLight);
        textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
        return new OptionHolder(textView);
      }
    }
  }

  private static class OptionsAdapter extends RecyclerView.Adapter<OptionHolder> {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private Options options;
    private final Tdlib tdlib;
    private final MessageOptionsController parent;
    @Nullable
    private final ThemeListenerList themeProvider;
    private final long[] emojiPackIds;
    private final long emojiPackFirstEmoji;

    private int textInfoPosition, emojiInfoPosition, subtitlePosition;
    /** XGram: the reaction bar row's place among the options (Reaction bar: Bottom), -1 without it. */
    private final int xgramBarIndex;

    public void updateSubtitle (Options options) {
      this.options = options;
      int prevSubtitlePosition = this.subtitlePosition;
      boolean hadSubtitle = prevSubtitlePosition >= 0;
      boolean hasSubtitle = options.subtitle != null;
      if (hadSubtitle != hasSubtitle) {
        if (hadSubtitle) {
          subtitlePosition = -1;
          if (textInfoPosition >= 0) {
            textInfoPosition--;
          }
          if (emojiInfoPosition >= 0) {
            emojiInfoPosition--;
          }
          notifyItemRemoved(prevSubtitlePosition);
        } else {
          subtitlePosition = 0;
          if (textInfoPosition >= 0) {
            textInfoPosition++;
          }
          if (emojiInfoPosition >= 0) {
            emojiInfoPosition++;
          }
          notifyItemInserted(subtitlePosition);
        }
      } else if (hasSubtitle) {
        notifyItemChanged(subtitlePosition);
      }
    }

    public static final int TYPE_OPTION = 0;
    public static final int TYPE_INFO = 1;
    public static final int TYPE_EMOJI_PACK_INFO = 2;
    public static final int TYPE_SUBTITLE = 3;
    public static final int TYPE_XGRAM_REACTION_BAR = 4;

    OptionsAdapter (Context context, MessageOptionsController parent, Options options, long emojiPackFirstEmoji, long[] emojiPackIds, View.OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider) {
      this.parent = parent;
      this.tdlib = parent.tdlib();
      this.onClickListener = onClickListener;
      this.context = context;
      this.options = options;
      this.themeProvider = themeProvider;
      this.emojiPackIds = emojiPackIds;
      this.emojiPackFirstEmoji = emojiPackFirstEmoji;

      this.subtitlePosition = options.subtitle != null ? 0 : -1;
      this.emojiInfoPosition = emojiPackIds.length > 0 ? (subtitlePosition + 1) : -1;
      this.textInfoPosition = StringUtils.isEmpty(options.info) ? -1 : (Math.max(emojiInfoPosition, subtitlePosition) + 1);
      this.xgramBarIndex = parent.xgramReactionBarState != null ? com.fahadali.xgram.chats.XgramReactionBar.indexIn(options.items) : -1;
    }

    @NonNull
    @Override
    public OptionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == TYPE_XGRAM_REACTION_BAR && this.parent.xgramReactionBarState != null) {
        // XGram: the reaction bar row (Reaction bar: Bottom), the header row's strip and expand button
        View row = com.fahadali.xgram.chats.XgramReactionBar.createRow(context, this.parent.xgramReactionBarState, this.parent.xgramExpandReactions);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(row);
        } else {
          this.parent.addThemeInvalidateListener(row);
        }
        return new OptionHolder(row);
      }
      return OptionHolder.create(context, this.parent, viewType, onClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull OptionHolder holder, int position) {
      int type = getItemViewType(position);
      switch (type) {
        case TYPE_OPTION: {
          if (subtitlePosition >= 0) {
            position--;
          }
          if (emojiInfoPosition >= 0) {
            position--;
          }
          if (textInfoPosition >= 0) {
            position--;
          }
          if (xgramBarIndex >= 0 && position > xgramBarIndex) {
            position--; // XGram: the options after the reaction bar row
          }
          OptionItem item = options.items[position];
          TextView textView = ((TextView) holder.itemView);
          textView.setId(item.id);
          final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
          final boolean xgramDelete = xgramM3 && item.id == R.id.btn_messageDelete; // XGram: destructive action in error
          if (xgramM3) {
            com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable morph = com.fahadali.xgram.appearance.XgramExpressive.findMorph(textView.getBackground());
            if (morph != null) {
              boolean joinsPrevious = position > 0 && xgramSameGroup(position - 1, position);
              boolean joinsNext = position < options.items.length - 1 && xgramSameGroup(position, position + 1);
              morph.setSegment(joinsPrevious ? (joinsNext ? com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_MIDDLE : com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_LAST) :
                (joinsNext ? com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_FIRST : com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_SINGLE));
            }
          }
          final int textColorId = xgramDelete ? ColorId.textNegative : OptionsLayout.getOptionColorId(item.textColor);
          textView.setTextColor(Theme.getColor(textColorId));
          if (themeProvider != null)
            themeProvider.addThemeColorListener(textView, textColorId);
          if (item.icon != 0) {
            Drawable drawable = Drawables.get(context.getResources(), item.icon);
            if (drawable != null) {
              final int drawableColorId = xgramDelete ? ColorId.iconNegative : item.iconColor == OptionColor.NORMAL ? ColorId.icon : item.iconColor;
              drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(drawableColorId)));
              if (themeProvider != null) {
                themeProvider.addThemeFilterListener(drawable, drawableColorId);
              }
              if (Lang.rtl()) {
                textView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
              } else {
                textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
              }
            }
          } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
          }
          textView.setText(item.name);
          break;
        }
        case TYPE_INFO: {
          CustomTextView textView = ((CustomTextView) holder.itemView);
          String str = options.info.toString();
          TextEntity[] parsed = TD.collectAllEntities(parent, tdlib, options.info, false, null);
          textView.setTextSize(15f);
          textView.setTextColorId(ColorId.textLight);
          textView.setText(str, parsed, false);
          break;
        }
        case TYPE_EMOJI_PACK_INFO: {
          EmojiPacksInfoView textView = ((EmojiPacksInfoView) holder.itemView);
          textView.setId(R.id.btn_emojiPackInfoButton);
          textView.setTextSize(15f);
          textView.setTextColorId(ColorId.textLight);
          textView.update(emojiPackFirstEmoji, emojiPackIds, new ClickableSpan() {
            @Override
            public void onClick (@NonNull View widget) {
              parent.listener.onClick(textView);
            }
          }, false);
          break;
        }
        case TYPE_SUBTITLE: {
          OptionItem item = options.subtitle;
          OptionsLayout.updateSubtitle((EmojiTextView) holder.itemView, item.name, item.icon, item.textColor, item.iconColor, null, parent);
          break;
        }
        case TYPE_XGRAM_REACTION_BAR: {
          break; // XGram: the reaction bar row binds its own reactions
        }
        default: {
          throw new IllegalStateException(Integer.toString(type));
        }
      }
    }

    /** XGram: the index in options.items of the row at {@code position}, or -1 for the subtitle and info rows. */
    int xgramOptionIndex (int position) {
      if (position < 0 || position >= getItemCount() || getItemViewType(position) != TYPE_OPTION) {
        return -1;
      }
      if (subtitlePosition >= 0) {
        position--;
      }
      if (emojiInfoPosition >= 0) {
        position--;
      }
      if (textInfoPosition >= 0) {
        position--;
      }
      if (xgramBarIndex >= 0 && position > xgramBarIndex) {
        position--;
      }
      return position >= 0 && position < options.items.length ? position : -1;
    }

    /** XGram: the adapter position of the reaction bar row, -1 without it. */
    private int xgramReactionBarPosition () {
      if (xgramBarIndex < 0) {
        return -1;
      }
      return (subtitlePosition >= 0 ? 1 : 0) + (emojiInfoPosition >= 0 ? 1 : 0) + (textInfoPosition >= 0 ? 1 : 0) + xgramBarIndex;
    }

    boolean xgramIsReactionBar (int position) {
      return xgramBarIndex >= 0 && position == xgramReactionBarPosition();
    }

    /** XGram: whether the reaction bar row is the last row (no Delete in the menu). */
    boolean xgramReactionBarLast () {
      return xgramBarIndex >= 0 && xgramBarIndex >= options.items.length;
    }

    boolean xgramSameGroup (int index, int nextIndex) {
      return com.fahadali.xgram.chats.XgramMessageMenu.sameMenuGroup(options.items[index].id, options.items[nextIndex].id);
    }

    @Override
    public int getItemViewType (int position) {
      if (position == textInfoPosition) {
        return TYPE_INFO;
      }
      if (position == emojiInfoPosition) {
        return TYPE_EMOJI_PACK_INFO;
      }
      if (position == subtitlePosition) {
        return TYPE_SUBTITLE;
      }
      if (xgramIsReactionBar(position)) {
        return TYPE_XGRAM_REACTION_BAR;
      }
      return TYPE_OPTION;
    }

    @Override
    public int getItemCount () {
      int itemCount = options.items.length;
      if (xgramBarIndex >= 0) {
        itemCount++; // XGram: the reaction bar row
      }
      if (textInfoPosition >= 0) {
        itemCount++;
      }
      if (emojiInfoPosition >= 0) {
        itemCount++;
      }
      if (subtitlePosition >= 0) {
        itemCount++;
      }
      return itemCount;
    }
  }

  public void updateSubtitle (Options options) {
    this.options = options;
    adapter.updateSubtitle(options);
  }

  /**
   * XGram: the height of the Expressive option tiles as this list lays them out (56dp tiles, 2dp gaps inside a group,
   * 8dp between groups, above the first and under the last), without the navigation bar room. The sheet opens at it
   * (MessageOptionsPagerController.getOptionItemsHeight), so the last group (Delete) is on screen.
   */
  public static int xgramTilesHeight (@Nullable OptionItem[] items) {
    if (items == null || items.length == 0) {
      return 0;
    }
    int height = items.length * Screen.dp(56f) + Screen.dp(8f) * 2;
    for (int i = 1; i < items.length; i++) {
      height += com.fahadali.xgram.chats.XgramMessageMenu.sameMenuGroup(items[i - 1].id, items[i].id) ? Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_GAP) : Screen.dp(8f);
    }
    return height;
  }

  /** XGram: room under the last group for the navigation bar when the app draws edge to edge (the keyboard is left out), less any bottom padding the list already has. */
  private int xgramNavigationBarSpace (@Nullable RecyclerView recyclerView) {
    if (!Settings.instance().useEdgeToEdge()) {
      return 0;
    }
    int inset = context().getRootView().getSystemInsetsWithoutIme().bottom;
    return Math.max(0, inset - (recyclerView != null ? recyclerView.getPaddingBottom() : 0));
  }

  @Override
  public int getItemsHeight (RecyclerView recyclerView) {
    int totalHeight = (options.items.length + 2) * Screen.dp(54);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && options.items.length > 0) {
      // XGram: 56dp tiles, 2dp gaps inside a group, 8dp between groups and around the list
      totalHeight += options.items.length * Screen.dp(2f) + Screen.dp(16f) + xgramNavigationBarSpace(recyclerView);
      for (int i = 1; i < options.items.length; i++) {
        totalHeight += adapter.xgramSameGroup(i - 1, i) ? Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_GAP) : Screen.dp(8f);
      }
    }
    if (adapter.xgramBarIndex >= 0) {
      totalHeight += com.fahadali.xgram.chats.XgramReactionBar.rowSpace(); // XGram: the reaction bar row (Reaction bar: Bottom)
    }
    if (adapter.textInfoPosition >= 0) {
      View view = recyclerView.getLayoutManager().findViewByPosition(adapter.textInfoPosition);
      int hintHeight =
        view instanceof CustomTextView && ((CustomTextView) view).checkMeasuredWidth(recyclerView.getMeasuredWidth()) ?
          view.getMeasuredHeight() : 0;
      if (hintHeight > 0) {
        totalHeight += hintHeight;
      } else {
        int availWidth = recyclerView.getMeasuredWidth() - Screen.dp(16f) * 2;
        if (availWidth > 0) {
          totalHeight += CustomTextView.measureHeight(this, options.info, 0, 15f, availWidth) + Screen.dp(14f) + Screen.dp(6f);
        } else {
          totalHeight += Screen.dp(14f) + Screen.dp(6f) + Screen.dp(15f);
        }
      }
    }
    if (adapter.emojiInfoPosition >= 0) {
      totalHeight += Screen.dp(40);
    }
    if (adapter.subtitlePosition >= 0) {
      View view = recyclerView.getLayoutManager().findViewByPosition(adapter.subtitlePosition);
      int height = view != null ? view.getMeasuredHeight() : 0;
      totalHeight += height != 0 ? height : Screen.dp(40f);
    }
    return totalHeight;
  }
}
