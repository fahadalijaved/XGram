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
 * File created on 21/02/2016 at 21:08
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.helper.FoundUrls;
import org.thunderdog.challegram.helper.LinkPreview;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.LinkPreviewToggleView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;
import tgx.td.data.MessageWithProperties;

public class ReplyBarView extends FrameLayoutFix implements View.OnClickListener, Destroyable {
  protected final Tdlib tdlib;
  private Callback callback;

  public ReplyBarView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
  }

  @Override
  public void onClick (View v) {
    if (callback != null) {
      final int id = v.getId();
      if (id == R.id.btn_close) {
        callback.onDismissReplyBar(this);
      } else if (id == R.id.btn_replace) {
        callback.onMessageMediaReplaceRequested(this, getMessage());
      } else if (id == R.id.btn_edit) {
        callback.onMessageMediaEditRequested(this, getMessage());
      }
    }
  }

  private TdApi.Message getMessage () {
    return displayedMessage != null ? displayedMessage.message : null;
  }

  ImageView closeView;
  ImageView replaceMediaView;
  ImageView editMediaView;
  LinkPreviewToggleView linkPreviewToggleView;

  public void checkRtl () {
    if (Views.setGravity(closeView, Lang.gravity())) {
      Views.updateLayoutParams(closeView);
    }
    if (Views.setGravity(closeView, Lang.reverseGravity())) {
      Views.updateLayoutParams(closeView);
    }
    invalidate();
  }

  private PinnedMessagesBar pinnedMessagesBar;

  public void initWithCallback (Callback callbacK, ViewController<?> themeProvider) {
    this.callback = callbacK;
    themeProvider.addThemeInvalidateListener(this);

    FrameLayoutFix.LayoutParams params;

    pinnedMessagesBar = new PinnedMessagesBar(getContext(), false);
    pinnedMessagesBar.setPadding(Screen.dp(49.5f), 0, 0, 0);
    pinnedMessagesBar.setCollapseButtonVisible(false);
    pinnedMessagesBar.setIgnoreAlbums(true);
    pinnedMessagesBar.setMessageListener(new PinnedMessagesBar.MessageListener() {
      @Override
      public void onCreateMessagePreview (PinnedMessagesBar view, MessagePreviewView previewView) {
        ViewUtils.setBackground(previewView, null);
        // previewView.setLinePadding(4f);
      }

      @Override
      public void onMessageDisplayed (PinnedMessagesBar view, MessagePreviewView previewView, TdApi.Message message) {
        previewView.clearPreviewChat();
      }

      @Override
      public void onSelectLinkPreviewUrl (PinnedMessagesBar view, MessagesController.MessageInputContext messageContext, String url) {
        if (callback != null) {
          callback.onSelectLinkPreviewUrl(ReplyBarView.this, messageContext, url);
        }
        updateLinkPreviewSettings(true);
      }

      @Override
      public boolean onToggleLargeMedia (PinnedMessagesBar view, MessagePreviewView previewView, MessagesController.MessageInputContext messageContext, LinkPreview linkPreview) {
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          // XGram M3E: a tap on the preview opens the link preview menu (larger or smaller media is one of its rows)
          xgramShowLinkPreviewMenu(previewView);
          return false;
        }
        if (callback != null && callback.onRequestToggleLargeMedia(ReplyBarView.this, previewView, messageContext, linkPreview)) {
          updateLinkPreviewSettings(true);
          return true;
        }
        return false;
      }

      @Override
      public void onMessageClick (PinnedMessagesBar view, TdApi.Message message, @Nullable TdApi.InputTextQuote quote) {
        if (callback != null) {
          com.fahadali.xgram.chats.XgramQuoteHighlight.prepareJump(tdlib, message.chatId, message.id, quote); // XGram: the quoted words light up there
          callback.onMessageHighlightRequested(ReplyBarView.this, message, quote);
        }
      }
    });
    pinnedMessagesBar.setAnimationsDisabled(animationsDisabled);
    pinnedMessagesBar.initialize(themeProvider);
    addView(pinnedMessagesBar);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.gravity();
    closeView = newButton(R.id.btn_close, R.drawable.baseline_close_24, themeProvider);
    closeView.setLayoutParams(params);
    addView(closeView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.reverseGravity();
    replaceMediaView = newButton(R.id.btn_replace, R.drawable.dot_baseline_image_replace_24, themeProvider);
    replaceMediaView.setLayoutParams(params);
    replaceMediaView.setVisibility(View.GONE);
    addView(replaceMediaView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.reverseGravity();
    params.rightMargin = Screen.dp(46);
    editMediaView = newButton(R.id.btn_edit, R.drawable.baseline_brush_24, themeProvider);
    editMediaView.setLayoutParams(params);
    editMediaView.setVisibility(View.GONE);
    addView(editMediaView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.reverseGravity();
    linkPreviewToggleView = new LinkPreviewToggleView(getContext());
    linkPreviewToggleView.setLayoutParams(params);
    linkPreviewToggleView.setVisibility(View.GONE);
    linkPreviewToggleView.addThemeListeners(themeProvider);
    xgramThemeProvider = themeProvider;
    linkPreviewToggleView.setOnClickListener(v -> {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        xgramShowLinkPreviewMenu(v); // XGram M3E: the link preview menu instead of flipping above / below at once
        return;
      }
      if (callback != null && inputContext != null && callback.onRequestToggleShowAbove(ReplyBarView.this, v, inputContext)) {
        updateLinkPreviewSettings(true);
      }
    });
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      linkPreviewToggleView.setContentDescription(Lang.getString(R.string.LinkPreview));
    }
    Views.setClickable(linkPreviewToggleView);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      com.fahadali.xgram.appearance.XgramAppMotion.iconButton(linkPreviewToggleView); // XGram: its state layer follows theme switches
    } else
    linkPreviewToggleView.setBackgroundResource(R.drawable.bg_btn_header);
    addView(linkPreviewToggleView);
  }

  // XGram M3E: the link preview editor menu

  private @Nullable ViewController<?> xgramThemeProvider;

  /**
   * XGram M3E: the link preview menu, opened by the toggle or a tap on the preview: show above / below the text,
   * larger / smaller media (when the preview has media), choose link (when the text has more than one), then, after
   * a divider, remove preview in error. Each row applies at once, without the info tooltip.
   */
  private void xgramShowLinkPreviewMenu (@NonNull View anchor) {
    final MessagesController.MessageInputContext context = inputContext;
    if (callback == null || context == null) {
      return;
    }
    final TdApi.LinkPreviewOptions options = context.takeOutputLinkPreviewOptions(false);
    final LinkPreview linkPreview = context.getSelectedLinkPreview();
    final List<com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry> entries = new ArrayList<>();
    if (!options.isDisabled) {
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(0,
        Lang.getString(options.showAboveText ? R.string.XgramR5ChatPreviewBelow : R.string.XgramR5ChatPreviewAbove),
        options.showAboveText ? R.drawable.baseline_arrow_downward_24 : R.drawable.baseline_arrow_upward_24, () -> {
          if (callback != null && inputContext == context && callback.onRequestToggleShowAbove(ReplyBarView.this, anchor, context)) {
            updateLinkPreviewSettings(true);
          }
        }));
      if (linkPreview != null && linkPreview.hasMedia()) {
        final boolean large = linkPreview.getOutputShowLargeMedia();
        entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(0,
          Lang.getString(large ? R.string.XgramR5ChatPreviewSmaller : R.string.XgramR5ChatPreviewLarger),
          large ? R.drawable.baseline_image_24 : R.drawable.baseline_zoom_in_24, () -> {
            if (callback != null && inputContext == context && callback.onRequestToggleLargeMedia(ReplyBarView.this, anchor, context, linkPreview)) {
              xgramUpdatePreviewMedia(pinnedMessagesBar);
              updateLinkPreviewSettings(true);
            }
          }));
      }
    }
    final FoundUrls foundUrls = context.getFoundUrls();
    if (foundUrls != null && foundUrls.urls.length > 1) {
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(0, Lang.getString(R.string.XgramR5ChatPreviewChooseLink), R.drawable.baseline_link_24,
        () -> xgramShowChooseLinkMenu(anchor, context)));
    }
    com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry remove = new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(0,
      Lang.getString(R.string.XgramR5ChatPreviewRemove), R.drawable.baseline_link_off_24, () -> {
        if (callback != null && inputContext == context) {
          callback.onDismissReplyBar(ReplyBarView.this);
        }
      }).destructive();
    if (!entries.isEmpty()) {
      remove.dividerAbove();
    }
    entries.add(remove);
    com.fahadali.xgram.appearance.XgramR5Chat.showMenu(anchor, xgramThemeProvider != null ? xgramThemeProvider.getThemeListeners() : null, entries);
  }

  /** XGram M3E: the found links as choices, the one in the preview checked. */
  private void xgramShowChooseLinkMenu (@NonNull View anchor, @NonNull MessagesController.MessageInputContext context) {
    final FoundUrls foundUrls = context.getFoundUrls();
    if (callback == null || inputContext != context || foundUrls == null) {
      return;
    }
    final LinkPreview selected = context.getSelectedLinkPreview();
    final TdApi.LinkPreviewOptions options = context.takeOutputLinkPreviewOptions(false);
    final String selectedUrl = selected != null ? selected.url : !me.vkryl.core.StringUtils.isEmpty(options.url) ? options.url : foundUrls.urls.length > 0 ? foundUrls.urls[0] : null;
    final List<com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry> entries = new ArrayList<>(foundUrls.urls.length);
    for (String url : foundUrls.urls) {
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(0, url, 0, () -> {
        if (callback != null && inputContext == context) {
          callback.onSelectLinkPreviewUrl(ReplyBarView.this, context, url);
          updateLinkPreviewSettings(true);
        }
      }).choice(url.equals(selectedUrl)));
    }
    com.fahadali.xgram.appearance.XgramR5Chat.showMenu(anchor, xgramThemeProvider != null ? xgramThemeProvider.getThemeListeners() : null, entries);
  }

  /** XGram M3E: the shown link previews pick up a media size changed from the menu (the preview's own tap did it before). */
  private static void xgramUpdatePreviewMedia (@Nullable View view) {
    if (view instanceof MessagePreviewView) {
      ((MessagePreviewView) view).updateShowSmallMedia(true);
    } else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        xgramUpdatePreviewMedia(group.getChildAt(i));
      }
    }
  }

  private boolean animationsDisabled;

  public void setAnimationsDisabled (boolean animationsDisabled) {
    if (this.animationsDisabled != animationsDisabled) {
      this.animationsDisabled = animationsDisabled;
      if (pinnedMessagesBar != null) {
        pinnedMessagesBar.setAnimationsDisabled(animationsDisabled);
      }
    }
  }

  public boolean areAnimationsDisabled () {
    return animationsDisabled;
  }

  private ImageView newButton (@IdRes int idRes, @DrawableRes int iconRes, ViewController<?> themeProvider) {
    ImageView btn = new ImageView(getContext());
    btn.setId(idRes);
    btn.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(iconRes));
    btn.setColorFilter(Theme.iconColor());
    themeProvider.addThemeFilterListener(btn, ColorId.icon);
    btn.setScaleType(ImageView.ScaleType.CENTER);
    btn.setOnClickListener(this);
    Views.setClickable(btn);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      com.fahadali.xgram.appearance.XgramAppMotion.iconButton(btn); // XGram: Material 3 Expressive standard icon button, its state layer follows theme switches
    } else
    btn.setBackgroundResource(R.drawable.bg_btn_header);
    return btn;
  }

  private void updateLinkPreviewSettings (boolean animated) {
    LinkPreview linkPreview = inputContext != null ? inputContext.getSelectedLinkPreview() : null;
    setPendingLinkPreview(linkPreview != null && linkPreview.isLoading() ? linkPreview : null);
    if (linkPreview != null) {
      TdApi.LinkPreviewOptions options = inputContext.takeOutputLinkPreviewOptions(false);
      @LinkPreviewToggleView.MediaVisibility int mediaState;
      if (!linkPreview.hasMedia()) {
        mediaState = LinkPreviewToggleView.MediaVisibility.NONE;
      } else {
        mediaState = linkPreview.getOutputShowLargeMedia() ? LinkPreviewToggleView.MediaVisibility.LARGE : LinkPreviewToggleView.MediaVisibility.SMALL;
      }
      linkPreviewToggleView.setMediaVisibility(mediaState, animated);
      linkPreviewToggleView.setShowAboveText(options.showAboveText, animated);
    }
  }

  private void setLinkPreviewToggleVisible (boolean isVisible) {
    if (isVisible != (linkPreviewToggleView.getVisibility() == View.VISIBLE)) {
      linkPreviewToggleView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      checkPinnedMessagesBarPadding();
    }
  }

  private void setMediaEditToggleVisible (boolean canReplace, boolean canEdit) {
    boolean needUpdate = false;
    if (canEdit != (editMediaView.getVisibility() == View.VISIBLE)) {
      editMediaView.setVisibility(canEdit ? View.VISIBLE : View.GONE);
      needUpdate = true;
    }
    if (canReplace != (replaceMediaView.getVisibility() == View.VISIBLE)) {
      replaceMediaView.setVisibility(canReplace ? View.VISIBLE : View.GONE);
      needUpdate = true;
    }
    if (needUpdate) {
      checkPinnedMessagesBarPadding();
    }
  }

  private MessagesController.MessageInputContext inputContext;
  private LinkPreview pendingLinkPreview;

  private final RunnableData<LinkPreview> onLinkPreviewLoadListener = linkPreview -> {
    if (linkPreview == pendingLinkPreview) {
      updateLinkPreviewSettings(true);
    }
  };

  private void setPendingLinkPreview (LinkPreview linkPreview) {
    if (this.pendingLinkPreview != linkPreview) {
      if (this.pendingLinkPreview != null) {
        this.pendingLinkPreview.removeLoadCallback(onLinkPreviewLoadListener);
      }
      this.pendingLinkPreview = linkPreview;
      if (linkPreview != null) {
        linkPreview.addLoadCallback(onLinkPreviewLoadListener);
      }
    }
  }

  private void setMessageInputContext (MessagesController.MessageInputContext context) {
    if (this.inputContext != context) {
      if (this.inputContext != null) {
        setPendingLinkPreview(null);
      }
      this.inputContext = context;
      if (context != null) {
        updateLinkPreviewSettings(false);
      } else {
        setPendingLinkPreview(null);
      }
    }
  }

  public void showWebPage (@NonNull MessagesController.MessageInputContext context, int selectedUrlIndex) {
    FoundUrls foundUrls = context.getFoundUrls();
    List<PinnedMessagesBar.Entry> entryList = new ArrayList<>();
    for (String url : foundUrls.urls) {
      entryList.add(new PinnedMessagesBar.Entry(tdlib, context, url));
    }
    pinnedMessagesBar.setStaticMessageList(entryList, selectedUrlIndex);
    setLinkPreviewToggleVisible(true);
    setMediaEditToggleVisible(false, false);
    setMessageInputContext(context);
  }

  private MessageWithProperties displayedMessage;

  public void setReplyTo (MessageWithProperties msg, @Nullable TdApi.InputTextQuote quote) {
    displayedMessage = msg;
    pinnedMessagesBar.setMessage(tdlib, msg != null ? msg.message : null, quote);
    setLinkPreviewToggleVisible(false);
    setMediaEditToggleVisible(false, false);
    setMessageInputContext(null);
  }

  public void setEditingMessage (MessageWithProperties msg, @Nullable MediaToReplacePickerManager.LocalPickedFile localPickedFile) {
    final boolean hasReplacedImage = localPickedFile != null && localPickedFile.imageGalleryFile != null;

    final boolean canReplace = localPickedFile != null || tdlib.canEditMedia(msg, false);
    final boolean canEdit = canReplace && (localPickedFile == null && tdlib.canEditMedia(msg, true) || hasReplacedImage);

    if (msg.message.mediaAlbumId != 0) {
      final boolean usePhotoIcon = Td.isPhoto(msg.message.content) || hasReplacedImage
        || (msg.message.content != null && msg.message.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR);

      replaceMediaView.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(usePhotoIcon ? R.drawable.dot_baseline_image_replace_24 : R.drawable.dot_baseline_file_replace_24));
    } else {
      replaceMediaView.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.dot_baseline_file_media_replace_24));
    }

    displayedMessage = msg;
    pinnedMessagesBar.setMessage(tdlib, msg.message, null, localPickedFile);
    setLinkPreviewToggleVisible(false);
    setMediaEditToggleVisible(canReplace, canEdit && TD.isFileLoaded(msg.message));
    setMessageInputContext(null);
  }

  public void reset () {
    performDestroy();
  }

  @Override
  public void performDestroy () {
    pinnedMessagesBar.performDestroy();
    setMessageInputContext(null);
  }

  public void completeDestroy () {
    pinnedMessagesBar.completeDestroy();
  }

  public LinkPreviewToggleView getLinkPreviewToggleView () {
    return linkPreviewToggleView;
  }

  public interface Callback {
    void onDismissReplyBar (ReplyBarView view);
    void onMessageMediaReplaceRequested (ReplyBarView view, TdApi.Message message);
    void onMessageMediaEditRequested (ReplyBarView view, TdApi.Message message);
    void onMessageHighlightRequested (ReplyBarView view, TdApi.Message message, @Nullable TdApi.InputTextQuote quote);
    void onSelectLinkPreviewUrl (ReplyBarView view, MessagesController.MessageInputContext messageContext, String url);
    boolean onRequestToggleShowAbove (ReplyBarView view, View buttonView, MessagesController.MessageInputContext messageContext);
    boolean onRequestToggleLargeMedia (ReplyBarView view, View buttonView, MessagesController.MessageInputContext messageContext, LinkPreview linkPreview);
  }

  private void checkPinnedMessagesBarPadding () {
    final int buttonsCount = (Views.isValid(linkPreviewToggleView) ? 1 : 0)
      + (Views.isValid(editMediaView) ? 1 : 0)
      + (Views.isValid(replaceMediaView) ? 1 : 0);
    pinnedMessagesBar.setPadding(Screen.dp(49.5f), 0, buttonsCount > 0 ? Screen.dp(buttonsCount * 48f + 1.5f) : 0, 0);
  }
}
