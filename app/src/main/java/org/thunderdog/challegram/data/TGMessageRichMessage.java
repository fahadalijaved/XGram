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
package org.thunderdog.challegram.data;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.widget.PageBlockView;

import java.util.ArrayList;
import java.util.List;

/**
 * XGram: a rich message (TdApi.MessageRichMessage, a post made of Instant View blocks) drawn the way official
 * Telegram shows it: the blocks right in the bubble (cover, title, text with its links, lists, quotes, code, photos,
 * collages, collapsible sections). Each block is Telegram X's own Instant View block, hosted by a PageBlockView that is
 * never attached to the window (it only gives the block its width, receivers and touch handling, and passes redraws
 * and relayouts on to the message). Sections open and close in place. Blocks a bubble can't host (embeds, tables,
 * audio) are left out, and an "Open" row then shows the whole post in Instant View. A post that isn't complete yet
 * (isFull == false) is completed with getFullRichMessage.
 */
public class TGMessageRichMessage extends TGMessage {
  /** The blocks' own text inset: the blocks are laid out this much wider on each side, so their text lines up with the bubble's. */
  private static final float BLOCK_INSET = PageBlockRichText.TEXT_HORIZONTAL_OFFSET;
  private static final float OPEN_ROW_HEIGHT = 44f;

  private TdApi.RichMessage richMessage;
  private TdApi.WebPageInstantView instantView;
  private final ArrayList<Entry> entries = new ArrayList<>();
  private boolean hasHiddenBlocks, requestedFull;
  private int blocksWidth, contentHeight;
  private int lastStartX, lastStartY;
  private @Nullable MessageView targetView;

  private static final class Entry {
    final PageBlock block;
    final HostView host;
    int top, height;
    /** Inside an opened section: such blocks have no text inset of their own (Instant View's list gives it), so they
     *  are laid out at the content width without the shift that lines the others' inset up with the bubble. */
    boolean nested;

    Entry (PageBlock block, HostView host) {
      this.block = block;
      this.host = host;
    }
  }

  /** A PageBlockView that is never attached: it hosts one block and passes redraws and relayouts on to the message. */
  private final class HostView extends PageBlockView {
    HostView (Context context, Tdlib tdlib) {
      super(context, tdlib);
    }

    @Override
    public void invalidate () {
      TGMessageRichMessage.this.invalidate();
    }

    @Override
    public void invalidate (Rect dirty) {
      TGMessageRichMessage.this.invalidate();
    }

    @Override
    public void invalidate (int l, int t, int r, int b) {
      TGMessageRichMessage.this.invalidate();
    }

    @Override
    public void postInvalidate () {
      UI.post(TGMessageRichMessage.this::invalidate);
    }

    @Override
    public void postInvalidateOnAnimation () {
      UI.post(TGMessageRichMessage.this::invalidate);
    }

    @Override
    public void requestLayout () {
      super.requestLayout();
      if (entries != null) {
        scheduleRelayout();
      }
    }
  }

  public TGMessageRichMessage (MessagesManager manager, TdApi.Message msg, TdApi.MessageRichMessage content) {
    super(manager, msg);
    setRichMessage(content.message);
    if (entries.isEmpty()) {
      throw new UnsupportedOperationException(); // nothing a bubble can show: the text fallback takes over
    }
  }

  // Blocks

  private static TdApi.PageBlock[] adapt (@Nullable TdApi.PageBlock[] blocks) {
    if (blocks == null) {
      return new TdApi.PageBlock[0];
    }
    final ArrayList<TdApi.PageBlock> out = new ArrayList<>(blocks.length);
    for (TdApi.PageBlock block : blocks) {
      final TdApi.PageBlock adapted = adapt(block);
      if (adapted != null) {
        out.add(adapted);
      }
    }
    return out.toArray(new TdApi.PageBlock[0]);
  }

  private static TdApi.RichText plain (String text) {
    return new TdApi.RichTextPlain(text);
  }

  /** A button row (rich messages): its buttons as links, side by side. */
  private static TdApi.PageBlock buttonsAsLinks (TdApi.PageBlockButtonRow row) {
    final ArrayList<TdApi.RichText> texts = new ArrayList<>();
    for (TdApi.InlineButton button : row.buttons) {
      if (!texts.isEmpty()) {
        texts.add(plain("   ·   "));
      }
      final String url = button.type instanceof TdApi.InlineKeyboardButtonTypeUrl ? ((TdApi.InlineKeyboardButtonTypeUrl) button.type).url : null;
      texts.add(url != null ? new TdApi.RichTextUrl(button.text, url, false) : button.text);
    }
    return new TdApi.PageBlockParagraph(new TdApi.RichTexts(texts.toArray(new TdApi.RichText[0])));
  }

