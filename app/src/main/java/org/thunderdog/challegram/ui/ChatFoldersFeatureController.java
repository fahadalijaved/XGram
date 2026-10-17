package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MainController.UnreadCounterColorSet;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.FutureBool;
import tgx.td.ChatPosition;

public class ChatFoldersFeatureController extends SinglePageBottomSheetViewController<ChatFoldersFeatureController.Page, Void> {

  public ChatFoldersFeatureController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected Page onCreateSinglePage () {
    return new Page(context, tdlib, this);
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    super.onCreateView(context, contentView, pager);
    tdlib.ui().post(this::launchOpenAnimation);
  }

  @Override
  protected int getHeaderHeight () {
    return 0; // no header
  }

  @Override
  protected HeaderView onCreateHeaderView () {
    return null; // no header
  }

  @Override
  protected void onAfterCreateView () {
    setLickViewColor(xgramLickColor());
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    setLickViewColor(xgramLickColor());
  }

  /** XGram M3E: the strip under the status bar is the sheet's color (surfaceContainerLow). */
  private static int xgramLickColor () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? com.fahadali.xgram.appearance.XgramM3Sheets.sheetColor() : Theme.getColor(ColorId.background);
  }

  @Override
  protected boolean canHideByScroll () {
    return true;
  }

  @Override
  protected float xgramSheetHandleAlpha () {
    return 0f; // XGram: the page starts at the sheet's top and covers where the handle would be
  }

  @Override
  protected int getContentOffset () {
    return getTargetHeight()
      - getHeaderHeight()
      - HeaderView.getTopOffset()
      - singlePage.getTotalHeight()
      - Views.getBottomMargin(singlePage.getRecyclerView());
  }

  @Override
  protected void setupPopupLayout (PopupLayout popupLayout) {
    super.setupPopupLayout(popupLayout);
    popupLayout.setHideKeyboard();
    popupLayout.setTouchDownInterceptor((popup, event) -> true);
  }

  protected class Page extends BottomSheetBaseRecyclerViewController<Void> {

    private static final float PREVIEW_BORDER_WIDTH = 2f;
    private static final float PREVIEW_BORDER_RADIUS = 30f;
    private static final float PREVIEW_HEADER_HEIGHT = 56f;

    private SingleViewAdapter<View> adapter;

    @Override
    protected int getRecyclerBackground () {
      // XGram: Material 3 Expressive: the page is the sheet's color (BottomSheetViewController.getBackgroundColorId)
      return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.drawer : super.getRecyclerBackground();
    }
    private ViewPagerTopView topView;
    private FrameLayoutFix previewView;
    private final BottomSheetViewController<?> parent;

    public Page (@NonNull Context context, Tdlib tdlib, BottomSheetViewController<?> parent) {
      super(context, tdlib);
      this.parent = parent;
    }

    @Override
    public boolean supportsBottomInset () {
      return true;
    }

    @Override
    protected boolean needRecyclerBottomInset () {
      return false;
    }

    @Override
    protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
      super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
      if (xgram) {
        Views.setBottomMargin(getRecyclerView(), extraBottomInsetWithoutIme); // XGram M3E: the hero button is in the page
        return;
      }
      int buttonHeight = Screen.dp(56f) + extraBottomInsetWithoutIme;
      Views.setLayoutHeight(bottomButton, buttonHeight);
      Views.setPaddingBottom(bottomButton, extraBottomInsetWithoutIme);
      Views.setBottomMargin(getRecyclerView(), buttonHeight);
    }

    private TextView bottomButton;

    @Override
    protected View onCreateView (Context context) {
      View view = super.onCreateView(context);

      if (xgram) {
        Views.setBottomMargin(getRecyclerView(), extraBottomInsetWithoutIme); // XGram M3E: no bottom bar, the hero button is in the page
        return view;
      }
      int buttonHeight = Screen.dp(56f) + extraBottomInsetWithoutIme;
      FrameLayoutFix wrap = (FrameLayoutFix) view;
      bottomButton = buildActionButton();
      Views.setPaddingBottom(bottomButton, extraBottomInsetWithoutIme);
      wrap.addView(bottomButton, FrameLayoutFix.newParams(LayoutHelper.MATCH_PARENT, buttonHeight, Gravity.BOTTOM));
      Views.setBottomMargin(getRecyclerView(), buttonHeight);
      return view;
    }

    // XGram M3E: a hero sheet: a 40dp close icon button at the top end, the folder icon on a 96dp cookie, the headline in
    // headlineSmall, the text in bodyMedium onSurfaceVariant, and a single "Set up folders" hero button
    private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();

    private View xgramBuildHero (Context context) {
      LinearLayout hero = new LinearLayout(context);
      hero.setOrientation(LinearLayout.VERTICAL);
      hero.setGravity(Gravity.CENTER_HORIZONTAL);
      hero.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
      hero.setPadding(0, 0, 0, Screen.dp(12f));

      FrameLayoutFix top = new FrameLayoutFix(context);
      android.widget.ImageView close = new android.widget.ImageView(context);
      close.setId(R.id.xgram_r6b6_sheetClose);
      close.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      close.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_close_24));
      close.setColorFilter(Theme.getColor(ColorId.icon));
      addThemeFilterListener(close, ColorId.icon);
      close.setContentDescription(Lang.getString(R.string.XgramR6B6Close));
      com.fahadali.xgram.appearance.XgramAppMotion.iconButton(close);
      close.setOnClickListener(v -> parent.hidePopupWindow(true));
      top.addView(close, FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), 0));
      hero.addView(top, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, Screen.dp(56f)));

      com.fahadali.xgram.appearance.XgramR5Sheets.ShapeIconView icon = new com.fahadali.xgram.appearance.XgramR5Sheets.ShapeIconView(context, 96f, 40f).setIcon(R.drawable.baseline_folder_24);
      addThemeInvalidateListener(icon);
      hero.addView(icon, new LinearLayout.LayoutParams(Screen.dp(96f), Screen.dp(96f)));

      final int side = Screen.dp(24f);
      com.fahadali.xgram.appearance.XgramR5Lists.RoleText title = new com.fahadali.xgram.appearance.XgramR5Lists.RoleText(context, com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S, com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE);
      title.setGravity(Gravity.CENTER_HORIZONTAL);
      title.setText(Lang.getString(R.string.ChatFoldersSetupSuggestionTitle));
      title.setPadding(side, Screen.dp(16f), side, Screen.dp(8f));
      addThemeInvalidateListener(title);
      hero.addView(title, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

      com.fahadali.xgram.appearance.XgramR5Lists.RoleText text = new com.fahadali.xgram.appearance.XgramR5Lists.RoleText(context, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
      text.setGravity(Gravity.CENTER_HORIZONTAL);
      text.setText(Lang.getMarkdownString(this, R.string.ChatFoldersSetupSuggestionText));
      text.setPadding(side, 0, side, Screen.dp(8f));
      text.setLineSpacing(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.lineHeightSp(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M) - com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M)), 1f);
      addThemeInvalidateListener(text);
      hero.addView(text, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

      android.widget.FrameLayout button = com.fahadali.xgram.appearance.XgramR5People.newHeroButton(context, Lang.getString(R.string.XgramR6B6SetUpFolders), com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY, v -> {
        parent.hidePopupWindow(true);
        // revokeFeatureNotifications is called inside SettingsFoldersController.onFocus
        UI.getContext(context).navigation().navigateTo(new SettingsFoldersController(context, tdlib));
      });
      View pill = com.fahadali.xgram.appearance.XgramR5People.actionOf(button);
      if (pill != null) {
        addThemeInvalidateListener(pill);
      }
      hero.addView(button, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
      return hero;
    }

    @Override
    protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
      if (xgram) {
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        com.fahadali.xgram.appearance.XgramMotion.stretch(recyclerView, com.fahadali.xgram.appearance.XgramMotion.EDGE_BOTTOM);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter = new SingleViewAdapter<>(xgramBuildHero(context)));
        return;
      }
      TdApi.ChatFolderInfo[] chatFolders = tdlib.chatFolders();

      FrameLayoutFix singleView = new FrameLayoutFix(context) {{
        int contentTopMargin;
        if (chatFolders.length > 0) {
          int previewHeight = 123;
          int previewTopMargin = 45;
          contentTopMargin = previewTopMargin + previewHeight;
          addView(buildPreviewView(chatFolders), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, previewHeight, Gravity.TOP, 39, previewTopMargin, 39, 0));

          CircleButton closeButton = new CircleButton(context);
          closeButton.init(R.drawable.baseline_close_20, 0, 32f, 12f, ColorId.circleButtonOverlay, ColorId.circleButtonOverlayIcon, false);
          closeButton.setOnClickListener(v -> parent.hidePopupWindow(true));
          addView(closeButton, LayoutHelper.createFrame(56f, 56f, Gravity.RIGHT | Gravity.TOP));
          addThemeInvalidateListener(closeButton);
        } else {
          contentTopMargin = 0;
        }
        addView(buildContentView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, contentTopMargin, 0, 0));
      }};
      recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      com.fahadali.xgram.appearance.XgramMotion.stretch(recyclerView, com.fahadali.xgram.appearance.XgramMotion.EDGE_BOTTOM);
      recyclerView.setItemAnimator(null);
      recyclerView.setAdapter(adapter = new SingleViewAdapter<>(singleView));
    }

    @Override
    public boolean needsTempUpdates () {
      return super.needsTempUpdates() || getPopupLayout().isBoundWindowShowing();
    }

    public int getTotalHeight () {
      return getItemsHeight(getRecyclerView());
    }

    @Override
    public int getItemsHeight (RecyclerView parent) {
      View singleView = adapter.getSingleView();
      if (singleView.getMeasuredHeight() == 0) {
        singleView.measure(
          View.MeasureSpec.makeMeasureSpec(Screen.currentWidth(), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
      }
      return singleView.getMeasuredHeight();
    }

    @Override
    public int getId () {
      return R.id.controller_chatFoldersFeature;
    }

    private TextView buildActionButton () {
      TextView button = new TextView(context);
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      button.setTypeface(Fonts.getRobotoMedium());
      button.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      button.setEllipsize(TextUtils.TruncateAt.END);
      button.setGravity(Gravity.CENTER);
      button.setAllCaps(true);
      button.setId(R.id.btn_done);
      button.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
      button.setOnClickListener(v -> {
        parent.hidePopupWindow(true);
        // revokeFeatureNotifications is called inside SettingsFoldersController.onFocus
        UI.getContext(context).navigation().navigateTo(new SettingsFoldersController(context, tdlib));
      });
      addThemeTextColorListener(button, ColorId.fillingPositiveContent);
      Views.setMediumText(button, Lang.getString(R.string.ChatFoldersSetupSuggestionAction));
      RippleSupport.setSimpleWhiteBackground(button, ColorId.fillingPositive, this);
      return button;
    }

    private View buildContentView () {
      LinearLayout contentView = new LinearLayout(context);
      contentView.setOrientation(LinearLayout.VERTICAL);
      ViewSupport.setThemedBackground(contentView, com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.drawer : ColorId.filling, this); // XGram: the Expressive sheet color

      int contentVerticalPadding = Screen.dp(18f);
      int contentHorizontalPadding = Screen.dp(16f);
      int contentSpacing = Screen.dp(12f);

      TextView title = new TextView(context);
      title.setTextColor(Theme.getColor(ColorId.text));
      addThemeTextColorListener(title, ColorId.text);
      title.setPadding(contentHorizontalPadding, contentVerticalPadding, contentHorizontalPadding, contentSpacing);
      title.setTextSize(18f);
      title.setTypeface(Fonts.getRobotoMedium());
      title.setText(Lang.getString(R.string.ChatFoldersSetupSuggestionTitle));
      TextViewCompat.setLineHeight(title, Screen.sp(21f));
      contentView.addView(title, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);

      TextView text = new TextView(context);
      text.setTextColor(Theme.getColor(ColorId.textLight));
      addThemeTextColorListener(text, ColorId.textLight);
      text.setPadding(contentHorizontalPadding, 0, contentHorizontalPadding, contentVerticalPadding);
      text.setTextSize(15f);
      text.setTypeface(Fonts.getRobotoRegular());
      text.setText(Lang.getMarkdownString(this, R.string.ChatFoldersSetupSuggestionText));
      TextViewCompat.setLineHeight(text, Screen.sp(17.6f));
      contentView.addView(text, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);

      return contentView;
    }

    private View buildPreviewView (TdApi.ChatFolderInfo[] chatFolders) {
      int borderWidth = Screen.dp(PREVIEW_BORDER_WIDTH);
      int borderRadius = Screen.dp(PREVIEW_BORDER_RADIUS);
      int headerHeight = Screen.dp(PREVIEW_HEADER_HEIGHT);

      LayerDrawable background = buildPreviewBackground(borderRadius, borderWidth, headerHeight);

      View headerView = buildPreviewHeaderView(chatFolders);
      addThemeInvalidateListener(headerView);
      previewView = new FrameLayoutFix(context) {
        @Override
        protected boolean drawChild (@NonNull Canvas canvas, View child, long drawingTime) {
          if (child == headerView) {
            ShadowView.drawDropShadow(canvas, child.getLeft(), child.getRight(), child.getBottom(), 1f);
          }
          return super.drawChild(canvas, child, drawingTime);
        }
      };
      previewView.setBackground(background);
      previewView.addView(headerView, FrameLayoutFix.newParams(LayoutHelper.MATCH_PARENT, headerHeight, Gravity.TOP, borderWidth, borderRadius, borderWidth, 0));
      updatePreviewBackground();
      return previewView;
    }

    private View buildPreviewHeaderView (TdApi.ChatFolderInfo[] chatFolders) {
      FrameLayoutFix headerView = new FrameLayoutFix(context);
      ViewSupport.setThemedBackground(headerView, ColorId.headerBackground, this);
      headerView.addView(buildPagerHeaderView(chatFolders), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

      BackHeaderButton backButton = new BackHeaderButton(context);
      headerView.addView(backButton, FrameLayoutFix.newParams(Screen.dp(56f), LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

      HeaderButton searchButton = new HeaderButton(context);
      searchButton.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_search_24));
      searchButton.setThemeColorId(getHeaderIconColorId());
      headerView.addView(searchButton, FrameLayoutFix.newParams(Screen.dp(56f), LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

      return headerView;
    }

    private View buildPagerHeaderView (TdApi.ChatFolderInfo[] chatFolders) {
      ViewPagerHeaderViewCompact pagerHeaderView = new ViewPagerHeaderViewCompact(context);
      pagerHeaderView.setFadingEdgeLength(16f);
      pagerHeaderView.setPadding(Screen.dp(44f), 0, Screen.dp(44f), 0);

      topView = pagerHeaderView.getTopView();
      topView.setUseDarkBackground(false);
      topView.setDrawSelectionAtTop(false);
      topView.setShowLabelOnActiveOnly(false);
      topView.setItemPadding(Screen.dp(ViewPagerTopView.xgramCompactItemPadding())); // XGram: matches the chat list tabs
      topView.setItemSpacing(Screen.dp(ViewPagerTopView.COMPACT_ITEM_SPACING));
      topView.setSelectionColorId(ColorId.headerTabActive);
      topView.setTextFromToColorId(ColorId.headerTabInactiveText, ColorId.headerTabActiveText);
      topView.setItems(buildPagerSections(chatFolders));
      topView.setOnItemClickListener(toIndex -> {
        Animator runningAnimator = (Animator) topView.getTag();
        if (runningAnimator != null) {
          runningAnimator.cancel();
        }
        int fromIndex = (int) topView.getSelectionFactor();
        topView.setFromTo(fromIndex, toIndex);
        ValueAnimator animator = ValueAnimator.ofFloat(fromIndex, toIndex);
        animator.setInterpolator(AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR);
        animator.setDuration(180L);
        animator.addUpdateListener(a -> topView.setSelectionFactor((float) a.getAnimatedValue()));
        animator.start();
        topView.setTag(animator);
      });

      RecyclerView recyclerView = pagerHeaderView.getRecyclerView();
      recyclerView.setClipToPadding(false);
      recyclerView.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);

      return pagerHeaderView;
    }

    private List<ViewPagerTopView.Item> buildPagerSections (TdApi.ChatFolderInfo[] chatFolders) {
      int mainChatListPosition = MathUtils.clamp(tdlib.mainChatListPosition(), 0, chatFolders.length);
      int chatListCount = chatFolders.length + 1;
      List<ViewPagerTopView.Item> sections = new ArrayList<>(chatListCount);
      int chatFolderIndex = 0;
      for (int position = 0; position < chatListCount; position++) {
        ViewPagerTopView.Item section;
        if (position == mainChatListPosition) {
          section = buildMainSectionItem(position);
        } else {
          TdApi.ChatFolderInfo chatFolder = chatFolders[chatFolderIndex++];
          section = buildSectionItem(chatFolder);
        }
        section.setMinWidth(Screen.dp(56f - ViewPagerTopView.xgramCompactItemPadding() * 2));
        sections.add(section);
      }
      return sections;
    }

    private ViewPagerTopView.Item buildMainSectionItem (int position) {
      boolean showAsIcon = position == 0;
      int iconResource = showAsIcon ? R.drawable.baseline_forum_24 : ResourcesCompat.ID_NULL;
      CharSequence label = showAsIcon ? null : Lang.getString(R.string.Chats);
      return new ViewPagerTopView.Item(label, iconResource, buildUnreadCounter(ChatPosition.CHAT_LIST_MAIN));
    }

    private ViewPagerTopView.Item buildSectionItem (TdApi.ChatFolderInfo chatFolder) {
      CharSequence label = TD.toCharSequence(chatFolder.name);
      return new ViewPagerTopView.Item(label, buildUnreadCounter(new TdApi.ChatListFolder(chatFolder.id)));
    }

    private Counter buildUnreadCounter (TdApi.ChatList chatList) {
      UnreadCounterColorSet unreadCounterColorSet = new UnreadCounterColorSet(FALSE);
      Counter unreadCounter = new Counter.Builder()
        .textSize(12f)
        .backgroundPadding(4f)
        .outlineAffectsBackgroundSize(false)
        .colorSet(unreadCounterColorSet)
        .build();
      unreadCounterColorSet.setCounter(unreadCounter);
      TdlibCounter counter = tdlib.getCounter(chatList);
      MainController.updateCounter(tdlib, chatList, unreadCounter, counter, /* animated */ false);
      return unreadCounter;
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, ColorState state) {
      super.onThemeColorsChanged(areTemp, state);
      if (!xgram) { // XGram M3E: no preview, the hero reads its roles when it draws
        updatePreviewBackground();
      }
    }

    private static final int BORDER = 0, FILLING = 1, FADE = 2;

    private void updatePreviewBackground () {
      LayerDrawable background = (LayerDrawable) previewView.getBackground();
      background.invalidateSelf();

      GradientDrawable border = (GradientDrawable) background.getDrawable(BORDER);
      border.setColor(HeaderView.defaultStatusColor());
      border.setStroke(Screen.dp(PREVIEW_BORDER_WIDTH), Theme.getColor(ColorId.seekReady)); // FIXME

      Drawable filling = background.getDrawable(FILLING);
      filling.invalidateSelf();

      GradientDrawable fade = (GradientDrawable) background.getDrawable(FADE);
      fade.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
      fade.setColors(new int[] {Color.TRANSPARENT, Theme.getColor(getRecyclerBackground())});
    }

    LayerDrawable buildPreviewBackground (int borderRadius, int borderWidth, int headerHeight) {
      GradientDrawable border = new GradientDrawable();
      border.setShape(GradientDrawable.RECTANGLE);
      border.setCornerRadius(borderRadius);

      FillingDrawable filling = new FillingDrawable(ColorId.filling);

      GradientDrawable fade = new GradientDrawable();
      fade.setShape(GradientDrawable.RECTANGLE);

      Drawable[] layers = new Drawable[3];
      layers[BORDER] = border;
      layers[FILLING] = filling;
      layers[FADE] = fade;

      LayerDrawable background = new LayerDrawable(layers);
      background.setLayerInset(BORDER, 0, 0, 0, -borderRadius);
      background.setLayerInset(FILLING, borderWidth, borderRadius, borderWidth, 0);
      background.setLayerInset(FADE, 0, headerHeight + borderRadius, 0, 0);
      return background;
    }

    @Override
    public boolean needBottomDecorationOffsets (RecyclerView parent) {
      return false;
    }
  }

  private static final FutureBool FALSE = () -> false;
}
