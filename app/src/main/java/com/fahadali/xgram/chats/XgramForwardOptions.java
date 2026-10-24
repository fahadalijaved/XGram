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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Connected;
import com.fahadali.xgram.appearance.XgramR5Sheets;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Forward without the sender, as exteraGram and AyuGram allow: the forward sheet's Show sender | Hide sender and Hide
 * captions, with a preview of how the first message arrives (ShareController), how the sheet starts (Settings → XGram →
 * Messages → Forwarding), and Forward without sender in the message menu and as a swipe. ShareController keeps the
 * choice in Telegram X's own fields (Send as copy, Remove captions in copies), so its ⋮ menu stays in sync. Hide sender
 * sends copies; a message that can't be copied (protected chats, some polls and quizzes, paid media) is still sent, as
 * a forward with its sender.
 */
public final class XgramForwardOptions {
  private XgramForwardOptions () { }

  /** Settings' choices (Forwarding), in the order they are offered. */
  public static final int[] DEFAULTS = {XgramSettings.FORWARD_SHOW_SENDER, XgramSettings.FORWARD_HIDE_SENDER, XgramSettings.FORWARD_REMEMBER};

  /** The message menu's and the swipe's icon: the forward arrives without a name. */
  public static final int ICON = R.drawable.infanf_baseline_incognito_24;

  public static String defaultName (int mode) {
    switch (mode) {
      case XgramSettings.FORWARD_HIDE_SENDER:
        return Lang.getString(R.string.XgramFwdHideSender);
      case XgramSettings.FORWARD_REMEMBER:
        return Lang.getString(R.string.XgramFwdRemember);
      case XgramSettings.FORWARD_SHOW_SENDER:
      default:
        return Lang.getString(R.string.XgramFwdShowSender);
    }
  }

  /** Whether the forward sheet starts with Hide sender: Forwarding's default, or the last forward's choice. */
  public static boolean hideSenderByDefault () {
    XgramSettings settings = XgramSettings.instance();
    switch (settings.forwardSender()) {
      case XgramSettings.FORWARD_HIDE_SENDER:
        return true;
      case XgramSettings.FORWARD_REMEMBER:
        return settings.forwardLastHidden();
      case XgramSettings.FORWARD_SHOW_SENDER:
      default:
        return false;
    }
  }

  /** After a forward whose sender could be hidden: its choice, for Remember my last choice. */
  public static void remember (boolean hideSender) {
    XgramSettings settings = XgramSettings.instance();
    if (settings.forwardSender() == XgramSettings.FORWARD_REMEMBER && settings.forwardLastHidden() != hideSender) {
      settings.setForwardLastHidden(hideSender);
    }
  }

  // What the messages allow. The properties are TDLib's (getMessageProperties), one per message, null while they load.

  private static boolean canCopy (@Nullable TdApi.MessageProperties[] properties, int index) {
    return properties != null && index < properties.length && properties[index] != null && properties[index].canBeCopied;
  }

  /**
   * The end (exclusive) of the album that starts at {@code start}, or {@code start + 1}: an album stays in one request,
   * the only place TDLib keeps copies of it an album.
   */
  private static int groupEnd (TdApi.Message[] messages, int start) {
    final TdApi.Message first = messages[start];
    int end = start + 1;
    if (first.mediaAlbumId != 0) {
      while (end < messages.length && messages[end].chatId == first.chatId && messages[end].mediaAlbumId == first.mediaAlbumId) {
        end++;
      }
    }
    return end;
  }

  /** Whether an album (or a message) is copied with Hide sender: all of it can be, or all of it keeps its sender. */
  private static boolean canCopyGroup (@Nullable TdApi.MessageProperties[] properties, int start, int end) {
    for (int i = start; i < end; i++) {
      if (!canCopy(properties, i)) {
        return false;
      }
    }
    return true;
  }

  /** How many of the messages keep their sender with Hide sender (they can't be copied), counted by album. */
  public static int notCopyableCount (TdApi.Message[] messages, @Nullable TdApi.MessageProperties[] properties) {
    int count = 0;
    for (int start = 0; start < messages.length; ) {
      final int end = groupEnd(messages, start);
      if (!canCopyGroup(properties, start, end)) {
        count += end - start;
      }
      start = end;
    }
    return count;
  }

  /** Whether Hide sender changes anything: some message can be copied (known once the properties are loaded). */
  public static boolean canHideSender (TdApi.Message[] messages, @Nullable TdApi.MessageProperties[] properties) {
    return properties != null && messages.length > 0 && notCopyableCount(messages, properties) < messages.length;
  }

  /** A media caption: Hide captions leaves the text of a text message alone. */
  public static boolean hasCaption (TdApi.Message message) {
    switch (message.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return !Td.isEmpty(Td.textOrCaption(message.content));
      default:
        return false;
    }
  }

  /** Whether Hide captions changes anything: a message that is copied has a caption. */
  public static boolean hasCopyableCaption (TdApi.Message[] messages, @Nullable TdApi.MessageProperties[] properties) {
    for (int start = 0; start < messages.length; ) {
      final int end = groupEnd(messages, start);
      if (canCopyGroup(properties, start, end)) {
        for (int i = start; i < end; i++) {
          if (hasCaption(messages[i])) {
            return true;
          }
        }
      }
      start = end;
    }
    return false;
  }

