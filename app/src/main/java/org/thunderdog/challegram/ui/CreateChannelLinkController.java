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
 * File created on 05/02/2016 at 18:17
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexScrollView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PrefixEditText;
import org.thunderdog.challegram.widget.RadioView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.TdConstants;

public class CreateChannelLinkController extends ViewController<CreateChannelLinkController.Args> implements View.OnClickListener, Unlockable {
  public static class Args {
    private TdApi.Chat chat;
    private ImageFile photo;

    public Args (TdApi.Chat chat, ImageFile photo) {
      this.chat = chat;
      this.photo = photo;
    }
  }

  public CreateChannelLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.Chat chat;
  private ImageFile photo;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.chat = args.chat;
    this.photo = args.photo;
  }

  private ComplexScrollView scrollView;
  private LinearLayout contentView;
  private PrefixEditText linkView;

  private RadioView publicRadio;
  private RadioView privateRadio;
  private TextView hintView;

  private ComplexHeaderView headerView;

  @Override
  public int getId () {
    return R.id.controller_newChannel_link;
  }

  // XGram M3E: a plain app bar ("Channel type") instead of the expanding avatar header; Public and Private as radio
  // tiles of one segmented group; the t.me/ link in an M3 filled field (its prefix kept) with the hint as supporting
  // text; a private link with a tonal Copy and a text Revoke under it. On the page color.
  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private @androidx.annotation.Nullable View xgramLinkActions;
  private @androidx.annotation.Nullable String xgramInviteLinkUrl;

  @Override
  public CharSequence getName () {
    if (xgram) {
      return Lang.getString(R.string.XgramR6B6ChannelType);
    }
    return super.getName();
  }

  private View xgramCreateView (Context context) {
    contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    contentView.setPadding(0, Screen.dp(8f), 0, Screen.dp(24f));

    publicRadio = addOption(context, R.id.btn_publicChannel, true, R.string.ChannelPublic, R.string.ChannelPublicInfo, 0);
    privateRadio = addOption(context, R.id.btn_privateChannel, false, R.string.ChannelPrivate, R.string.ChannelPrivateInfo, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_GAP));

    linkView = new PrefixEditText(context);
    linkView.setFocusable(false);
    linkView.setFocusableInTouchMode(false);
    linkView.setId(R.id.edit_link);
    linkView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(linkView);
    addThemeInvalidateListener(linkView);
    com.fahadali.xgram.appearance.XgramExpressive.applyText(linkView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false);
    linkView.setBackground(null);
    linkView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
    linkView.setSingleLine(true);
    linkView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    linkView.setInputType(linkView.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    linkView.setPrefix("t.me/");

    // An M3 filled field: surfaceContainerHighest with 4dp top corners and its active indicator (2dp primary focused)
    FrameLayoutFix field = new FrameLayoutFix(context) {
      @Override
      protected void dispatchDraw (android.graphics.Canvas c) {
        super.dispatchDraw(c);
        final boolean focused = linkView.isFocused();
        final float height = Screen.dp(focused ? 2f : 1f);
        c.drawRect(0, getHeight() - height, getWidth(), getHeight(), org.thunderdog.challegram.tool.Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(focused ? com.fahadali.xgram.appearance.XgramExpressive.PRIMARY : com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT)));
      }
    };
    com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable fieldBackground = new com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable(0, com.fahadali.xgram.appearance.XgramExpressive.CORNER_XS, Float.NaN);
    fieldBackground.setCorners(com.fahadali.xgram.appearance.XgramExpressive.CORNER_XS, com.fahadali.xgram.appearance.XgramExpressive.CORNER_XS, com.fahadali.xgram.appearance.XgramExpressive.CORNER_NONE, com.fahadali.xgram.appearance.XgramExpressive.CORNER_NONE);
    fieldBackground.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST);
    field.setBackground(fieldBackground);
    field.addView(linkView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    linkView.setOnFocusChangeListener((v, hasFocus) -> field.invalidate());
    addThemeInvalidateListener(field);
    LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    fieldParams.leftMargin = fieldParams.rightMargin = Screen.dp(16f);
    fieldParams.topMargin = Screen.dp(24f);
    contentView.addView(field, fieldParams);

    com.fahadali.xgram.appearance.XgramR5Lists.RoleText supporting = new com.fahadali.xgram.appearance.XgramR5Lists.RoleText(context, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_S, com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    supporting.setGravity(Lang.gravity());
    supporting.setPadding(Screen.dp(32f), Screen.dp(4f), Screen.dp(32f), Screen.dp(8f));
    supporting.setText(Lang.getString(R.string.ChannelUsernameHelp));
    addThemeInvalidateListener(supporting);
    contentView.addView(supporting, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    hintView = supporting;

    // The private link's actions: [Revoke] [Copy]
    TextView revoke = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context, com.fahadali.xgram.appearance.XgramR6B6Lists.title(R.string.RevokeLink), com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
    revoke.setId(R.id.btn_revokeLink);
    revoke.setOnClickListener(this);
    addThemeInvalidateListener(revoke);
    TextView copy = com.fahadali.xgram.appearance.XgramR5Sheets.tonalButton(context, Lang.getString(R.string.CopyLink), null);
    copy.setId(R.id.btn_copyLink);
    copy.setOnClickListener(this);
    addThemeInvalidateListener(copy);
    LinearLayout actions = com.fahadali.xgram.appearance.XgramR5Sheets.actionsRow(context, null, revoke, copy);
    actions.setVisibility(View.GONE);
    contentView.addView(actions);
    xgramLinkActions = actions;

    scrollView = new ComplexScrollView(context);
    ViewSupport.setThemedBackground(scrollView, ColorId.background, this);
    scrollView.addView(contentView);
    scrollView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return scrollView;
  }

  @Override
  protected View onCreateView (Context context) {
    if (xgram) {
      return xgramCreateView(context);
    }
    contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    contentView.setPadding(0, Size.getHeaderSizeDifference(false), 0, 0);

    publicRadio = addOption(context, R.id.btn_publicChannel, true, R.string.ChannelPublic, R.string.ChannelPublicInfo, Screen.dp(33f));
    privateRadio = addOption(context, R.id.btn_privateChannel, false, R.string.ChannelPrivate, R.string.ChannelPrivateInfo, Screen.dp(2f));

    LinearLayout ll = new LinearLayout(context);
    ll.setOrientation(LinearLayout.HORIZONTAL);
    ll.setPadding(Screen.dp(16f), Screen.dp(32f), Screen.dp(16f), 0);

    ImageView iconView = new ImageView(context);
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_link_24));
    iconView.setColorFilter(Theme.iconColor());
    addThemeFilterListener(iconView, ColorId.icon);
    iconView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(46f)));

    LinearLayout.LayoutParams params;

    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = Screen.dp(32f);

    int padding = Screen.dp(9f);

    linkView = new PrefixEditText(context);
    linkView.setFocusable(false);
    linkView.setFocusableInTouchMode(false);
    linkView.setId(R.id.edit_link);
    linkView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(linkView);
    addThemeInvalidateListener(linkView);
    if (Lang.rtl()) {
      linkView.setPadding(padding, padding, 0, padding);
    } else {
      linkView.setPadding(0, padding, padding, padding);
    }
    linkView.setSingleLine(true);
    linkView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    linkView.setInputType(linkView.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    linkView.setLayoutParams(params);
    linkView.setPrefix("t.me/");

    ll.addView(iconView);
    ll.addView(linkView);

    contentView.addView(ll);

    hintView = new NoScrollTextView(context);
    hintView.setTextColor(Theme.textDecentColor());
    addThemeTextDecentColorListener(hintView);
    hintView.setTypeface(Fonts.getRobotoRegular());
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    hintView.setGravity(Lang.gravity());
    hintView.setPadding(Screen.dp(72f), Screen.dp(5f), Screen.dp(16f), Screen.dp(16f));
    hintView.setText(Lang.getString(R.string.ChannelUsernameHelp));
    contentView.addView(hintView);

    // HeaderView

    headerView = new ComplexHeaderView(context, tdlib, this);
    headerView.setNoExpand(true);
    headerView.initWithController(this, true);
    headerView.setInnerMargins(Screen.dp(56f), 0);
    headerView.setText(chat.title, Lang.plural(R.string.xMembers, 1));
    headerView.getAvatarReceiver().requestChat(tdlib, chat != null ? chat.id : 0, AvatarReceiver.Options.NONE);

    final Runnable scroller = () -> scrollView.fullScroll(View.FOCUS_DOWN);

    scrollView = new ComplexScrollView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        post(scroller);
      }
    };
    scrollView.setHeaderView(headerView);
    ViewSupport.setThemedBackground(scrollView, ColorId.filling, this);
    scrollView.addView(contentView);
    scrollView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return scrollView;
  }

  private long getSupergroupId () {
    return ChatId.toSupergroupId(chat.id);
  }

  private String username;

  private void updateLink () {
    if (xgramLinkActions != null) {
      xgramLinkActions.setVisibility(privateRadio.isChecked() ? View.VISIBLE : View.GONE); // XGram M3E: Copy and Revoke under a private link
    }
    if (privateRadio.isChecked()) {
      linkView.setEditable(false);
      if (inviteLink == null) {
        linkView.setSuffix("...");
        loadInviteLink();
      } else {
        linkView.setSuffix(inviteLink);
      }
    } else {
      linkView.setEditable(true);
      linkView.setSuffix(username == null ? "" : username);
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_publicChannel) {
      if (privateRadio.isChecked()) {
        privateRadio.toggleChecked();
        publicRadio.toggleChecked();
        updateLink();
        hintView.setText(Lang.getString(R.string.ChannelUsernameHelp));
      }
    } else if (viewId == R.id.btn_privateChannel) {
      if (publicRadio.isChecked()) {
        username = linkView.getSuffix();
        publicRadio.toggleChecked();
        privateRadio.toggleChecked();
        updateLink();
        hintView.setText(Lang.getString(R.string.ChannelPrivateLinkHelp));
      }
    } else if (viewId == R.id.btn_copyLink) {
      // XGram M3E: the private link's tonal Copy
      if (xgramInviteLinkUrl != null) {
        UI.copyText(xgramInviteLinkUrl, R.string.CopiedLink);
      }
    } else if (viewId == R.id.btn_revokeLink) {
      // XGram M3E: Revoke makes a new primary link
      if (!xgramRevoking && chat != null) {
        xgramRevoking = true;
        tdlib.send(new TdApi.ReplacePrimaryChatInviteLink(chat.id), (newInviteLink, error) -> runOnUiThreadOptional(() -> {
          xgramRevoking = false;
          if (error != null) {
            UI.showError(error);
          } else {
            xgramSetInviteLink(newInviteLink.inviteLink);
            updateLink();
          }
        }));
      }
    }
  }

  private boolean xgramRevoking;

  /** The primary link as shown after t.me/ (no protocol, no host), and the whole link to copy. */
  private void xgramSetInviteLink (String url) {
    xgramInviteLinkUrl = url;
    String link = StringUtils.urlWithoutProtocol(url);
    for (String host : TdConstants.TME_HOSTS) {
      if (link.startsWith(host)) {
        link = link.substring(host.length() + 1);
        break;
      }
    }
    inviteLink = link;
  }

  private RadioView addOption (Context context, @IdRes int id, boolean isChecked, @StringRes int titleResId, @StringRes int hintResId, int offset) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = offset;

    FrameLayoutFix content = new FrameLayoutFix(context);
    content.setId(id);
    content.setPadding(0, Screen.dp(8f), 0, Screen.dp(10f));
    content.setOnClickListener(this);
    content.setLayoutParams(lp);
    Views.setClickable(content);
    if (xgram) {
      // XGram M3E: a radio tile of the two-tile group (Public first, Private last)
      lp.leftMargin = lp.rightMargin = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_INSET);
      content.setPadding(0, Screen.dp(14f), 0, Screen.dp(14f));
      com.fahadali.xgram.appearance.XgramR5People.bindTile(content).setSegment(id == R.id.btn_publicChannel ? com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_FIRST : com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_LAST);
      addThemeInvalidateListener(content);
    } else {
      RippleSupport.setTransparentSelector(content);
    }

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(20f), Screen.dp(20f));
    params.topMargin = Screen.dp(2f);
    if (Lang.rtl()) {
      params.gravity = Gravity.RIGHT;
      params.rightMargin = Screen.dp(18f);
    } else {
      params.gravity = Gravity.LEFT;
      params.leftMargin = Screen.dp(18f);
    }
    RadioView radio = new RadioView(context);
    radio.setChecked(isChecked, false);
    radio.setLayoutParams(params);
    content.addView(radio);
    addThemeInvalidateListener(radio);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (Lang.rtl()) {
      params.gravity = Gravity.RIGHT;
      params.rightMargin = Screen.dp(73f);
      params.leftMargin = Screen.dp(12f);
    } else {
      params.gravity = Gravity.LEFT;
      params.leftMargin = Screen.dp(73f);
      params.rightMargin = Screen.dp(12f);
    }

    TextView text = new NoScrollTextView(context);
    text.setGravity(Lang.gravity());
    text.setText(Lang.getString(titleResId));
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    text.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(text);
    text.setTypeface(Fonts.getRobotoRegular());
    if (xgram) {
      // XGram M3E: bodyLarge, sentence case
      com.fahadali.xgram.appearance.XgramExpressive.applyText(text, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false);
      text.setText(com.fahadali.xgram.appearance.XgramR6B6Lists.title(titleResId));
    }
    text.setSingleLine();
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setLayoutParams(params);
    content.addView(text);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(24f);
    if (Lang.rtl()) {
      params.gravity = Gravity.RIGHT;
      params.rightMargin = Screen.dp(73f);
      params.leftMargin = Screen.dp(12f);
    } else {
      params.gravity = Gravity.LEFT;
      params.leftMargin = Screen.dp(73f);
      params.rightMargin = Screen.dp(12f);
    }

    text = new NoScrollTextView(context);
    text.setGravity(Lang.gravity());
    text.setText(Lang.getString(hintResId));
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    text.setTextColor(Theme.textDecentColor());
    text.setTypeface(Fonts.getRobotoRegular());
    if (xgram) {
      com.fahadali.xgram.appearance.XgramExpressive.applyText(text, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false); // XGram M3E
    }
    addThemeTextDecentColorListener(text);
    text.setLayoutParams(params);
    content.addView(text);

    contentView.addView(content);

    return radio;
  }

  private boolean linkRequested;

  private void loadInviteLink () {
    if (!linkRequested) {
      linkRequested = true;
      tdlib.getPrimaryChatInviteLink(chat.id, (chatInviteLink, error) -> {
        if (error != null) {
          UI.showError(error);
        } else {
          xgramInviteLinkUrl = chatInviteLink.inviteLink; // XGram M3E: the whole link for Copy
          inviteLink = StringUtils.urlWithoutProtocol(chatInviteLink.inviteLink);
          for (String host : TdConstants.TME_HOSTS) {
            if (inviteLink.startsWith(host)) {
              inviteLink = inviteLink.substring(host.length() + 1);
              break;
            }
          }
          runOnUiThreadOptional(this::updateLink);
        }
      });
    }
  }

  private String inviteLink;

  @Override
  public View getCustomHeaderCell () {
    return xgram ? null : headerView; // XGram M3E: a plain app bar
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderHeight () {
    return xgram ? Size.getHeaderPortraitSize() : Size.getHeaderBigPortraitSize(false);
  }

  @Override
  protected int getFloatingButtonId () {
    return R.drawable.baseline_arrow_forward_24;
  }

  @Override
  protected void onFloatingButtonPressed () {
    if (publicRadio.isChecked()) {
      String username = linkView.getSuffix().trim();
      if (username.length() < 5) {
        UI.showToast(R.string.PublicLinkTooShort, Toast.LENGTH_SHORT);
        return;
      }
      if (username.length() != 0 && !TD.matchUsername(username)) {
        UI.showToast(R.string.PublicLinkIsInvalid, Toast.LENGTH_SHORT);
        return;
      }
      setUsername(username);
    } else {
      nextStep();
    }
  }

  private void setEnabled (boolean enabled) {
    privateRadio.setEnabled(enabled);
    publicRadio.setEnabled(enabled);
    linkView.setEnabled(enabled);
  }

  private boolean usernameRequested;

  @Override
  public void unlock () {
    usernameRequested = false;
    setEnabled(true);
  }

  private void setUsername (String username) {
    if (usernameRequested) {
      return;
    }
    usernameRequested = true;
    setEnabled(false);
    tdlib.send(new TdApi.SetSupergroupUsername(getSupergroupId(), username), (ok, error) -> {
      if (error != null) {
        UI.showError(error);
        UI.unlock(CreateChannelLinkController.this);
      } else {
        UI.post(this::nextStep);
      }
    });
  }

  private void nextStep () {
    Keyboard.hide(linkView);
    ContactsController c = new ContactsController(context, tdlib);
    c.initWithMode(ContactsController.MODE_CHANNEL_MEMBERS);
    c.setChat(chat);
    navigateTo(c);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (publicRadio.isChecked()) {
      linkView.setFocusable(true);
      linkView.setFocusableInTouchMode(true);
    }
    if (stackSize() == 3 && stackItemAt(1) instanceof CreateChannelController) {
      destroyStackItemAt(1);
    }
  }

  // Updates

  /*@Override
  public void updateChatTitle (long chatId, String title) {
    if (chat != null && chat.id == chatId) {
      chat.title = title;
    }
  }

  @Override
  public void updateChatPhoto (long chatId, TdApi.ChatPhoto photo) {
    if (chat != null && chat.id == chatId) {
      chat.photo = photo;
    }
  }

  @Override
  public void updateChannel (TdApi.Channel channel) {
    if (chat != null && chat.type.getConstructor() == TdApi.ChannelChatInfo.CONSTRUCTOR && ((TdApi.ChannelChatInfo) chat.type).channel.id == channel.id) {
      ((TdApi.ChannelChatInfo) chat.type).channel = channel;
    }
  }*/
}
