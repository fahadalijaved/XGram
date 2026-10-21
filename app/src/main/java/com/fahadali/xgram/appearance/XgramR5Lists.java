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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ScalableTextView;

import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Material 3 Expressive for the shared list layer (SettingsAdapter, SettingHolder, ChatsAdapter): the empty states
 * (an icon on a soft burst in primaryContainer, titleLarge, bodyMedium onSurfaceVariant, centred, at most 320dp wide),
 * the list's button row (a centred 40dp pill, filled primary for a page's main action, filled tonal otherwise), the
 * build number footer and the sentence-case labels. Everything here is only built with Expressive design on; colors
 * are roles read at draw time, so light, dark and Pure black all apply to views already built. Main thread only.
 */
public final class XgramR5Lists {
  private XgramR5Lists () { }

  // Sentence case

  /**
   * {@code sentenceRes} while Telegram X's string for {@code tgxRes} is the bundled English one (title case there);
   * a translation, or Expressive design off, keeps Telegram X's string.
   */
  @NonNull
  public static String sentence (@StringRes int tgxRes, @StringRes int sentenceRes) {
    final String tgx = Lang.getString(tgxRes);
    if (!XgramExpressive.isEnabled()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return Lang.getString(sentenceRes);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  @StringRes
  private static int sentenceFor (@StringRes int res) {
    if (res == 0) {
      return 0;
    }
    if (res == R.string.PhoneNumberChange) {
      return R.string.XgramR5ListsChangeNumber;
    } else if (res == R.string.InviteContacts) {
      return R.string.XgramR5ListsInviteContacts;
    } else if (res == R.string.OpenArchive) {
      return R.string.XgramR5ListsArchivedChats;
    } else if (res == R.string.EditFolder) {
      return R.string.XgramR5ListsEditFolder;
    } else if (res == R.string.TerminateAllWebSessions) {
      return R.string.XgramR5ListsDisconnectAll;
    } else if (res == R.string.OtherWebSessions) {
      return R.string.XgramR5ListsConnectedWebsites;
    } else if (res == R.string.WebSessionsTitle) {
      return R.string.XgramR5ListsLoggedIn;
    } else if (res == R.string.NoOtherSessions) {
      return R.string.XgramR5ListsNoOtherSessions;
    }
    return XgramR6B6Lists.sentenceFor(res); // round 6: the title case rows, headers and page titles of settings pages
  }

  /** The label of a list item in Expressive design: as written (never in capitals), sentence case where Telegram X's English is title case. */
  @Nullable
  public static CharSequence label (@NonNull ListItem item) {
    final int res = item.getStringResource();
    final int sentence = sentenceFor(res);
    if (sentence != 0) {
      return sentence(res, sentence);
    }
    return item.getString();
  }

  /** {@link #sentence} for one of the strings this file knows, else Telegram X's string. */
  @NonNull
  public static String label (@StringRes int res) {
    final int sentence = sentenceFor(res);
    return sentence != 0 ? sentence(res, sentence) : Lang.getString(res);
  }

  // Empty states

  /**
   * The empty state's icon: the item's own ({@code iconRes}), else one chosen by the empty text's string id, else the
   * info glyph. Not mapped through the icon pack yet ({@link #bindEmpty} does that).
   */
  @DrawableRes
  public static int emptyIcon (@StringRes int stringRes, @DrawableRes int iconRes) {
    if (iconRes != 0) {
      return iconRes;
    }
    if (stringRes == 0) {
      return R.drawable.baseline_info_24;
    }
    if (stringRes == R.string.NoCalls) {
      return R.drawable.baseline_call_24;
    } else if (stringRes == R.string.NothingFound || stringRes == R.string.RegionNotFound) {
      return R.drawable.baseline_search_24;
    } else if (stringRes == R.string.NoContacts) {
      return R.drawable.baseline_group_96; // as the Contacts page's empty state
    } else if (stringRes == R.string.BlockListEmpty) {
      return R.drawable.baseline_block_24;
    } else if (stringRes == R.string.NoArchive) {
      return R.drawable.baseline_archive_24;
    } else if (stringRes == R.string.NoChats || stringRes == R.string.NoGroups || stringRes == R.string.NoGroupsToShow ||
      stringRes == R.string.NoChannels || stringRes == R.string.NoPrivateChats || stringRes == R.string.NoBotsChats ||
      stringRes == R.string.NoUnreadChats || stringRes == R.string.NoCommentChats) {
      return R.drawable.baseline_forum_96; // as the main chat list's empty state (JoinedUsersView)
    } else if (stringRes == R.string.NoStickerSets) {
      return R.drawable.deproko_baseline_stickers_24;
    } else if (stringRes == R.string.NoMasks) {
      return R.drawable.deproko_baseline_masks_24;
    } else if (stringRes == R.string.ExperimentalSettingsUnavailable) {
      return R.drawable.baseline_bug_report_24;
    } else if (stringRes == R.string.XgramHistoryEmpty) {
      return R.drawable.baseline_history_24;
    } else if (stringRes == R.string.NoOtherSessions || stringRes == R.string.NoOtherSessionsInfo) {
      return R.drawable.baseline_devices_other_24;
    } else if (stringRes == R.string.NoActiveLogins) {
      return R.drawable.baseline_language_24;
    }
    return R.drawable.baseline_info_24;
  }

  /**
   * Splits an empty text into the headline and the supporting text: "Title\n\nText" (Telegram X's bold first line)
   * is both, a short line is the headline alone, a long hint is the supporting text alone.
   */
  public static void bindEmpty (@NonNull XgramListStateView view, @DrawableRes int iconRes, @Nullable CharSequence s) {
    final CharSequence[] parts = splitEmpty(s);
    view.setEmpty(iconRes != 0 ? XgramIconPack.map(iconRes) : 0, parts[0], parts[1]);
  }

  /** {headline, supporting text} of an empty text, either may be null (see {@link #bindEmpty}). */
  @NonNull
  public static CharSequence[] splitEmpty (@Nullable CharSequence s) {
    CharSequence title = null, text = null;
    if (!TextUtils.isEmpty(s)) {
      final String str = s.toString();
      final int split = str.indexOf("\n\n");
      if (split > 0) {
        title = str.substring(0, split).trim(); // plain: the type scale sets the weight, not a bold span
        text = s.subSequence(split + 2, s.length());
        if (TextUtils.isEmpty(text)) {
          text = null;
        }
      } else if (str.length() <= 48 && str.indexOf('\n') == -1) {
        title = s;
      } else {
        text = s;
      }
    }
    return new CharSequence[] {title, text};
  }

  /** {@link #bindEmpty(XgramListStateView, int, CharSequence)} for a SettingsAdapter item. */
  public static void bindEmpty (@NonNull XgramListStateView view, @NonNull ListItem item) {
    if (item.getStringResource() == R.string.NoContacts && item.getIconResource() == 0) {
      // The same state as the Contacts page: people, "No contacts" and its supporting line (the line only with the
      // sentence case English title: a translation keeps Telegram X's string alone)
      final String title = sentence(R.string.NoContacts, R.string.XgramR5PeopleNoContacts);
      final boolean english = !title.equals(Lang.getString(R.string.NoContacts));
      view.setEmpty(XgramIconPack.map(R.drawable.baseline_group_96), title, english ? Lang.getString(R.string.XgramR5PeopleNoContactsHint) : null);
      return;
    }
    bindEmpty(view, emptyIcon(item.getStringResource(), item.getIconResource()), item.getString());
  }

  /** A full-page empty state (TYPE_EMPTY, ChatsAdapter's empty row): the page's height, the state centred in it. */
  public static XgramListStateView newEmptyPage (Context context, @Nullable View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
    XgramListStateView view = new XgramListStateView(context);
    view.setPadding(0, Screen.dp(16f), 0, Screen.dp(16f));
    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (onClickListener != null) {
      view.setOnClickListener(onClickListener);
    }
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
    return view;
  }

  /**
   * TYPE_ICONIZED_EMPTY and TYPE_WEBSITES_EMPTY: the empty state at its own height, with the space above it grown so the
   * state and the rows after it (the button group) sit in the middle of the list. The state view is child 0.
   */
  public static FrameLayoutFix newEmptyBlock (Context context, @Nullable ViewController<?> themeProvider) {
    final XgramListStateView state = new XgramListStateView(context);
    state.setWrapContent(true);
    state.setPadding(0, Screen.dp(24f), 0, Screen.dp(24f));
    state.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    FrameLayoutFix wrap = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        final int contentHeight = state.getMeasuredHeight();
        int height = contentHeight;
        RecyclerView list = findList(this);
        Object tag = getTag();
        if (list != null && list.getAdapter() instanceof org.thunderdog.challegram.ui.SettingsAdapter && tag instanceof ListItem) {
          List<ListItem> items = ((org.thunderdog.challegram.ui.SettingsAdapter) list.getAdapter()).getItems();
          final int position = items.indexOf(tag);
          final int listHeight = list.getMeasuredHeight() - list.getPaddingTop() - list.getPaddingBottom();
          final int above = position > 0 ? heightOf(items, 0, position) : 0;
          final int below = position != -1 ? heightOf(items, position + 1, items.size()) : 0;
          final int free = listHeight - above - contentHeight - below;
          if (free > 0) {
            height = contentHeight + free / 2;
          }
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
      }
    };
    wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    wrap.addView(state);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(state);
    }
    return wrap;
  }