  // Sending

  /**
   * The requests forwarding {@code messages} to a chat, as {@link TD#forwardMessages} (one per source chat), with Hide
   * sender (copies) and Hide captions (copies without their media captions). TDLib leaves out a message it can't copy
   * (null in its result), so with a mixed selection the messages that can't be copied go, album by album and in their
   * place in the order, in requests that forward them with their sender. Secret chats keep Telegram X's request: TDLib
   * copies everything there, and the sheet refuses one that a message can't be copied to.
   */
  public static boolean forwardMessages (Tdlib tdlib, long toChatId, @Nullable TdApi.MessageTopic topicId, TdApi.Message[] messages, @Nullable TdApi.MessageProperties[] properties, boolean hideSender, boolean hideCaptions, TdApi.MessageSendOptions options, ArrayList<TdApi.Function<?>> out) {
    final boolean removeCaptions = hideSender && hideCaptions; // TDLib only removes the captions of copies
    if (!hideSender || ChatId.isSecret(toChatId) || messages.length == 0 || properties == null || properties.length != messages.length) {
      return TD.forwardMessages(toChatId, topicId, messages, hideSender, removeCaptions, options, out);
    }
    final int notCopyable = notCopyableCount(messages, properties);
    if (notCopyable == 0 || notCopyable == messages.length || inSlowMode(tdlib, toChatId)) {
      // One way for all of them: copies, or forwards when some can't be copied and the group is in slow mode (it
      // refuses a second send right after the first, so a split would lose its second part)
      final boolean copy = notCopyable == 0;
      return TD.forwardMessages(toChatId, topicId, messages, copy, copy && removeCaptions, options, out);
    }
    int runStart = 0;
    boolean runCopy = canCopyGroup(properties, 0, groupEnd(messages, 0));
    for (int start = 0; start < messages.length; ) {
      final int end = groupEnd(messages, start);
      final boolean copy = canCopyGroup(properties, start, end);
      if (copy != runCopy) {
        TD.forwardMessages(toChatId, topicId, Arrays.copyOfRange(messages, runStart, start), runCopy, runCopy && removeCaptions, options, out);
        runStart = start;
        runCopy = copy;
      }
      start = end;
    }
    return TD.forwardMessages(toChatId, topicId, Arrays.copyOfRange(messages, runStart, messages.length), runCopy, runCopy && removeCaptions, options, out);
  }

  /** A group in slow mode for this account (an admin isn't held to it), as far as its cached full info tells. */
  private static boolean inSlowMode (Tdlib tdlib, long chatId) {
    if (!ChatId.isSupergroup(chatId)) {
      return false;
    }
    TdApi.SupergroupFullInfo fullInfo = tdlib.cache().supergroupFull(ChatId.toSupergroupId(chatId), false);
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    return fullInfo != null && fullInfo.slowModeDelay != 0 && !(status != null && TD.isAdmin(status));
  }

  // The message menu

  /** Forward without sender, after Share in a message's menu: the forward sheet opens with Hide sender set. */
  public static void fillMenu (TGMessage msg, IntList ids, IntList icons, StringList strings) {
    if (msg.xgramCanBeCopied() && !msg.isSecretChat()) {
      ids.append(R.id.btn_xgramForwardNoSender);
      strings.append(R.string.XgramFwdWithoutSender);
      icons.append(ICON);
    }
  }

  // The forward sheet: the options above its comment field, sliding in with it as chats are picked

  /** What the options change; ShareController keeps them (needHideAuthor, needRemoveCaptions). */
  public interface Callback {
    void onForwardSenderChanged (boolean hideSender);
    void onForwardCaptionsChanged (boolean hideCaptions);
  }

  /** Below this window height (dp, a phone in landscape) the preview is left out, and every option while typing. */
  private static final int SHORT_WINDOW_DP = 560;
  /** From this window height (dp) the reason line and Hide captions stay while typing; the preview never does. */
  private static final int TALL_WINDOW_DP = 800;

  /**
   * The forward sheet's options: a preview of the first message as it will arrive (its "Forwarded from" line, its media
   * or text, its caption), Show sender | Hide sender (an M3E connected button group with Expressive design, Telegram
   * X's radio buttons otherwise), why the sender can't be hidden or which messages keep it, and Hide captions while a
   * copied message has one. The sheet's own color behind it; the classic look ends it with a separator.
   */
  public static final class Panel extends LinearLayout {
    private final TdApi.Message[] messages;
    private final boolean m3;
    private final PreviewView preview;
    private final SenderGroup sender;
    private final TextView info;
    private final SwitchRow captions;

    private boolean typing, hasInfo, hasCaptions;