  /** A slideshow (a horizontal pager in Instant View) becomes a collage, which a bubble can draw; nested blocks too. */
  private static TdApi.PageBlock adapt (TdApi.PageBlock block) {
    if (block == null) {
      return null;
    }
    switch (block.getConstructor()) {
      case TdApi.PageBlockSlideshow.CONSTRUCTOR: {
        TdApi.PageBlockSlideshow slideshow = (TdApi.PageBlockSlideshow) block;
        return new TdApi.PageBlockCollage(adapt(slideshow.blocks), slideshow.caption);
      }
      case TdApi.PageBlockDetails.CONSTRUCTOR: {
        TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block;
        return new TdApi.PageBlockDetails(details.header, adapt(details.blocks), details.isOpen);
      }
      case TdApi.PageBlockBlockQuote.CONSTRUCTOR: {
        TdApi.PageBlockBlockQuote quote = (TdApi.PageBlockBlockQuote) block;
        return new TdApi.PageBlockBlockQuote(adapt(quote.blocks), quote.credit);
      }
      case TdApi.PageBlockList.CONSTRUCTOR: {
        TdApi.PageBlockList list = (TdApi.PageBlockList) block;
        TdApi.PageBlockListItem[] items = new TdApi.PageBlockListItem[list.items.length];
        for (int i = 0; i < items.length; i++) {
          TdApi.PageBlockListItem item = list.items[i];
          items[i] = new TdApi.PageBlockListItem(item.label, adapt(item.blocks), item.hasCheckbox, item.isChecked, item.value, item.type);
        }
        return new TdApi.PageBlockList(items);
      }
      case TdApi.PageBlockCover.CONSTRUCTOR:
        return new TdApi.PageBlockCover(adapt(((TdApi.PageBlockCover) block).cover));
      // Block types newer than Telegram X's Instant View, as the nearest ones it draws
      case TdApi.PageBlockSectionHeading.CONSTRUCTOR: {
        TdApi.PageBlockSectionHeading heading = (TdApi.PageBlockSectionHeading) block;
        return heading.size <= 1 ? new TdApi.PageBlockHeader(heading.text) : new TdApi.PageBlockSubheader(heading.text);
      }
      case TdApi.PageBlockExpandableBlockQuote.CONSTRUCTOR: {
        TdApi.PageBlockExpandableBlockQuote quote = (TdApi.PageBlockExpandableBlockQuote) block;
        return new TdApi.PageBlockBlockQuote(new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(quote.text)}, quote.credit);
      }
      case TdApi.PageBlockThinking.CONSTRUCTOR:
        return new TdApi.PageBlockDetails(plain("💭"), new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(((TdApi.PageBlockThinking) block).text)}, false);
      case TdApi.PageBlockMathematicalExpression.CONSTRUCTOR:
        return new TdApi.PageBlockPreformatted(plain(((TdApi.PageBlockMathematicalExpression) block).expression), "latex");
      case TdApi.PageBlockDocument.CONSTRUCTOR: {
        TdApi.PageBlockDocument document = (TdApi.PageBlockDocument) block;
        final String name = document.document != null && document.document.fileName != null && !document.document.fileName.isEmpty() ? document.document.fileName : "File";
        return new TdApi.PageBlockParagraph(plain("📄 " + name));
      }
      case TdApi.PageBlockButtonRow.CONSTRUCTOR:
        return buttonsAsLinks((TdApi.PageBlockButtonRow) block);
      case TdApi.PageBlockUnsupported.CONSTRUCTOR:
        return null;
      default:
        return block;
    }
  }

  private static int modeOf (int viewType) {
    switch (viewType) {
      case ListItem.TYPE_PAGE_BLOCK:
        return 0;
      case ListItem.TYPE_PAGE_BLOCK_MEDIA:
        return PageBlockView.MODE_IMAGE;
      case ListItem.TYPE_PAGE_BLOCK_GIF:
        return PageBlockView.MODE_GIF;
      case ListItem.TYPE_PAGE_BLOCK_COLLAGE:
        return PageBlockView.MODE_COLLAGE;
      case ListItem.TYPE_PAGE_BLOCK_AVATAR:
        return PageBlockView.MODE_AVATAR;
      default:
        return -1; // embeds, tables, files, slideshows: Instant View only
    }
  }

  private void setRichMessage (@Nullable TdApi.RichMessage message) {
    if (message == null || message.blocks == null) {
      return;
    }
    this.richMessage = message;
    this.instantView = new TdApi.WebPageInstantView(adapt(message.blocks), 0, 2, message.isRtl, message.isFull, null);
    destroyEntries();
    hasHiddenBlocks = false;
    List<PageBlock> blocks;
    try {
      blocks = PageBlock.parse(controller(), "", instantView, null, null, null);
    } catch (Throwable t) {
      android.util.Log.w("XgramRich", "whole post failed, per block now: " + blockTypes(instantView.blocks), t);
      // One block Telegram X can't read must not cost the whole post: each block on its own, the bad ones left out
      blocks = new ArrayList<>();
      for (TdApi.PageBlock block : instantView.blocks) {
        try {
          blocks.addAll(PageBlock.parse(controller(), "", new TdApi.WebPageInstantView(new TdApi.PageBlock[] {block}, 0, 2, message.isRtl, message.isFull, null), null, null, null));
        } catch (Throwable blockError) {
          android.util.Log.w("XgramRich", "left out " + block.getClass().getSimpleName(), blockError);
          hasHiddenBlocks = true;
        }
      }
    }
    addEntries(entries.size(), blocks);
    if (entries.isEmpty()) {
      android.util.Log.w("XgramRich", "nothing drawable in " + blockTypes(instantView.blocks));
    }
    if (!message.isFull && !requestedFull) {
      requestedFull = true;
      tdlib().send(new TdApi.GetFullRichMessage(msg.chatId, msg.id), (result, error) -> {
        if (result != null) {
          runOnUiThreadOptional(() -> {
            setRichMessage(result);
            rebuildAndUpdateContent();
          });
        }
      });
    }
  }

  private void addEntries (int index, List<PageBlock> blocks) {
    for (PageBlock block : blocks) {
      int mode = modeOf(block.getRelatedViewType());
      if (mode == -1) {
        hasHiddenBlocks = true;
        continue;
      }
      HostView host = new HostView(controller().context(), tdlib());
      if (mode != 0) {
        host.initWithMode(mode);
      }
      host.setBlock(block);
      if (targetView != null) {
        host.attach();
      }
      entries.add(index++, new Entry(block, host));
    }
  }

  private void destroyEntries () {
    for (Entry entry : entries) {
      entry.host.setBlock(null);
      entry.host.performDestroy();
    }
    entries.clear();
  }

  private boolean relayoutScheduled;

  private void scheduleRelayout () {
    if (!relayoutScheduled) {
      relayoutScheduled = true;
      UI.post(() -> {
        relayoutScheduled = false;
        rebuildAndUpdateContent();
      });
    }
  }

  // Layout

  private boolean isNested (Entry entry) {
    for (Entry other : entries) {
      if (other == entry) {
        return false;
      }
      if (other.block.getOriginalBlock() instanceof TdApi.PageBlockDetails && entry.block.isChildOf(other.block)) {
        return true;
      }
    }
    return false;
  }

  private int widthOf (Entry entry) {
    return entry.nested ? blocksWidth - Screen.dp(BLOCK_INSET) * 2 : blocksWidth;
  }

  private int leftOf (Entry entry, int startX) {
    return entry.nested ? startX : startX - Screen.dp(BLOCK_INSET);
  }

  @Override
  protected void buildContent (int maxWidth) {
    blocksWidth = maxWidth + Screen.dp(BLOCK_INSET) * 2;
    int top = 0;
    for (Entry entry : entries) {
      entry.nested = isNested(entry);
      final int width = widthOf(entry);
      entry.top = top;
      entry.height = entry.block.getHeight(entry.host, width);
      entry.host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(entry.height, View.MeasureSpec.EXACTLY));
      entry.host.layout(0, 0, width, entry.height);
      top += entry.height;
    }
    if (hasHiddenBlocks) {
      top += Screen.dp(OPEN_ROW_HEIGHT);
    }
    contentHeight = Math.max(top, Screen.dp(20f));
  }

  @Override
  protected int getContentWidth () {
    return Math.max(0, blocksWidth - Screen.dp(BLOCK_INSET) * 2);
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  // Drawing

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    lastStartX = startX;
    lastStartY = startY;
    final int saveCount = Views.save(c);
    if (useBubble() && !bubbleClipPathRect.isEmpty()) {
      c.clipPath(getBubbleClipPath()); // photos end at the bubble's content edge and take its corners
    }
    final Rect visible = c.getClipBounds();
    for (Entry entry : entries) {
      final int top = startY + entry.top;
      if (top > visible.bottom || top + entry.height < visible.top) {
        continue;
      }
      final int save = Views.save(c);
      c.translate(leftOf(entry, startX), top);
      entry.host.draw(c);
      Views.restore(c, save);
    }
    if (hasHiddenBlocks) {
      final int rowTop = startY + contentHeight - Screen.dp(OPEN_ROW_HEIGHT);
      final String label = Lang.getString(R.string.XgramRichMessageOpen);
      final android.text.TextPaint paint = org.thunderdog.challegram.tool.Paints.getBoldPaint15(false);
      paint.setColor(org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLink));
      c.drawText(label, startX, rowTop + Screen.dp(OPEN_ROW_HEIGHT) / 2f + Screen.dp(5f), paint);
    }
    Views.restore(c, saveCount);
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (attached) {
      targetView = view;
      for (Entry entry : entries) {
        entry.host.attach();
      }
    } else if (targetView == view) {
      targetView = null;
      for (Entry entry : entries) {
        entry.host.detach();
      }
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    super.onMessageContainerDestroyed();
    destroyEntries();
  }

  // Touches

  private @Nullable Entry touchEntry;
  private float touchStartX, touchStartY;
  private boolean touchMayClick, touchOpenRow;

  private @Nullable Entry entryAt (float x, float y) {
    final float localY = y - lastStartY;
    for (Entry entry : entries) {
      if (localY >= entry.top && localY < entry.top + entry.height) {
        return entry;
      }
    }
    return null;
  }

  private MotionEvent toBlock (MotionEvent e, Entry entry) {
    MotionEvent copy = MotionEvent.obtain(e);
    copy.offsetLocation(-leftOf(entry, lastStartX), -(lastStartY + entry.top));
    return copy;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        touchStartX = e.getX();
        touchStartY = e.getY();
        touchEntry = entryAt(e.getX(), e.getY());
        touchOpenRow = hasHiddenBlocks && touchEntry == null && e.getY() >= lastStartY + contentHeight - Screen.dp(OPEN_ROW_HEIGHT) && e.getY() < lastStartY + contentHeight && e.getX() >= lastStartX && e.getX() < lastStartX + getContentWidth();
        touchMayClick = touchOpenRow || (touchEntry != null && touchEntry.block.isClickable());
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        final int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        if (Math.abs(e.getX() - touchStartX) > slop || Math.abs(e.getY() - touchStartY) > slop) {
          touchMayClick = false;
        }
        break;
      }
    }
    boolean handled = false;
    if (touchEntry != null) {
      MotionEvent blockEvent = toBlock(e, touchEntry);
      handled = touchEntry.block.onTouchEvent(touchEntry.host, blockEvent);
      blockEvent.recycle();
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP && touchMayClick && !handled) {
      final Entry entry = touchEntry;
      touchEntry = null;
      touchMayClick = false;
      if (touchOpenRow) {
        openInstantView();
        return true;
      }
      if (entry != null && onBlockClick(entry)) {
        return true;
      }
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      touchEntry = null;
      touchMayClick = false;
    }
    return handled || touchMayClick || super.onTouchEvent(view, e);
  }

  /** A tap on a clickable block: a section opens or closes in place; media opens the viewer. */
  private boolean onBlockClick (Entry entry) {
    final PageBlock block = entry.block;
    if (block.onClick(entry.host, false)) {
      return true;
    }
    if (!(block.getOriginalBlock() instanceof TdApi.PageBlockDetails) || !(block instanceof PageBlockRichText)) {
      return false;
    }
    final int index = entries.indexOf(entry);
    if (index == -1) {
      return false;
    }
    final boolean isOpen = ((PageBlockRichText) block).toggleDetailsOpened();
    if (isOpen) {
      final List<PageBlock> children;
      try {
        children = PageBlock.parse(controller(), "", instantView, block, null, null);
      } catch (Throwable t) {
        ((PageBlockRichText) block).toggleDetailsOpened();
        return false;
      }
      addEntries(index + 1, children);
    } else {
      int i = index + 1;
      while (i < entries.size() && entries.get(i).block.isChildOf(block)) {
        Entry child = entries.remove(i);
        child.host.setBlock(null);
        child.host.performDestroy();
      }
    }
    rebuildAndUpdateContent();
    return true;
  }

  private void openInstantView () {
    if (instantView == null) {
      return;
    }
    TdApi.LinkPreview preview = new TdApi.LinkPreview();
    String url = tdlib().tMeMessageUrl(tdlib().chatUsername(msg.chatId), msg.id);
    preview.url = url != null ? url : "";
    preview.displayUrl = preview.url;
    preview.siteName = tdlib().chatTitle(msg.chatId);
    InstantViewController c = new InstantViewController(controller().context(), tdlib());
    c.setArguments(new InstantViewController.Args(preview, instantView, null));
    controller().navigateTo(c);
  }

  private static String blockTypes (TdApi.PageBlock[] blocks) {
    StringBuilder b = new StringBuilder();
    for (TdApi.PageBlock block : blocks) {
      if (b.length() > 0) {
        b.append(", ");
      }
      b.append(block != null ? block.getClass().getSimpleName() : "null");
    }
    return b.toString();
  }
}
