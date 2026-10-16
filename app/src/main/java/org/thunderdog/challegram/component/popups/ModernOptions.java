/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;

public class ModernOptions {
  public static void showLocationAlert (ViewController<?> context, @Nullable String currentInlineUsername, Runnable onCancel, Runnable onAgree) {
    String inlineUsername = "@" + currentInlineUsername;
    showLocationAlert(context, Lang.getStringBold(R.string.LocationAlertBot, inlineUsername), Lang.getStringBold(R.string.LocationAlertBotDisclaimer, inlineUsername), onCancel, onAgree);
  }

  public static void showLocationAlert (ViewController<?> context, boolean isBackground, Runnable onCancel, Runnable onAgree) {
    Permissions permissions = context.context().permissions();
    boolean hasPermissions = permissions.canAccessLocation() && (!isBackground || permissions.canAccessLocationInBackground());
    if (hasPermissions) {
      onAgree.run();
      return;
    }
    // FIXME: should it actually check for permission rationale?
    if (
      (isBackground && permissions.shouldShowBackgroundLocationRationale()) ||
      permissions.shouldShowAccessLocationRationale()) {
      showLocationAlert(context, Lang.getString(isBackground ? R.string.LocationAlertLiveLocation : R.string.LocationAlertLocation), Lang.getString(R.string.LocationAlertLocationDisclaimer), isBackground ? onCancel : () -> {}, onAgree);
    } else {
      onAgree.run();
    }
  }

  private static void showLocationAlert (ViewController<?> context, CharSequence firstLine, CharSequence secondLine, Runnable onCancel, Runnable onAgree) {
    if (!context.isFocused()) {
      context.addOneShotFocusListener(() -> {
        showLocationAlert(context, firstLine, secondLine, onCancel, onAgree);
      });
      return;
    }
    Lang.SpanCreator firstItalicCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 1 ? Lang.newItalicSpan(needFakeBold) : null;
    CharSequence desc = Lang.getString(R.string.format_doubleLines, firstItalicCreator, firstLine, secondLine);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramShowHeroConfirm(context, R.drawable.baseline_location_on_48, Lang.getString(R.string.XgramR5SheetsLocationTitle), desc, onCancel, onAgree);
      return;
    }

    addBigColoredHeader(context.showOptions(
      desc,
      new int[]{R.id.btn_done, R.id.btn_privacyPolicy, R.id.btn_cancel},
      new String[]{Lang.getString(R.string.Continue), Lang.getString(R.string.PrivacyPolicy), Lang.getString(R.string.Cancel)},
      new int[]{ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[]{R.drawable.baseline_check_circle_24, R.drawable.baseline_policy_24, R.drawable.baseline_cancel_24},
      new OptionDelegate() {
        @Override
        public boolean disableCancelOnTouchdown () {
          return true;
        }

        @Override
        public boolean onOptionItemPressed (View optionItemView, int id) {
          final int optionItemId = optionItemView.getId();
          if (optionItemId == R.id.btn_done) {
            onAgree.run();
          } else if (optionItemId == R.id.btn_privacyPolicy) {
            onCancel.run();
            context.tdlib().ui().openUrl(context, Lang.getStringSecure(R.string.url_privacyPolicy), new TdlibUi.UrlOpenParameters().forceInstantView());
          } else if (optionItemId == R.id.btn_cancel) {
            onCancel.run();
          }

          return true;
        }
      }
    ), R.drawable.baseline_location_on_48);
  }

