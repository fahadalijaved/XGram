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
 *
 * File created on 25/02/2016 at 15:56
 */
package org.thunderdog.challegram.component.preview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.EmbeddedService;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.widget.FrameLayoutFix;

public abstract class PreviewLayout extends FrameLayoutFix implements View.OnClickListener, PopupLayout.ShowListener, PopupLayout.DismissListener, RootFrameLayout.InsetsChangeListener, com.fahadali.xgram.appearance.XgramSheetDrag.Sheet {
  protected EmbeddedService nativeEmbed;
  protected int footerHeight;
  protected final ViewController<?> parent;

  private final ThemeListenerList themeListeners = new ThemeListenerList();

  /** XGram M3E r5: the embed preview is an M3 sheet (see xgramAddActions and onDraw). Fixed for the layout's life. */
  protected final boolean xgramSheet = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();

  public PreviewLayout (Context context, final ViewController<?> parent) {
    super(context);
    this.parent = parent;

    if (xgramSheet) {
      xgramAddActions();
      setWillNotDraw(false);
    } else {
    addFooterItem(R.id.btn_share, R.string.Share, R.drawable.baseline_forward_24);
    addFooterItem(R.id.btn_openLink, R.string.OpenInExternalApp,  R.drawable.baseline_open_in_browser_24);
    }

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, xgramSheet ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM));
    themeListeners.addThemeInvalidateListener(this);

    UI.getContext(context).addGlobalThemeListeners(themeListeners);
  }

  // XGram M3E r5: an M3 sheet at most 640dp wide and centered: the 28dp sheet top in surfaceContainerLow with its drag
  // handle (the first 48dp), the preview under it, and the actions row (two filled tonal buttons) at the bottom; the
  // sheet color goes on under the navigation bar.

  /** The height of the handle band above the preview (dp). */
  public static final float XGRAM_BAND_DP = 48f;

  private void xgramAddActions () {
    TextView share = com.fahadali.xgram.appearance.XgramR5Sheets.tonalButton(getContext(), Lang.getString(R.string.Share), null);
    share.setId(R.id.btn_share);
    share.setOnClickListener(this);
    TextView open = com.fahadali.xgram.appearance.XgramR5Sheets.tonalButton(getContext(), Lang.getString(R.string.XgramR5SheetsOpenInBrowser), null);
    open.setId(R.id.btn_openLink);
    open.setOnClickListener(this);
    android.widget.LinearLayout row = com.fahadali.xgram.appearance.XgramR5Sheets.actionsRow(getContext(), null, share, open);
    final int height = Screen.dp(com.fahadali.xgram.appearance.XgramR5Sheets.ACTIONS_HEIGHT);
    row.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM));
    footerHeight = height;
    addView(row);
  }

  /** XGram M3E r5: true while the preview plays full screen (the status bar inset then counts again). */
  protected boolean xgramIsFullscreen () {
    return false;
  }

  // XGram M3E: the sheet drag (XgramSheetDrag through PopupLayout) starts on the 48dp handle band only: the embedded
  // player under it keeps its own gestures. Far or fast enough down the sheet closes, otherwise it springs back.

  @Override
  public boolean xgramSheetContains (float x, float y) {
    return xgramSheet && !xgramIsFullscreen() && y >= 0f && y < Screen.dp(XGRAM_BAND_DP);
  }

  @androidx.annotation.Nullable
  @Override
  public androidx.recyclerview.widget.RecyclerView xgramSheetList () {
    return null;
  }

  private final android.graphics.RectF xgramRect = new android.graphics.RectF();
  private final android.graphics.Paint xgramPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

  private void xgramDrawSheet (@NonNull Canvas c) {
    final float corner = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_XL);
    final int width = getMeasuredWidth(), height = getMeasuredHeight();
    xgramRect.set(0, 0, width, height + corner); // the bottom corners stay below the view
    xgramPaint.setColor(com.fahadali.xgram.appearance.XgramM3Sheets.sheetColor());
    c.drawRoundRect(xgramRect, corner, corner, xgramPaint);
    final float handleWidth = Screen.dp(32f), handleHeight = Screen.dp(4f);
    final float handleTop = (Screen.dp(XGRAM_BAND_DP) - handleHeight) / 2f;
    xgramRect.set(width / 2f - handleWidth / 2f, handleTop, width / 2f + handleWidth / 2f, handleTop + handleHeight);
    xgramPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT), .4f));
    c.drawRoundRect(xgramRect, handleHeight / 2f, handleHeight / 2f, xgramPaint);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_openLink) {
      popupLayout.hideWindow(true);
      UI.openUrl(nativeEmbed.viewUrl);
    } else if (viewId == R.id.btn_share) {
      popupLayout.hideWindow(true);
      TD.shareLink(parent, nativeEmbed.viewUrl);
    }
  }

  protected void setFooterVisibility (int visibility) {
    int i = 0;
    View view;
    while (i < getChildCount() && (view = getChildAt(i)) instanceof TextView) {
      view.setVisibility(visibility);
      i++;
    }
  }

  protected void addFooterItem (int id, @StringRes int stringRes, int icon) {
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f), Gravity.BOTTOM);
    params.bottomMargin = footerHeight;
    footerHeight += params.height;

    TextView item;
    item = OptionsLayout.genOptionView(getContext(), id, Lang.getString(stringRes), ViewController.OptionColor.NORMAL, icon, ViewController.OptionColor.NORMAL, this, themeListeners, null);
    RippleSupport.setSimpleWhiteBackground(item);
    themeListeners.addThemeInvalidateListener(item);
    item.setLayoutParams(params);
    addView(item);
  }

  @CallSuper
  public boolean setPreview (EmbeddedService nativeEmbed) {
    this.nativeEmbed = nativeEmbed;
    return buildLayout();
  }

  protected PopupLayout popupLayout;

  private void show () {
    popupLayout = new PopupLayout(getContext());
    popupLayout.setIgnoreBottom(true);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setOverlayStatusBar(true);
    popupLayout.setShowListener(this);
    popupLayout.setDismissListener(this);
    popupLayout.init(true);
    popupLayout.showSimplePopupView(this, getPreviewHeight());
  }

  private RootFrameLayout rootFrameLayout;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (Settings.instance().useEdgeToEdge()) {
      rootFrameLayout = Views.findAncestor(this, RootFrameLayout.class, false);
      if (rootFrameLayout != null) {
        rootFrameLayout.addInsetsChangeListener(this);
        Rect rect = rootFrameLayout.getSystemInsetsWithoutIme();
        setVerticalInsets(rect.top, rect.bottom);
      }
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (rootFrameLayout != null) {
      rootFrameLayout.removeInsetsChangeListener(this);
      rootFrameLayout = null;
    }
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate) {
    setVerticalInsets(systemInsetsWithoutIme.top, systemInsetsWithoutIme.bottom);
  }

  private int topInset, bottomInset;

  private void setVerticalInsets (int top, int bottomInset) {
    if (this.topInset != top || this.bottomInset != bottomInset) {
      this.topInset = top;
      this.bottomInset = bottomInset;
      Views.setPaddingBottom(this, bottomInset);
      setWillNotDraw(!xgramSheet && bottomInset == 0);
      UI.post(this::requestLayout);
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    super.onDraw(c);
    if (xgramSheet) {
      if (!xgramIsFullscreen()) {
        xgramDrawSheet(c); // the sheet color under the navigation bar too: no separate filling strip
      }
      return;
    }
    if (getPaddingBottom() != 0) {
      c.drawRect(0, getMeasuredHeight() - getPaddingBottom(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (xgramSheet) {
      widthMeasureSpec = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidthSpec(widthMeasureSpec);
      final int top = xgramIsFullscreen() ? topInset : 0;
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(computeHeight(MeasureSpec.getSize(widthMeasureSpec)) + bottomInset + top, MeasureSpec.EXACTLY));
      return;
    }
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(computeHeight(MeasureSpec.getSize(widthMeasureSpec)) + bottomInset + topInset, MeasureSpec.EXACTLY));
  }

  protected abstract int computeHeight (int currentWidth);
  protected abstract int getPreviewHeight ();
  protected abstract boolean buildLayout ();
  protected abstract boolean onPrepareNextPreview (String pageUrl);
  protected abstract void onDestroyPopupInternal ();
  public abstract void forceClose (boolean animated);

  @Override
  public final void onPopupDismiss (PopupLayout popup) {
    onDestroyPopupInternal();
    UI.getContext(getContext()).removeGlobalThemeListeners(themeListeners);
  }

  public static boolean show (ViewController<?> parent, TdApi.LinkPreview linkPreview, boolean needConfirmation) {
    EmbeddedService service = EmbeddedService.parse(linkPreview);
    return show(parent, service, needConfirmation);
  }

  public static boolean show (ViewController<?> parent, String webPageUrl, boolean needConfirmation) {
    EmbeddedService service = EmbeddedService.parseUrl(webPageUrl);
    return show(parent, service, needConfirmation);
  }

  public static boolean show (ViewController<?> parent, EmbeddedService service, boolean needConfirmation) {
    if (service != null) {
      BaseActivity context = parent.context();

      SparseArrayCompat<PopupLayout> popups = context.getForgottenWindows();
      for (int i = 0; i < popups.size(); i++) {
        PopupLayout popupLayout = popups.valueAt(i);
        if (popupLayout.getBoundView() instanceof PreviewLayout && ((PreviewLayout) popupLayout.getBoundView()).onPrepareNextPreview(service.viewUrl)) {
          return true;
        }
      }
      context.closeOtherPips();

      if (service.type == EmbeddedService.TYPE_UNKNOWN && needConfirmation) {
        Uri viewUri = Uri.parse(service.viewUrl);

        parent.showOptions(Lang.getStringBold(
          R.string.OpenPreviewInSecretChatWarning,
          Strings.unwrapWww(viewUri.getHost()),
          service.embedUrl
        ), new int[]{
          R.id.btn_openLink,
          R.id.btn_useInAppBrowser,
          R.id.btn_cancel
        }, new String[]{
          Lang.getString(R.string.OpenPreviewInSecretChatActionOpen),
          Lang.getString(R.string.OpenPreviewInSecretChatActionBrowser),
          Lang.getString(R.string.Cancel),
        }, null, new int[]{
          R.drawable.baseline_play_circle_filled_24_white,
          R.drawable.baseline_open_in_browser_24,
          R.drawable.baseline_cancel_24
        }, (optionItemView, id) -> {
          if (id == R.id.btn_openLink) {
            show(parent, service, false);
          } else if (id == R.id.btn_useInAppBrowser) {
            UI.openUrl(service.viewUrl);
          }

          return true;
        });

        return true;
      }

      PreviewLayout popup = null;
      switch (service.type) {
        case EmbeddedService.TYPE_YOUTUBE:
        case EmbeddedService.TYPE_SOUNDCLOUD:
        case EmbeddedService.TYPE_DAILYMOTION:
        case EmbeddedService.TYPE_VIMEO:
        case EmbeddedService.TYPE_UNKNOWN:
        case EmbeddedService.TYPE_CUSTOM_EMBED:
          popup = new WebViewPreviewLayout(context, parent);
          break;
      }

      if (popup != null && popup.setPreview(service)) {
        if (!parent.tdlib().context().calls().promptActiveCall()) {
          popup.show();
        }
        return true;
      }
    }
    return false;
  }
}