    public Panel (Context context, Tdlib tdlib, @Nullable ViewController<?> themeProvider, TdApi.Message[] messages, int backgroundColorId, Callback callback) {
      super(context);
      this.messages = messages;
      this.m3 = XgramExpressive.isEnabled();
      setOrientation(VERTICAL);
      ViewSupport.setThemedBackground(this, backgroundColorId, themeProvider);

      preview = new PreviewView(context, tdlib, messages, m3);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (m3) {
        // a surfaceContainerHigh card inside the sheet, as the Quote and AI sheets'
        params.leftMargin = params.rightMargin = Screen.dp(16f);
        params.topMargin = Screen.dp(8f);
      }
      addView(preview, params);

      sender = new SenderGroup(context, m3, index -> callback.onForwardSenderChanged(index == 1));
      addView(sender, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      info = new TextView(context);
      if (m3) {
        XgramExpressive.applyText(info, XgramExpressive.TS_BODY_S, false);
        info.setGravity(Gravity.CENTER); // under the centered buttons
      } else {
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        info.setTypeface(Fonts.getRobotoRegular());
        info.setGravity(Lang.gravity());
      }
      info.setMaxLines(2);
      info.setEllipsize(TextUtils.TruncateAt.END);
      info.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      captions = new SwitchRow(context, m3, Lang.getString(R.string.XgramFwdHideCaptions));
      captions.setOnClickListener(v -> callback.onForwardCaptionsChanged(!captions.checked));
      addView(captions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(m3 ? 56f : 54f)));

      setState(null, false, false, false);
      refreshColors();
    }

    /** The options as ShareController has them; {@code properties} is null while TDLib's are loading. */
    public void setState (@Nullable TdApi.MessageProperties[] properties, boolean hideSender, boolean hideCaptions, boolean animated) {
      final int notCopyable = properties != null ? notCopyableCount(messages, properties) : 0;
      final boolean canHide = properties == null || notCopyable < messages.length;
      sender.setState(hideSender ? 1 : 0, canHide, animated);
      final String reason;
      if (properties == null || notCopyable == 0) {
        reason = null;
      } else if (!canHide) {
        reason = Lang.getString(messages.length == 1 ? R.string.XgramFwdCantHideOne : R.string.XgramFwdCantHideAll);
      } else if (notCopyable == 1) {
        reason = Lang.getString(R.string.XgramFwdOneKeepsSender);
      } else {
        reason = Lang.getString(R.string.XgramFwdSomeKeepSender, notCopyable);
      }
      info.setText(reason);
      hasInfo = reason != null;
      // TalkBack: why Hide sender is greyed out is read with the button, not as a line after it
      final String disabledReason = canHide ? null : reason;
      sender.setHideReason(disabledReason);
      info.setImportantForAccessibility(disabledReason != null ? IMPORTANT_FOR_ACCESSIBILITY_NO : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      hasCaptions = properties != null && hasCopyableCaption(messages, properties);
      captions.setChecked(hideSender && hideCaptions, animated);
      // The first message (or album) as it arrives: a copy has no "Forwarded from" line, only a copy loses its caption
      final boolean firstCopied = hideSender && (properties == null || canCopyGroup(properties, 0, groupEnd(messages, 0)));
      preview.setState(!firstCopied, firstCopied && hideCaptions, animated);
      updateVisibility();
    }

    /** While the keyboard (or the emoji keyboard) is up. */
    public void setTyping (boolean typing) {
      if (this.typing != typing) {
        this.typing = typing;
        updateVisibility();
      }
    }

    private void updateVisibility () {
      // A short window (a phone in landscape) keeps its room for the chats: no preview, and while typing the comment
      // field and the keyboard take it all. While typing, the keyboard takes half of any phone: only Show sender |
      // Hide sender stays, unless the window is tall (a tablet, an unfolded Fold)
      final int heightDp = getResources().getConfiguration().screenHeightDp;
      final boolean shortWindow = heightDp < SHORT_WINDOW_DP;
      final boolean shown = !(typing && shortWindow);
      final boolean extras = shown && (!typing || heightDp >= TALL_WINDOW_DP);
      setVisible(preview, shown && !shortWindow && !typing);
      setVisible(sender, shown);
      setVisible(info, extras && hasInfo);
      setVisible(captions, extras && hasCaptions);
    }

    private static void setVisible (View view, boolean visible) {
      final int visibility = visible ? View.VISIBLE : View.GONE;
      if (view.getVisibility() != visibility) {
        view.setVisibility(visibility);
      }
    }

    @Override
    protected void onConfigurationChanged (Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      updateVisibility(); // turned, folded or unfolded
    }

    /** Theme changes (ShareController.onThemeColorsChanged). */
    public void refreshColors () {
      preview.refreshColors();
      sender.refreshColors();
      captions.refreshColors();
      info.setTextColor(m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.textDecentColor());
      invalidate();
    }

    public void performDestroy () {
      preview.performDestroy();
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      super.dispatchDraw(c);
      if (!m3 && getHeight() > 0) {
        // Telegram X's separator between the options and the comment field
        final int size = Math.max(1, Screen.separatorSize());
        c.drawRect(0, getHeight() - size, getWidth(), getHeight(), Paints.fillingPaint(Theme.separatorColor()));
      }
    }
  }