  /**
   * XGram M3E r5: the big colored header sheet as an M3E hero sheet. Under the handle a 96dp 9-sided cookie in
   * primaryContainer holds the icon in onPrimaryContainer, then the headlineSmall title and the bodyMedium text
   * (onSurfaceVariant), centered, and the actions row: "Privacy policy" as a text button at the start, then "Cancel"
   * and the filled "Continue" at the end. The scrim does not close it (as Telegram X's), so Cancel stays.
   */
  private static void xgramShowHeroConfirm (ViewController<?> context, @DrawableRes int icon, CharSequence title, CharSequence text, Runnable onCancel, Runnable onAgree) {
    PopupLayout popup = context.showPopup(null, false, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      final Context ctx = optionsLayout.getContext();
      final int width = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidth(context.context().getControllerWidth(optionsLayout));

      com.fahadali.xgram.appearance.XgramR5Sheets.ShapeIconView hero = new com.fahadali.xgram.appearance.XgramR5Sheets.ShapeIconView(ctx, 96f, 48f).setIcon(icon);
      hero.setContentDescription(title);
      hero.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      TextView titleView = new NoScrollTextView(ctx);
      com.fahadali.xgram.appearance.XgramExpressive.applyText(titleView, com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S, false);
      titleView.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
      titleView.setGravity(Gravity.CENTER_HORIZONTAL);
      titleView.setText(title);
      titleView.setPadding(Screen.dp(24f), 0, Screen.dp(24f), 0);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      titleParams.topMargin = Screen.dp(16f);
      titleView.setLayoutParams(titleParams);

      TextView textView = new NoScrollTextView(ctx);
      com.fahadali.xgram.appearance.XgramExpressive.applyText(textView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
      textView.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT));
      textView.setGravity(Gravity.CENTER_HORIZONTAL);
      textView.setText(text);
      textView.setPadding(Screen.dp(24f), 0, Screen.dp(24f), 0);
      LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      textParams.topMargin = Screen.dp(8f);
      textView.setLayoutParams(textParams);

      TextView privacy = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(ctx,
        com.fahadali.xgram.appearance.XgramR5Sheets.sentenceCase(R.string.PrivacyPolicy, R.string.XgramR5SheetsPrivacyPolicy),
        com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
      privacy.setOnClickListener(v -> {
        popupLayout.hideWindow(true);
        onCancel.run();
        context.tdlib().ui().openUrl(context, Lang.getStringSecure(R.string.url_privacyPolicy), new TdlibUi.UrlOpenParameters().forceInstantView());
      });
      TextView cancel = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(ctx, Lang.getString(R.string.Cancel), com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
      cancel.setOnClickListener(v -> {
        popupLayout.hideWindow(true);
        onCancel.run();
      });
      TextView agree = com.fahadali.xgram.appearance.XgramR5Sheets.filledButton(ctx, Lang.getString(R.string.Continue), false, null);
      agree.setOnClickListener(v -> {
        popupLayout.hideWindow(true);
        onAgree.run();
      });
      LinearLayout actions = com.fahadali.xgram.appearance.XgramR5Sheets.actionsRow(ctx, privacy, cancel, agree);
      LinearLayout.LayoutParams actionsParams = (LinearLayout.LayoutParams) actions.getLayoutParams();
      actionsParams.topMargin = Screen.dp(16f);
      actionsParams.bottomMargin = Screen.dp(8f);
      actions.setLayoutParams(actionsParams);

      optionsLayout.addView(hero);
      optionsLayout.addView(titleView);
      optionsLayout.addView(textView);
      optionsLayout.addView(actions);

      // the height the sheet slides in by: every part measured at the sheet's width
      int height = 0;
      final int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
      final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
      View[] parts = {hero, titleView, textView};
      for (View part : parts) {
        part.measure(widthSpec, heightSpec);
        height += part.getMeasuredHeight() + ((LinearLayout.LayoutParams) part.getLayoutParams()).topMargin;
      }
      height += actionsParams.height + actionsParams.topMargin + actionsParams.bottomMargin;
      return height;
    }, null, com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S);
    if (popup != null) {
      popup.setDisableCancelOnTouchDown(true);
    }
  }

  private static void addBigColoredHeader (PopupLayout layout, @DrawableRes int icon) {
    if (!(layout.getChildAt(1) instanceof OptionsLayout)) {
      return;
    }

    OptionsLayout optionsLayout = (OptionsLayout) layout.getChildAt(1);

    ImageView coloredHeader = new ImageView(layout.getContext());
    coloredHeader.setBackgroundColor(Theme.getColor(ColorId.fillingPositive));
    coloredHeader.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(icon));
    coloredHeader.setScaleType(ImageView.ScaleType.CENTER);
    coloredHeader.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.fillingPositiveContent)));
    coloredHeader.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Screen.dp(132f)));

    optionsLayout.addView(coloredHeader, 1);
  }
}
