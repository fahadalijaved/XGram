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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.RecentEmoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Telegram's emoji search, under the message box: a search field and the emoji that match it (TDLib's emoji keywords in
 * the keyboard languages, and Telegram X's own English ones), with the keyboard below. A tap puts the emoji in the
 * message (where its cursor was) and keeps the search open for more; ← or Back goes back to the emoji panel. Before
 * anything is typed it shows the recent emoji.
 */
public final class XgramEmojiSearch extends LinearLayout {
  public interface Host {
    /** An emoji was picked: into the message box. */
    void onEmojiPicked (String emoji);
    /** ← was pressed: the search closes and the emoji panel comes back. */
    void onSearchClose ();
  }

  private static final float FIELD_DP = 44f, CELL_DP = 46f;
  private static final int ROWS = 2;

  private final Tdlib tdlib;
  private final Host host;
  private final EditText field;
  private final RecyclerView results;
  private final TextView empty;
  private final Adapter adapter = new Adapter();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private String query = "";
  private int requestId;

  public XgramEmojiSearch (Context context, Tdlib tdlib, Host host) {
    super(context);
    this.tdlib = tdlib;
    this.host = host;
    setOrientation(VERTICAL);
    final boolean m3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    final int surface = m3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE) : Theme.getColor(ColorId.filling);
    final int fieldFill = m3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH) : Theme.getColor(ColorId.background);
    final int onSurface = m3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE) : Theme.getColor(ColorId.text);
    final int onSurfaceVariant = m3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.textPlaceholder);
    setBackgroundColor(surface);
    setPadding(0, Screen.dp(6f), 0, Screen.dp(4f));

    // The field: ←, the query, ✕
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
    pill.setColor(fieldFill);
    pill.setCornerRadius(Screen.dp(FIELD_DP / 2f));
    row.setBackground(pill);
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(FIELD_DP));
    rowParams.leftMargin = rowParams.rightMargin = Screen.dp(12f);
    row.setLayoutParams(rowParams);

    ImageView back = newIcon(context, R.drawable.baseline_arrow_back_24, onSurfaceVariant);
    back.setContentDescription(Lang.getString(R.string.XgramEmojiSearchBack));
    back.setOnClickListener(v -> host.onSearchClose());
    row.addView(back);

    field = new EditText(context);
    field.setBackground(null);
    field.setSingleLine(true);
    field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    field.setTypeface(Fonts.getRobotoRegular());
    field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    field.setTextColor(onSurface);
    field.setHintTextColor(onSurfaceVariant);
    field.setHint(R.string.XgramEmojiSearch);
    field.setPadding(0, 0, 0, 0);
    field.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    row.addView(field);

    ImageView clear = newIcon(context, R.drawable.baseline_close_24, onSurfaceVariant);
    clear.setContentDescription(Lang.getString(R.string.Clear));
    clear.setVisibility(View.INVISIBLE);
    clear.setOnClickListener(v -> field.setText(""));
    row.addView(clear);
    addView(row);

    // The results: two rows of emoji (more scroll), or a line when nothing matches
    FrameLayout resultsWrap = new FrameLayout(context);
    resultsWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(CELL_DP) * ROWS + Screen.dp(8f)));
    results = new RecyclerView(context);
    results.setItemAnimator(null);
    results.setOverScrollMode(OVER_SCROLL_NEVER);
    results.setPadding(Screen.dp(8f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
    results.setClipToPadding(false);
    final GridLayoutManager grid = new GridLayoutManager(context, 8);
    results.setLayoutManager(grid);
    results.setAdapter(adapter);
    results.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      int columns = Math.max(4, (r - l - v.getPaddingLeft() - v.getPaddingRight()) / Screen.dp(CELL_DP));
      if (grid.getSpanCount() != columns) {
        grid.setSpanCount(columns);
      }
    });
    resultsWrap.addView(results, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    empty = new TextView(context);
    empty.setText(R.string.XgramEmojiSearchNone);
    empty.setTextColor(onSurfaceVariant);
    empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    empty.setGravity(Gravity.CENTER);
    empty.setVisibility(View.GONE);
    resultsWrap.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(resultsWrap);

    field.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

      @Override
      public void onTextChanged (CharSequence s, int start, int before, int count) { }

      @Override
      public void afterTextChanged (Editable s) {
        clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.INVISIBLE);
        search(s.toString().trim());
      }
    });
    field.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        Keyboard.hide(field); // the results stay, above the message box
        return true;
      }
      return false;
    });
    showRecents();
  }

  private static ImageView newIcon (Context context, int res, int color) {
    ImageView view = new ImageView(context);
    view.setScaleType(ImageView.ScaleType.CENTER);
    view.setImageResource(res);
    view.setColorFilter(color);
    view.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(FIELD_DP), Screen.dp(FIELD_DP)));
    view.setBackgroundResource(R.drawable.bg_btn_header);
    return view;
  }

  /** Focuses the field with the keyboard (the emoji panel has gone by now). */
  public void focus () {
    field.requestFocus();
    Keyboard.show(field);
  }

  public void hideKeyboard () {
    Keyboard.hide(field);
  }

  private void showRecents () {
    List<String> emoji = new ArrayList<>();
    for (RecentEmoji recent : Emoji.instance().getRecents()) {
      if (!recent.isCustomEmoji) {
        emoji.add(recent.emoji);
      }
    }
    setResults(emoji, false);
  }

  private void search (String text) {
    if (text.equals(query)) {
      return;
    }
    query = text;
    handler.removeCallbacksAndMessages(null);
    final int id = ++requestId;
    if (text.isEmpty()) {
      showRecents();
      return;
    }
    handler.postDelayed(() -> {
      // Telegram X's own (English) matches at once, then TDLib's keywords in the keyboard languages
      final LinkedHashSet<String> found = new LinkedHashSet<>();
      if (text.length() < N.getEmojiSuggestionMaxLength()) {
        N.Suggestion[] suggestions = N.getEmojiSuggestions(text);
        if (suggestions != null) {
          for (N.Suggestion suggestion : suggestions) {
            found.add(suggestion.emoji);
          }
        }
      }
      setResults(new ArrayList<>(found), false);
      tdlib.send(new TdApi.SearchEmojis(text, U.getInputLanguages()), (keywords, error) -> UI.post(() -> {
        if (id != requestId) {
          return; // typed on meanwhile
        }
        if (keywords != null) {
          for (TdApi.EmojiKeyword keyword : keywords.emojiKeywords) {
            found.add(Emoji.cleanupEmoji(keyword.emoji));
          }
        }
        setResults(new ArrayList<>(found), true);
      }));
    }, 120);
  }

  private void setResults (List<String> emoji, boolean final_) {
    adapter.items.clear();
    adapter.items.addAll(emoji);
    adapter.notifyDataSetChanged();
    results.scrollToPosition(0);
    empty.setVisibility(emoji.isEmpty() && final_ && !query.isEmpty() ? View.VISIBLE : View.GONE);
  }

  private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final List<String> items = new ArrayList<>();

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      EmojiCell cell = new EmojiCell(parent.getContext());
      cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(CELL_DP)));
      cell.setOnClickListener(v -> {
        if (v.getTag() instanceof String) {
          String picked = (String) v.getTag();
          Emoji.instance().saveRecentEmoji(picked);
          host.onEmojiPicked(picked);
        }
      });
      return new RecyclerView.ViewHolder(cell) { };
    }

    @Override
    public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
      ((EmojiCell) holder.itemView).setEmoji(items.get(position));
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }

  /** One emoji, drawn with Telegram X's emoji set as the panel does. */
  private static final class EmojiCell extends View {
    private @Nullable Drawable drawable;

    EmojiCell (Context context) {
      super(context);
      setBackgroundResource(R.drawable.bg_btn_header);
    }

    void setEmoji (String emoji) {
      setTag(emoji);
      setContentDescription(emoji);
      drawable = Drawables.emojiDrawable(emoji);
      invalidate();
    }

    @Override
    protected void onDraw (Canvas c) {
      if (drawable != null) {
        final int size = Screen.dp(30f);
        final int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        drawable.setBounds(left, top, left + size, top + size);
        drawable.draw(c);
      }
    }
  }
}