  /** TalkBack: a choice is a radio button, checked while selected. */
  private static final View.AccessibilityDelegate RADIO_DELEGATE = new View.AccessibilityDelegate() {
    @Override
    public void onInitializeAccessibilityNodeInfo (View host, AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(host, info);
      info.setClassName(android.widget.RadioButton.class.getName());
      info.setCheckable(true);
      info.setChecked(host.isSelected());
      info.setSelected(false); // announced once, as checked (View.setSelected marks the node selected too)
    }
  };

  /** TalkBack: the row is a switch. */
  private static final View.AccessibilityDelegate SWITCH_DELEGATE = new View.AccessibilityDelegate() {
    @Override
    public void onInitializeAccessibilityNodeInfo (View host, AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(host, info);
      info.setClassName(android.widget.Switch.class.getName());
      info.setCheckable(true);
      info.setChecked(host instanceof SwitchRow && ((SwitchRow) host).checked);
    }
  };

  /** A 0..1 state: a spring with Expressive design (snapped with Reduce motion), Telegram X's 180ms otherwise. */
  private static final class Factor {
    private final @Nullable XgramExpressive.Spring spring;
    private final @Nullable BoolAnimator animator;

    Factor (boolean m3, boolean value, Runnable onChange) {
      if (m3) {
        spring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, value ? 1f : 0f, (s, v) -> onChange.run()).setThreshold(.002f);
        animator = null;
      } else {
        spring = null;
        animator = new BoolAnimator(0, (id, factor, fraction, callee) -> onChange.run(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, value);
      }
    }

    void set (boolean value, boolean animated) {
      if (spring != null) {
        if (animated) {
          spring.animateTo(value ? 1f : 0f);
        } else {
          spring.setValue(value ? 1f : 0f);
        }
      } else if (animator != null) {
        animator.setValue(value, animated);
      }
    }

    float get () {
      if (spring != null) {
        return MathUtils.clamp(spring.getValue());
      }
      return animator != null ? animator.getFloatValue() : 0f;
    }
  }

  // Show sender | Hide sender

  /** Show sender | Hide sender (0 and 1, in reading order; the first is on the right in RTL). */
  private static final class SenderGroup extends ViewGroup {
    interface Listener {
      void onSenderSelected (int index);
    }

    /** M3E connected button group, size S: 40dp buttons 2dp apart, 8dp above and below, 16dp from the sheet's sides. */
    private static final float M3_HEIGHT = 40f, M3_VERTICAL = 8f, M3_SIDE = 16f, CLASSIC_HEIGHT = 48f;

    private final boolean m3;
    private final View[] cells = new View[2];
    private final TextView[] labels = new TextView[2];
    private final XgramExpressive.MorphDrawable[] morphs = new XgramExpressive.MorphDrawable[2];
    private final RadioView[] radios = new RadioView[2];
    private int selected = -1;
    private boolean hideEnabled = true;

    SenderGroup (Context context, boolean m3, Listener listener) {
      super(context);
      this.m3 = m3;
      final boolean rtl = Lang.rtl();
      final int[] names = {R.string.XgramFwdShowSender, R.string.XgramFwdHideSender};
      for (int i = 0; i < 2; i++) {
        final int index = i;
        TextView label = new TextView(context);
        label.setText(Lang.getString(names[i]));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        View cell;
        if (m3) {
          // The selected button is secondaryContainer with a leading check and rounds into a pill, the other one is
          // surfaceContainerHighest (as XgramR5Security.SegmentedRow)
          XgramExpressive.applyText(label, XgramExpressive.TS_LABEL_L, false);
          label.setGravity(Gravity.CENTER);
          label.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
          label.setCompoundDrawablePadding(Screen.dp(8f));
          morphs[i] = XgramM3Connected.attachButton(label, XgramExpressive.SURFACE_CONTAINER_HIGHEST);
          XgramM3Connected.shape(morphs[i], true, true, rtl ? 1 - i : i, 2); // physical column
          cell = label;
        } else {
          // Telegram X: a radio button and its label, each choice half of the row
          label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
          label.setTypeface(Fonts.getRobotoRegular());
          label.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
          label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
          RadioView radio = new RadioView(context);
          radio.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
          radios[i] = radio;
          LinearLayout row = new LinearLayout(context);
          row.setOrientation(LinearLayout.HORIZONTAL);
          row.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
          row.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
          LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f));
          LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
          if (rtl) {
            labelParams.rightMargin = Screen.dp(12f);
            row.addView(label, labelParams);
            row.addView(radio, radioParams);
          } else {
            labelParams.leftMargin = Screen.dp(12f);
            row.addView(radio, radioParams);
            row.addView(label, labelParams);
          }
          row.setContentDescription(label.getText());
          RippleSupport.setTransparentSelector(row);
          cell = row;
        }
        cell.setOnClickListener(v -> {
          if (index != selected && v.isEnabled()) {
            if (m3) {
              v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            listener.onSenderSelected(index);
          }
        });
        cell.setAccessibilityDelegate(RADIO_DELEGATE);
        labels[i] = label;
        cells[i] = cell;
        addView(cell);
      }
    }

