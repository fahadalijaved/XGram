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
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import tgx.td.Td;

/**
 * XGram: Saved Messages tags, like official Telegram: a row of chips under the app bar (the tag's reaction, its name if
 * it has one, how many messages carry it). A tap shows only the messages with that tag (again: all of them); a long
 * press names the tag.
 */
public final class XgramSavedTagsBar extends HorizontalScrollView {
  public interface Callback {
    void onTagClick (@NonNull TdApi.SavedMessagesTag tag);
    void onTagLongClick (@NonNull TdApi.SavedMessagesTag tag);
  }

  private final LinearLayout row;
  private TdApi.SavedMessagesTag[] tags = new TdApi.SavedMessagesTag[0];
  private @Nullable TdApi.ReactionType selected;
  private @Nullable Callback callback;

  public static int height () {
    return Screen.dp(48f);
  }

  public XgramSavedTagsBar (Context context) {
    super(context);
    setHorizontalScrollBarEnabled(false);
    setOverScrollMode(OVER_SCROLL_NEVER);
    setClipToPadding(false);
    setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height()));
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  public boolean isEmpty () {
    return tags.length == 0;
  }

  public void setTags (@Nullable TdApi.SavedMessagesTag[] tags) {
    this.tags = tags != null ? tags : new TdApi.SavedMessagesTag[0];
    if (selected != null && indexOf(selected) == -1) {
      selected = null;
    }
    rebuild();
  }

  public void setSelected (@Nullable TdApi.ReactionType tag) {
    this.selected = tag;
    for (int i = 0; i < row.getChildCount(); i++) {
      style((TextView) row.getChildAt(i), selected != null && Td.equalsTo(tags[i].tag, selected));
    }
  }

  private int indexOf (TdApi.ReactionType type) {
    for (int i = 0; i < tags.length; i++) {
      if (Td.equalsTo(tags[i].tag, type)) {
        return i;
      }
    }
    return -1;
  }

  private static String emojiOf (TdApi.ReactionType type) {
    switch (type.getConstructor()) {
      case TdApi.ReactionTypeEmoji.CONSTRUCTOR:
        return ((TdApi.ReactionTypeEmoji) type).emoji;
      case TdApi.ReactionTypePaid.CONSTRUCTOR:
        return "⭐";
      default:
        return "🏷";
    }
  }

  private void rebuild () {
    row.removeAllViews();
    for (TdApi.SavedMessagesTag tag : tags) {
      TextView chip = new TextView(getContext());
      StringBuilder b = new StringBuilder(emojiOf(tag.tag));
      if (!TextUtils.isEmpty(tag.label)) {
        b.append(' ').append(tag.label);
      }
      if (tag.count > 0) {
        b.append("  ").append(tag.count);
      }
      chip.setText(Emoji.instance().replaceEmoji(b.toString()));
      chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      chip.setGravity(Gravity.CENTER_VERTICAL);
      chip.setSingleLine(true);
      chip.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      chip.setOnClickListener(v -> {
        if (callback != null) {
          callback.onTagClick(tag);
        }
      });
      chip.setOnLongClickListener(v -> {
        if (callback != null) {
          callback.onTagLongClick(tag);
        }
        return true;
      });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(32f));
      params.rightMargin = Screen.dp(8f);
      row.addView(chip, params);
      style(chip, selected != null && Td.equalsTo(tag.tag, selected));
    }
  }

  /** M3 filter chips: the selected one filled with the secondary container, the others outlined. */
  private static void style (TextView chip, boolean isSelected) {
    final boolean m3 = XgramExpressive.isEnabled();
    final int fill = isSelected ? (m3 ? XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER) : Theme.getColor(ColorId.fillingPressed)) : 0;
    final int text = isSelected ? (m3 ? XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER) : Theme.getColor(ColorId.text)) :
      (m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.textLight));
    final int outline = m3 ? XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT) : Theme.getColor(ColorId.separator);
    GradientDrawable background = new GradientDrawable();
    background.setCornerRadius(Screen.dp(8f));
    background.setColor(fill);
    if (!isSelected) {
      background.setStroke(Screen.dp(1f), outline);
    }
    chip.setBackground(background);
    chip.setTextColor(text);
  }
}