  /** The heights SettingHolder knows for the rows {@code from} until {@code to}; rows of other heights count as none. */
  private static int heightOf (List<ListItem> items, int from, int to) {
    int sum = 0;
    for (int i = Math.max(0, from); i < Math.min(to, items.size()); i++) {
      final int type = items.get(i).getViewType();
      if (type == ListItem.TYPE_ICONIZED_EMPTY || type == ListItem.TYPE_WEBSITES_EMPTY || type == ListItem.TYPE_DESCRIPTION) {
        continue;
      }
      try {
        sum += SettingHolder.measureHeightForType(items.get(i));
      } catch (AssertionError ignored) {
        // a row without a fixed height
      }
    }
    return sum;
  }

  @Nullable
  static RecyclerView findList (View view) {
    ViewParent parent = view.getParent();
    while (parent != null && !(parent instanceof RecyclerView)) {
      parent = parent.getParent();
    }
    return (RecyclerView) parent;
  }

  /** The state view of {@link #newEmptyBlock}, or null when {@code itemView} is Telegram X's. */
  @Nullable
  public static XgramListStateView emptyBlockState (View itemView) {
    if (itemView instanceof ViewGroup && ((ViewGroup) itemView).getChildCount() > 0 && ((ViewGroup) itemView).getChildAt(0) instanceof XgramListStateView) {
      return (XgramListStateView) ((ViewGroup) itemView).getChildAt(0);
    }
    return null;
  }