    void setState (int selected, boolean hideEnabled, boolean animated) {
      if (this.selected == selected && this.hideEnabled == hideEnabled) {
        return;
      }
      this.selected = selected;
      this.hideEnabled = hideEnabled;
      final boolean rtl = Lang.rtl();
      for (int i = 0; i < 2; i++) {
        final boolean on = i == selected;
        cells[i].setSelected(on);
        cells[i].setEnabled(i == 0 || hideEnabled);
        if (m3) {
          morphs[i].setCornerTarget(on ? XgramExpressive.CORNER_FULL : Float.NaN, animated && isAttachedToWindow());
          Drawable check = null;
          if (on) {
            check = Drawables.get(getResources(), R.drawable.baseline_check_24);
            if (check != null) {
              final int size = Screen.dp(18f);
              check.setBounds(0, 0, size, size);
            }
          }
          labels[i].setCompoundDrawables(rtl ? null : check, null, rtl ? check : null, null);
        } else {
          radios[i].setChecked(on, animated);
          radios[i].setActive(cells[i].isEnabled(), animated);
        }
      }
      refreshColors();
      requestLayout(); // the check widens its button
    }

    /** TalkBack: why Hide sender is greyed out, read with the button itself; null while it isn't. */
    void setHideReason (@Nullable String reason) {
      final CharSequence label = labels[1].getText();
      cells[1].setContentDescription(reason != null ? label + ". " + reason : m3 ? null : label);
    }

