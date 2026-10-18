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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.CustomTextView;

/**
 * XGram M3E: the headline and supporting text of the confirm sheets. Telegram X shows many confirmations as an action
 * tile and a Cancel tile with no text at all (the tile was the message); as an M3E confirm sheet ([Cancel] [action]
 * under the text) they need a headline (headlineSmall) and supporting text (bodyMedium), built here. The headline row
 * of the settings choice sheets (showSettings) is here too. Main thread only; used while
 * {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramConfirmSheets {
  private XgramConfirmSheets () { }

  // Delete messages (TdlibUi.showDeleteOptions)

  /** "Delete message?", "Delete 2 messages?", or the scheduled message / reminder forms of it. */
  @NonNull
  public static CharSequence deleteMessagesHeadline (int count, boolean scheduled, boolean selfChat) {
    if (scheduled) {
      if (selfChat) {
        return count == 1 ? Lang.getString(R.string.XgramConfirmDeleteReminder) : Lang.getString(R.string.XgramConfirmDeleteXReminders, count);
      }
      return count == 1 ? Lang.getString(R.string.XgramConfirmDeleteScheduled) : Lang.getString(R.string.XgramConfirmDeleteXScheduled, count);
    }
    return count == 1 ? Lang.getString(R.string.XgramConfirmDeleteMessage) : Lang.plural(R.string.QDeleteXMessages, count);
  }

  /**
   * Who the messages are deleted for: everyone in the chat ({@code forEveryone} in a group or channel), you and
   * {@code userName} ({@code forEveryone} in a private chat), only you; that scheduled messages are not sent and
   * reminders do not come; Saved Messages: that it cannot be undone.
   */
  @NonNull
  public static CharSequence deleteMessagesText (int count, boolean scheduled, boolean selfChat, boolean forEveryone, @Nullable String userName) {
    final boolean one = count == 1;
    if (scheduled) {
      if (selfChat) {
        return Lang.getString(R.string.XgramConfirmDeleteReminderText);
      }
      return Lang.getString(one ? R.string.XgramConfirmDeleteScheduledOne : R.string.XgramConfirmDeleteScheduledMany);
    }
    if (selfChat) {
      return Lang.getString(R.string.NoUndoWarn);
    }
    if (forEveryone) {
      if (userName != null && !userName.isEmpty()) {
        return Lang.getString(one ? R.string.XgramConfirmDeleteForMeAndXOne : R.string.XgramConfirmDeleteForMeAndXMany, userName);
      }
      return Lang.getString(one ? R.string.XgramConfirmDeleteForEveryoneOne : R.string.XgramConfirmDeleteForEveryoneMany);
    }
    return Lang.getString(one ? R.string.XgramConfirmDeleteForMeOne : R.string.XgramConfirmDeleteForMeMany);
  }

  // The headline row of the settings choice sheets (SettingsWrapBuilder.xgramSetHeadline)

  /** The custom view type of the headline row (ListItem.TYPE_CUSTOM - HEADLINE_ROW). */
  public static final int HEADLINE_ROW = 9121;

  /**
   * The headline of a settings choice sheet: headlineSmall in onSurface on the sheet's own surface, 16dp from the
   * sides, right under the 48dp handle band (the band's padding row is 40dp: the SHADOW_TOP that makes up the rest
   * comes after the headline, see showSettings). Follows theme changes (the color is read at draw time).
   */
  @NonNull
  public static View newSheetHeadline (@NonNull Context context, @Nullable Tdlib tdlib, @Nullable ViewController<?> themeProvider) {
    CustomTextView view = new CustomTextView(context, tdlib);
    // a shared style (its size is never changed on this view)
    view.setTextStyleProvider(XgramM3Profile.flexTextProvider(XgramExpressive.TS_HEADLINE_S, XgramExpressive.textSizeSp(XgramExpressive.TS_HEADLINE_S)));
    view.setTextColorId(ColorId.text);
    view.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));
    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
    return view;
  }

  /**
   * Binds {@code headline} to a {@link #newSheetHeadline} view. {@code textFollows}: supporting text (a header item
   * with its own 12dp above) comes next, so the headline keeps 4dp under it (16dp between the two, as the M3 dialog);
   * 8dp otherwise (with the 8dp SHADOW_TOP, 16dp above the option rows).
   */
  public static void bindSheetHeadline (@NonNull View view, @Nullable CharSequence headline, boolean textFollows) {
    if (!(view instanceof CustomTextView)) {
      return;
    }
    CustomTextView textView = (CustomTextView) view;
    final int bottom = Screen.dp(textFollows ? 4f : 8f);
    if (textView.getPaddingBottom() != bottom) {
      textView.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), bottom);
    }
    textView.setText(headline, null, false);
  }
}
