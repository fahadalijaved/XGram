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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Sheets;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessageOptionsPagerController;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * The quick reaction bar of the message menu (Messages → Message menu → Reaction bar): in the sheet's header row as in
 * Telegram X, under the options (a row of the options list, just above Delete), or off. Off keeps the header row's
 * who reacted and who viewed counters, so the reaction pages stay reachable.
 */
public final class XgramReactionBar {
  private XgramReactionBar () { }

  /** Height of the bar row in the options list: the header row's (the strip's 40dp reactions and its padding). */
  public static final float ROW_HEIGHT = 54f;

  public static String modeName (int mode) {
    switch (mode) {
      case XgramSettings.REACTION_BAR_BOTTOM:
        return Lang.getString(R.string.XgramReactionBarBottom);
      case XgramSettings.REACTION_BAR_OFF:
        return Lang.getString(R.string.XgramReactionBarOff);
      case XgramSettings.REACTION_BAR_TOP:
      default:
        return Lang.getString(R.string.XgramReactionBarTop);
    }
  }

  /**
   * The mode's name while the popup menu is on (XgramMessagePopup): it always shows the reactions above the message,
   * so Top and Bottom are both just On there.
   */
  public static String popupModeName (int mode) {
    return Lang.getString(mode == XgramSettings.REACTION_BAR_OFF ? R.string.XgramReactionBarOff : R.string.XgramStateOn);
  }

  /**
   * The mode of one message sheet: the setting, or Top for a sheet without the options page (a tap on reactions). A
   * channel post gets Off while Reactions in channels is off: the sheet is the one Off gives, whatever the setting.
   */
  public static int mode (@NonNull TGMessage message, boolean hasOptions) {
    if (!hasOptions) {
      return XgramSettings.REACTION_BAR_TOP;
    }
    return message.xgramHidesReactions() ? XgramSettings.REACTION_BAR_OFF : XgramSettings.instance().reactionBar();
  }

  /**
   * Where the bar row goes among the options: just above Delete when it ends the menu (only moderation options or More
   * after it, as in Telegram X's order), otherwise after the last option.
   */
  public static int indexIn (@Nullable ViewController.OptionItem[] items) {
    if (items == null) {
      return 0;
    }
    for (int i = items.length - 1; i >= 0; i--) {
      final int id = items[i] != null ? items[i].id : 0;
      if (id == R.id.btn_messageDelete) {
        return i;
      }
      if (!isTrailingOption(id)) {
        break;
      }
    }
    return items.length;
  }

  /** The options Telegram X lists after Delete: moderation (as XgramMessageMenu.GROUP_MODERATION) and More. */
  private static boolean isTrailingOption (int id) {
    return id == R.id.btn_messageReport || id == R.id.btn_messageRestrictMember || id == R.id.btn_messageBlockUser ||
      id == R.id.btn_xgramShadowBan || id == R.id.btn_messageMore;
  }

  /** The room the bar row takes in the options list: its height, and with Expressive design the 8dp gap of its own group. */
  public static int rowSpace () {
    return Screen.dp(ROW_HEIGHT) + (XgramExpressive.isEnabled() ? Screen.dp(8f) : 0);
  }

  /** The bar row: the reaction strip, and the expand button when {@code onExpand} opens the popup picker. */
  public static View createRow (@NonNull Context context, @NonNull MessageOptionsPagerController.State state, @Nullable Runnable onExpand) {
    return new Row(context, state, onExpand);
  }

  /**
   * The header row's reaction strip and expand button, laid out alone. With Expressive design: the strip in its pill from
   * the 16dp inset to 8dp before the 40dp filled tonal expand button, which ends at the inset. Otherwise Telegram X's
   * strip on the header row's background color, with its 56dp arrow button at the end.
   */
  @SuppressLint("ViewConstructor")
  private static final class Row extends FrameLayoutFix implements ThemeInvalidateListener {
    private final boolean m3;
    private final ReactionsSelectorRecyclerView strip;
    private final @Nullable ImageView expand;

    Row (Context context, MessageOptionsPagerController.State state, @Nullable Runnable onExpand) {
      super(context);
      this.m3 = XgramExpressive.isEnabled();
      final boolean hasExpand = onExpand != null;

      strip = new ReactionsSelectorRecyclerView(context, state);
      if (m3) {
        // the strip draws its pill 4dp inside its bounds
        final float pillInset = 4f;
        strip.setLayoutParams(FrameLayoutFix.newParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL,
          Screen.dp(XgramExpressive.LIST_INSET - pillInset), 0,
          Screen.dp(XgramExpressive.LIST_INSET - pillInset) + (hasExpand ? Screen.dp(XgramM3Sheets.ICON_BUTTON_SIZE + 8f) : 0), 0));
        strip.setNeedDrawBorderGradient(true);
        strip.addItemDecoration(new RecyclerView.ItemDecoration() {
          @Override
          public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State rvState) {
            // cancels the strip's own -1dp overlap of its 40dp reaction views, as in the header row
            outRect.set(Screen.dp(1f), 0, Screen.dp(1f), 0);
          }
        });
      } else {
        strip.setLayoutParams(FrameLayoutFix.newParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL,
          0, 0, hasExpand ? Screen.dp(56f) : 0, 0));
        strip.setNeedDrawBorderGradient(hasExpand);
      }
      addView(strip);

      if (hasExpand) {
        expand = new ImageView(context);
        if (m3) {
          // 40dp secondaryContainer circle in a 48dp target, ending at the 16dp inset
          expand.setLayoutParams(FrameLayoutFix.newParams(
            Screen.dp(XgramM3Sheets.TOUCH_TARGET), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL,
            0, 0, Screen.dp(XgramExpressive.LIST_INSET - (XgramM3Sheets.TOUCH_TARGET - XgramM3Sheets.ICON_BUTTON_SIZE) / 2f), 0));
          expand.setImageResource(R.drawable.xgram_w2_menu_sheets_expand_more_24);
          XgramM3Sheets.attachTonalIconButton(expand);
        } else {
          expand.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
          expand.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_small_arrow_down_24));
          RippleSupport.setTransparentSelector(expand);
        }
        expand.setScaleType(ImageView.ScaleType.CENTER);
        expand.setContentDescription(Lang.getString(R.string.XgramMoreReactions));
        expand.setOnClickListener(v -> onExpand.run());
        Views.setClickable(expand);
        addView(expand);
      } else {
        expand = null;
      }
      updateColors();

      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ROW_HEIGHT)));
    }

    private void updateColors () {
      if (expand != null) {
        expand.setColorFilter(Paints.getColorFilter(m3 ? XgramM3Sheets.tonalContentColor() : Theme.getColor(ColorId.icon)));
      }
      if (!m3) {
        setBackgroundColor(Theme.backgroundColor()); // the header row's color, which the strip's end gradient fades into
      }
    }

    @Override
    public void onThemeInvalidate (boolean isTempUpdate) {
      updateColors();
      strip.invalidate();
      invalidate();
    }
  }
}
