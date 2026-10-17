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
 * File created on 02/02/2016 at 23:55
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.AvatarPickerManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.EditHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
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
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.v.EditText;
import org.thunderdog.challegram.widget.EmojiEditText;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import tgx.td.TdConstants;

public class CreateChannelController extends ViewController<String[]> implements EditHeaderView.ReadyCallback, ActivityResultHandler, TextView.OnEditorActionListener {
  private final AvatarPickerManager avatarPickerManager;

  public CreateChannelController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    avatarPickerManager = new AvatarPickerManager(this);
  }

  private EditText descView;
  private ImageView iconView;

  // Header view

  private EditHeaderView headerCell;
  private TextView hintView;

  // XGram M3E: a standard app bar ("New channel"); the content holds the 96dp avatar picker, the filled "Channel name"
  // field and the filled description field (both with length counters), the hint as bodySmall supporting text under
  // it, on the page color, at most 480dp wide. The EditHeaderView stays as the photo's holder, off screen.
  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private @androidx.annotation.Nullable org.thunderdog.challegram.widget.MaterialEditTextGroup xgramNameField;
  private @androidx.annotation.Nullable org.thunderdog.challegram.widget.AvatarView xgramAvatar;
  private boolean xgramReady;

  @Override
  public CharSequence getName () {
    if (xgram) {
      return com.fahadali.xgram.appearance.XgramR6B6Lists.title(R.string.NewChannel);
    }
    return super.getName();
  }

  private View xgramCreateView (Context context) {
    final String[] args = getArguments();
    android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
    scrollView.setFillViewport(true);
    scrollView.setVerticalScrollBarEnabled(false);
    ViewSupport.setThemedBackground(scrollView, ColorId.background, this);

    LinearLayout column = new LinearLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // the fields: the width less 16dp on each side, at most 480dp, centred
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
          int want = Math.max(0, Math.min(MeasureSpec.getSize(widthMeasureSpec) - Screen.dp(32f), Screen.dp(com.fahadali.xgram.appearance.XgramR5People.FORM_MAX_WIDTH)));
          for (int i = 1; i < getChildCount(); i++) {
            ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
            if (params != null && params.width != want) {
              params.width = want;
            }
          }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    column.setOrientation(LinearLayout.VERTICAL);
    column.setGravity(Gravity.CENTER_HORIZONTAL);
    scrollView.addView(column, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    org.thunderdog.challegram.widget.AvatarView avatarView = new org.thunderdog.challegram.widget.AvatarView(context);
    avatarView.setId(R.id.avatar);
    avatarView.xgramSetPicker(true);
    avatarView.setContentDescription(Lang.getString(R.string.SetChannelPhoto));
    avatarView.setOnClickListener(v -> {
      if (!isCreating) {
        hideSoftwareKeyboard();
        avatarPickerManager.showMenuForNonCreatedChat(headerCell, true);
      }
    });
    addThemeInvalidateListener(avatarView);
    LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(Screen.dp(com.fahadali.xgram.appearance.XgramR6B6Lists.AVATAR_HERO), Screen.dp(com.fahadali.xgram.appearance.XgramR6B6Lists.AVATAR_HERO));
    avatarParams.topMargin = avatarParams.bottomMargin = Screen.dp(16f);
    column.addView(avatarView, avatarParams);
    xgramAvatar = avatarView;

    org.thunderdog.challegram.widget.MaterialEditTextGroup nameField = new org.thunderdog.challegram.widget.MaterialEditTextGroup(context, tdlib, true);
    nameField.applyRtl(Lang.rtl());
    nameField.addThemeListeners(this);
    nameField.setHint(com.fahadali.xgram.appearance.XgramR6B6Lists.title(R.string.ChannelName));
    nameField.addLengthCounter(false);
    nameField.setMaxLength(TdConstants.MAX_CHAT_TITLE_LENGTH);
    nameField.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    nameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    nameField.getEditText().setNextFocusDownId(R.id.edit_description);
    nameField.getEditText().setFilters(new InputFilter[] {
      new CodePointCountFilter(TdConstants.MAX_CHAT_TITLE_LENGTH),
      new CharacterStyleFilter()
    });
    if (args != null && args[0] != null) {
      nameField.setText(args[0]);
    }
    xgramReady = args != null && args[0] != null && args[0].trim().length() > 0;
    nameField.setTextListener((v, text) -> {
      final boolean ready = text != null && text.toString().trim().length() > 0;
      if (xgramReady != ready) { // as EditHeaderView does: only when it changes
        xgramReady = ready;
        onReadyStateChanged(ready);
      }
    });
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameParams.bottomMargin = Screen.dp(8f);
    column.addView(nameField, nameParams);
    xgramNameField = nameField;

    org.thunderdog.challegram.widget.MaterialEditTextGroup descField = new org.thunderdog.challegram.widget.MaterialEditTextGroup(context, tdlib, true);
    descField.applyRtl(Lang.rtl());
    descField.addThemeListeners(this);
    descField.setHint(Lang.getString(R.string.Description));
    descField.addLengthCounter(false);
    descField.setMaxLength(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH);
    descView = descField.getEditText();
    descView.setId(R.id.edit_description);
    descView.setSingleLine(false);
    descView.setMaxLines(4);
    descView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    descView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    descView.setFilters(new InputFilter[] {
      new CodePointCountFilter(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH),
      new EmojiFilter(),
      new CharacterStyleFilter()
    });
    if (args != null && args[1] != null) {
      descField.setText(args[1]);
    }
    column.addView(descField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    com.fahadali.xgram.appearance.XgramR5Lists.RoleText supporting = new com.fahadali.xgram.appearance.XgramR5Lists.RoleText(context, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_S, com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    supporting.setGravity(Lang.gravity());
    supporting.setPadding(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(24f));
    supporting.setText(Lang.getString(R.string.DescriptionInfo));
    addThemeInvalidateListener(supporting);
    column.addView(supporting, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    hintView = supporting;

    // The photo's holder for the avatar picker manager (not shown)
    headerCell = new EditHeaderView(context, this);
    headerCell.xgramSetOnPhotoChanged(() -> runOnUiThreadOptional(() -> {
      if (xgramAvatar != null) {
        xgramAvatar.xgramSetImageFile(headerCell.getImageFile());
      }
    }));
    setLockFocusView(nameField.getEditText());
    return scrollView;
  }

  @Override
  protected View onCreateView (Context context) {
    if (xgram) {
      return xgramCreateView(context);
    }
    LinearLayout contentView;

    contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    ViewSupport.setThemedBackground(contentView, ColorId.filling, this);
    contentView.setPadding(0, Size.getHeaderSizeDifference(false), 0, 0);

    FrameLayoutFix frameLayout = new FrameLayoutFix(context);
    frameLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frameLayout.setPadding(Screen.dp(16f), Screen.dp(32f), Screen.dp(16f), 0);

    iconView = new ImageView(context);
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_info_24));
    iconView.setColorFilter(Theme.iconColor());
    addThemeFilterListener(iconView, ColorId.icon);
    iconView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(24f), Screen.dp(46f), Lang.gravity(), Lang.rtl() ? 0 : Screen.dp(6f), 0, Lang.rtl() ? Screen.dp(6f) : 0, 0));
    frameLayout.addView(iconView);

    String[] args = getArguments();

    int marginRight = 0; // Screen.dp(32f);
    int marginLeft = Screen.dp(24f) + Screen.dp(16f) * 2;

    int padding = Screen.dp(9f);

    descView = new EmojiEditText(context);
    descView.initDefault();
    descView.setId(R.id.edit_description);
    descView.setOnFocusChangeListener((v, hasFocus) -> {
      removeThemeListenerByTarget(iconView);
      iconView.setColorFilter(hasFocus ? Theme.togglerActiveColor() : Theme.iconColor());
      int id = hasFocus ? ColorId.togglerActive : ColorId.icon;
      addThemeFilterListener(iconView, id);
    });
    descView.setPadding(0, padding, 0, padding);
    descView.setSingleLine(false);
    descView.setMaxLines(4);
    descView.setHint(Lang.getString(R.string.Description));
    descView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    descView.setGravity(Lang.gravity());
    descView.setFilters(new InputFilter[] {
      new CodePointCountFilter(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH),
      new EmojiFilter(),
      new CharacterStyleFilter()
    });
    descView.setInputType(descView.getInputType() | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    descView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.NO_GRAVITY, Lang.rtl() ? marginRight : marginLeft, 0, Lang.rtl() ? marginLeft : marginRight, 0));
    frameLayout.addView(descView);

    if (args != null) {
      Views.setText(descView, args[1]);
    }
    contentView.addView(frameLayout);

    hintView = new NoScrollTextView(context);
    hintView.setTextColor(Theme.textDecent2Color());
    hintView.setTypeface(Fonts.getRobotoRegular());
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    hintView.setPadding(Screen.dp(Lang.rtl() ? 22f : 72f), Screen.dp(5f), Screen.dp(Lang.rtl() ? 72f : 22f), Screen.dp(16f));
    hintView.setGravity(Lang.gravity());
    hintView.setText(Lang.getString(R.string.DescriptionInfo));
    contentView.addView(hintView);

    // Header view

    headerCell = new EditHeaderView(context, this);
    headerCell.setInputOptions(R.string.ChannelName, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    headerCell.setOnPhotoClickListener(() -> {
      avatarPickerManager.showMenuForNonCreatedChat(headerCell, true);
    });
    headerCell.setNextField(R.id.edit_description);
    headerCell.setReadyCallback(this);
    setLockFocusView(headerCell.getInputView());

    if (args != null) {
      Views.setText(headerCell.getInputView(), args[0]);
    }

    return contentView;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putString(keyPrefix + "title", getTitle());
    outState.putString(keyPrefix + "description", getDescription());
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    String title = in.getString(keyPrefix + "title", "");
    String description = in.getString(keyPrefix + "description", "");
    setArguments(new String[] {title, description});
    return true;
  }

  @Override
  public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_DONE && getTitle().length() > 0) {
      createChannel();
      return true;
    }
    return false;
  }

  @Override
  public int getId () {
    return R.id.controller_newChannel;
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hideList(headerCell == null ? null : headerCell.getInputView(), descView);
    if (xgramNameField != null) {
      Keyboard.hide(xgramNameField.getEditText()); // XGram M3E
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return xgram ? null : headerCell; // XGram M3E: a standard app bar, no input in it
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
    if (xgram) {
      return getTitle().isEmpty() ? 0 : R.drawable.baseline_arrow_forward_24; // XGram M3E: the name is in the content
    }
    return headerCell.isInputEmpty() ? 0 : R.drawable.baseline_arrow_forward_24;
  }

  @Override
  protected void onFloatingButtonPressed () {
    createChannel();
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    avatarPickerManager.handleActivityResult(requestCode, resultCode, data, AvatarPickerManager.MODE_NON_CREATED, null, headerCell);
  }

  @Override
  public void onReadyStateChanged (boolean ready) {
    if (floatingButton != null) {
      if (ready) {
        floatingButton.show(this);
        setLockFocusView(descView);
      } else {
        floatingButton.hide();
        setLockFocusView(xgramNameField != null ? xgramNameField.getEditText() : headerCell.getInputView());
      }
    }
  }

  public String getTitle () {
    if (xgramNameField != null) {
      return xgramNameField.getText().toString().trim(); // XGram M3E
    }
    return headerCell.getInput().trim();
  }

  public String getDescription () {
    return descView.getText().toString();
  }

  public void setDescription (String description) {
    if (description != null) {
      descView.setText(description);
      descView.setSelection(description.length());
    }
  }

  private boolean isCreating;
  private ImageGalleryFile currentImageFile;

  private void toggleCreating () {
    isCreating = !isCreating;
    headerCell.setInputEnabled(!isCreating);
    descView.setEnabled(!isCreating);
    if (xgramNameField != null) {
      xgramNameField.setInputEnabled(!isCreating); // XGram M3E
    }
  }

  public void createChannel () {
    if (isCreating) {
      return;
    }

    String title = getTitle();
    if (StringUtils.isEmptyOrBlank(title))
      return;
    String desc = getDescription();

    toggleCreating();

    currentImageFile = headerCell.getImageFile();

    UI.showProgress(Lang.getString(R.string.ProgressCreateChannel), null, 300L);

    tdlib.send(new TdApi.CreateNewSupergroupChat(title, false, true, desc, null, 0, false), (remoteChat, error) -> {
      UI.hideProgress();
      if (error != null) {
        UI.showError(error);
        chat = null;
      } else {
        long chatId = remoteChat.id;
        chat = tdlib.chatStrict(chatId);
        if (currentImageFile != null) {
          tdlib.send(new TdApi.SetChatPhoto(chat.id, new TdApi.InputChatPhotoStatic(PhotoGenerationInfo.newFile(currentImageFile))), tdlib.typedOkHandler());
        }
      }
      UI.post(() -> channelCreated(chat));
    });
  }

  public void channelCreated (TdApi.Chat chat) {
    if (!isCreating) {
      return;
    }

    toggleCreating();

    if (chat != null) {
      CreateChannelLinkController c = new CreateChannelLinkController(context, tdlib);
      c.setArguments(new CreateChannelLinkController.Args(chat, currentImageFile));
      navigateTo(c);
    }
  }

  private TdApi.Chat chat;

  @Override
  public void destroy () {
    super.destroy();
    if (headerCell != null) {
      headerCell.performDestroy();
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
