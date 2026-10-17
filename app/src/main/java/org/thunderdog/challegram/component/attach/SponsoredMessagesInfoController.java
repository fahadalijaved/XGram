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
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;

public class SponsoredMessagesInfoController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public SponsoredMessagesInfoController (MediaLayout context, int titleResource) {
    super(context, titleResource);
  }

  // XGram M3E r6: the sheet in the modal sheet color (ColorId.drawer, surfaceContainerLow) with no shadow bands: a
  // headlineSmall title and bodyMedium paragraphs 24dp in, the link as a text button, and an actions row with
  // [Learn more] at the start and a filled [Continue] at the end.

  private static final int XGRAM_TYPE_CONTENT = 1;
  private @androidx.annotation.Nullable View xgramContent;

  @Override
  protected int getRecyclerBackgroundColorId () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.drawer : super.getRecyclerBackgroundColorId();
  }

  @Override
  protected int getInitialContentHeight () {
    if (xgramContent != null) {
      int width = getCurrentWidth();
      if (width <= 0) {
        width = org.thunderdog.challegram.tool.Screen.currentWidth();
      }
      xgramContent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      return xgramContent.getMeasuredHeight();
    }
    return super.getInitialContentHeight();
  }

  private View xgramBuildContent (Context context) {
    final int pad = org.thunderdog.challegram.tool.Screen.dp(24f);
    android.widget.LinearLayout content = new android.widget.LinearLayout(context);
    content.setOrientation(android.widget.LinearLayout.VERTICAL);
    content.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    android.widget.LinearLayout column = new android.widget.LinearLayout(context);
    column.setOrientation(android.widget.LinearLayout.VERTICAL);
    column.setPadding(pad, org.thunderdog.challegram.tool.Screen.dp(8f), pad, 0);

    android.widget.TextView title = new org.thunderdog.challegram.widget.NoScrollTextView(context);
    com.fahadali.xgram.appearance.XgramExpressive.applyText(title, com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S, false);
    title.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
    title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    title.setText(Lang.getString(R.string.SponsoredInfoMenu));
    addThemeTextColorListener(title, ColorId.text);
    column.addView(title, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    column.addView(xgramParagraph(context, Lang.getString(R.string.SponsoredInfoText)), xgramParagraphParams(16f));

    final String url = Lang.getString(R.string.url_promote);
    android.widget.TextView link = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context, url, com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
    link.setId(R.id.btn_openLink);
    link.setOnClickListener(this);
    android.widget.LinearLayout.LayoutParams linkParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, org.thunderdog.challegram.tool.Screen.dp(40f));
    linkParams.setMarginStart(-org.thunderdog.challegram.tool.Screen.dp(12f)); // the label lines up with the text, the state layer reaches out
    linkParams.topMargin = org.thunderdog.challegram.tool.Screen.dp(8f);
    column.addView(link, linkParams);

    column.addView(xgramParagraph(context, Lang.getString(R.string.SponsoredInfoText2)), xgramParagraphParams(4f));
    content.addView(column, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    android.widget.TextView learnMore = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context,
      com.fahadali.xgram.appearance.XgramR5Sheets.sentenceCase(R.string.SponsoredInfoAction, R.string.XgramR6B3LearnMore), com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
    learnMore.setId(R.id.btn_openLink);
    learnMore.setOnClickListener(this);
    android.widget.TextView proceed = com.fahadali.xgram.appearance.XgramR5Sheets.filledButton(context, Lang.getString(R.string.Continue), false, null);
    proceed.setId(R.id.btn_close);
    proceed.setOnClickListener(this);
    android.widget.LinearLayout actions = com.fahadali.xgram.appearance.XgramR5Sheets.actionsRow(context, learnMore, proceed);
    android.widget.LinearLayout.LayoutParams actionsParams = (android.widget.LinearLayout.LayoutParams) actions.getLayoutParams();
    actionsParams.topMargin = org.thunderdog.challegram.tool.Screen.dp(12f);
    actionsParams.bottomMargin = org.thunderdog.challegram.tool.Screen.dp(8f);
    content.addView(actions, actionsParams);
    return content;
  }

  private android.widget.TextView xgramParagraph (Context context, CharSequence text) {
    android.widget.TextView view = new org.thunderdog.challegram.widget.NoScrollTextView(context);
    com.fahadali.xgram.appearance.XgramExpressive.applyText(view, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
    view.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT));
    view.setLineSpacing(org.thunderdog.challegram.tool.Screen.dp(4f), 1f);
    view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    view.setText(text);
    addThemeTextColorListener(view, ColorId.textLight);
    return view;
  }

  private static android.widget.LinearLayout.LayoutParams xgramParagraphParams (float topMarginDp) {
    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = org.thunderdog.challegram.tool.Screen.dp(topMarginDp);
    return params;
  }

  @Override
  protected View onCreateView (Context context) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      buildContentView(false);
      setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
      xgramContent = xgramBuildContent(context);
      setAdapter(adapter = new SettingsAdapter(this) {
        @Override
        protected org.thunderdog.challegram.ui.SettingHolder initCustom (ViewGroup parent, int customViewType) {
          if (customViewType == XGRAM_TYPE_CONTENT && xgramContent != null) {
            if (xgramContent.getParent() instanceof ViewGroup) {
              ((ViewGroup) xgramContent.getParent()).removeView(xgramContent);
            }
            return new org.thunderdog.challegram.ui.SettingHolder(xgramContent);
          }
          throw new IllegalArgumentException("customViewType=" + customViewType);
        }
      });
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_CUSTOM - XGRAM_TYPE_CONTENT)
      }, false);
      initMetrics(); // the sheet opens at the content's height
      return contentView;
    }
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    setAdapter(adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_openLink) {
          view.setIconColorId(ColorId.textNeutral);
        } else {
          view.setIconColorId(ColorId.icon);
        }
      }
    });

    ViewSupport.setThemedBackground(recyclerView, ColorId.background);

    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL),
      new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SponsoredInfoText),
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_openLink, R.drawable.baseline_language_24, Lang.getString(R.string.url_promote), false).setTextColorId(ColorId.textNeutral),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
      new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SponsoredInfoText2),
    }, false);

    return contentView;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    RecyclerView rv = new RecyclerView(context);
    rv.setLayoutManager(new LinearLayoutManager(context));
    SettingsAdapter sa = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_close) {
          view.setIconColorId(ColorId.textNeutral);
        } else {
          view.setIconColorId(ColorId.icon);
        }
      }
    };
    rv.setAdapter(sa);
    sa.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_close, R.drawable.baseline_check_circle_24, R.string.Continue).setTextColorId(ColorId.textNeutral),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_openLink, R.drawable.baseline_help_24, R.string.SponsoredInfoAction),
    }, false);
    return rv;
  }

  @Override
  public int getId () {
    return R.id.controller_sponsoredMessagesInfo;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_openLink) {
      mediaLayout.hide(false);
      Intents.openUriInBrowser(Uri.parse(Lang.getString(R.string.url_promote)));
    } else if (v.getId() == R.id.btn_close && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      mediaLayout.hide(false); // XGram M3E r6: Continue
    }
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (!mediaLayout.isHidden()) {
      if (commit) {
        mediaLayout.hide(false);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }
}