    /** Label and check colours (the containers read theirs when drawn). */
    void refreshColors () {
      for (int i = 0; i < 2; i++) {
        final boolean on = i == selected;
        final boolean enabled = cells[i].isEnabled();
        final int color;
        if (m3) {
          final int onSurface = XgramExpressive.color(XgramExpressive.ON_SURFACE);
          if (enabled) {
            morphs[i].setColorRole(on ? XgramExpressive.SECONDARY_CONTAINER : XgramExpressive.SURFACE_CONTAINER_HIGHEST);
            color = on ? XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER) : onSurface;
          } else {
            morphs[i].setColor(XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER));
            color = XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT);
          }
        } else {
          color = enabled ? Theme.textAccentColor() : Theme.textPlaceholderColor();
          radios[i].invalidate();
        }
        labels[i].setTextColor(color);
        for (Drawable d : labels[i].getCompoundDrawables()) {
          if (d != null) {
            d.setTint(color);
          }
        }
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int width = MeasureSpec.getSize(widthMeasureSpec);
      final int height = Screen.dp(m3 ? M3_HEIGHT : CLASSIC_HEIGHT);
      final int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
      final int each;
      if (m3) {
        // Both buttons take the wider one's width, so nothing moves as the check changes sides, and share the room
        // when it is short (the cover screen): the labels end in an ellipsis rather than scroll
        final int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int natural = 0;
        for (View cell : cells) {
          cell.measure(unspecified, heightSpec);
          natural = Math.max(natural, cell.getMeasuredWidth());
        }
        final int room = width - Screen.dp(M3_SIDE) * 2 - Screen.dp(XgramM3Connected.GAP);
        each = Math.max(0, Math.min(natural, room / 2));
        setMeasuredDimension(width, height + Screen.dp(M3_VERTICAL) * 2);
      } else {
        each = width / 2;
        setMeasuredDimension(width, height);
      }
      final int eachSpec = MeasureSpec.makeMeasureSpec(each, MeasureSpec.EXACTLY);
      for (View cell : cells) {
        cell.measure(eachSpec, heightSpec);
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      final int each = cells[0].getMeasuredWidth();
      final int height = cells[0].getMeasuredHeight();
      final int gap = m3 ? Screen.dp(XgramM3Connected.GAP) : 0;
      final int top = m3 ? Screen.dp(M3_VERTICAL) : 0;
      int left = m3 ? (r - l - each * 2 - gap) / 2 : 0;
      final boolean rtl = Lang.rtl();
      for (int p = 0; p < 2; p++) {
        View cell = cells[rtl ? 1 - p : p];
        cell.layout(left, top, left + each, top + height);
        left += each + gap;
      }
    }
  }

  // Hide captions

  /** A row with a switch at its end; the whole row toggles it (Telegram X's switch, the M3 one with Expressive design). */
  private static final class SwitchRow extends FrameLayout {
    private final boolean m3;
    private final TextView title;
    private final TogglerView toggler;
    private boolean checked;

    SwitchRow (Context context, boolean m3, String text) {
      super(context);
      this.m3 = m3;
      final boolean rtl = Lang.rtl();
      final int switchWidth = Screen.dp(m3 ? 72f : 66f);

      title = new TextView(context);
      title.setText(text);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      title.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      if (m3) {
        XgramExpressive.applyText(title, XgramExpressive.TS_BODY_L, false);
      } else {
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        title.setTypeface(Fonts.getRobotoRegular());
      }
      title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      params.leftMargin = rtl ? switchWidth : Screen.dp(16f);
      params.rightMargin = rtl ? Screen.dp(16f) : switchWidth;
      addView(title, params);

      toggler = new TogglerView(context);
      toggler.init(false);
      toggler.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      // As in SettingView: the classic track sits 20dp from the switch's top (centered in this 54dp row), the M3 one
      // is centered on its height
      params = new FrameLayout.LayoutParams(switchWidth, m3 ? ViewGroup.LayoutParams.MATCH_PARENT : Screen.dp(48f), (rtl ? Gravity.LEFT : Gravity.RIGHT) | (m3 ? Gravity.CENTER_VERTICAL : Gravity.TOP));
      params.leftMargin = Screen.dp(4f);
      addView(toggler, params);

      RippleSupport.setTransparentSelector(this);
      setContentDescription(text);
      setAccessibilityDelegate(SWITCH_DELEGATE);
    }

    void setChecked (boolean checked, boolean animated) {
      if (this.checked != checked) {
        this.checked = checked;
        toggler.setRadioEnabled(checked, animated && isAttachedToWindow());
      }
    }

    void refreshColors () {
      title.setTextColor(m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE) : Theme.textAccentColor());
      toggler.invalidate();
    }
  }

  // The preview

  /** The first message (or album) as it will arrive, and how many messages follow it. */
  private static final class PreviewView extends FrameLayout {
    private final boolean m3;
    private final boolean bodyIsCaption;
    private final int moreCount;
    private final String headerText;
    private final @Nullable String mediaText, bodyText;
    private final @Nullable ThumbView thumb;
    private final LinearLayout column;
    private final EmojiTextView header;
    private final @Nullable EmojiTextView media;
    private final @Nullable CaptionView body;
    private final @Nullable TextView more;
    private final Factor senderShown, captionHidden;

    PreviewView (Context context, Tdlib tdlib, TdApi.Message[] messages, boolean m3) {
      super(context);
      this.m3 = m3;
      final boolean rtl = Lang.rtl();
      final int end = groupEnd(messages, 0);
      final TdApi.Message first = messages[0];

      String name = tdlib.senderName(first, true, false); // the original sender of a message forwarded before
      if (StringUtils.isEmpty(name)) {
        name = tdlib.chatTitle(first.chatId);
      }
      headerText = Lang.getString(R.string.ForwardedFromX, name != null ? name : "");
      // An album's caption is on one of its messages
      TdApi.Message captioned = first;
      for (int i = 0; i < end; i++) {
        if (hasCaption(messages[i])) {
          captioned = messages[i];
          break;
        }
      }
      bodyIsCaption = hasCaption(captioned);
      final boolean isText = first.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR;
      if (isText) {
        mediaText = null;
        bodyText = ((TdApi.MessageText) first.content).text.text;
      } else {
        final String label = mediaLabel(tdlib, messages, end);
        mediaText = StringUtils.isEmpty(label) ? null : label;
        TdApi.FormattedText caption = bodyIsCaption ? Td.textOrCaption(captioned.content) : null;
        bodyText = caption != null ? caption.text : null;
      }
      moreCount = messages.length - end;

      final int thumbSize = Screen.dp(m3 ? 40f : 36f);
      final int startPadding;
      if (m3) {
        XgramR5Sheets.applyCard(this, XgramExpressive.SURFACE_CONTAINER_HIGH, XgramExpressive.CORNER_L);
        startPadding = Screen.dp(16f);
        setPadding(rtl ? Screen.dp(16f) : startPadding, Screen.dp(10f), rtl ? startPadding : Screen.dp(16f), Screen.dp(10f));
      } else {
        // Telegram X's quote bar at 16dp (dispatchDraw), the content 10dp after it
        startPadding = Screen.dp(28f);
        setPadding(rtl ? Screen.dp(16f) : startPadding, Screen.dp(8f), rtl ? startPadding : Screen.dp(16f), Screen.dp(8f));
      }

      Bitmap bitmap = decode(minithumbnail(first.content));
      if (bitmap != null) {
        thumb = new ThumbView(context, bitmap, first.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR ? -1f : m3 ? 8f : 4f);
        thumb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(thumb, new FrameLayout.LayoutParams(thumbSize, thumbSize, Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT)));
      } else {
        thumb = null;
      }

      if (moreCount > 0) {
        more = new TextView(context);
        more.setText(Lang.getString(R.string.XgramFwdMore, moreCount));
        more.setSingleLine(true);
        if (m3) {
          XgramExpressive.applyText(more, XgramExpressive.TS_LABEL_L, false);
        } else {
          more.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
          more.setTypeface(Fonts.getRobotoMedium());
        }
        more.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(more, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT)));
      } else {
        more = null;
      }

      column = new LinearLayout(context);
      column.setOrientation(LinearLayout.VERTICAL);
      column.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      header = newLine(context, m3 ? XgramExpressive.TS_LABEL_L : -1, 14f, true);
      header.setText(headerText);
      column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      if (mediaText != null) {
        media = newLine(context, m3 ? XgramExpressive.TS_BODY_M : -1, 15f, false);
        media.setText(mediaText);
        column.addView(media, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      } else {
        media = null;
      }
      if (!StringUtils.isEmpty(bodyText)) {
        body = new CaptionView(context);
        styleLine(body, m3 ? XgramExpressive.TS_BODY_M : -1, 15f, false);
        if (isText) {
          // a text message: up to two lines of it
          body.setMaxLines(2);
          body.setEllipsize(TextUtils.TruncateAt.END);
        } else {
          body.setSingleLine(true);
          body.setEllipsize(TextUtils.TruncateAt.END);
        }
        body.setText(bodyText);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      } else {
        body = null;
      }
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
      final int thumbSpace = thumb != null ? thumbSize + Screen.dp(12f) : 0;
      final int moreSpace = more != null ? Screen.dp(44f) : 0;
      params.leftMargin = rtl ? moreSpace : thumbSpace;
      params.rightMargin = rtl ? thumbSpace : moreSpace;
      addView(column, params);

      senderShown = new Factor(m3, true, this::applyFactors);
      captionHidden = new Factor(m3, false, this::applyFactors);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      refreshColors();
    }

    private static EmojiTextView newLine (Context context, int ts, float classicSize, boolean classicMedium) {
      EmojiTextView view = new EmojiTextView(context);
      styleLine(view, ts, classicSize, classicMedium);
      view.setSingleLine(true);
      view.setEllipsize(TextUtils.TruncateAt.END);
      return view;
    }

    private static void styleLine (TextView view, int ts, float classicSize, boolean classicMedium) {
      if (ts >= 0) {
        XgramExpressive.applyText(view, ts, false);
      } else {
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, classicSize);
        view.setTypeface(classicMedium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
      }
      view.setGravity(Lang.gravity());
    }

    /** {@code showSender}: the "Forwarded from" line stays (a forward); {@code hideCaption}: the caption is struck out. */
    void setState (boolean showSender, boolean hideCaption, boolean animated) {
      final boolean animate = animated && isAttachedToWindow();
      senderShown.set(showSender, animate);
      captionHidden.set(hideCaption && bodyIsCaption, animate);
      applyFactors();
      // TalkBack reads the card as one line
      StringBuilder b = new StringBuilder(showSender ? headerText : Lang.getString(R.string.XgramFwdPreviewNoSender));
      if (mediaText != null) {
        b.append(". ").append(mediaText);
      }
      if (!StringUtils.isEmpty(bodyText)) {
        b.append(". ").append(hideCaption && bodyIsCaption ? Lang.getString(R.string.XgramFwdPreviewCaptionHidden) : cut(bodyText, 200));
      }
      if (moreCount > 0) {
        b.append(". ").append(Lang.getString(R.string.XgramFwdPreviewMore, moreCount));
      }
      setContentDescription(Lang.getString(R.string.XgramFwdPreview, b.toString()));
    }

    /**
     * The "Forwarded from" line fades out and the lines under it rise by half of its height, so what stays is centered
     * in the card, whose height never changes (the sheet does not move as the options change).
     */
    private void applyFactors () {
      final float shown = senderShown.get();
      header.setAlpha(shown);
      column.setTranslationY(-header.getHeight() / 2f * (1f - shown));
      if (body != null) {
        body.setStrike(captionHidden.get());
      }
    }

    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
      super.onLayout(changed, left, top, right, bottom);
      applyFactors(); // the header's height is known now
    }

    void refreshColors () {
      if (m3) {
        header.setTextColor(XgramExpressive.color(XgramExpressive.PRIMARY));
        if (media != null) {
          media.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
        }
        if (body != null) {
          body.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
        }
        if (more != null) {
          more.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
        }
      } else {
        header.setTextColor(Theme.textLinkColor());
        if (media != null) {
          media.setTextColor(Theme.textAccentColor());
        }
        if (body != null) {
          body.setTextColor(Theme.textDecentColor());
        }
        if (more != null) {
          more.setTextColor(Theme.textDecentColor());
        }
      }
      invalidate();
    }

    void performDestroy () {
      header.performDestroy();
      if (media != null) {
        media.performDestroy();
      }
      if (body != null) {
        body.performDestroy();
      }
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (!m3) {
        // Telegram X's quote bar, as in the message box's reply and forward preview
        final int barWidth = Screen.dp(2f);
        final int x = Lang.rtl() ? getWidth() - Screen.dp(16f) - barWidth : Screen.dp(16f);
        c.drawRect(x, getPaddingTop(), x + barWidth, getHeight() - getPaddingBottom(), Paints.fillingPaint(Theme.getColor(ColorId.messageVerticalLine)));
      }
      super.dispatchDraw(c);
    }

    private static String cut (String text, int max) {
      if (text.length() <= max) {
        return text;
      }
      int end = max;
      if (Character.isHighSurrogate(text.charAt(end - 1))) {
        end--;
      }
      return text.substring(0, end) + "…";
    }
  }

  /** The preview's media line: "Photo", "3 photos", a file's or a song's name; Telegram X's chat list text otherwise. */
  @Nullable
  private static String mediaLabel (Tdlib tdlib, TdApi.Message[] messages, int end) {
    final TdApi.Message first = messages[0];
    if (end > 1) {
      int type = first.content.getConstructor();
      for (int i = 1; i < end; i++) {
        if (messages[i].content.getConstructor() != type) {
          type = 0;
          break;
        }
      }
      final int res;
      switch (type) {
        case TdApi.MessagePhoto.CONSTRUCTOR:
          res = R.string.xPhotos;
          break;
        case TdApi.MessageVideo.CONSTRUCTOR:
          res = R.string.xVideos;
          break;
        case TdApi.MessageDocument.CONSTRUCTOR:
          res = R.string.xFiles;
          break;
        case TdApi.MessageAudio.CONSTRUCTOR:
          res = R.string.xAudios;
          break;
        default:
          res = R.string.xMedia;
          break;
      }
      return Lang.plural(res, end);
    }
    switch (first.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return Lang.getString(R.string.ChatContentPhoto);
      case TdApi.MessageVideo.CONSTRUCTOR:
        return Lang.getString(R.string.ChatContentVideo);
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return Lang.getString(R.string.ChatContentAnimation);
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return Lang.getString(R.string.ChatContentVoice);
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return Lang.getString(R.string.ChatContentRoundVideo);
      case TdApi.MessageDocument.CONSTRUCTOR: {
        String fileName = ((TdApi.MessageDocument) first.content).document.fileName;
        return StringUtils.isEmpty(fileName) ? Lang.getString(R.string.ChatContentFile) : fileName;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) first.content).audio;
        return Lang.getString(R.string.ChatContentSong, TD.getTitle(audio), TD.getSubtitle(audio));
      }
      default:
        try {
          return ContentPreview.getChatListPreview(tdlib, first.chatId, first, false).buildText(false);
        } catch (Throwable t) {
          return null; // a content type this build doesn't know: the preview keeps its other lines
        }
    }
  }

  /** The tiny blurred image TDLib keeps in the message (no download); none for media under a spoiler. */
  @Nullable
  private static TdApi.Minithumbnail minithumbnail (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto photo = (TdApi.MessagePhoto) content;
        return photo.hasSpoiler ? null : photo.photo.minithumbnail;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.MessageVideo video = (TdApi.MessageVideo) content;
        return video.hasSpoiler ? null : video.video.minithumbnail;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.MessageAnimation animation = (TdApi.MessageAnimation) content;
        return animation.hasSpoiler ? null : animation.animation.minithumbnail;
      }
      case TdApi.MessageDocument.CONSTRUCTOR:
        return ((TdApi.MessageDocument) content).document.minithumbnail;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return ((TdApi.MessageVideoNote) content).videoNote.minithumbnail;
      case TdApi.MessageAudio.CONSTRUCTOR:
        return ((TdApi.MessageAudio) content).audio.albumCoverMinithumbnail;
      default:
        return null;
    }
  }

  @Nullable
  private static Bitmap decode (@Nullable TdApi.Minithumbnail minithumbnail) {
    if (minithumbnail == null || minithumbnail.data == null || minithumbnail.data.length == 0) {
      return null;
    }
    try {
      return BitmapFactory.decodeByteArray(minithumbnail.data, 0, minithumbnail.data.length);
    } catch (Throwable t) {
      return null;
    }
  }

  /** The preview's thumbnail: centre-cropped into a rounded square (a circle for a video message). */
  private static final class ThumbView extends View {
    private final Bitmap bitmap;
    private final float cornerDp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final BitmapShader shader;
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();

    /** {@code cornerDp} below zero: a circle. */
    ThumbView (Context context, Bitmap bitmap, float cornerDp) {
      super(context);
      this.bitmap = bitmap;
      this.cornerDp = cornerDp;
      this.shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
      paint.setShader(shader);
    }

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      if (width == 0 || height == 0 || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
        return;
      }
      final float scale = Math.max((float) width / bitmap.getWidth(), (float) height / bitmap.getHeight());
      matrix.setScale(scale, scale);
      matrix.postTranslate((width - bitmap.getWidth() * scale) / 2f, (height - bitmap.getHeight() * scale) / 2f);
      shader.setLocalMatrix(matrix);
      rect.set(0, 0, width, height);
      final float radius = cornerDp < 0f ? Math.min(width, height) / 2f : Screen.dp(cornerDp);
      c.drawRoundRect(rect, radius, radius, paint);
    }
  }

  /** A caption that Hide captions strikes out: a line drawn through its first line (as far as {@link #setStrike} goes), faded to disabled. */
  private static final class CaptionView extends EmojiTextView {
    private float strike;

    CaptionView (Context context) {
      super(context);
    }

    void setStrike (float strike) {
      if (this.strike != strike) {
        this.strike = strike;
        setAlpha(1f - (1f - XgramExpressive.DISABLED_CONTENT) * strike);
        invalidate();
      }
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      super.onDraw(c);
      final Layout layout = getLayout();
      if (strike <= 0f || layout == null || layout.getLineCount() == 0) {
        return;
      }
      final float left = getTotalPaddingLeft() + layout.getLineLeft(0);
      final float right = Math.min(getTotalPaddingLeft() + layout.getLineRight(0), getWidth() - getTotalPaddingRight());
      if (right <= left) {
        return;
      }
      final float y = getTotalPaddingTop() + layout.getLineBaseline(0) - getTextSize() * .3f;
      final float length = (right - left) * MathUtils.clamp(strike);
      final boolean rtl = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
      c.drawLine(rtl ? right - length : left, y, rtl ? right : left + length, y, Paints.getProgressPaint(getCurrentTextColor(), Screen.dpf(1.5f)));
    }
  }
}
