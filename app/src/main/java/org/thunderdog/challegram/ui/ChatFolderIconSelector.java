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
 * File created on 16/01/2023
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class ChatFolderIconSelector {

  private final Context context;
  private final Delegate delegate;
  private final View popupView;
  private final GridLayoutManager layoutManager;
  private final @Nullable String selectedIconName;

  private PopupLayout popupLayout;

  // XGram M3E: an M3 modal sheet (28dp top corners and the drag handle on the sheet color, at most 640dp wide, a
  // titleLarge "Folder icon" title) holding an adaptive grid of 48dp icon toggle buttons in 64dp cells: the selected
  // one in secondaryContainer under onSecondaryContainer, its shape springing from 16dp corners to full. Icons come
  // through the icon pack. It closes on a choice, a tap outside or a drag down.
  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private static final float XGRAM_CELL = 64f, XGRAM_BUTTON = 48f, XGRAM_SIDE = 16f, XGRAM_TITLE_AREA = 64f, XGRAM_BOTTOM = 16f;
  private @Nullable String xgramChosenIconName;

  public ChatFolderIconSelector (ViewController<?> owner, @Nullable String selectedIconName, boolean isDefault, Delegate delegate) {
    this.context = owner.context();
    this.delegate = delegate;
    this.selectedIconName = selectedIconName;

    List<ListItem> items = new ArrayList<>(TD.ICON_NAMES.length);
    for (String iconName : TD.ICON_NAMES) {
      items.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE, 0, TD.iconByName(iconName, 0), 0).setStringValue(iconName));
    }
    SettingsAdapter popupAdapter = new SettingsAdapter(owner, null, owner) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent) {
        if (xgram) {
          return new SettingHolder(xgramNewCell(parent.getContext()));
        }
        ImageView imageView = new ImageView(parent.getContext()) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            int size = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(size, size);
          }

          @Override
          protected void onDraw (Canvas canvas) {
            ListItem item = (ListItem) getTag();
            String iconName = item.getStringValue();
            if (isSelectedIcon(iconName)) {
              canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Screen.dp(24f), Paints.fillingPaint(Theme.fillingColor()));
            }
            super.onDraw(canvas);
          }
        };
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        Views.setClickable(imageView);
        imageView.setOnClickListener(v -> {
          ListItem item = (ListItem) imageView.getTag();
          String iconName = item.getStringValue();
          TdApi.ChatFolderIcon icon = !StringUtils.isEmpty(iconName) ? new TdApi.ChatFolderIcon(iconName) : null;
          delegate.onIconClick(icon);
          hide(/* animated */ true);
        });
        RippleSupport.setTransparentSelector(imageView);
        return new SettingHolder(imageView);
      }

      @Override
      protected void setCustom (ListItem item, SettingHolder holder, int position) {
        if (xgram) {
          xgramBindCell(owner, (ImageView) holder.itemView, item, isDefault, false);
          return;
        }
        ImageView imageView = (ImageView) holder.itemView;
        int iconResource = item.getIconResource();
        if (iconResource != 0) {
          imageView.setImageDrawable(Drawables.get(imageView.getResources(), iconResource));
          String iconName = item.getStringValue();
          boolean isSelected = !isDefault && isSelectedIcon(iconName);
          int iconColorId = isSelected ? ColorId.iconActive : ColorId.icon;
          imageView.setColorFilter(Theme.getColor(iconColorId));
          owner.removeThemeListenerByTarget(imageView);
          owner.addThemeFilterListener(imageView, iconColorId);
        } else {
          imageView.setImageDrawable(null);
        }
      }
    };

    layoutManager = new GridLayoutManager(context, computeSpanCount(Screen.currentWidth()));
    popupAdapter.setItems(items, false);
    RecyclerView recyclerView = new RecyclerView(context);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(popupAdapter);
    if (xgram) {
      recyclerView.setItemAnimator(null);
      recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      this.popupView = xgramBuildSheet(owner, recyclerView);
      return;
    }
    ViewSupport.setThemedBackground(recyclerView, ColorId.background);

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleTopShadow(true);
    owner.addThemeInvalidateListener(shadowView);

    FrameLayoutFix popupView = new FrameLayoutFix(context);
    popupView.addView(shadowView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(7f), Gravity.TOP));
    popupView.addView(recyclerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP, 0, Screen.dp(7f), 0, 0));
    popupView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    this.popupView = popupView;
  }

  public void show () {
    int itemCount = layoutManager.getItemCount();
    int spanCount = layoutManager.getSpanCount();
    int rowCount = (itemCount + spanCount - 1) / spanCount;
    int popupHeight = xgram ?
      Screen.dp(XGRAM_TITLE_AREA + XGRAM_BOTTOM) + rowCount * Screen.dp(XGRAM_CELL) : // XGram M3E: the sheet's height
      rowCount * (Screen.currentWidth() / spanCount) + Screen.dp(7f);

    popupLayout = new PopupLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int popupWidth = getDefaultSize(Screen.currentWidth(), widthMeasureSpec);
        layoutManager.setSpanCount(computeSpanCount(popupWidth));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    popupLayout.init(true);
    popupLayout.setShowListener(popup -> delegate.onShow());
    popupLayout.setDismissListener(popup -> delegate.onDismiss());
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.showSimplePopupView(popupView, popupHeight);
  }

  public void hide (boolean animated) {
    if (popupLayout != null) {
      popupLayout.hideWindow(animated);
      popupLayout = null;
    }
  }

  private int computeSpanCount (int width) {
    if (xgram) {
      // XGram M3E: 64dp cells across the sheet (at most 640dp wide) less its 16dp sides
      int sheetWidth = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidth(width) - Screen.dp(XGRAM_SIDE) * 2;
      return Math.max(sheetWidth / Screen.dp(XGRAM_CELL), 4);
    }
    int itemSize = Screen.dp(56f);
    return Math.max(width / itemSize, 3);
  }

  // XGram M3E: the sheet

  private View xgramNewCell (Context context) {
    ImageView imageView = new ImageView(context);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(XGRAM_CELL)));
    Views.setClickable(imageView);
    // a 48dp icon toggle button: no container at rest (16dp corners), secondaryContainer and full corners selected
    com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable button = com.fahadali.xgram.appearance.XgramExpressive.attachPressMorph(imageView, 0, com.fahadali.xgram.appearance.XgramExpressive.CORNER_L, com.fahadali.xgram.appearance.XgramExpressive.CORNER_S);
    button.setSize(XGRAM_BUTTON, XGRAM_BUTTON, Gravity.CENTER);
    imageView.setTag(R.id.xgram_r6b6_status, button);
    imageView.setOnClickListener(v -> {
      Object tag = v.getTag();
      if (!(tag instanceof ListItem)) {
        return;
      }
      String iconName = ((ListItem) tag).getStringValue();
      xgramChosenIconName = iconName;
      RecyclerView parent = v.getParent() instanceof RecyclerView ? (RecyclerView) v.getParent() : null;
      if (parent != null) {
        // the choice springs into the selected shape while the sheet goes away
        for (int i = 0; i < parent.getChildCount(); i++) {
          View child = parent.getChildAt(i);
          if (child instanceof ImageView && child.getTag() instanceof ListItem) {
            xgramBindCell(null, (ImageView) child, (ListItem) child.getTag(), false, true);
          }
        }
      }
      TdApi.ChatFolderIcon icon = !StringUtils.isEmpty(iconName) ? new TdApi.ChatFolderIcon(iconName) : null;
      delegate.onIconClick(icon);
      hide(/* animated */ true);
    });
    return imageView;
  }

  private void xgramBindCell (@Nullable ViewController<?> owner, ImageView imageView, ListItem item, boolean isDefault, boolean animated) {
    int iconResource = item.getIconResource();
    String iconName = item.getStringValue();
    boolean isSelected = xgramChosenIconName != null ? isSameIcon(xgramChosenIconName, iconName) : !isDefault && isSelectedIcon(iconName);
    if (!animated) {
      imageView.setImageDrawable(iconResource != 0 ? Drawables.get(imageView.getResources(), iconResource) : null);
      imageView.setContentDescription(iconName);
    }
    Object tag = imageView.getTag(R.id.xgram_r6b6_status);
    if (tag instanceof com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable) {
      com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable button = (com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable) tag;
      if (isSelected) {
        button.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER);
      } else {
        button.setColor(0);
      }
      button.setCornerTarget(isSelected ? com.fahadali.xgram.appearance.XgramExpressive.CORNER_FULL : Float.NaN, animated);
    }
    int iconColorId = isSelected ? ColorId.fillingActiveContent : ColorId.icon;
    imageView.setColorFilter(Theme.getColor(iconColorId));
    if (owner != null) {
      owner.removeThemeListenerByTarget(imageView);
      owner.addThemeFilterListener(imageView, iconColorId);
    }
  }

  private View xgramBuildSheet (ViewController<?> owner, RecyclerView recyclerView) {
    final int side = Screen.dp(XGRAM_SIDE);
    recyclerView.setPadding(side, 0, side, Screen.dp(XGRAM_BOTTOM));
    recyclerView.setClipToPadding(false);

    android.widget.TextView title = new android.widget.TextView(context) {
      @Override
      protected void onDraw (android.graphics.Canvas c) {
        final int color = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE);
        if (getCurrentTextColor() != color) {
          setTextColor(color);
        }
        super.onDraw(c);
      }
    };
    com.fahadali.xgram.appearance.XgramExpressive.applyText(title, com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_L, false);
    title.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    title.setText(org.thunderdog.challegram.core.Lang.getString(R.string.XgramR6B6FolderIcon));
    title.setPadding(Screen.dp(24f), Screen.dp(20f), Screen.dp(24f), 0);
    owner.addThemeInvalidateListener(title);

    android.widget.LinearLayout sheet = new android.widget.LinearLayout(context);
    sheet.setOrientation(android.widget.LinearLayout.VERTICAL);
    sheet.setBackground(new com.fahadali.xgram.appearance.XgramR6B6Lists.SheetDrawable());
    sheet.addView(title, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(XGRAM_TITLE_AREA)));
    sheet.addView(recyclerView, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    owner.addThemeInvalidateListener(sheet);

    XgramSheetFrame frame = new XgramSheetFrame(context, sheet);
    frame.addView(sheet, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    frame.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    return frame;
  }

  /**
   * The frame PopupLayout slides in and out; the sheet in it is at most 640dp wide, centred, and follows a drag that
   * starts on its handle or title: far or fast enough down it closes, otherwise it springs back.
   */
  private final class XgramSheetFrame extends FrameLayoutFix {
    private final View sheet;
    private final int touchSlop;
    private float startY;
    private boolean tracking, dragging;
    private @Nullable android.view.VelocityTracker velocityTracker;
    private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring settle;

    XgramSheetFrame (Context context, View sheet) {
      super(context);
      this.sheet = sheet;
      this.touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      ViewGroup.LayoutParams params = sheet.getLayoutParams();
      if (params != null && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
        int width = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidth(MeasureSpec.getSize(widthMeasureSpec));
        if (params.width != width) {
          params.width = width;
        }
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onInterceptTouchEvent (android.view.MotionEvent e) {
      switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_DOWN: {
          startY = e.getY();
          tracking = e.getY() - sheet.getTop() - sheet.getTranslationY() <= Screen.dp(XGRAM_TITLE_AREA);
          dragging = false;
          break;
        }
        case android.view.MotionEvent.ACTION_MOVE: {
          if (tracking && !dragging && e.getY() - startY > touchSlop) {
            dragging = true;
            startY = e.getY();
            return true;
          }
          break;
        }
      }
      return false;
    }

    @Override
    public boolean onTouchEvent (android.view.MotionEvent e) {
      final int action = e.getActionMasked();
      if (!dragging) {
        // a touch that started on the title (nothing under it took it) comes here directly
        if (!tracking) {
          return false;
        }
        if (action == android.view.MotionEvent.ACTION_MOVE && e.getY() - startY > touchSlop) {
          dragging = true;
          startY = e.getY();
        } else {
          if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
            tracking = false;
          }
          return true;
        }
      }
      if (velocityTracker == null) {
        velocityTracker = android.view.VelocityTracker.obtain();
      }
      velocityTracker.addMovement(e);
      switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_MOVE: {
          if (settle != null) {
            settle.cancel();
          }
          sheet.setTranslationY(Math.max(0f, e.getY() - startY));
          return true;
        }
        case android.view.MotionEvent.ACTION_UP:
        case android.view.MotionEvent.ACTION_CANCEL: {
          velocityTracker.computeCurrentVelocity(1000);
          final float velocity = velocityTracker.getYVelocity();
          velocityTracker.recycle();
          velocityTracker = null;
          dragging = tracking = false;
          final float dy = sheet.getTranslationY();
          if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && (dy > sheet.getHeight() / 4f || velocity > Screen.dp(800f))) {
            hide(true);
          } else if (dy != 0f) {
            if (settle == null) {
              settle = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, dy, (s, value) -> sheet.setTranslationY(Math.max(0f, value))).setThreshold(.5f);
            } else {
              settle.setValue(dy);
            }
            settle.animateTo(0f);
          }
          return true;
        }
      }
      return true;
    }
  }

  private boolean isSelectedIcon (@Nullable String iconName) {
    return isSameIcon(selectedIconName, iconName);
  }

  private static boolean isSameIcon (@Nullable String a, @Nullable String b) {
    return StringUtils.equalsOrBothEmpty(a, b) || (isFolderIcon(a) && isFolderIcon(b));
  }

  private static boolean isFolderIcon (@Nullable String iconName) {
    return StringUtils.isEmpty(iconName) || "Custom".equals(iconName);
  }

  public interface Delegate {
    void onIconClick (@Nullable TdApi.ChatFolderIcon icon);

    default void onShow () {}

    default void onDismiss () {}
  }

  public static ChatFolderIconSelector show (ViewController<?> owner, TdApi.ChatFolder chatFolder, Delegate delegate) {
    TdApi.ChatFolderIcon icon = chatFolder.icon;
    boolean isDefault = false;
    if (Td.isEmpty(icon)) {
      icon = owner.tdlib().chatFolderIcon(chatFolder);
      isDefault = true;
    }
    return show(owner, icon, isDefault, delegate);
  }

  public static ChatFolderIconSelector show (ViewController<?> owner, @Nullable TdApi.ChatFolderIcon selectedIcon, boolean isDefault, Delegate delegate) {
    String iconName = selectedIcon != null && !StringUtils.isEmpty(selectedIcon.name) ? selectedIcon.name : null;
    ChatFolderIconSelector selector = new ChatFolderIconSelector(owner, iconName, isDefault || StringUtils.isEmpty(iconName), delegate);
    selector.show();
    return selector;
  }
}