  // TYPE_BUTTON

  /** A page's single main action (a filled primary pill); every other list button is filled tonal. */
  public static boolean isPrimaryButton (int id) {
    return id == R.id.btn_redeemGiftLink || id == R.id.btn_setPassword || id == R.id.btn_changePhoneNumber || id == R.id.btn_buyPremium;
  }

  /**
   * TYPE_BUTTON with Expressive design: the row keeps Telegram X's height and its ScalableTextView child, which is now a
   * centred 40dp pill (48dp touch target) that takes the click. The row's background is a morph that draws nothing, so
   * the list decoration shapes it instead of drawing a tile behind the button.
   */
  public static FrameLayoutFix newButtonRow (Context context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
    FrameLayoutFix frame = new FrameLayoutFix(context);
    frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(57f)));
    frame.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    frame.setBackground(new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, Float.NaN));
    PillButton pill = new PillButton(context);
    pill.setOnClickListener(onClickListener);
    pill.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f), Gravity.CENTER));
    frame.addView(pill);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(pill);
    }
    return frame;
  }

  /** Binds a {@link #newButtonRow} to {@code item}; false for Telegram X's row. */
  public static boolean bindButtonRow (View itemView, ListItem item) {
    if (!(itemView instanceof ViewGroup) || ((ViewGroup) itemView).getChildCount() == 0 || !(((ViewGroup) itemView).getChildAt(0) instanceof PillButton)) {
      return false;
    }
    PillButton pill = (PillButton) ((ViewGroup) itemView).getChildAt(0);
    pill.setId(item.getId());
    pill.setTag(item);
    pill.setPrimary(isPrimaryButton(item.getId()));
    return true;
  }

  /** An M3 filled button (40dp, full corners morphing to 8dp on press, labelLarge), primary or tonal. */
  public static final class PillButton extends ScalableTextView {
    private boolean primary, built;

    public PillButton (Context context) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setGravity(Gravity.CENTER);
      setMinimumWidth(Screen.dp(64f));
      setMaxWidth(Screen.dp(400f));
      Views.setClickable(this);
      buildBackground();
    }

    public void setPrimary (boolean primary) {
      if (this.primary != primary || !built) {
        this.primary = primary;
        buildBackground();
        invalidate();
      }
    }

    private int containerRole () {
      return primary ? XgramExpressive.PRIMARY : XgramExpressive.SECONDARY_CONTAINER;
    }

    private int contentRole () {
      return primary ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_SECONDARY_CONTAINER;
    }

    private void buildBackground () {
      built = true;
      final int role = containerRole();
      XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(this, XgramExpressive.color(role), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
      morph.setColorRole(role);
      Drawable background = getBackground();
      // 40dp container in the 48dp touch target
      setBackground(new InsetDrawable(background, 0, Screen.dp(4f), 0, Screen.dp(4f)));
      // after setBackground: it replaces the padding with the inset's (0 at the sides), so the label touched the pill's edges
      setPadding(Screen.dp(24f), 0, Screen.dp(24f), 0);
    }

    @Override
    protected void onReplaceText (@NonNull CharSequence replacement) {
      setText(replacement);
    }

    @Override
    protected void onDraw (Canvas c) {
      int color = XgramExpressive.color(contentRole());
      if (!isEnabled()) {
        color = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.DISABLED_CONTENT);
      }
      if (getCurrentTextColor() != color) {
        setTextColor(color);
      }
      super.onDraw(c);
    }
  }

  // Text in a role

  /** A TextView in a type scale style whose color is a role, re-read when it draws (theme changes only invalidate). */
  public static class RoleText extends NoScrollTextView {
    private int role;

    public RoleText (Context context, int textStyle, int role) {
      super(context);
      this.role = role;
      XgramExpressive.applyText(this, textStyle, false);
      setTextColor(XgramExpressive.color(role));
    }

    public void setRole (int role) {
      if (this.role != role) {
        this.role = role;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final int color = XgramExpressive.color(role);
      if (getCurrentTextColor() != color) {
        setTextColor(color);
      }
      super.onDraw(c);
    }
  }

  /** TYPE_BUILD_NO (Settings root): labelMedium onSurfaceVariant, centred, tabular figures, 24dp below. */
  public static TextView newBuildNo (Context context, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, @Nullable ViewController<?> themeProvider) {
    RoleText textView = new RoleText(context, XgramExpressive.TS_LABEL_M, XgramExpressive.ON_SURFACE_VARIANT);
    textView.setGravity(Gravity.CENTER);
    textView.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(24f));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      textView.setFontFeatureSettings("tnum");
    }
    textView.setOnClickListener(onClickListener);
    textView.setOnLongClickListener(onLongClickListener);
    textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(textView);
    }
    return textView;
  }
}
